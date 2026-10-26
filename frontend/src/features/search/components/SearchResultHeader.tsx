import { Callout } from '../../../shared/components/Callout.tsx'
import { formatDuration } from '../../../shared/format/numberFormat.ts'
import { shortVectorWarning } from '../model/searchNotices.ts'
import type { SearchResultResponse } from '../model/types.ts'
import styles from './SearchResultHeader.module.css'

type SearchResultHeaderProps = { result: SearchResultResponse }

export function SearchResultHeader({ result }: SearchResultHeaderProps) {
  const warning = shortVectorWarning(result)
  return (
    <>
      <div className={styles.header} aria-live="polite">
        <h2 className={styles.query}>{`“${result.query}”`}</h2>
        <span className={styles.meta}>{`${result.returnedCount} sonuç`}</span>
        <span className={styles.meta}>{`toplam ${formatDuration(result.timings.totalMs)}`}</span>
      </div>
      {warning !== null ? <Callout tone="warning">{warning}</Callout> : null}
    </>
  )
}
