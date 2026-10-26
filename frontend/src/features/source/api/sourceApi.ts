import { request, withQuery } from '../../../shared/api/httpClient.ts'
import type { PageResponse } from '../../../shared/api/pagination.ts'
import { CHUNK_PAGE_SIZE, SOURCE_LIST_SIZE } from '../model/sourceRules.ts'
import { normalizeSourceName, normalizeVersionLabel } from '../model/uploadRules.ts'
import type { ChunkResponse } from '../../../shared/chunk/types.ts'
import type {
  CreateSourceInput,
  IngestionJobResponse,
  SourceResponse,
  UploadSourceVersionInput,
} from '../model/types.ts'

export function listSources(workspaceId: number): Promise<PageResponse<SourceResponse>> {
  return request(withQuery(`/api/workspaces/${workspaceId}/sources`, { page: 0, size: SOURCE_LIST_SIZE }))
}

export function getSource(workspaceId: number, sourceId: number): Promise<SourceResponse> {
  return request(`/api/workspaces/${workspaceId}/sources/${sourceId}`)
}

export function listChunks(workspaceId: number, sourceId: number, page: number): Promise<PageResponse<ChunkResponse>> {
  return request(
    withQuery(`/api/workspaces/${workspaceId}/sources/${sourceId}/chunks`, { page, size: CHUNK_PAGE_SIZE }),
  )
}

export function createSource(workspaceId: number, { name, file }: CreateSourceInput): Promise<IngestionJobResponse> {
  const body = new FormData()
  body.append('name', normalizeSourceName(name))
  body.append('file', file)
  return request(`/api/workspaces/${workspaceId}/sources`, { method: 'POST', body })
}

export function uploadSourceVersion(
  workspaceId: number,
  sourceId: number,
  { file, version }: UploadSourceVersionInput,
): Promise<IngestionJobResponse> {
  const body = new FormData()
  body.append('file', file)
  const versionLabel = normalizeVersionLabel(version)
  if (versionLabel !== '') {
    body.append('version', versionLabel)
  }
  return request(`/api/workspaces/${workspaceId}/sources/${sourceId}/versions`, { method: 'POST', body })
}

export function getIngestionJob(workspaceId: number, jobId: number): Promise<IngestionJobResponse> {
  return request(`/api/workspaces/${workspaceId}/ingestion-jobs/${jobId}`)
}
