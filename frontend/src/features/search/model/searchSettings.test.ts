import { describe, expect, it } from 'vitest'
import { DEFAULT_SEARCH_SETTINGS } from './searchRules.ts'
import { SEARCH_SETTINGS_STORAGE_KEY, readSearchSettings, writeSearchSettings } from './searchSettings.ts'

describe('readSearchSettings', () => {
  it('givenEmptyStorage_whenReadSearchSettings_thenReturnsDefaults', () => {
    // Act
    const settings = readSearchSettings()

    // Assert
    expect(settings).toEqual({
      mode: 'HYBRID',
      filterStrategy: 'ITERATIVE_SCAN',
      efSearch: 40,
      limit: 10,
      explain: false,
    })
  })

  it('givenCorruptJson_whenReadSearchSettings_thenReturnsDefaults', () => {
    // Arrange
    window.localStorage.setItem(SEARCH_SETTINGS_STORAGE_KEY, '{bozuk')

    // Act
    const settings = readSearchSettings()

    // Assert
    expect(settings).toEqual(DEFAULT_SEARCH_SETTINGS)
  })

  it('givenOutOfRangeEfSearch_whenReadSearchSettings_thenFallsBackPerField', () => {
    // Arrange
    window.localStorage.setItem(SEARCH_SETTINGS_STORAGE_KEY, JSON.stringify({ mode: 'VECTOR', efSearch: 5000 }))

    // Act
    const settings = readSearchSettings()

    // Assert
    expect(settings.mode).toBe('VECTOR')
    expect(settings.efSearch).toBe(40)
    expect(settings.limit).toBe(10)
  })

  it.each([
    ['null', 'null'],
    ['array', '[1,2]'],
    ['wrong types', JSON.stringify({ mode: 7, filterStrategy: 'X', efSearch: '50', limit: 'a', explain: 'yes' })],
  ])('givenUnexpectedShape_whenReadSearchSettings_thenReturnsDefaults (%s)', (_name, raw) => {
    // Arrange
    window.localStorage.setItem(SEARCH_SETTINGS_STORAGE_KEY, raw)

    // Act
    const settings = readSearchSettings()

    // Assert
    expect(settings).toEqual(DEFAULT_SEARCH_SETTINGS)
  })
})

describe('writeSearchSettings', () => {
  it('givenWrittenSettings_whenReadSearchSettings_thenReturnsSameSettings', () => {
    // Arrange
    const settings = { mode: 'VECTOR', filterStrategy: 'POST_FILTER', efSearch: 120, limit: 20, explain: true } as const

    // Act
    writeSearchSettings(settings)

    // Assert
    expect(readSearchSettings()).toEqual(settings)
    expect(window.localStorage.getItem('emb:search-settings:v1')).not.toBeNull()
  })
})
