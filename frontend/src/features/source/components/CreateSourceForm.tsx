import { useId, useRef, useState } from 'react'
import type { FormEvent, RefObject } from 'react'
import button from '../../../shared/components/button.module.css'
import { ErrorAlert } from '../../../shared/components/ErrorAlert.tsx'
import { toUploadFieldErrors } from '../model/uploadFormErrors.ts'
import type { UploadFieldErrors } from '../model/uploadFormErrors.ts'
import {
  SOURCE_NAME_HINT,
  SOURCE_NAME_MAX_LENGTH,
  normalizeSourceName,
  resolveSourceName,
  validateSourceName,
  validateUploadFile,
} from '../model/uploadRules.ts'
import type { CreateSourceInput, IngestionJobResponse } from '../model/types.ts'
import { FileField } from './FileField.tsx'
import styles from './uploadDialog.module.css'

type CreateSourceFormProps = {
  nameInputRef: RefObject<HTMLInputElement | null>
  isPending: boolean
  submitLabel: string
  pendingLabel: string
  onCreate: (input: CreateSourceInput) => Promise<IngestionJobResponse>
  onCancel?: () => void
  onCreated: (job: IngestionJobResponse) => void
}

export function CreateSourceForm({
  nameInputRef,
  isPending,
  submitLabel,
  pendingLabel,
  onCreate,
  onCancel,
  onCreated,
}: CreateSourceFormProps) {
  const fileInputRef = useRef<HTMLInputElement>(null)
  const [file, setFile] = useState<File | null>(null)
  const [errors, setErrors] = useState<UploadFieldErrors>({})
  const nameId = useId()
  const nameHintId = `${nameId}-hint`
  const nameErrorId = `${nameId}-error`

  function showErrors(nextErrors: UploadFieldErrors) {
    setErrors(nextErrors)
    if (nextErrors.name !== undefined) {
      nameInputRef.current?.focus()
    } else if (nextErrors.file !== undefined) {
      fileInputRef.current?.focus()
    }
  }

  function handleFileChange(selectedFile: File | null, fileError: string | null) {
    setFile(selectedFile)
    setErrors((current) => ({ ...current, file: fileError ?? undefined, form: undefined }))
  }

  function handleNameChange() {
    if (errors.name !== undefined) {
      setErrors((current) => ({ ...current, name: undefined }))
    }
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (isPending) {
      return
    }
    const rawName = String(new FormData(event.currentTarget).get('name') ?? '')
    const typedName = normalizeSourceName(rawName)
    const fileError = validateUploadFile(file)
    if (fileError !== null || file === null) {
      const typedNameError = typedName === '' ? null : validateSourceName(typedName)
      showErrors({ name: typedNameError ?? undefined, file: fileError ?? undefined })
      return
    }
    const name = resolveSourceName(rawName, file)
    if (typedName === '' && nameInputRef.current !== null) {
      nameInputRef.current.value = name
    }
    const nameError = validateSourceName(name)
    if (nameError !== null) {
      showErrors({ name: nameError })
      return
    }
    setErrors({})
    try {
      const job = await onCreate({ name, file })
      onCreated(job)
    } catch (error) {
      showErrors(toUploadFieldErrors(error))
    }
  }

  return (
    <form className={styles.form} noValidate onSubmit={(event) => void handleSubmit(event)}>
      {errors.form !== undefined ? <ErrorAlert message={errors.form} /> : null}
      <div className={styles.field}>
        <label className={styles.label} htmlFor={nameId}>
          Kaynak adı
        </label>
        <input
          ref={nameInputRef}
          id={nameId}
          className={styles.input}
          name="name"
          type="text"
          placeholder="ör. Süreç el kitabı"
          maxLength={SOURCE_NAME_MAX_LENGTH}
          autoComplete="off"
          aria-invalid={errors.name !== undefined}
          aria-describedby={errors.name !== undefined ? `${nameHintId} ${nameErrorId}` : nameHintId}
          onChange={handleNameChange}
        />
        <p id={nameHintId} className={styles.hint}>
          {SOURCE_NAME_HINT}
        </p>
        {errors.name !== undefined ? (
          <p id={nameErrorId} className={styles.error} role="alert">
            {errors.name}
          </p>
        ) : null}
      </div>
      <FileField ref={fileInputRef} label="Dosya" file={file} error={errors.file} onFileChange={handleFileChange} />
      <div className={styles.actions}>
        {onCancel !== undefined ? (
          <button type="button" className={button.secondary} disabled={isPending} onClick={onCancel}>
            Vazgeç
          </button>
        ) : null}
        <button type="submit" className={button.primary} disabled={isPending}>
          {isPending ? pendingLabel : submitLabel}
        </button>
      </div>
    </form>
  )
}
