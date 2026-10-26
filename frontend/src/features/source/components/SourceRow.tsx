import type { MouseEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import table from '../../../shared/components/table.module.css'
import { EMPTY_VALUE, formatInteger } from '../../../shared/format/numberFormat.ts'
import { formatDateTime } from '../../../shared/format/dateFormat.ts'
import { sourceDetailPath } from '../../../shared/workspace/workspaceRules.ts'
import { useRecentlyCompletedSourceIds } from '../hooks/useRecentlyCompletedSourceIds.ts'
import { useSourceIngestion } from '../hooks/useSourceIngestion.ts'
import { displayedSourceStatus, isJobActive, isJobFailed } from '../model/ingestionRules.ts'
import { SOURCE_KIND_LABELS, sourceFileLine } from '../model/sourceRules.ts'
import type { SourceResponse } from '../model/types.ts'
import { IngestionFailure } from './IngestionFailure.tsx'
import { IngestionProgress } from './IngestionProgress.tsx'
import { SourceStatusBadge } from './SourceStatusBadge.tsx'
import { UploadVersionButton } from './UploadVersionButton.tsx'
import styles from './SourceRow.module.css'

const ROW_CLICK_IGNORED_SELECTOR = 'a, button, dialog'

type SourceRowProps = {
  workspaceId: number
  source: SourceResponse
}

export function SourceRow({ workspaceId, source }: SourceRowProps) {
  const navigate = useNavigate()
  const { job, isIngesting } = useSourceIngestion(workspaceId, source)
  const isJustCompleted = useRecentlyCompletedSourceIds().has(source.sourceId)
  const detailPath = sourceDetailPath(workspaceId, source.sourceId)

  function handleRowClick(event: MouseEvent<HTMLTableRowElement>) {
    if (event.target instanceof Element && event.target.closest(ROW_CLICK_IGNORED_SELECTOR) !== null) {
      return
    }
    void navigate(detailPath)
  }

  return (
    <tr className={isJustCompleted ? `${styles.row} ${styles.isJustCompleted}` : styles.row} onClick={handleRowClick}>
      <td>
        <Link className={`${table.primary} ${styles.name}`} to={detailPath}>
          {source.name}
        </Link>
        <span className={table.secondary}>{sourceFileLine(source)}</span>
        {job !== null && isJobActive(job) ? <IngestionProgress sourceName={source.name} job={job} /> : null}
        {job !== null && isJobFailed(job) ? <IngestionFailure job={job} /> : null}
        <div className={styles.actions}>
          <UploadVersionButton workspaceId={workspaceId} source={source} isIngesting={isIngesting} />
        </div>
      </td>
      <td>{SOURCE_KIND_LABELS[source.kind]}</td>
      <td className={table.num}>{formatInteger(source.chunkCount)}</td>
      <td>
        <SourceStatusBadge status={displayedSourceStatus(source, job)} />
      </td>
      <td className={table.num}>
        {source.syncedAt ? <time dateTime={source.syncedAt}>{formatDateTime(source.syncedAt)}</time> : EMPTY_VALUE}
      </td>
    </tr>
  )
}
