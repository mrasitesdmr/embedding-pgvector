import table from '../../../shared/components/table.module.css'
import { EMPTY_VALUE, formatDuration, formatInteger, formatScore } from '../../../shared/format/numberFormat.ts'
import { FILTER_LABELS, MODE_LABELS } from '../model/searchRules.ts'
import type { SearchResultResponse } from '../model/types.ts'
import styles from './SearchTechnicalDetails.module.css'

type SearchTechnicalDetailsProps = { result: SearchResultResponse }

export function SearchTechnicalDetails({ result }: SearchTechnicalDetailsProps) {
  const summary: [string, string][] = [
    ['Yöntem', MODE_LABELS[result.mode]],
    ['Filtre stratejisi', result.filterStrategy ? FILTER_LABELS[result.filterStrategy] : EMPTY_VALUE],
    ['ef_search', formatInteger(result.efSearch)],
    ['Kelime kolu satır', formatInteger(result.keywordCount)],
    ['Vektör kolu satır', formatInteger(result.vectorCount)],
    ['Embedding süresi', formatDuration(result.timings.embeddingMs)],
    ['SQL süresi', formatDuration(result.timings.sqlMs)],
    ['Toplam süre', formatDuration(result.timings.totalMs)],
  ]

  return (
    <details className={styles.details}>
      <summary className={styles.summary}>Teknik detay</summary>
      <div className={styles.body}>
        <dl className={styles.facts}>
          {summary.map(([term, value]) => (
            <div key={term} className={styles.fact}>
              <dt>{term}</dt>
              <dd>{value}</dd>
            </div>
          ))}
        </dl>

        {result.hits.length > 0 ? (
          <div className={table.scroll}>
            <table className={table.table}>
              <caption className={styles.caption}>Sonuç başına sıralar ve puanlar</caption>
              <thead>
                <tr>
                  <th scope="col">Sıra</th>
                  <th scope="col">Parça</th>
                  <th scope="col">Kelime sırası</th>
                  <th scope="col">Kelime puanı</th>
                  <th scope="col">Vektör sırası</th>
                  <th scope="col">Cosine mesafesi</th>
                  <th scope="col">RRF puanı</th>
                </tr>
              </thead>
              <tbody>
                {result.hits.map((hit) => (
                  <tr key={hit.chunkId}>
                    <td className={table.num}>{hit.rank}</td>
                    <td className={table.num}>{`#${hit.chunkId}`}</td>
                    <td className={table.num}>{formatInteger(hit.keywordRank)}</td>
                    <td className={table.num}>{formatScore(hit.keywordScore)}</td>
                    <td className={table.num}>{formatInteger(hit.vectorRank)}</td>
                    <td className={table.num}>{formatScore(hit.distance)}</td>
                    <td className={table.num}>{formatScore(hit.rrfScore)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : null}

        {result.vectorPlan ? (
          <div className={styles.plan}>
            <h3 className={styles.planTitle}>Sorgu planı</h3>
            <div className={styles.planScroll} role="region" aria-label="Sorgu planı metni" tabIndex={0}>
              <pre className={styles.planText}>{result.vectorPlan}</pre>
            </div>
          </div>
        ) : null}
      </div>
    </details>
  )
}
