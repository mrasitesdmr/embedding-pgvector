import { useRef } from 'react'
import { useNavigate, useSearchParams } from 'react-router'
import { useCurrentWorkspace } from '../../../shared/workspace/useCurrentWorkspace.ts'
import { UPLOAD_SOURCE_PARAM, parsePositiveId, uploadPath } from '../../../shared/workspace/workspaceRules.ts'
import { useCreateSourceMutation } from '../api/sourceQueries.ts'
import { CreateSourceForm } from '../components/CreateSourceForm.tsx'
import { UploadStatusPanel } from '../components/UploadStatusPanel.tsx'
import { UPLOAD_LIMITS_TEXT } from '../model/uploadRules.ts'
import styles from './UploadPage.module.css'

export function UploadPage() {
  const { workspaceId } = useCurrentWorkspace()
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const nameInputRef = useRef<HTMLInputElement>(null)
  const createSourceMutation = useCreateSourceMutation(workspaceId)
  const sourceId = parsePositiveId(searchParams.get(UPLOAD_SOURCE_PARAM))

  return (
    <div className={styles.page}>
      <title>Belge yükle · yanıt.</title>
      <header className={styles.header}>
        <h1 className={styles.title}>Belge yükle</h1>
        <p className={styles.description}>
          Belgeni yükle; parçalara bölünür ve embedding'e çevrilir. Bitince belgene soru sorabilirsin.
        </p>
      </header>

      <section className={styles.card} aria-label="Belge yükleme">
        {sourceId !== null ? (
          <UploadStatusPanel key={sourceId} workspaceId={workspaceId} sourceId={sourceId} />
        ) : (
          <>
            <p className={styles.limits}>{UPLOAD_LIMITS_TEXT}</p>
            <CreateSourceForm
              nameInputRef={nameInputRef}
              isPending={createSourceMutation.isPending}
              submitLabel="Belgeyi yükle"
              pendingLabel="Yükleniyor…"
              onCreate={createSourceMutation.mutateAsync}
              onCreated={(job) => void navigate(uploadPath(workspaceId, job.sourceId), { replace: true })}
            />
          </>
        )}
      </section>
    </div>
  )
}
