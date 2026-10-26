import { useEffect, useEffectEvent, useMemo, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { useToast } from '../../../shared/toast/useToast.ts'
import { workspaceKeys } from '../../../shared/workspace/workspaceQueries.ts'
import {
  invalidateSourceData,
  invalidateSourceListAndDetail,
  isClientError,
  sourceKeys,
  useIngestionJobsPolling,
  useSourcesQuery,
} from '../api/sourceQueries.ts'
import { RecentlyCompletedSourceIdsContext } from '../hooks/useRecentlyCompletedSourceIds.ts'
import { COMPLETED_HIGHLIGHT_MS, ingestionDoneMessage, isJobActive } from '../model/ingestionRules.ts'
import type { IngestionJobResponse, SourceResponse } from '../model/types.ts'

const NO_SOURCES: SourceResponse[] = []
const NO_SOURCE_IDS: ReadonlySet<number> = new Set()

type IngestionWatcherProviderProps = {
  workspaceId: number
  children: ReactNode
}

export function IngestionWatcherProvider({ workspaceId, children }: IngestionWatcherProviderProps) {
  const queryClient = useQueryClient()
  const { showToast } = useToast()
  const sourcesQuery = useSourcesQuery(workspaceId)
  const sources = sourcesQuery.data?.content ?? NO_SOURCES
  const activeJobs = useMemo(
    () =>
      sources.flatMap((source) =>
        source.latestJob !== null && source.latestJob !== undefined && isJobActive(source.latestJob)
          ? [source.latestJob]
          : [],
      ),
    [sources],
  )
  const jobResults = useIngestionJobsPolling(workspaceId, activeJobs)
  const [recentlyCompletedSourceIds, setRecentlyCompletedSourceIds] = useState<ReadonlySet<number>>(NO_SOURCE_IDS)
  const seenActiveJobIdsRef = useRef(new Set<number>())
  const finishedJobIdsRef = useRef(new Set<number>())
  const missingJobIdsRef = useRef(new Set<number>())
  const highlightTimersRef = useRef(new Set<number>())

  useEffect(() => {
    const timers = highlightTimersRef.current
    return () => {
      for (const timer of timers) {
        window.clearTimeout(timer)
      }
      timers.clear()
    }
  }, [])

  function highlightSource(sourceId: number) {
    setRecentlyCompletedSourceIds((current) => new Set(current).add(sourceId))
    const timer = window.setTimeout(() => {
      highlightTimersRef.current.delete(timer)
      setRecentlyCompletedSourceIds((current) => {
        const next = new Set(current)
        next.delete(sourceId)
        return next
      })
    }, COMPLETED_HIGHLIGHT_MS)
    highlightTimersRef.current.add(timer)
  }

  function handleJobFinished(job: IngestionJobResponse) {
    if (job.stage === 'DONE') {
      const sourceName = sources.find((source) => source.sourceId === job.sourceId)?.name ?? job.fileName
      showToast(ingestionDoneMessage(sourceName, job))
      highlightSource(job.sourceId)
      void invalidateSourceData(queryClient, workspaceId)
    } else {
      void invalidateSourceListAndDetail(queryClient, workspaceId, job.sourceId)
    }
    void queryClient.invalidateQueries({ queryKey: workspaceKeys.all })
  }

  function wasSeenActive(job: IngestionJobResponse): boolean {
    if (seenActiveJobIdsRef.current.has(job.jobId)) {
      return true
    }
    const cachedJob = queryClient.getQueryData<IngestionJobResponse>(sourceKeys.ingestionJob(workspaceId, job.jobId))
    return isJobActive(cachedJob)
  }

  const trackJobs = useEffectEvent(() => {
    const polledJobs = jobResults.flatMap((result) => (result.data === undefined ? [] : [result.data]))
    const listedJobs = sources.flatMap((source) =>
      source.latestJob === null || source.latestJob === undefined ? [] : [source.latestJob],
    )
    for (const job of [...polledJobs, ...listedJobs]) {
      if (isJobActive(job)) {
        seenActiveJobIdsRef.current.add(job.jobId)
        continue
      }
      if (finishedJobIdsRef.current.has(job.jobId) || !wasSeenActive(job)) {
        continue
      }
      finishedJobIdsRef.current.add(job.jobId)
      queryClient.setQueryData(sourceKeys.ingestionJob(workspaceId, job.jobId), job)
      handleJobFinished(job)
    }
    for (const result of jobResults) {
      const jobId = result.data?.jobId
      if (jobId !== undefined && isClientError(result.error) && !missingJobIdsRef.current.has(jobId)) {
        missingJobIdsRef.current.add(jobId)
        void queryClient.invalidateQueries({ queryKey: sourceKeys.list(workspaceId) })
      }
    }
  })

  useEffect(() => {
    trackJobs()
  }, [sources, jobResults])

  return <RecentlyCompletedSourceIdsContext value={recentlyCompletedSourceIds}>{children}</RecentlyCompletedSourceIdsContext>
}
