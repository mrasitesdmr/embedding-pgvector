import { useState } from 'react'
import { messageForError } from '../../../shared/api/errorMessages.ts'
import { EmptyState } from '../../../shared/components/EmptyState.tsx'
import { ErrorAlert } from '../../../shared/components/ErrorAlert.tsx'
import { Pagination } from '../../../shared/components/Pagination.tsx'
import { useCurrentWorkspace } from '../../../shared/workspace/useCurrentWorkspace.ts'
import { useSearchEventsQuery } from '../api/searchQueries.ts'
import { SearchEventStats } from '../components/SearchEventStats.tsx'
import { SearchEventTable } from '../components/SearchEventTable.tsx'
import styles from './SearchEventsPage.module.css'

export function SearchEventsPage() {
  const { workspaceId } = useCurrentWorkspace()
  const [page, setPage] = useState(0)
  const eventsQuery = useSearchEventsQuery(workspaceId, page)
  const events = eventsQuery.data

  return (
    <div className={styles.page}>
      <title>Arama kayıtları · yanıt.</title>
      <header className={styles.header}>
        <h1 className={styles.title}>Arama kayıtları</h1>
        <p className={styles.description}>
          Bu çalışma alanında yapılan aramalar. Sonuçsuz ve istenenden az sonuç dönen aramalar, bulunamayan içeriğin ilk
          işaretidir.
        </p>
      </header>

      <SearchEventStats workspaceId={workspaceId} />

      {eventsQuery.isError ? (
        <ErrorAlert message={messageForError(eventsQuery.error)} onRetry={() => void eventsQuery.refetch()} />
      ) : events === undefined ? (
        <p className={styles.loading} aria-busy="true">
          Kayıtlar yükleniyor…
        </p>
      ) : events.content.length === 0 ? (
        <EmptyState title="Henüz arama kaydı yok" />
      ) : (
        <>
          <SearchEventTable events={events.content} />
          {events.totalPages > 1 ? (
            <Pagination
              page={events.page}
              totalPages={events.totalPages}
              isDisabled={eventsQuery.isPlaceholderData}
              onPageChange={setPage}
            />
          ) : null}
        </>
      )}
    </div>
  )
}
