import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { SearchChunksRequest } from '../model/types.ts'
import { getSearchEventStats, listPopularQueries, listSearchEvents, searchChunks } from './searchApi.ts'

export const searchKeys = {
  all: (workspaceId: number) => ['search', workspaceId] as const,
  events: (workspaceId: number, page: number) => [...searchKeys.all(workspaceId), 'events', page] as const,
  stats: (workspaceId: number) => [...searchKeys.all(workspaceId), 'stats'] as const,
  popularQueries: (workspaceId: number) => [...searchKeys.all(workspaceId), 'popular-queries'] as const,
}

export function useSearchChunksMutation(workspaceId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (searchRequest: SearchChunksRequest) => searchChunks(workspaceId, searchRequest),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: searchKeys.all(workspaceId) }),
  })
}

export function useSearchEventsQuery(workspaceId: number, page: number) {
  return useQuery({
    queryKey: searchKeys.events(workspaceId, page),
    queryFn: () => listSearchEvents(workspaceId, page),
    placeholderData: keepPreviousData,
  })
}

export function useSearchEventStatsQuery(workspaceId: number) {
  return useQuery({
    queryKey: searchKeys.stats(workspaceId),
    queryFn: () => getSearchEventStats(workspaceId),
  })
}

export function usePopularQueriesQuery(workspaceId: number) {
  return useQuery({
    queryKey: searchKeys.popularQueries(workspaceId),
    queryFn: () => listPopularQueries(workspaceId),
  })
}
