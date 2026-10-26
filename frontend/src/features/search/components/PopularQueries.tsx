import { useId } from 'react'
import type { PopularQueryResponse } from '../model/types.ts'
import styles from './PopularQueries.module.css'

type PopularQueriesProps = {
  queries: PopularQueryResponse[]
  isDisabled: boolean
  onSelect: (query: string) => void
}

export function PopularQueries({ queries, isDisabled, onSelect }: PopularQueriesProps) {
  const labelId = useId()
  return (
    <div className={styles.popularQueries}>
      <span id={labelId}>Sık aranan:</span>
      <ul className={styles.list} aria-labelledby={labelId}>
        {queries.map(({ query }) => (
          <li key={query}>
            <button type="button" className={styles.button} disabled={isDisabled} onClick={() => onSelect(query)}>
              {query}
            </button>
          </li>
        ))}
      </ul>
    </div>
  )
}
