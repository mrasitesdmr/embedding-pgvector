import { QueryClient } from '@tanstack/react-query'
import { ApiError } from '../shared/api/ApiError.ts'

const QUERY_RETRY_COUNT = 2
const QUERY_STALE_TIME_MS = 30_000

export function shouldRetryQuery(failureCount: number, error: Error): boolean {
  if (error instanceof ApiError && error.status >= 400 && error.status < 500) {
    return false
  }
  return failureCount < QUERY_RETRY_COUNT
}

export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        retry: shouldRetryQuery,
        staleTime: QUERY_STALE_TIME_MS,
        refetchOnWindowFocus: false,
      },
    },
  })
}
