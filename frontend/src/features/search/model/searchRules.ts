import type { FilterStrategy, SearchChunksRequest, SearchMode, SearchSettings } from './types.ts'

export const QUERY_MAX_LENGTH = 500
export const EF_SEARCH_MIN = 10
export const EF_SEARCH_MAX = 1000
export const EF_SEARCH_DEFAULT = 40
export const SEARCH_LIMIT_MIN = 1
export const SEARCH_LIMIT_MAX = 50
export const SEARCH_LIMIT_DEFAULT = 10
export const SEARCH_EVENTS_PAGE_SIZE = 20

export const DEFAULT_SEARCH_SETTINGS: SearchSettings = {
  mode: 'HYBRID',
  filterStrategy: 'ITERATIVE_SCAN',
  efSearch: EF_SEARCH_DEFAULT,
  limit: SEARCH_LIMIT_DEFAULT,
  explain: false,
}

export const SEARCH_CHANNEL = 'Temsilci paneli'

export const QUERY_RULE_MESSAGE = `Arama metni 1-${QUERY_MAX_LENGTH} karakter olmalı.`
export const EF_SEARCH_RULE_MESSAGE = `ef_search ${EF_SEARCH_MIN} ile ${EF_SEARCH_MAX} arasında bir tam sayı olmalı.`
export const LIMIT_RULE_MESSAGE = `Sonuç sayısı ${SEARCH_LIMIT_MIN} ile ${SEARCH_LIMIT_MAX} arasında bir tam sayı olmalı.`

export const MODE_LABELS: Record<SearchMode, string> = {
  KEYWORD: 'Kelime',
  VECTOR: 'Anlam',
  HYBRID: 'Hybrid',
}

export const MODE_INFO_LABELS: Record<SearchMode, string> = {
  KEYWORD: 'Kelime',
  VECTOR: 'Anlam (vector)',
  HYBRID: 'Hybrid (kelime + anlam)',
}

export const FILTER_LABELS: Record<FilterStrategy, string> = {
  POST_FILTER: 'post-filter',
  ITERATIVE_SCAN: 'iterative scan',
}

export function normalizeQuery(input: string): string {
  return input.trim()
}

export function canSearch(input: string): boolean {
  const query = normalizeQuery(input)
  return query.length >= 1 && query.length <= QUERY_MAX_LENGTH
}

export function parseIntegerInput(raw: string): number | null {
  const value = raw.trim()
  if (!/^[0-9]+$/.test(value)) {
    return null
  }
  const parsed = Number(value)
  return Number.isSafeInteger(parsed) ? parsed : null
}

export function isValidEfSearch(value: number): boolean {
  return Number.isInteger(value) && value >= EF_SEARCH_MIN && value <= EF_SEARCH_MAX
}

export function isValidLimit(value: number): boolean {
  return Number.isInteger(value) && value >= SEARCH_LIMIT_MIN && value <= SEARCH_LIMIT_MAX
}

export function validateEfSearch(raw: string): string | null {
  const value = parseIntegerInput(raw)
  return value !== null && isValidEfSearch(value) ? null : EF_SEARCH_RULE_MESSAGE
}

export function validateLimit(raw: string): string | null {
  const value = parseIntegerInput(raw)
  return value !== null && isValidLimit(value) ? null : LIMIT_RULE_MESSAGE
}

export function toSearchRequest(query: string, settings: SearchSettings): SearchChunksRequest {
  const request: SearchChunksRequest = {
    query: normalizeQuery(query),
    mode: settings.mode,
    limit: settings.limit,
    channel: SEARCH_CHANNEL,
  }
  if (settings.mode !== 'KEYWORD') {
    request.filterStrategy = settings.filterStrategy
    request.efSearch = settings.efSearch
    request.explain = settings.explain
  }
  return request
}
