import { describe, expect, it } from 'vitest'
import {
  DEFAULT_SEARCH_SETTINGS,
  EF_SEARCH_RULE_MESSAGE,
  LIMIT_RULE_MESSAGE,
  SEARCH_CHANNEL,
  canSearch,
  toSearchRequest,
  validateEfSearch,
  validateLimit,
} from './searchRules.ts'

describe('canSearch', () => {
  it.each([
    ['', false],
    ['   ', false],
    ['\t\n', false],
    [' a ', true],
    ['x'.repeat(500), true],
    ['x'.repeat(501), false],
    [` ${'x'.repeat(500)} `, true],
  ])('givenQuery_whenCanSearch_thenReturnsExpected (%j)', (input, expected) => {
    // Act
    const result = canSearch(input)

    // Assert
    expect(result).toBe(expected)
  })
})

describe('validateEfSearch', () => {
  it.each([
    ['9', EF_SEARCH_RULE_MESSAGE],
    ['1001', EF_SEARCH_RULE_MESSAGE],
    ['40.5', EF_SEARCH_RULE_MESSAGE],
    ['', EF_SEARCH_RULE_MESSAGE],
    ['abc', EF_SEARCH_RULE_MESSAGE],
    ['-20', EF_SEARCH_RULE_MESSAGE],
    ['10', null],
    ['1000', null],
    [' 40 ', null],
  ])('givenEfSearchInput_whenValidate_thenReturnsRuleMessageOutsideRange (%j)', (raw, expected) => {
    // Act
    const result = validateEfSearch(raw)

    // Assert
    expect(result).toBe(expected)
  })

  it('givenRuleMessage_whenRead_thenIsTurkishWithBounds', () => {
    expect(EF_SEARCH_RULE_MESSAGE).toBe('ef_search 10 ile 1000 arasında bir tam sayı olmalı.')
  })
})

describe('validateLimit', () => {
  it.each([
    ['0', LIMIT_RULE_MESSAGE],
    ['51', LIMIT_RULE_MESSAGE],
    ['', LIMIT_RULE_MESSAGE],
    ['2.5', LIMIT_RULE_MESSAGE],
    ['1', null],
    ['50', null],
  ])('givenLimitInput_whenValidate_thenReturnsRuleMessageOutsideRange (%j)', (raw, expected) => {
    // Act
    const result = validateLimit(raw)

    // Assert
    expect(result).toBe(expected)
  })

  it('givenRuleMessage_whenRead_thenIsTurkishWithBounds', () => {
    expect(LIMIT_RULE_MESSAGE).toBe('Sonuç sayısı 1 ile 50 arasında bir tam sayı olmalı.')
  })
})

describe('toSearchRequest', () => {
  it('givenDefaultSettings_whenToSearchRequest_thenSendsEverySettingWithAgentChannel', () => {
    // Act
    const request = toSearchRequest('  Yıllık izin kaç gün? ', DEFAULT_SEARCH_SETTINGS)

    // Assert
    expect(request).toEqual({
      query: 'Yıllık izin kaç gün?',
      mode: 'HYBRID',
      filterStrategy: 'ITERATIVE_SCAN',
      efSearch: 40,
      limit: 10,
      channel: 'Temsilci paneli',
      explain: false,
    })
    expect(SEARCH_CHANNEL).toBe('Temsilci paneli')
  })

  it('givenKeywordMode_whenToSearchRequest_thenOmitsVectorOnlySettings', () => {
    // Act
    const request = toSearchRequest('izin', { ...DEFAULT_SEARCH_SETTINGS, mode: 'KEYWORD', limit: 5 })

    // Assert
    expect(request).toEqual({ query: 'izin', mode: 'KEYWORD', limit: 5, channel: 'Temsilci paneli' })
  })
})
