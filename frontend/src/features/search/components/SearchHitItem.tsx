import { hitSnippetSegments, hitSourceLine, hitTitleSegments } from '../model/searchHitText.ts'
import type { SearchHitResponse } from '../model/types.ts'
import { HighlightedText } from './HighlightedText.tsx'
import styles from './SearchHitItem.module.css'
import { SearchHitTags } from './SearchHitTags.tsx'

type SearchHitItemProps = {
  hit: SearchHitResponse
  onOpen: (hit: SearchHitResponse, opener: HTMLButtonElement) => void
}

export function SearchHitItem({ hit, onOpen }: SearchHitItemProps) {
  return (
    <button type="button" className={styles.hit} onClick={(event) => onOpen(hit, event.currentTarget)}>
      <span className={styles.rank}>{hit.rank}</span>
      <span className={styles.body}>
        <span className={styles.titleRow}>
          <span className={styles.title}>
            <HighlightedText segments={hitTitleSegments(hit)} />
          </span>
          <SearchHitTags matchedBy={hit.matchedBy} />
        </span>
        <span className={styles.snippet}>
          <HighlightedText segments={hitSnippetSegments(hit)} />
        </span>
        <span className={styles.source}>{hitSourceLine(hit)}</span>
      </span>
    </button>
  )
}
