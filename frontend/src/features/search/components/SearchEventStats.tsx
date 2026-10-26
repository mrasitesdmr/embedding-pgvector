import { messageForError } from '../../../shared/api/errorMessages.ts'
import { ErrorAlert } from '../../../shared/components/ErrorAlert.tsx'
import { EMPTY_VALUE, formatInteger, formatPercent } from '../../../shared/format/numberFormat.ts'
import { useSearchEventStatsQuery } from '../api/searchQueries.ts'
import styles from './SearchEventStats.module.css'

type SearchEventStatsProps = { workspaceId: number }

export function SearchEventStats({ workspaceId }: SearchEventStatsProps) {
  const statsQuery = useSearchEventStatsQuery(workspaceId)

  if (statsQuery.isError) {
    return <ErrorAlert message={messageForError(statsQuery.error)} onRetry={() => void statsQuery.refetch()} />
  }

  const stats = statsQuery.data
  const indicators = [
    { label: 'Bugün', value: stats ? formatInteger(stats.todayCount) : EMPTY_VALUE, unit: 'arama' },
    { label: 'Sonuçsuz arama', value: stats ? formatPercent(stats.zeroResultRate) : EMPTY_VALUE, unit: 'son 7 gün' },
    { label: 'Az sonuç dönen', value: stats ? formatPercent(stats.lowResultRate) : EMPTY_VALUE, unit: 'son 7 gün' },
  ]

  return (
    <dl className={styles.indicators} aria-busy={statsQuery.isPending}>
      {indicators.map((indicator) => (
        <div key={indicator.label} className={styles.indicator}>
          <dt className={styles.label}>{indicator.label}</dt>
          <dd className={styles.value}>
            <span className={styles.number}>{indicator.value}</span> <span className={styles.unit}>{indicator.unit}</span>
          </dd>
        </div>
      ))}
    </dl>
  )
}
