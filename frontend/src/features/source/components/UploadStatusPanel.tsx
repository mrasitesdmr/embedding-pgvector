import { useState } from 'react'
import { Link } from 'react-router'
import { messageForError } from '../../../shared/api/errorMessages.ts'
import button from '../../../shared/components/button.module.css'
import { ErrorAlert } from '../../../shared/components/ErrorAlert.tsx'
import { formatInteger } from '../../../shared/format/numberFormat.ts'
import { uploadPath, workspacePath } from '../../../shared/workspace/workspaceRules.ts'
import { useSourceQuery } from '../api/sourceQueries.ts'
import { useSourceIngestion } from '../hooks/useSourceIngestion.ts'
import { ingestionDoneMessage, isJobFailed } from '../model/ingestionRules.ts'
import type { IngestionJobResponse, SourceResponse } from '../model/types.ts'
import { IngestionFailure } from './IngestionFailure.tsx'
import { IngestionProgress } from './IngestionProgress.tsx'
import { UploadVersionDialog } from './UploadVersionDialog.tsx'
import styles from './UploadStatusPanel.module.css'

type UploadStatusPanelProps = {
  workspaceId: number
  sourceId: number
}

export function UploadStatusPanel({ workspaceId, sourceId }: UploadStatusPanelProps) {
  const sourceQuery = useSourceQuery(workspaceId, sourceId)

  if (sourceQuery.data !== undefined && !sourceQuery.isError) {
    return <SourceUploadStatus workspaceId={workspaceId} source={sourceQuery.data} />
  }

  return (
    <div className={styles.panel} aria-live="polite">
      {sourceQuery.isError ? (
        <>
          <ErrorAlert message={messageForError(sourceQuery.error)} />
          <div className={styles.actions}>
            <Link className={button.secondary} to={uploadPath(workspaceId)}>
              Yeni belge yükle
            </Link>
          </div>
        </>
      ) : (
        <p className={styles.loading} aria-busy="true">
          Belgenin durumu yükleniyor…
        </p>
      )}
    </div>
  )
}

type SourceUploadStatusProps = {
  workspaceId: number
  source: SourceResponse
}

function SourceUploadStatus({ workspaceId, source }: SourceUploadStatusProps) {
  const { job, isIngesting } = useSourceIngestion(workspaceId, source)
  const [isRetryDialogOpen, setIsRetryDialogOpen] = useState(false)
  const isFailed = !isIngesting && (isJobFailed(job) || source.status === 'FAILED')

  return (
    <>
      <div className={styles.panel} aria-live="polite">
        {isIngesting ? (
          <>
            <h2 className={styles.title}>{source.name}</h2>
            {job !== null ? <IngestionProgress sourceName={source.name} job={job} /> : null}
            <p className={styles.note}>Bu sayfadan ayrılabilirsin; yükleme arka planda sürer.</p>
          </>
        ) : isFailed ? (
          <>
            <h2 className={styles.title}>Belge yüklenemedi</h2>
            <p className={styles.sourceLine}>{source.name}</p>
            {job !== null ? <IngestionFailure job={job} /> : null}
            <div className={styles.actions}>
              <button type="button" className={button.primary} onClick={() => setIsRetryDialogOpen(true)}>
                Yeniden dene
              </button>
              <Link className={button.secondary} to={uploadPath(workspaceId)}>
                Başka bir belge yükle
              </Link>
            </div>
          </>
        ) : (
          <>
            <h2 className={styles.title}>Belge hazır</h2>
            <p className={styles.sourceLine}>{readyLine(source, job)}</p>
            <div className={styles.actions}>
              <Link className={button.primary} to={workspacePath(workspaceId, 'arama')}>
                Soru sor
              </Link>
              <Link className={button.secondary} to={uploadPath(workspaceId)}>
                Başka bir belge yükle
              </Link>
            </div>
          </>
        )}
      </div>
      {isFailed ? (
        <UploadVersionDialog
          workspaceId={workspaceId}
          source={source}
          isOpen={isRetryDialogOpen}
          onClose={() => setIsRetryDialogOpen(false)}
        />
      ) : null}
    </>
  )
}

function readyLine(source: SourceResponse, job: IngestionJobResponse | null): string {
  return job !== null && job.stage === 'DONE'
    ? ingestionDoneMessage(source.name, job)
    : `${source.name} hazır · ${formatInteger(source.chunkCount)} parça`
}
