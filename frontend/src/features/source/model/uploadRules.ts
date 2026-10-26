import { isUploadableKind } from './sourceRules.ts'
import type { SourceKind, UploadableSourceKind } from './types.ts'

export const ACCEPTED_EXTENSIONS = ['.pdf', '.txt', '.md', '.markdown', '.csv']

export const KIND_EXTENSIONS: Record<UploadableSourceKind, string[]> = {
  PDF: ['.pdf'],
  MARKDOWN: ['.txt', '.md', '.markdown'],
  CSV: ['.csv'],
}

export const UPLOAD_KIND_LABELS: Record<UploadableSourceKind, string> = {
  PDF: 'PDF',
  MARKDOWN: 'TXT, MD ya da MARKDOWN',
  CSV: 'CSV',
}

export const MAX_FILE_SIZE_BYTES = 20_971_520
export const SOURCE_NAME_MIN_LENGTH = 2
export const SOURCE_NAME_MAX_LENGTH = 80
export const VERSION_LABEL_MAX_LENGTH = 40

export const FILE_REQUIRED_MESSAGE = 'Yüklenecek dosyayı seç.'
export const FILE_TYPE_RULE_MESSAGE = 'Bu dosya türü desteklenmiyor. PDF, TXT, MD, MARKDOWN ya da CSV seç.'
export const FILE_EMPTY_RULE_MESSAGE = 'Dosya boş. İçinde metin olan bir dosya seç.'
export const FILE_SIZE_RULE_MESSAGE = "Dosya 20 MB'tan büyük olamaz."
export const SOURCE_NAME_RULE_MESSAGE = `Kaynak adı ${SOURCE_NAME_MIN_LENGTH}-${SOURCE_NAME_MAX_LENGTH} karakter olmalı.`
export const VERSION_RULE_MESSAGE = `Sürüm etiketi en fazla ${VERSION_LABEL_MAX_LENGTH} karakter olabilir.`
export const SOURCE_NAME_HINT = 'Boş bırakırsan dosya adı kullanılır.'
export const UPLOAD_LIMITS_TEXT = 'PDF, TXT, MD, MARKDOWN ya da CSV · en fazla 20 MB'

export function fileKindRuleMessage(kind: UploadableSourceKind): string {
  return `Bu kaynağa sadece ${UPLOAD_KIND_LABELS[kind]} dosyası yüklenebilir.`
}

export function fileExtension(fileName: string): string {
  const dotIndex = fileName.lastIndexOf('.')
  return dotIndex === -1 ? '' : fileName.slice(dotIndex).toLowerCase()
}

function allowedExtensions(kind?: SourceKind): string[] {
  return kind !== undefined && isUploadableKind(kind) ? KIND_EXTENSIONS[kind] : ACCEPTED_EXTENSIONS
}

export function acceptAttribute(kind?: SourceKind): string {
  return allowedExtensions(kind).join(',')
}

export function validateUploadFile(file: File | null, kind?: SourceKind): string | null {
  if (file === null) {
    return FILE_REQUIRED_MESSAGE
  }
  const extension = fileExtension(file.name)
  if (!ACCEPTED_EXTENSIONS.includes(extension)) {
    return FILE_TYPE_RULE_MESSAGE
  }
  if (kind !== undefined && isUploadableKind(kind) && !KIND_EXTENSIONS[kind].includes(extension)) {
    return fileKindRuleMessage(kind)
  }
  if (file.size === 0) {
    return FILE_EMPTY_RULE_MESSAGE
  }
  if (file.size > MAX_FILE_SIZE_BYTES) {
    return FILE_SIZE_RULE_MESSAGE
  }
  return null
}

export function normalizeSourceName(raw: string): string {
  return raw.trim()
}

export function sourceNameFromFileName(fileName: string): string {
  const dotIndex = fileName.lastIndexOf('.')
  const baseName = (dotIndex === -1 ? fileName : fileName.slice(0, dotIndex)).trim()
  return baseName.length > SOURCE_NAME_MAX_LENGTH ? baseName.slice(0, SOURCE_NAME_MAX_LENGTH).trimEnd() : baseName
}

export function resolveSourceName(rawName: string, file: File): string {
  const name = normalizeSourceName(rawName)
  return name === '' ? sourceNameFromFileName(file.name) : name
}

export function validateSourceName(raw: string): string | null {
  const name = normalizeSourceName(raw)
  return name.length < SOURCE_NAME_MIN_LENGTH || name.length > SOURCE_NAME_MAX_LENGTH ? SOURCE_NAME_RULE_MESSAGE : null
}

export function normalizeVersionLabel(raw: string): string {
  return raw.trim()
}

export function validateVersionLabel(raw: string): string | null {
  return normalizeVersionLabel(raw).length > VERSION_LABEL_MAX_LENGTH ? VERSION_RULE_MESSAGE : null
}
