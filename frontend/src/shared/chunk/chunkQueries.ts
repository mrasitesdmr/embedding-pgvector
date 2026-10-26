import { skipToken, useQuery } from '@tanstack/react-query'
import { getChunk } from './chunkApi.ts'

export const SOURCES_QUERY_SCOPE = 'sources'

export function chunkKey(workspaceId: number, sourceId: number, chunkId: number) {
  return [SOURCES_QUERY_SCOPE, workspaceId, sourceId, 'chunk', chunkId] as const
}

export function useChunkQuery(workspaceId: number, sourceId: number | null, chunkId: number | null) {
  return useQuery({
    queryKey: chunkKey(workspaceId, sourceId ?? 0, chunkId ?? 0),
    queryFn: sourceId === null || chunkId === null ? skipToken : () => getChunk(workspaceId, sourceId, chunkId),
    staleTime: 0,
  })
}
