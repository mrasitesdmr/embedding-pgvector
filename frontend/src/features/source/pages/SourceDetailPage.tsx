import { useParams, useSearchParams } from 'react-router'
import { isApiErrorWithCode } from '../../../shared/api/ApiError.ts'
import { messageForError } from '../../../shared/api/errorMessages.ts'
import { useChunkQuery } from '../../../shared/chunk/chunkQueries.ts'
import type { ChunkResponse } from '../../../shared/chunk/types.ts'
import { Breadcrumbs } from '../../../shared/components/Breadcrumbs.tsx'
import { ErrorAlert } from '../../../shared/components/ErrorAlert.tsx'
import { NotFoundState } from '../../../shared/components/NotFoundState.tsx'
import { useCurrentWorkspace } from '../../../shared/workspace/useCurrentWorkspace.ts'
import { HIGHLIGHTED_CHUNK_PARAM, parsePositiveId, workspacePath } from '../../../shared/workspace/workspaceRules.ts'
import { useChunksInfiniteQuery, useSourceQuery } from '../api/sourceQueries.ts'
import { ChunkTable } from '../components/ChunkTable.tsx'
import { HighlightedChunkNotice } from '../components/HighlightedChunkNotice.tsx'
import { IngestionFailure } from '../components/IngestionFailure.tsx'
import { IngestionProgress } from '../components/IngestionProgress.tsx'
import { SourceInfoBoxes } from '../components/SourceInfoBoxes.tsx'
import { UploadVersionButton } from '../components/UploadVersionButton.tsx'
import { useSourceIngestion } from '../hooks/useSourceIngestion.ts'
import { displayedSourceStatus, isJobActive, isJobFailed } from '../model/ingestionRules.ts'
import type { SourceResponse } from '../model/types.ts'
import styles from './SourceDetailPage.module.css'

export function SourceDetailPage() {
  const { workspaceId } = useCurrentWorkspace()
  const { sourceId: sourceParam } = useParams()
  const [searchParams] = useSearchParams()
  const sourceId = parsePositiveId(sourceParam)
  const highlightedChunkId = parsePositiveId(searchParams.get(HIGHLIGHTED_CHUNK_PARAM))

  const sourceQuery = useSourceQuery(workspaceId, sourceId)
  const chunksQuery = useChunksInfiniteQuery(workspaceId, sourceId)
  const highlightedChunkQuery = useChunkQuery(workspaceId, sourceId, highlightedChunkId)
  const sourcesPath = workspacePath(workspaceId, 'kaynaklar')

  if (sourceId === null || isApiErrorWithCode(sourceQuery.error, 'SOURCE_NOT_FOUND')) {
    return <NotFoundState title="Kaynak bulunamadı" linkTo={sourcesPath} linkLabel="Bilgi kaynaklarına dön" />
  }
  if (sourceQuery.isError) {
    return <ErrorAlert message={messageForError(sourceQuery.error)} onRetry={() => void sourceQuery.refetch()} />
  }
  const source = sourceQuery.data
  if (source === undefined) {
    return (
      <p className={styles.loading} aria-busy="true">
        Kaynak yükleniyor…
      </p>
    )
  }

  const loadedChunks = chunksQuery.data?.pages.flatMap((page) => page.content) ?? []
  const highlightedChunk = highlightedChunkQuery.data
  const chunks: ChunkResponse[] =
    highlightedChunk === undefined
      ? loadedChunks
      : [highlightedChunk, ...loadedChunks.filter((chunk) => chunk.chunkId !== highlightedChunk.chunkId)]
  const embeddingModel = chunksQuery.data?.pages[0]?.content[0]?.embeddingModel ?? highlightedChunk?.embeddingModel ?? null

  return (
    <div className={styles.page}>
      <title>{`${source.name} · yanıt.`}</title>
      <Breadcrumbs items={[{ label: 'Bilgi kaynakları', to: sourcesPath }, { label: source.name }]} />
      <SourceDetailHeader workspaceId={workspaceId} source={source} embeddingModel={embeddingModel} />

      {highlightedChunkId !== null ? (
        <HighlightedChunkNotice
          chunkId={highlightedChunkId}
          isLoaded={highlightedChunk !== undefined}
          error={highlightedChunkQuery.error}
        />
      ) : null}

      {chunksQuery.isError ? (
        <ErrorAlert message={messageForError(chunksQuery.error)} onRetry={() => void chunksQuery.refetch()} />
      ) : chunksQuery.data === undefined ? (
        <p className={styles.loading} aria-busy="true">
          Parçalar yükleniyor…
        </p>
      ) : chunks.length === 0 ? (
        <p className={styles.loading}>Bu kaynağın henüz parçası yok.</p>
      ) : (
        <ChunkTable chunks={chunks} highlightedChunkId={highlightedChunk?.chunkId ?? null} />
      )}

      {chunksQuery.hasNextPage ? (
        <div className={styles.more}>
          <button
            type="button"
            className={styles.moreButton}
            disabled={chunksQuery.isFetchingNextPage}
            onClick={() => void chunksQuery.fetchNextPage()}
          >
            Daha fazla göster
          </button>
        </div>
      ) : null}
    </div>
  )
}

type SourceDetailHeaderProps = {
  workspaceId: number
  source: SourceResponse
  embeddingModel: string | null
}

function SourceDetailHeader({ workspaceId, source, embeddingModel }: SourceDetailHeaderProps) {
  const { job, isIngesting } = useSourceIngestion(workspaceId, source)
  return (
    <>
      <header className={styles.header}>
        <div className={styles.heading}>
          <h1 className={styles.title}>{source.name}</h1>
          {job !== null && isJobActive(job) ? <IngestionProgress sourceName={source.name} job={job} /> : null}
          {job !== null && isJobFailed(job) ? <IngestionFailure job={job} /> : null}
        </div>
        <UploadVersionButton workspaceId={workspaceId} source={source} isIngesting={isIngesting} />
      </header>
      <SourceInfoBoxes source={source} status={displayedSourceStatus(source, job)} embeddingModel={embeddingModel} />
    </>
  )
}
