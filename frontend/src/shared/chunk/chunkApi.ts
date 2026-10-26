import { request } from '../api/httpClient.ts'
import type { ChunkResponse } from './types.ts'

export function getChunk(workspaceId: number, sourceId: number, chunkId: number): Promise<ChunkResponse> {
  return request(`/api/workspaces/${workspaceId}/sources/${sourceId}/chunks/${chunkId}`)
}
