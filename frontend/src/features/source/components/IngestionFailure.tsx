import { ingestionFailureLine } from '../model/ingestionRules.ts'
import type { IngestionJobResponse } from '../model/types.ts'
import styles from './IngestionFailure.module.css'

type IngestionFailureProps = {
  job: IngestionJobResponse
}

export function IngestionFailure({ job }: IngestionFailureProps) {
  return <p className={styles.failure}>{ingestionFailureLine(job)}</p>
}
