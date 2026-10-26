import { skipToken, useQuery } from '@tanstack/react-query'
import { getWorkspace, listWorkspaces } from './workspaceApi.ts'
import { WORKSPACE_LIST_SIZE } from './workspaceRules.ts'

export const workspaceKeys = {
  all: ['workspaces'] as const,
  list: () => [...workspaceKeys.all, 'list'] as const,
  detail: (workspaceId: number) => [...workspaceKeys.all, workspaceId] as const,
}

export function useWorkspacesQuery() {
  return useQuery({
    queryKey: workspaceKeys.list(),
    queryFn: () => listWorkspaces({ size: WORKSPACE_LIST_SIZE }),
  })
}

export function useWorkspaceQuery(workspaceId: number | null) {
  return useQuery({
    queryKey: workspaceKeys.detail(workspaceId ?? 0),
    queryFn: workspaceId === null ? skipToken : () => getWorkspace(workspaceId),
  })
}
