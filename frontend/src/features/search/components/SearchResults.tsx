import type { SearchResultResponse } from '../model/types.ts'
import { SearchHitList } from './SearchHitList.tsx'
import { SearchResultEmpty } from './SearchResultEmpty.tsx'
import { SearchResultHeader } from './SearchResultHeader.tsx'
import { SearchTechnicalDetails } from './SearchTechnicalDetails.tsx'
import styles from './SearchResults.module.css'

type SearchResultsProps = {
  workspaceId: number
  result: SearchResultResponse
}

export function SearchResults({ workspaceId, result }: SearchResultsProps) {
  return (
    <section className={styles.results} aria-label="Sonuçlar">
      <SearchResultHeader result={result} />
      {result.hits.length > 0 ? (
        <SearchHitList workspaceId={workspaceId} hits={result.hits} />
      ) : (
        <SearchResultEmpty result={result} />
      )}
      <SearchTechnicalDetails result={result} />
    </section>
  )
}
