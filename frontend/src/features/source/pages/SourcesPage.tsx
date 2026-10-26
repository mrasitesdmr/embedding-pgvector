import { useState } from 'react'
import { messageForError } from '../../../shared/api/errorMessages.ts'
import { Breadcrumbs } from '../../../shared/components/Breadcrumbs.tsx'
import button from '../../../shared/components/button.module.css'
import { Callout } from '../../../shared/components/Callout.tsx'
import { EmptyState } from '../../../shared/components/EmptyState.tsx'
import { ErrorAlert } from '../../../shared/components/ErrorAlert.tsx'
import { formatInteger } from '../../../shared/format/numberFormat.ts'
import { useCurrentWorkspace } from '../../../shared/workspace/useCurrentWorkspace.ts'
import { embeddingModelsLabel, workspacePath } from '../../../shared/workspace/workspaceRules.ts'
import { useSourcesQuery } from '../api/sourceQueries.ts'
import { CreateSourceDialog } from '../components/CreateSourceDialog.tsx'
import { SourceTable } from '../components/SourceTable.tsx'
import styles from './SourcesPage.module.css'

export function SourcesPage() {
  const { workspaceId, workspace } = useCurrentWorkspace()
  const sourcesQuery = useSourcesQuery(workspaceId)
  const [isCreateDialogOpen, setIsCreateDialogOpen] = useState(false)

  return (
    <div className={styles.page}>
      <title>Bilgi kaynakları · yanıt.</title>
      <Breadcrumbs items={[{ label: workspace.name, to: workspacePath(workspaceId, 'arama') }, { label: 'Bilgi kaynakları' }]} />
      <header className={styles.header}>
        <div className={styles.titleRow}>
          <h1 className={styles.title}>Bilgi kaynakları</h1>
          <button type="button" className={button.primary} onClick={() => setIsCreateDialogOpen(true)}>
            Yeni kaynak ekle
          </button>
        </div>
        <p className={styles.description}>
          Arama sadece buradaki içerikte yapılır. Her kaynak parçalara bölünür ve embedding'e çevrilir.
        </p>
      </header>

      {sourcesQuery.isError ? (
        <ErrorAlert message={messageForError(sourcesQuery.error)} onRetry={() => void sourcesQuery.refetch()} />
      ) : sourcesQuery.data === undefined ? (
        <p className={styles.loading} aria-busy="true">
          Kaynaklar yükleniyor…
        </p>
      ) : sourcesQuery.data.content.length === 0 ? (
        <EmptyState title="Bu çalışma alanında henüz kaynak yok" />
      ) : (
        <SourceTable workspaceId={workspaceId} sources={sourcesQuery.data.content} />
      )}

      <CreateSourceDialog
        workspaceId={workspaceId}
        isOpen={isCreateDialogOpen}
        onClose={() => setIsCreateDialogOpen(false)}
      />

      <Callout>
        {`Toplam ${formatInteger(workspace.chunkCount)} parça · embedding modeli ${embeddingModelsLabel(workspace)}`}
      </Callout>
    </div>
  )
}
