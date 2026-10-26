import { useId } from 'react'
import type { FormEvent } from 'react'
import hidden from '../../../shared/components/visuallyHidden.module.css'
import { QUERY_MAX_LENGTH, canSearch } from '../model/searchRules.ts'
import styles from './SearchForm.module.css'

type SearchFormProps = {
  value: string
  isPending: boolean
  queryError: string | null
  onChange: (value: string) => void
  onSubmit: () => void
}

export function SearchForm({ value, isPending, queryError, onChange, onSubmit }: SearchFormProps) {
  const inputId = useId()
  const errorId = `${inputId}-error`
  const isSubmitDisabled = isPending || !canSearch(value)

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!isSubmitDisabled) {
      onSubmit()
    }
  }

  return (
    <form className={styles.form} role="search" noValidate onSubmit={handleSubmit}>
      <div className={styles.box}>
        <SearchIcon />
        <label htmlFor={inputId} className={hidden.visuallyHidden}>
          Arama metni
        </label>
        <input
          id={inputId}
          className={styles.input}
          type="text"
          enterKeyHint="search"
          name="query"
          value={value}
          maxLength={QUERY_MAX_LENGTH}
          placeholder="Örn. Yıllık izin kaç gün?"
          autoComplete="off"
          aria-invalid={queryError !== null}
          aria-describedby={queryError !== null ? errorId : undefined}
          onChange={(event) => onChange(event.target.value)}
        />
        <button type="submit" className={styles.submit} disabled={isSubmitDisabled}>
          {isPending ? 'Aranıyor…' : 'Ara'}
        </button>
      </div>
      {queryError !== null ? (
        <p id={errorId} className={styles.error} role="alert">
          {queryError}
        </p>
      ) : null}
    </form>
  )
}

function SearchIcon() {
  return (
    <svg className={styles.icon} width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" aria-hidden="true" focusable="false">
      <circle cx="11" cy="11" r="7" />
      <path d="m20 20-3.5-3.5" />
    </svg>
  )
}
