import { NotFoundState } from '../shared/components/NotFoundState.tsx'
import { DEFAULT_WORKSPACE_ID, workspaceHomePath } from '../shared/workspace/workspaceRules.ts'
import { AppHeader } from './AppHeader.tsx'
import styles from './WorkspaceLayout.module.css'

export function NotFoundPage() {
  return (
    <>
      <AppHeader workspaceId={null} />
      <main className={styles.content}>
        <NotFoundState
          title="Sayfa bulunamadı"
          linkTo={workspaceHomePath(DEFAULT_WORKSPACE_ID)}
          linkLabel="Varsayılan çalışma alanına dön"
        />
      </main>
    </>
  )
}
