import { useParams } from 'react-router'
import type { WorkspaceResponse } from './types.ts'
import { useWorkspaceQuery } from './workspaceQueries.ts'
import { parseWorkspaceId } from './workspaceRules.ts'

export type CurrentWorkspace = { workspaceId: number; workspace: WorkspaceResponse }

export function useCurrentWorkspace(): CurrentWorkspace {
  const { workspaceId: param } = useParams()
  const workspaceId = parseWorkspaceId(param)
  const { data: workspace } = useWorkspaceQuery(workspaceId)
  if (workspaceId === null || workspace === undefined) {
    throw new Error('useCurrentWorkspace must be used inside a loaded WorkspaceLayout')
  }
  return { workspaceId, workspace }
}
