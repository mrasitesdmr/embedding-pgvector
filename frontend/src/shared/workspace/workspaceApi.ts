import { request, withQuery } from '../api/httpClient.ts'
import type { PageParams, PageResponse } from '../api/pagination.ts'
import type { WorkspaceResponse } from './types.ts'

export function listWorkspaces(params: PageParams = {}): Promise<PageResponse<WorkspaceResponse>> {
  return request(withQuery('/api/workspaces', { page: params.page, size: params.size }))
}

export function getWorkspace(workspaceId: number): Promise<WorkspaceResponse> {
  return request(`/api/workspaces/${workspaceId}`)
}
