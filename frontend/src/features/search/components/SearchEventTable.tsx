import table from '../../../shared/components/table.module.css'
import { EMPTY_VALUE } from '../../../shared/format/numberFormat.ts'
import { formatClock } from '../../../shared/format/dateFormat.ts'
import { MODE_LABELS } from '../model/searchRules.ts'
import type { SearchEventResponse } from '../model/types.ts'
import { ResultCountBadge } from './ResultCountBadge.tsx'
import styles from './SearchEventTable.module.css'

type SearchEventTableProps = { events: SearchEventResponse[] }

export function SearchEventTable({ events }: SearchEventTableProps) {
  return (
    <div className={table.scroll}>
      <table className={table.table}>
        <thead>
          <tr>
            <th scope="col">Saat</th>
            <th scope="col">Nereden</th>
            <th scope="col">Arama</th>
            <th scope="col">Yöntem</th>
            <th scope="col">Sonuç</th>
            <th scope="col">İlk sonuç</th>
          </tr>
        </thead>
        <tbody>
          {events.map((event) => (
            <tr key={event.searchEventId}>
              <td className={table.num}>
                <time dateTime={event.createdAt}>{formatClock(event.createdAt)}</time>
              </td>
              <td>{event.channel ?? EMPTY_VALUE}</td>
              <td className={styles.query}>{event.query}</td>
              <td>{MODE_LABELS[event.mode]}</td>
              <td>
                <ResultCountBadge resultCount={event.resultCount} limit={event.limit ?? null} />
              </td>
              <td>{event.topChunkHeading ?? EMPTY_VALUE}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
