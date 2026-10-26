import { useId } from 'react'
import type { ChangeEvent } from 'react'
import { useLocation, useNavigate } from 'react-router'
import hidden from '../shared/components/visuallyHidden.module.css'
import { formatInteger } from '../shared/format/numberFormat.ts'
import type { WorkspaceResponse } from '../shared/workspace/types.ts'
import { useWorkspacesQuery } from '../shared/workspace/workspaceQueries.ts'
import {
  UPLOAD_SECTION,
  parseWorkspaceId,
  shouldShowWorkspaceSelect,
  workspacePath,
} from '../shared/workspace/workspaceRules.ts'
import styles from './WorkspaceSelect.module.css'

const SECTION_PATTERN = /^\/w\/[^/]+\/([^/]+)/

function targetSection(pathname: string): string {
  const section = SECTION_PATTERN.exec(pathname)?.[1]
  if (section === 'arama-kayitlari') {
    return 'arama-kayitlari'
  }
  if (section === 'kaynaklar') {
    return 'kaynaklar'
  }
  if (section === UPLOAD_SECTION) {
    return UPLOAD_SECTION
  }
  return 'arama'
}

function optionLabel(workspace: WorkspaceResponse): string {
  return `${workspace.name} · ${formatInteger(workspace.sourceCount)} kaynak · ${formatInteger(workspace.chunkCount)} parça`
}

type WorkspaceSelectProps = { workspaceId: number | null }

export function WorkspaceSelect({ workspaceId }: WorkspaceSelectProps) {
  const selectId = useId()
  const navigate = useNavigate()
  const { pathname } = useLocation()
  const workspacesQuery = useWorkspacesQuery()
  const workspacePage = workspacesQuery.data

  if (workspacePage === undefined || !shouldShowWorkspaceSelect(workspacePage.totalElements)) {
    return null
  }

  const workspaces = workspacePage.content
  const current = workspaces.find((workspace) => workspace.workspaceId === workspaceId)
  const initial = current?.name.charAt(0).toLocaleUpperCase('tr') ?? '?'

  function handleChange(event: ChangeEvent<HTMLSelectElement>) {
    const nextWorkspaceId = parseWorkspaceId(event.target.value)
    if (nextWorkspaceId !== null && nextWorkspaceId !== workspaceId) {
      void navigate(workspacePath(nextWorkspaceId, targetSection(pathname)))
    }
  }

  return (
    <div className={styles.workspace}>
      <span className={styles.avatar} aria-hidden="true">
        {initial}
      </span>
      <label htmlFor={selectId} className={hidden.visuallyHidden}>
        Çalışma alanı
      </label>
      <select
        id={selectId}
        className={styles.select}
        value={current === undefined ? '' : String(current.workspaceId)}
        onChange={handleChange}
      >
        {current === undefined ? (
          <option value="" disabled>
            Çalışma alanı seç
          </option>
        ) : null}
        {workspaces.map((workspace) => (
          <option key={workspace.workspaceId} value={String(workspace.workspaceId)}>
            {optionLabel(workspace)}
          </option>
        ))}
      </select>
    </div>
  )
}
