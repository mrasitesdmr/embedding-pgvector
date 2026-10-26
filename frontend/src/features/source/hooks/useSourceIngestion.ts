import { useIngestionJobQuery } from '../api/sourceQueries.ts'
import { freshestJob, isSourceIngesting } from '../model/ingestionRules.ts'
import type { IngestionJobResponse, SourceResponse } from '../model/types.ts'

export type SourceIngestion = {
  job: IngestionJobResponse | null
  isIngesting: boolean
}

export function useSourceIngestion(workspaceId: number, source: SourceResponse): SourceIngestion {
  const latestJob = source.latestJob ?? null
  const cachedJob = useIngestionJobQuery(workspaceId, latestJob)
  const job = freshestJob(cachedJob, latestJob)
  return { job, isIngesting: isSourceIngesting(source, job) }
}
