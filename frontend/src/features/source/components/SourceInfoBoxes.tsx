import { EMPTY_VALUE, formatInteger } from '../../../shared/format/numberFormat.ts'
import { formatDateTime } from '../../../shared/format/dateFormat.ts'
import { SOURCE_KIND_LABELS } from '../model/sourceRules.ts'
import type { SourceResponse, SourceStatus } from '../model/types.ts'
import { SourceStatusBadge } from './SourceStatusBadge.tsx'
import styles from './SourceInfoBoxes.module.css'

type SourceInfoBoxesProps = {
  source: SourceResponse
  status: SourceStatus
  embeddingModel: string | null
}

export function SourceInfoBoxes({ source, status, embeddingModel }: SourceInfoBoxesProps) {
  const boxes = [
    { label: 'Tür', value: SOURCE_KIND_LABELS[source.kind] },
    { label: 'Sürüm', value: source.version ?? EMPTY_VALUE },
    { label: 'Dosya adı', value: source.fileName ?? EMPTY_VALUE },
    { label: 'Parça', value: formatInteger(source.chunkCount) },
    { label: 'Embedding modeli', value: embeddingModel ?? EMPTY_VALUE },
    { label: 'Son senkron', value: source.syncedAt ? formatDateTime(source.syncedAt) : EMPTY_VALUE },
  ]
  return (
    <dl className={styles.boxes}>
      {boxes.map((box) => (
        <div key={box.label} className={styles.box}>
          <dt className={styles.label}>{box.label}</dt>
          <dd className={styles.value}>{box.value}</dd>
        </div>
      ))}
      <div className={styles.box}>
        <dt className={styles.label}>Durum</dt>
        <dd className={styles.value}>
          <SourceStatusBadge status={status} />
        </dd>
      </div>
    </dl>
  )
}
