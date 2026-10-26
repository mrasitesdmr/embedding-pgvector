import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { ApiError } from '../../../shared/api/ApiError.ts'
import { messageForError } from '../../../shared/api/errorMessages.ts'
import button from '../../../shared/components/button.module.css'
import { EmptyState } from '../../../shared/components/EmptyState.tsx'
import { ErrorAlert } from '../../../shared/components/ErrorAlert.tsx'
import { useCurrentWorkspace } from '../../../shared/workspace/useCurrentWorkspace.ts'
import type { WorkspaceResponse } from '../../../shared/workspace/types.ts'
import { embeddingModelsLabel, uploadPath } from '../../../shared/workspace/workspaceRules.ts'
import { usePopularQueriesQuery } from '../api/searchQueries.ts'
import { PopularQueries } from '../components/PopularQueries.tsx'
import { SearchForm } from '../components/SearchForm.tsx'
import { SearchInfoLine } from '../components/SearchInfoLine.tsx'
import { SearchPendingResults } from '../components/SearchPendingResults.tsx'
import { SearchResults } from '../components/SearchResults.tsx'
import { SearchSettingsPanel } from '../components/SearchSettingsPanel.tsx'
import { useLastSearch } from '../hooks/useLastSearch.ts'
import { useSearchSettings } from '../hooks/useSearchSettings.ts'
import { QUERY_RULE_MESSAGE, canSearch, toSearchRequest } from '../model/searchRules.ts'
import styles from './SearchPage.module.css'

function isQueryFieldError(error: unknown): boolean {
  return (
    error instanceof ApiError &&
    error.errorCode === 'VALIDATION_FAILED' &&
    error.violations.some((violation) => violation.field === 'query')
  )
}

export function SearchPage() {
  const { workspaceId, workspace } = useCurrentWorkspace()

  return (
    <div className={styles.page}>
      <title>Bilgi bankası · yanıt.</title>
      <header className={styles.header}>
        <h1 className={styles.title}>Bilgi bankası</h1>
        <p className={styles.description}>
          {`Sorunu kendi kelimelerinle yaz; ${workspace.name} çalışma alanındaki belgelerde aranır.`}
        </p>
      </header>
      {workspace.sourceCount === 0 ? (
        <EmptyState title="Henüz belge yüklenmedi" description="Soru sorabilmek için önce bir belge yükle.">
          <Link className={`${button.primary} ${styles.link}`} to={uploadPath(workspaceId)}>
            Belge yükle
          </Link>
        </EmptyState>
      ) : (
        <SearchWorkspace workspaceId={workspaceId} workspace={workspace} />
      )}
    </div>
  )
}

type SearchWorkspaceProps = {
  workspaceId: number
  workspace: WorkspaceResponse
}

function SearchWorkspace({ workspaceId, workspace }: SearchWorkspaceProps) {
  const lastSearch = useLastSearch()
  const searchSettings = useSearchSettings()
  const [queryInput, setQueryInput] = useState(() => lastSearch.query ?? '')
  const [isSettingsOpen, setIsSettingsOpen] = useState(false)
  const popularQueriesQuery = usePopularQueriesQuery(workspaceId)
  const popularQueries = popularQueriesQuery.data?.queries ?? []
  const hasPopularQueries = popularQueries.length > 0

  useEffect(() => {
    if (popularQueriesQuery.error !== null) {
      console.error(popularQueriesQuery.error)
    }
  }, [popularQueriesQuery.error])

  const queryError = isQueryFieldError(lastSearch.error) ? QUERY_RULE_MESSAGE : null
  const pageError = lastSearch.error !== null && queryError === null ? messageForError(lastSearch.error) : null

  function runSearch(query: string) {
    if (lastSearch.isPending || !canSearch(query)) {
      return
    }
    if (!searchSettings.isValid) {
      setIsSettingsOpen(true)
      return
    }
    lastSearch.search(toSearchRequest(query, searchSettings.settings))
  }

  function handleSuggestionSelect(query: string) {
    setQueryInput(query)
    runSearch(query)
  }

  return (
    <>
      <SearchForm
        value={queryInput}
        isPending={lastSearch.isPending}
        queryError={queryError}
        onChange={setQueryInput}
        onSubmit={() => runSearch(queryInput)}
      />
      {hasPopularQueries ? (
        <PopularQueries queries={popularQueries} isDisabled={lastSearch.isPending} onSelect={handleSuggestionSelect} />
      ) : null}
      <SearchInfoLine settings={searchSettings.settings} embeddingModels={embeddingModelsLabel(workspace)} />
      <SearchSettingsPanel
        draft={searchSettings.draft}
        errors={searchSettings.errors}
        isOpen={isSettingsOpen}
        onOpenChange={setIsSettingsOpen}
        onChange={searchSettings.update}
        onReset={searchSettings.reset}
      />

      {lastSearch.isPending ? (
        <SearchPendingResults query={lastSearch.query ?? ''} />
      ) : pageError !== null ? (
        <ErrorAlert message={pageError} />
      ) : lastSearch.result !== undefined ? (
        <SearchResults workspaceId={workspaceId} result={lastSearch.result} />
      ) : queryError === null ? (
        <EmptyState
          title="Belgene bir soru sorarak başla"
          description={
            hasPopularQueries
              ? 'Sık arananlardan birini seç ya da kendi sorunu yaz.'
              : 'Sorunu kendi kelimelerinle yaz; en ilgili parçalar listelenir.'
          }
        />
      ) : null}
    </>
  )
}
