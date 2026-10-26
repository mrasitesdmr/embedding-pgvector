import styles from './SearchPendingResults.module.css'

const SKELETON_ROWS = [0, 1, 2]

type SearchPendingResultsProps = { query: string }

export function SearchPendingResults({ query }: SearchPendingResultsProps) {
  return (
    <section className={styles.pending} aria-label="Sonuçlar" aria-busy="true">
      <div className={styles.header} aria-live="polite">
        <h2 className={styles.query}>{`“${query}”`}</h2>
        <span className={styles.meta}>aranıyor…</span>
      </div>
      <div className={styles.skeletons} aria-hidden="true">
        {SKELETON_ROWS.map((row) => (
          <div key={row} className={styles.skeleton}>
            <i />
            <i />
            <i />
          </div>
        ))}
      </div>
    </section>
  )
}
