import { useId } from 'react'
import type { ChangeEvent, Ref } from 'react'
import { formatFileSize } from '../../../shared/format/numberFormat.ts'
import { acceptAttribute, validateUploadFile } from '../model/uploadRules.ts'
import type { SourceKind } from '../model/types.ts'
import styles from './FileField.module.css'

type FileFieldProps = {
  label: string
  kind?: SourceKind
  file: File | null
  error: string | undefined
  disabled?: boolean
  ref?: Ref<HTMLInputElement>
  onFileChange: (file: File | null, error: string | null) => void
}

export function FileField({ label, kind, file, error, disabled = false, ref, onFileChange }: FileFieldProps) {
  const inputId = useId()
  const errorId = `${inputId}-error`

  function handleChange(event: ChangeEvent<HTMLInputElement>) {
    const selectedFile = event.target.files?.[0] ?? null
    onFileChange(selectedFile, selectedFile === null ? null : validateUploadFile(selectedFile, kind))
  }

  return (
    <div className={styles.field}>
      <label className={styles.label} htmlFor={inputId}>
        {label}
      </label>
      <input
        ref={ref}
        id={inputId}
        className={styles.input}
        type="file"
        name="file"
        accept={acceptAttribute(kind)}
        required
        disabled={disabled}
        aria-invalid={error !== undefined}
        aria-describedby={error !== undefined ? errorId : undefined}
        onChange={handleChange}
      />
      {file !== null ? (
        <div className={styles.selected}>
          <FileIcon />
          <span className={styles.selectedText}>
            <b className={styles.fileName}>{file.name}</b>
            <small className={styles.fileSize}>{formatFileSize(file.size)}</small>
          </span>
        </div>
      ) : null}
      {error !== undefined ? (
        <p id={errorId} className={styles.error} role="alert">
          {error}
        </p>
      ) : null}
    </div>
  )
}

function FileIcon() {
  return (
    <svg className={styles.icon} width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true" focusable="false">
      <path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z" />
      <path d="M14 3v5h5" />
    </svg>
  )
}
