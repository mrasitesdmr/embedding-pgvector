import type { StatusTone } from '../../../shared/components/StatusBadge.tsx'
import { UPLOADABLE_SOURCE_KINDS } from './types.ts'
import type { SourceKind, SourceResponse, SourceStatus, UploadableSourceKind } from './types.ts'

export const SOURCE_KIND_LABELS: Record<SourceKind, string> = {
  WEB_CRAWL: 'Web sitesi',
  PDF: 'PDF',
  MARKDOWN: 'Metin / Markdown',
  CSV: 'CSV',
  TICKET_EXPORT: 'Talep geçmişi',
}

export const SOURCE_STATUS_LABELS: Record<SourceStatus, string> = {
  READY: 'Hazır',
  INGESTING: 'İşleniyor',
  FAILED: 'Başarısız',
}

export const SOURCE_STATUS_TONES: Record<SourceStatus, StatusTone> = {
  READY: 'ok',
  INGESTING: 'run',
  FAILED: 'bad',
}

export const SOURCE_LIST_SIZE = 20
export const CHUNK_PAGE_SIZE = 20
export const CHUNK_PREVIEW_LENGTH = 160
export const UNTITLED_CHUNK_TITLE = 'Başlıksız parça'

export function chunkTitle(heading: string | null | undefined): string {
  return heading ?? UNTITLED_CHUNK_TITLE
}

export function previewChunkContent(content: string): string {
  return content.length > CHUNK_PREVIEW_LENGTH ? `${content.slice(0, CHUNK_PREVIEW_LENGTH)}…` : content
}

export function versionLabel(version: string | null | undefined): string {
  return version ? `v${version}` : 'sürüm yok'
}

export function sourceFileLine(source: SourceResponse): string {
  return `${source.fileName ?? '—'} · ${versionLabel(source.version)}`
}

export function isUploadableKind(kind: SourceKind): kind is UploadableSourceKind {
  return (UPLOADABLE_SOURCE_KINDS as readonly SourceKind[]).includes(kind)
}
