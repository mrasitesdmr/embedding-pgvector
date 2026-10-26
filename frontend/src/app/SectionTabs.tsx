import { NavLink } from 'react-router'
import { useSearchEventStatsQuery } from '../features/search/api/searchQueries.ts'
import { formatInteger } from '../shared/format/numberFormat.ts'
import { UPLOAD_SECTION, workspacePath } from '../shared/workspace/workspaceRules.ts'
import styles from './SectionTabs.module.css'

type SectionTabsProps = { workspaceId: number }

function tabClassName({ isActive }: { isActive: boolean }): string {
  return isActive ? `${styles.tab} ${styles.active}` : styles.tab
}

export function SectionTabs({ workspaceId }: SectionTabsProps) {
  const statsQuery = useSearchEventStatsQuery(workspaceId)
  const totalCount = statsQuery.data?.totalCount

  return (
    <nav className={styles.tabs} aria-label="Bölümler">
      <NavLink className={tabClassName} to={workspacePath(workspaceId, 'arama')} end>
        <SearchIcon />
        Arama
      </NavLink>
      <NavLink className={tabClassName} to={workspacePath(workspaceId, 'arama-kayitlari')} end>
        <ListIcon />
        Arama kayıtları
        {totalCount !== undefined ? <span className={styles.count}> {formatInteger(totalCount)}</span> : null}
      </NavLink>
      <NavLink className={tabClassName} to={workspacePath(workspaceId, 'kaynaklar')}>
        <DocumentIcon />
        Bilgi kaynakları
      </NavLink>
      <NavLink className={tabClassName} to={workspacePath(workspaceId, UPLOAD_SECTION)} end>
        <UploadIcon />
        Belge yükle
      </NavLink>
    </nav>
  )
}

function SearchIcon() {
  return (
    <svg className={styles.icon} width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true" focusable="false">
      <circle cx="11" cy="11" r="7" />
      <path d="m20 20-3.5-3.5" />
    </svg>
  )
}

function ListIcon() {
  return (
    <svg className={styles.icon} width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true" focusable="false">
      <path d="M4 6h16M4 12h16M4 18h10" />
    </svg>
  )
}

function DocumentIcon() {
  return (
    <svg className={styles.icon} width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true" focusable="false">
      <path d="M5 4h10l4 4v12H5z" />
      <path d="M15 4v4h4M8 12h8M8 16h6" />
    </svg>
  )
}

function UploadIcon() {
  return (
    <svg className={styles.icon} width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true" focusable="false">
      <path d="M12 15V4M7.5 8.5 12 4l4.5 4.5" />
      <path d="M5 14v5h14v-5" />
    </svg>
  )
}
