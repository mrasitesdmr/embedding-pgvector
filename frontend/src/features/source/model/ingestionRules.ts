import { ingestionFailureReasonFor } from '../../../shared/api/errorMessages.ts'
import { formatInteger } from '../../../shared/format/numberFormat.ts'
import type { IngestionJobResponse, IngestionStage, SourceResponse, SourceStatus } from './types.ts'

export const INGESTION_POLL_INTERVAL_MS = 1000
export const COMPLETED_HIGHLIGHT_MS = 3000

export const INGESTION_STAGE_LABELS: Record<Exclude<IngestionStage, 'EMBEDDING'>, string> = {
  QUEUED: 'Sıraya alındı',
  PARSING: 'Dosya okunuyor',
  CHUNKING: 'Parçalara bölünüyor',
  WRITING: 'Yazılıyor',
  DONE: 'Hazır',
  FAILED: 'Başarısız',
}

export function isJobActive(job: IngestionJobResponse | null | undefined): boolean {
  return job !== null && job !== undefined && job.stage !== 'DONE' && job.stage !== 'FAILED'
}

export function isJobFailed(job: IngestionJobResponse | null | undefined): boolean {
  return job !== null && job !== undefined && job.stage === 'FAILED'
}

export function isSourceIngesting(source: SourceResponse, job: IngestionJobResponse | null | undefined): boolean {
  return isJobActive(job) || source.status === 'INGESTING'
}

export function displayedSourceStatus(source: SourceResponse, job: IngestionJobResponse | null | undefined): SourceStatus {
  return isSourceIngesting(source, job) ? 'INGESTING' : source.status
}

export function freshestJob(
  cached: IngestionJobResponse | null | undefined,
  listed: IngestionJobResponse | null | undefined,
): IngestionJobResponse | null {
  if (cached === null || cached === undefined || listed === null || listed === undefined) {
    return listed ?? cached ?? null
  }
  if (cached.jobId !== listed.jobId) {
    return listed
  }
  if (!isJobActive(listed)) {
    return listed
  }
  if (!isJobActive(cached)) {
    return cached
  }
  return cached.progress >= listed.progress ? cached : listed
}

export function ingestionStageText(job: IngestionJobResponse): string {
  if (job.stage !== 'EMBEDDING') {
    return INGESTION_STAGE_LABELS[job.stage]
  }
  const total = job.totalChunks === null || job.totalChunks === undefined ? '…' : formatInteger(job.totalChunks)
  return `Embed ediliyor · ${formatInteger(job.embeddedChunks)}/${total} parça`
}

export function progressPercent(job: IngestionJobResponse): number {
  return Math.min(100, Math.max(0, Math.round(job.progress * 100)))
}

export function ingestionDoneMessage(sourceName: string, job: IngestionJobResponse): string {
  const version = job.version ? ` v${job.version}` : ''
  return `${sourceName}${version} hazır · ${formatInteger(job.totalChunks ?? 0)} parça`
}

export function ingestionFailureLine(job: IngestionJobResponse): string {
  return `Son yükleme başarısız: ${ingestionFailureReasonFor(job.errorCode)}`
}
