import { describe, expect, it } from 'vitest'
import { searchResult } from '../../../test/fixtures.ts'
import { noResultHint, shortVectorWarning } from './searchNotices.ts'

describe('searchNotices', () => {
  it('givenKeywordMode_whenNoResultHint_thenSuggestsSemanticOrHybrid', () => {
    expect(noResultHint('KEYWORD')).toBe(
      'Kelime araması sadece aynı kelimeleri bulur. Anlam ya da Hybrid yöntemiyle tekrar dene.',
    )
  })

  it.each(['VECTOR', 'HYBRID'] as const)('givenMode_whenNoResultHint_thenSuggestsOtherWords (%s)', (mode) => {
    expect(noResultHint(mode)).toBe('Farklı kelimelerle aramayı dene.')
  })

  it('givenShortVectorBranchWithPostFilter_whenShortVectorWarning_thenSuggestsIterativeScan', () => {
    // Act
    const warning = shortVectorWarning(
      searchResult({ vectorShort: true, vectorCount: 4, limit: 10, filterStrategy: 'POST_FILTER' }),
    )

    // Assert
    expect(warning).toBe(
      'Anlam araması istenen 10 sonuçtan sadece 4 tanesini buldu (kısa kaldı). Gelişmiş ayarlarda filtre stratejisini iterative scan yapıp tekrar dene.',
    )
  })

  it('givenShortVectorBranchWithIterativeScan_whenShortVectorWarning_thenSaysNotEnoughChunks', () => {
    // Act
    const warning = shortVectorWarning(
      searchResult({ vectorShort: true, vectorCount: 4, limit: 10, filterStrategy: 'ITERATIVE_SCAN' }),
    )

    // Assert
    expect(warning).toBe(
      'Anlam araması istenen 10 sonuçtan sadece 4 tanesini buldu. Bu çalışma alanında yeterli parça olmayabilir.',
    )
  })

  it('givenVectorNotShort_whenShortVectorWarning_thenReturnsNull', () => {
    expect(shortVectorWarning(searchResult({ vectorShort: false }))).toBeNull()
  })
})
