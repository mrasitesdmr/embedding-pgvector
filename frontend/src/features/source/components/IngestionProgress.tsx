import { ingestionStageText, progressPercent } from '../model/ingestionRules.ts'
import type { IngestionJobResponse } from '../model/types.ts'
import styles from './IngestionProgress.module.css'

type IngestionProgressProps = {
  sourceName: string
  job: IngestionJobResponse
}

export function IngestionProgress({ sourceName, job }: IngestionProgressProps) {
  const stageText = ingestionStageText(job)
  const percent = progressPercent(job)
  return (
    <div className={styles.progress}>
      <span className={styles.stage}>{stageText}</span>
      <span
        className={styles.track}
        role="progressbar"
        aria-label={`${sourceName} yükleme ilerlemesi`}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={percent}
        aria-valuetext={stageText}
      >
        <span className={styles.fill} style={{ width: `${percent}%` }} />
      </span>
    </div>
  )
}
