import { useId } from 'react'
import type { SearchSettingsDraft, SearchSettingsErrors } from '../hooks/useSearchSettings.ts'
import {
  EF_SEARCH_MAX,
  EF_SEARCH_MIN,
  FILTER_LABELS,
  MODE_LABELS,
  SEARCH_LIMIT_MAX,
  SEARCH_LIMIT_MIN,
} from '../model/searchRules.ts'
import { FILTER_STRATEGIES, SEARCH_MODES } from '../model/types.ts'
import styles from './SearchSettingsPanel.module.css'

type SearchSettingsPanelProps = {
  draft: SearchSettingsDraft
  errors: SearchSettingsErrors
  isOpen: boolean
  onOpenChange: (isOpen: boolean) => void
  onChange: (changes: Partial<SearchSettingsDraft>) => void
  onReset: () => void
}

export function SearchSettingsPanel({ draft, errors, isOpen, onOpenChange, onChange, onReset }: SearchSettingsPanelProps) {
  const baseId = useId()
  const efSearchId = `${baseId}-ef-search`
  const efSearchErrorId = `${efSearchId}-error`
  const limitId = `${baseId}-limit`
  const limitErrorId = `${limitId}-error`
  const explainId = `${baseId}-explain`
  const isKeyword = draft.mode === 'KEYWORD'

  return (
    <details className={styles.panel} open={isOpen} onToggle={(event) => onOpenChange(event.currentTarget.open)}>
      <summary className={styles.summary}>Gelişmiş ayarlar</summary>
      <div className={styles.body}>
        <fieldset className={styles.group}>
          <legend className={styles.legend}>Arama yöntemi</legend>
          <div className={styles.options}>
            {SEARCH_MODES.map((mode) => (
              <label key={mode} className={styles.option}>
                <input
                  type="radio"
                  name={`${baseId}-mode`}
                  value={mode}
                  checked={draft.mode === mode}
                  onChange={() => onChange({ mode })}
                />
                {MODE_LABELS[mode]}
              </label>
            ))}
          </div>
        </fieldset>

        <fieldset className={styles.group} disabled={isKeyword}>
          <legend className={styles.legend}>Filtre stratejisi</legend>
          <div className={styles.options}>
            {FILTER_STRATEGIES.map((filterStrategy) => (
              <label key={filterStrategy} className={styles.option}>
                <input
                  type="radio"
                  name={`${baseId}-filter-strategy`}
                  value={filterStrategy}
                  checked={draft.filterStrategy === filterStrategy}
                  onChange={() => onChange({ filterStrategy })}
                />
                {FILTER_LABELS[filterStrategy]}
              </label>
            ))}
          </div>
        </fieldset>

        <div className={styles.field}>
          <label htmlFor={efSearchId} className={styles.label}>
            ef_search
          </label>
          <input
            id={efSearchId}
            className={styles.number}
            type="number"
            inputMode="numeric"
            min={EF_SEARCH_MIN}
            max={EF_SEARCH_MAX}
            step={1}
            value={draft.efSearch}
            disabled={isKeyword}
            aria-invalid={errors.efSearch !== null}
            aria-describedby={errors.efSearch !== null ? efSearchErrorId : undefined}
            onChange={(event) => onChange({ efSearch: event.target.value })}
          />
          {errors.efSearch !== null ? (
            <p id={efSearchErrorId} className={styles.error}>
              {errors.efSearch}
            </p>
          ) : null}
        </div>

        <div className={styles.field}>
          <label htmlFor={limitId} className={styles.label}>
            Sonuç sayısı
          </label>
          <input
            id={limitId}
            className={styles.number}
            type="number"
            inputMode="numeric"
            min={SEARCH_LIMIT_MIN}
            max={SEARCH_LIMIT_MAX}
            step={1}
            value={draft.limit}
            aria-invalid={errors.limit !== null}
            aria-describedby={errors.limit !== null ? limitErrorId : undefined}
            onChange={(event) => onChange({ limit: event.target.value })}
          />
          {errors.limit !== null ? (
            <p id={limitErrorId} className={styles.error}>
              {errors.limit}
            </p>
          ) : null}
        </div>

        <div className={styles.checkboxField}>
          <input
            id={explainId}
            type="checkbox"
            checked={draft.explain}
            disabled={isKeyword}
            onChange={(event) => onChange({ explain: event.target.checked })}
          />
          <label htmlFor={explainId}>Sorgu planını getir</label>
        </div>

        <div className={styles.actions}>
          <button type="button" className={styles.reset} onClick={onReset}>
            Varsayılana dön
          </button>
        </div>
      </div>
    </details>
  )
}
