import type { WorkspaceResponse } from './types.ts'

export const DEFAULT_WORKSPACE_ID = 42
export const WORKSPACE_LIST_SIZE = 100

export function parseWorkspaceId(param: string | undefined): number | null {
  return parsePositiveId(param)
}

export function parsePositiveId(param: string | null | undefined): number | null {
  if (param === null || param === undefined || !/^[0-9]+$/.test(param)) {
    return null
  }
  const id = Number(param)
  return Number.isSafeInteger(id) && id > 0 ? id : null
}

export const HIGHLIGHTED_CHUNK_PARAM = 'parca'
export const UPLOAD_SECTION = 'belge-yukle'
export const UPLOAD_SOURCE_PARAM = 'kaynak'

export function workspacePath(workspaceId: number, section: string): string {
  return `/w/${workspaceId}/${section}`
}

export function workspaceHomePath(workspaceId: number): string {
  return `/w/${workspaceId}`
}

export function uploadPath(workspaceId: number, sourceId?: number): string {
  const path = workspacePath(workspaceId, UPLOAD_SECTION)
  return sourceId === undefined ? path : `${path}?${UPLOAD_SOURCE_PARAM}=${sourceId}`
}

export function homeSection(workspace: WorkspaceResponse): string {
  return workspace.sourceCount === 0 ? UPLOAD_SECTION : 'arama'
}

export function shouldShowWorkspaceSelect(totalElements: number): boolean {
  return totalElements >= 2
}

export function sourceDetailPath(workspaceId: number, sourceId: number, highlightedChunkId?: number): string {
  const path = `/w/${workspaceId}/kaynaklar/${sourceId}`
  return highlightedChunkId === undefined ? path : `${path}?${HIGHLIGHTED_CHUNK_PARAM}=${highlightedChunkId}`
}

export function embeddingModelsLabel(workspace: WorkspaceResponse): string {
  return workspace.embeddingModels.length === 0 ? '—' : workspace.embeddingModels.join(', ')
}
