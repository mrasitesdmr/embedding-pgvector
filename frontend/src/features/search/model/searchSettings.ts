import { DEFAULT_SEARCH_SETTINGS, isValidEfSearch, isValidLimit } from './searchRules.ts'
import { FILTER_STRATEGIES, SEARCH_MODES } from './types.ts'
import type { FilterStrategy, SearchMode, SearchSettings } from './types.ts'

export const SEARCH_SETTINGS_STORAGE_KEY = 'emb:search-settings:v1'

export function readSearchSettings(): SearchSettings {
  let stored: unknown
  try {
    const raw = window.localStorage.getItem(SEARCH_SETTINGS_STORAGE_KEY)
    if (raw === null) {
      return DEFAULT_SEARCH_SETTINGS
    }
    stored = JSON.parse(raw)
  } catch {
    return DEFAULT_SEARCH_SETTINGS
  }
  if (typeof stored !== 'object' || stored === null || Array.isArray(stored)) {
    return DEFAULT_SEARCH_SETTINGS
  }
  const value = stored as Record<string, unknown>
  return {
    mode: isSearchMode(value.mode) ? value.mode : DEFAULT_SEARCH_SETTINGS.mode,
    filterStrategy: isFilterStrategy(value.filterStrategy) ? value.filterStrategy : DEFAULT_SEARCH_SETTINGS.filterStrategy,
    efSearch: typeof value.efSearch === 'number' && isValidEfSearch(value.efSearch) ? value.efSearch : DEFAULT_SEARCH_SETTINGS.efSearch,
    limit: typeof value.limit === 'number' && isValidLimit(value.limit) ? value.limit : DEFAULT_SEARCH_SETTINGS.limit,
    explain: typeof value.explain === 'boolean' ? value.explain : DEFAULT_SEARCH_SETTINGS.explain,
  }
}

export function writeSearchSettings(settings: SearchSettings): void {
  try {
    window.localStorage.setItem(SEARCH_SETTINGS_STORAGE_KEY, JSON.stringify(settings))
  } catch {
    return
  }
}

function isSearchMode(value: unknown): value is SearchMode {
  return typeof value === 'string' && (SEARCH_MODES as readonly string[]).includes(value)
}

function isFilterStrategy(value: unknown): value is FilterStrategy {
  return typeof value === 'string' && (FILTER_STRATEGIES as readonly string[]).includes(value)
}
