import { StatusBadge } from '../../../shared/components/StatusBadge.tsx'
import { SOURCE_STATUS_LABELS, SOURCE_STATUS_TONES } from '../model/sourceRules.ts'
import type { SourceStatus } from '../model/types.ts'
import styles from './SourceStatusBadge.module.css'

type SourceStatusBadgeProps = { status: SourceStatus }

export function SourceStatusBadge({ status }: SourceStatusBadgeProps) {
  return (
    <StatusBadge tone={SOURCE_STATUS_TONES[status]}>
      {status === 'INGESTING' ? <span className={styles.spinner} aria-hidden="true" /> : null}
      {SOURCE_STATUS_LABELS[status]}
    </StatusBadge>
  )
}
