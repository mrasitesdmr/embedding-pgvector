import { useId, useRef, useState } from 'react'
import type { FormEvent, RefObject } from 'react'
import { Callout } from '../../../shared/components/Callout.tsx'
import button from '../../../shared/components/button.module.css'
import { Dialog } from '../../../shared/components/Dialog.tsx'
import { ErrorAlert } from '../../../shared/components/ErrorAlert.tsx'
import { formatInteger } from '../../../shared/format/numberFormat.ts'
import { useUploadSourceVersionMutation } from '../api/sourceQueries.ts'
import { versionLabel } from '../model/sourceRules.ts'
import { toUploadFieldErrors } from '../model/uploadFormErrors.ts'
import type { UploadFieldErrors } from '../model/uploadFormErrors.ts'
import { VERSION_LABEL_MAX_LENGTH, validateUploadFile, validateVersionLabel } from '../model/uploadRules.ts'
import type { SourceResponse, UploadSourceVersionInput } from '../model/types.ts'
import { FileField } from './FileField.tsx'
import styles from './uploadDialog.module.css'

type UploadVersionDialogProps = {
  workspaceId: number
  source: SourceResponse
  isOpen: boolean
  onClose: () => void
}

export function UploadVersionDialog({ workspaceId, source, isOpen, onClose }: UploadVersionDialogProps) {
  const fileInputRef = useRef<HTMLInputElement>(null)
  const uploadMutation = useUploadSourceVersionMutation(workspaceId, source.sourceId)
  const isPending = uploadMutation.isPending

  return (
    <Dialog
      title="Yeni sürüm yükle"
      isOpen={isOpen}
      onClose={onClose}
      canClose={!isPending}
      initialFocusRef={fileInputRef}
    >
      <UploadVersionForm
        source={source}
        fileInputRef={fileInputRef}
        isPending={isPending}
        onUpload={uploadMutation.mutateAsync}
        onCancel={onClose}
        onUploaded={onClose}
      />
    </Dialog>
  )
}

type UploadVersionFormProps = {
  source: SourceResponse
  fileInputRef: RefObject<HTMLInputElement | null>
  isPending: boolean
  onUpload: (input: UploadSourceVersionInput) => Promise<unknown>
  onCancel: () => void
  onUploaded: () => void
}

function UploadVersionForm({ source, fileInputRef, isPending, onUpload, onCancel, onUploaded }: UploadVersionFormProps) {
  const versionInputRef = useRef<HTMLInputElement>(null)
  const [file, setFile] = useState<File | null>(null)
  const [errors, setErrors] = useState<UploadFieldErrors>({})
  const versionId = useId()
  const versionErrorId = `${versionId}-error`

  function showErrors(nextErrors: UploadFieldErrors) {
    setErrors(nextErrors)
    if (nextErrors.file !== undefined) {
      fileInputRef.current?.focus()
    } else if (nextErrors.version !== undefined) {
      versionInputRef.current?.focus()
    }
  }

  function handleFileChange(selectedFile: File | null, fileError: string | null) {
    setFile(selectedFile)
    setErrors((current) => ({ ...current, file: fileError ?? undefined, form: undefined }))
  }

  function handleVersionChange() {
    if (errors.version !== undefined) {
      setErrors((current) => ({ ...current, version: undefined }))
    }
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (isPending) {
      return
    }
    const version = String(new FormData(event.currentTarget).get('version') ?? '')
    const fileError = validateUploadFile(file, source.kind)
    const versionError = validateVersionLabel(version)
    if (fileError !== null || versionError !== null || file === null) {
      showErrors({ file: fileError ?? undefined, version: versionError ?? undefined })
      return
    }
    setErrors({})
    try {
      await onUpload({ file, version })
      onUploaded()
    } catch (error) {
      showErrors(toUploadFieldErrors(error))
    }
  }

  return (
    <form className={styles.form} noValidate onSubmit={(event) => void handleSubmit(event)}>
      <p className={styles.description}>
        {`${source.name} · şu an ${versionLabel(source.version)} · ${formatInteger(source.chunkCount)} parça`}
      </p>
      <Callout>Yeni sürüm hazır olana kadar arama mevcut sürümün parçalarını döndürür.</Callout>
      {errors.form !== undefined ? <ErrorAlert message={errors.form} /> : null}
      <FileField
        ref={fileInputRef}
        label="Dosya"
        kind={source.kind}
        file={file}
        error={errors.file}
        onFileChange={handleFileChange}
      />
      <div className={styles.field}>
        <label className={styles.label} htmlFor={versionId}>
          Sürüm etiketi (isteğe bağlı)
        </label>
        <input
          ref={versionInputRef}
          id={versionId}
          className={styles.input}
          name="version"
          type="text"
          placeholder="ör. 3.2"
          maxLength={VERSION_LABEL_MAX_LENGTH}
          autoComplete="off"
          aria-invalid={errors.version !== undefined}
          aria-describedby={errors.version !== undefined ? versionErrorId : undefined}
          onChange={handleVersionChange}
        />
        {errors.version !== undefined ? (
          <p id={versionErrorId} className={styles.error} role="alert">
            {errors.version}
          </p>
        ) : null}
      </div>
      <div className={styles.actions}>
        <button type="button" className={button.secondary} disabled={isPending} onClick={onCancel}>
          Vazgeç
        </button>
        <button type="submit" className={button.primary} disabled={isPending}>
          {isPending ? 'Yükleniyor…' : 'Yeni sürümü yükle'}
        </button>
      </div>
    </form>
  )
}
