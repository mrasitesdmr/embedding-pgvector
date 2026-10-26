import { Link } from 'react-router'
import { DEFAULT_WORKSPACE_ID, workspaceHomePath } from '../shared/workspace/workspaceRules.ts'
import styles from './AppHeader.module.css'
import { WorkspaceSelect } from './WorkspaceSelect.tsx'

type AppHeaderProps = { workspaceId: number | null }

export function AppHeader({ workspaceId }: AppHeaderProps) {
  return (
    <header className={styles.header}>
      <Link className={styles.logo} to={workspaceHomePath(workspaceId ?? DEFAULT_WORKSPACE_ID)}>
        <span className={styles.mark} aria-hidden="true">
          <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.3" strokeLinecap="round" strokeLinejoin="round" focusable="false">
            <path d="M5 5h14v10H10l-5 4z" />
            <path d="M9 9.5h6M9 12h3.5" />
          </svg>
        </span>
        <span className={styles.word}>
          yanıt<span className={styles.dot}>.</span>
        </span>
      </Link>
      <WorkspaceSelect workspaceId={workspaceId} />
    </header>
  )
}
