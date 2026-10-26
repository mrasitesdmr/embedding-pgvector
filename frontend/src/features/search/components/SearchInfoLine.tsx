import { MODE_INFO_LABELS } from '../model/searchRules.ts'
import type { SearchSettings } from '../model/types.ts'
import styles from './SearchInfoLine.module.css'

type SearchInfoLineProps = {
  settings: SearchSettings
  embeddingModels: string
}

export function SearchInfoLine({ settings, embeddingModels }: SearchInfoLineProps) {
  return (
    <ul className={styles.info} aria-label="Arama bilgisi">
      <li className={styles.item}>
        Arama: <b>{MODE_INFO_LABELS[settings.mode]}</b>
      </li>
      <li className={styles.item}>
        Sonuç: <b>{`en fazla ${settings.limit}`}</b>
      </li>
      <li className={styles.item}>
        Embedding: <b>{embeddingModels}</b>
      </li>
    </ul>
  )
}
