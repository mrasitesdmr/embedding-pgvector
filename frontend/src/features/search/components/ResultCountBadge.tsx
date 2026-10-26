import { StatusBadge } from '../../../shared/components/StatusBadge.tsx'
import { formatInteger } from '../../../shared/format/numberFormat.ts'
import { resultCountTone } from '../model/resultCountTone.ts'

type ResultCountBadgeProps = {
  resultCount: number
  limit: number | null
}

export function ResultCountBadge({ resultCount, limit }: ResultCountBadgeProps) {
  const tone = resultCountTone(resultCount, limit)
  return <StatusBadge tone={tone}>{tone === 'bad' ? 'Sonuçsuz' : formatInteger(resultCount)}</StatusBadge>
}
