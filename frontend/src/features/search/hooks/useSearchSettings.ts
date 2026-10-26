import { useState } from 'react'
import {
  DEFAULT_SEARCH_SETTINGS,
  parseIntegerInput,
  validateEfSearch,
  validateLimit,
} from '../model/searchRules.ts'
import { readSearchSettings, writeSearchSettings } from '../model/searchSettings.ts'
import type { FilterStrategy, SearchMode, SearchSettings } from '../model/types.ts'

export type SearchSettingsDraft = {
  mode: SearchMode
  filterStrategy: FilterStrategy
  efSearch: string
  limit: string
  explain: boolean
}

export type SearchSettingsErrors = {
  efSearch: string | null
  limit: string | null
}

export type SearchSettingsState = {
  draft: SearchSettingsDraft
  errors: SearchSettingsErrors
  settings: SearchSettings
  isValid: boolean
  update: (changes: Partial<SearchSettingsDraft>) => void
  reset: () => void
}

function toDraft(settings: SearchSettings): SearchSettingsDraft {
  return { ...settings, efSearch: String(settings.efSearch), limit: String(settings.limit) }
}

function validateDraft(draft: SearchSettingsDraft): SearchSettingsErrors {
  return {
    efSearch: draft.mode === 'KEYWORD' ? null : validateEfSearch(draft.efSearch),
    limit: validateLimit(draft.limit),
  }
}

function mergeValid(draft: SearchSettingsDraft, previous: SearchSettings): SearchSettings {
  const efSearch = parseIntegerInput(draft.efSearch)
  const limit = parseIntegerInput(draft.limit)
  return {
    mode: draft.mode,
    filterStrategy: draft.filterStrategy,
    explain: draft.explain,
    efSearch: validateEfSearch(draft.efSearch) === null && efSearch !== null ? efSearch : previous.efSearch,
    limit: validateLimit(draft.limit) === null && limit !== null ? limit : previous.limit,
  }
}

export function useSearchSettings(): SearchSettingsState {
  const [state, setState] = useState(() => {
    const stored = readSearchSettings()
    return { draft: toDraft(stored), settings: stored }
  })

  function update(changes: Partial<SearchSettingsDraft>) {
    setState((previous) => {
      const draft = { ...previous.draft, ...changes }
      const settings = mergeValid(draft, previous.settings)
      writeSearchSettings(settings)
      return { draft, settings }
    })
  }

  function reset() {
    writeSearchSettings(DEFAULT_SEARCH_SETTINGS)
    setState({ draft: toDraft(DEFAULT_SEARCH_SETTINGS), settings: DEFAULT_SEARCH_SETTINGS })
  }

  const errors = validateDraft(state.draft)
  return {
    draft: state.draft,
    errors,
    settings: state.settings,
    isValid: errors.efSearch === null && errors.limit === null,
    update,
    reset,
  }
}
