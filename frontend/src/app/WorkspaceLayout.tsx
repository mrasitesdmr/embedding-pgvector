import { Outlet, useParams } from 'react-router'
import { LastSearchProvider } from '../features/search/components/LastSearchProvider.tsx'
import { IngestionWatcherProvider } from '../features/source/components/IngestionWatcherProvider.tsx'
import { isApiErrorWithCode } from '../shared/api/ApiError.ts'
import { messageForError } from '../shared/api/errorMessages.ts'
import { ErrorAlert } from '../shared/components/ErrorAlert.tsx'
import { NotFoundState } from '../shared/components/NotFoundState.tsx'
import { ToastProvider } from '../shared/toast/ToastProvider.tsx'
import { useWorkspaceQuery } from '../shared/workspace/workspaceQueries.ts'
import { DEFAULT_WORKSPACE_ID, parseWorkspaceId, workspaceHomePath } from '../shared/workspace/workspaceRules.ts'
import { AppHeader } from './AppHeader.tsx'
import { SectionTabs } from './SectionTabs.tsx'
import styles from './WorkspaceLayout.module.css'

export function WorkspaceLayout() {
  const { workspaceId: param } = useParams()
  const workspaceId = parseWorkspaceId(param)
  const workspaceQuery = useWorkspaceQuery(workspaceId)
  const isNotFound = workspaceId === null || isApiErrorWithCode(workspaceQuery.error, 'WORKSPACE_NOT_FOUND')

  return (
    <>
      <AppHeader workspaceId={isNotFound ? null : workspaceId} />
      {!isNotFound && workspaceId !== null ? <SectionTabs workspaceId={workspaceId} /> : null}
      <main className={styles.content}>
        {isNotFound ? (
          <NotFoundState
            title="Çalışma alanı bulunamadı"
            description="Adresteki çalışma alanı yok ya da silinmiş."
            linkTo={workspaceHomePath(DEFAULT_WORKSPACE_ID)}
            linkLabel="Varsayılan çalışma alanına dön"
          />
        ) : workspaceQuery.isError ? (
          <ErrorAlert message={messageForError(workspaceQuery.error)} onRetry={() => void workspaceQuery.refetch()} />
        ) : workspaceQuery.data === undefined ? (
          <p className={styles.loading} aria-busy="true">
            Çalışma alanı yükleniyor…
          </p>
        ) : (
          <ToastProvider>
            <LastSearchProvider key={workspaceId} workspaceId={workspaceQuery.data.workspaceId}>
              <IngestionWatcherProvider key={workspaceId} workspaceId={workspaceQuery.data.workspaceId}>
                <Outlet />
              </IngestionWatcherProvider>
            </LastSearchProvider>
          </ToastProvider>
        )}
      </main>
    </>
  )
}
