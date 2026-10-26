export const SOURCE_KINDS = ['WEB_CRAWL', 'PDF', 'MARKDOWN', 'CSV', 'TICKET_EXPORT'] as const
export type SourceKind = (typeof SOURCE_KINDS)[number]

export const UPLOADABLE_SOURCE_KINDS = ['PDF', 'MARKDOWN', 'CSV'] as const
export type UploadableSourceKind = (typeof UPLOADABLE_SOURCE_KINDS)[number]

export const SOURCE_STATUSES = ['INGESTING', 'READY', 'FAILED'] as const
export type SourceStatus = (typeof SOURCE_STATUSES)[number]

export const INGESTION_STAGES = ['QUEUED', 'PARSING', 'CHUNKING', 'EMBEDDING', 'WRITING', 'DONE', 'FAILED'] as const
export type IngestionStage = (typeof INGESTION_STAGES)[number]

export const INGESTION_ERROR_CODES = [
  'NO_TEXT_EXTRACTED',
  'FILE_UNREADABLE',
  'EMBEDDING_UNAVAILABLE',
  'EMBEDDING_MODEL_NOT_FOUND',
  'EMBEDDING_MODEL_MISMATCH',
  'INGESTION_INTERRUPTED',
  'INTERNAL_ERROR',
] as const
export type IngestionErrorCode = (typeof INGESTION_ERROR_CODES)[number]

export type IngestionJobResponse = {
  jobId: number
  workspaceId: number
  sourceId: number
  version?: string | null
  fileName: string
  stage: IngestionStage
  progress: number
  embeddedChunks: number
  totalChunks?: number | null
  errorCode?: IngestionErrorCode | null
  createdAt: string
  startedAt?: string | null
  finishedAt?: string | null
  statusUrl: string
}

export type SourceResponse = {
  sourceId: number
  workspaceId: number
  name: string
  kind: SourceKind
  version?: string | null
  chunkCount: number
  status: SourceStatus
  syncedAt?: string | null
  fileName?: string | null
  createdAt: string
  latestJob?: IngestionJobResponse | null
}

export type CreateSourceInput = {
  name: string
  file: File
}

export type UploadSourceVersionInput = {
  file: File
  version: string
}
