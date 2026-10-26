import {
  queryOptions,
  skipToken,
  useInfiniteQuery,
  useMutation,
  useQueries,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query'
import type { QueryClient, QueryKey } from '@tanstack/react-query'
import { ApiError, isApiErrorWithCode } from '../../../shared/api/ApiError.ts'
import { SOURCES_QUERY_SCOPE } from '../../../shared/chunk/chunkQueries.ts'
import { workspaceKeys } from '../../../shared/workspace/workspaceQueries.ts'
import { INGESTION_POLL_INTERVAL_MS, isJobActive } from '../model/ingestionRules.ts'
import type { CreateSourceInput, IngestionJobResponse, UploadSourceVersionInput } from '../model/types.ts'
import {
  createSource,
  getIngestionJob,
  getSource,
  listChunks,
  listSources,
  uploadSourceVersion,
} from './sourceApi.ts'

const INGESTION_JOBS_SEGMENT = 'ingestion-jobs'

export const sourceKeys = {
  all: (workspaceId: number) => [SOURCES_QUERY_SCOPE, workspaceId] as const,
  list: (workspaceId: number) => [...sourceKeys.all(workspaceId), 'list'] as const,
  detail: (workspaceId: number, sourceId: number) => [...sourceKeys.all(workspaceId), sourceId] as const,
  chunks: (workspaceId: number, sourceId: number) => [...sourceKeys.detail(workspaceId, sourceId), 'chunks'] as const,
  ingestionJob: (workspaceId: number, jobId: number) =>
    [...sourceKeys.all(workspaceId), INGESTION_JOBS_SEGMENT, jobId] as const,
}

function isIngestionJobKey(queryKey: QueryKey): boolean {
  return queryKey[2] === INGESTION_JOBS_SEGMENT
}

export function isClientError(error: unknown): boolean {
  return error instanceof ApiError && error.status >= 400 && error.status < 500
}

export function invalidateSourceData(queryClient: QueryClient, workspaceId: number): Promise<void> {
  return queryClient.invalidateQueries({
    queryKey: sourceKeys.all(workspaceId),
    predicate: (query) => !isIngestionJobKey(query.queryKey),
  })
}

export function invalidateSourceListAndDetail(
  queryClient: QueryClient,
  workspaceId: number,
  sourceId: number,
): Promise<unknown> {
  return Promise.all([
    queryClient.invalidateQueries({ queryKey: sourceKeys.list(workspaceId) }),
    queryClient.invalidateQueries({ queryKey: sourceKeys.detail(workspaceId, sourceId), exact: true }),
  ])
}

export function useSourcesQuery(workspaceId: number) {
  return useQuery({
    queryKey: sourceKeys.list(workspaceId),
    queryFn: () => listSources(workspaceId),
  })
}

export function useSourceQuery(workspaceId: number, sourceId: number | null) {
  return useQuery({
    queryKey: sourceKeys.detail(workspaceId, sourceId ?? 0),
    queryFn: sourceId === null ? skipToken : () => getSource(workspaceId, sourceId),
  })
}

export function useChunksInfiniteQuery(workspaceId: number, sourceId: number | null) {
  return useInfiniteQuery({
    queryKey: sourceKeys.chunks(workspaceId, sourceId ?? 0),
    queryFn: sourceId === null ? skipToken : ({ pageParam }) => listChunks(workspaceId, sourceId, pageParam),
    initialPageParam: 0,
    getNextPageParam: (lastPage) => (lastPage.page + 1 < lastPage.totalPages ? lastPage.page + 1 : undefined),
  })
}

export function useCreateSourceMutation(workspaceId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: CreateSourceInput) => createSource(workspaceId, input),
    onSuccess: (job) => {
      queryClient.setQueryData(sourceKeys.ingestionJob(workspaceId, job.jobId), job)
      void queryClient.invalidateQueries({ queryKey: sourceKeys.list(workspaceId) })
      void queryClient.invalidateQueries({ queryKey: workspaceKeys.all })
    },
  })
}

export function useUploadSourceVersionMutation(workspaceId: number, sourceId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: UploadSourceVersionInput) => uploadSourceVersion(workspaceId, sourceId, input),
    onSuccess: (job) => {
      queryClient.setQueryData(sourceKeys.ingestionJob(workspaceId, job.jobId), job)
      void invalidateSourceListAndDetail(queryClient, workspaceId, sourceId)
    },
    onError: (error) => {
      if (isApiErrorWithCode(error, 'INGESTION_IN_PROGRESS')) {
        void invalidateSourceListAndDetail(queryClient, workspaceId, sourceId)
      }
    },
  })
}

export function useIngestionJobsPolling(workspaceId: number, jobs: IngestionJobResponse[]) {
  return useQueries({
    queries: jobs.map((job) =>
      queryOptions({
        queryKey: sourceKeys.ingestionJob(workspaceId, job.jobId),
        queryFn: () => getIngestionJob(workspaceId, job.jobId),
        initialData: job,
        staleTime: INGESTION_POLL_INTERVAL_MS,
        refetchInterval: (query) =>
          !isClientError(query.state.error) && isJobActive(query.state.data) ? INGESTION_POLL_INTERVAL_MS : false,
        refetchIntervalInBackground: true,
        retry: false,
      }),
    ),
  })
}

export function useIngestionJobQuery(workspaceId: number, latestJob: IngestionJobResponse | null) {
  const jobQuery = useQuery({
    queryKey: sourceKeys.ingestionJob(workspaceId, latestJob?.jobId ?? 0),
    queryFn: skipToken,
    initialData: latestJob ?? undefined,
  })
  return jobQuery.data ?? latestJob
}
