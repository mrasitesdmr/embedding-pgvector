import { Navigate } from 'react-router'
import { useCurrentWorkspace } from '../shared/workspace/useCurrentWorkspace.ts'
import { homeSection } from '../shared/workspace/workspaceRules.ts'

export function WorkspaceHomeRedirect() {
  const { workspace } = useCurrentWorkspace()
  return <Navigate to={homeSection(workspace)} replace />
}
