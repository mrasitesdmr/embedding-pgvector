import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { searchResult } from '../../../test/fixtures.ts'
import { renderApp } from '../../../test/renderWithProviders.tsx'
import { server } from '../../../test/server.ts'
import { EF_SEARCH_RULE_MESSAGE, LIMIT_RULE_MESSAGE } from '../model/searchRules.ts'
import { SEARCH_SETTINGS_STORAGE_KEY } from '../model/searchSettings.ts'

async function openPage() {
  const user = userEvent.setup()
  const view = renderApp('/w/42/arama')
  await screen.findByLabelText('Arama metni')
  return { user, view }
}

function settingsPanel(container: HTMLElement): HTMLDetailsElement {
  const panel = container.querySelector('details')
  if (panel === null) {
    throw new Error('settings panel not found')
  }
  return panel
}

describe('SearchSettingsPanel', () => {
  it('givenFreshPage_whenRendered_thenPanelClosedWithDefaultValuesAndLabels', async () => {
    // Act
    const { view } = await openPage()

    // Assert
    expect(settingsPanel(view.container).open).toBe(false)
    expect(screen.getByText('Gelişmiş ayarlar')).toBeInTheDocument()
    expect(screen.getByLabelText('Hybrid')).toBeChecked()
    expect(screen.getByLabelText('iterative scan')).toBeChecked()
    expect(screen.getByLabelText('ef_search')).toHaveValue(40)
    expect(screen.getByLabelText('Sonuç sayısı')).toHaveValue(10)
    expect(screen.getByLabelText('Sorgu planını getir')).not.toBeChecked()
  })

  it('givenKeywordMode_whenOpenSettings_thenVectorSettingsDisabled', async () => {
    // Arrange
    const { user } = await openPage()

    // Act
    await user.click(screen.getByLabelText('Kelime'))

    // Assert
    expect(screen.getByLabelText('post-filter')).toBeDisabled()
    expect(screen.getByLabelText('iterative scan')).toBeDisabled()
    expect(screen.getByLabelText('ef_search')).toBeDisabled()
    expect(screen.getByLabelText('Sorgu planını getir')).toBeDisabled()
    expect(screen.getByLabelText('Sonuç sayısı')).toBeEnabled()
  })

  it('givenVectorMode_whenSettingsShown_thenVectorSettingsEnabled', async () => {
    // Arrange
    const { user } = await openPage()

    // Act
    await user.click(screen.getByLabelText('Anlam'))

    // Assert
    expect(screen.getByLabelText('ef_search')).toBeEnabled()
    expect(screen.getByLabelText('post-filter')).toBeEnabled()
    expect(screen.getByLabelText('Sorgu planını getir')).toBeEnabled()
  })

  it('givenInvalidEfSearch_whenSubmit_thenShowsRuleAndDoesNotSearch', async () => {
    // Arrange
    let calls = 0
    server.use(
      http.post('/api/workspaces/:workspaceId/search', () => {
        calls += 1
        return HttpResponse.json(searchResult())
      }),
    )
    const { user, view } = await openPage()
    const efSearch = screen.getByLabelText('ef_search')
    await user.clear(efSearch)
    await user.type(efSearch, '5')

    // Act
    await user.type(screen.getByLabelText('Arama metni'), 'izin{Enter}')

    // Assert
    expect(screen.getByText(EF_SEARCH_RULE_MESSAGE)).toBeInTheDocument()
    expect(efSearch).toHaveAttribute('aria-invalid', 'true')
    expect(efSearch).toHaveAccessibleDescription(EF_SEARCH_RULE_MESSAGE)
    expect(settingsPanel(view.container).open).toBe(true)
    expect(calls).toBe(0)
    expect(JSON.parse(window.localStorage.getItem(SEARCH_SETTINGS_STORAGE_KEY) ?? '{}').efSearch).toBe(40)
  })

  it('givenInvalidLimit_whenTyped_thenShowsLimitRuleAndKeepsLastValidLimitInStorage', async () => {
    // Arrange
    const { user } = await openPage()
    const limit = screen.getByLabelText('Sonuç sayısı')

    // Act
    await user.clear(limit)
    await user.type(limit, '51')

    // Assert
    expect(screen.getByText(LIMIT_RULE_MESSAGE)).toBeInTheDocument()
    expect(limit).toHaveAttribute('aria-invalid', 'true')
    expect(JSON.parse(window.localStorage.getItem(SEARCH_SETTINGS_STORAGE_KEY) ?? '{}').limit).toBe(5)
  })

  it('givenInvalidEfSearchInKeywordMode_whenSearch_thenSearchRuns', async () => {
    // Arrange
    let calls = 0
    server.use(
      http.post('/api/workspaces/:workspaceId/search', () => {
        calls += 1
        return HttpResponse.json(searchResult())
      }),
    )
    const { user } = await openPage()
    const efSearch = screen.getByLabelText('ef_search')
    await user.clear(efSearch)
    await user.type(efSearch, '5')
    await user.click(screen.getByLabelText('Kelime'))

    // Act
    await user.type(screen.getByLabelText('Arama metni'), 'izin{Enter}')

    // Assert
    await screen.findByText('2 sonuç')
    expect(calls).toBe(1)
    expect(screen.queryByText(EF_SEARCH_RULE_MESSAGE)).not.toBeInTheDocument()
  })

  it('givenChangedSettings_whenResetToDefaults_thenRestoresDefaults', async () => {
    // Arrange
    const { user } = await openPage()
    await user.click(screen.getByLabelText('Anlam'))
    await user.click(screen.getByLabelText('post-filter'))
    const efSearch = screen.getByLabelText('ef_search')
    await user.clear(efSearch)
    await user.type(efSearch, '120')
    const limit = screen.getByLabelText('Sonuç sayısı')
    await user.clear(limit)
    await user.type(limit, '20')
    await user.click(screen.getByLabelText('Sorgu planını getir'))

    // Act
    await user.click(screen.getByRole('button', { name: 'Varsayılana dön' }))

    // Assert
    expect(screen.getByLabelText('Hybrid')).toBeChecked()
    expect(screen.getByLabelText('iterative scan')).toBeChecked()
    expect(screen.getByLabelText('ef_search')).toHaveValue(40)
    expect(screen.getByLabelText('Sonuç sayısı')).toHaveValue(10)
    expect(screen.getByLabelText('Sorgu planını getir')).not.toBeChecked()
    expect(JSON.parse(window.localStorage.getItem(SEARCH_SETTINGS_STORAGE_KEY) ?? '{}')).toEqual({
      mode: 'HYBRID',
      filterStrategy: 'ITERATIVE_SCAN',
      efSearch: 40,
      limit: 10,
      explain: false,
    })
  })

  it('givenChangedSettings_whenRemount_thenSettingsRestoredFromStorage', async () => {
    // Arrange
    const first = await openPage()
    await first.user.click(screen.getByLabelText('Anlam'))
    const limit = screen.getByLabelText('Sonuç sayısı')
    await first.user.clear(limit)
    await first.user.type(limit, '20')
    first.view.unmount()

    // Act
    await openPage()

    // Assert
    expect(screen.getByLabelText('Anlam')).toBeChecked()
    expect(screen.getByLabelText('Sonuç sayısı')).toHaveValue(20)
  })

  it('givenStoredCorruptSettings_whenRendered_thenDefaultsAreShown', async () => {
    // Arrange
    window.localStorage.setItem(SEARCH_SETTINGS_STORAGE_KEY, '{bozuk')

    // Act
    await openPage()

    // Assert
    expect(screen.getByLabelText('Hybrid')).toBeChecked()
  })
})
