import { describe, expect, it } from 'vitest'
import { hit } from '../../../test/fixtures.ts'
import {
  buildSnippet,
  hitContentSegments,
  hitSourceLine,
  hitTitle,
  hitTitleSegments,
  toHighlightSegments,
} from './searchHitText.ts'

describe('buildSnippet', () => {
  it('givenLongContent_whenBuildSnippet_thenCutsAtLastSpaceBefore188', () => {
    // Arrange
    const head = 'a'.repeat(179)
    const content = `${head} ${'b'.repeat(20)}`

    // Act
    const snippet = buildSnippet(content)

    // Assert
    expect(content).toHaveLength(200)
    expect(snippet).toBe(`${head} …`)
  })

  it('givenShortContent_whenBuildSnippet_thenReturnsContentAsIs', () => {
    // Arrange
    const content = 'x '.repeat(75).trim()

    // Act
    const snippet = buildSnippet(content)

    // Assert
    expect(snippet).toBe(content)
  })

  it('givenContentOfExactly190Characters_whenBuildSnippet_thenReturnsContentAsIs', () => {
    expect(buildSnippet('c'.repeat(190))).toBe('c'.repeat(190))
  })

  it('givenLongContentWithoutSpaces_whenBuildSnippet_thenCutsAt188', () => {
    expect(buildSnippet('c'.repeat(200))).toBe(`${'c'.repeat(188)} …`)
  })
})

describe('toHighlightSegments', () => {
  it('givenMatchedTerms_whenToHighlightSegments_thenSplitsTextIntoMarkedSegments', () => {
    // Act
    const segments = toHighlightSegments(
      'Yıllık izin kaç gün',
      [{ field: 'CONTENT', start: 0, end: 6 }],
      'CONTENT',
    )

    // Assert
    expect(segments).toEqual([
      { text: 'Yıllık', isMatch: true },
      { text: ' izin kaç gün', isMatch: false },
    ])
  })

  it('givenTurkishText_whenToHighlightSegments_thenOffsetsDoNotShift', () => {
    // Act
    const segments = toHighlightSegments('Parolamı unuttum', [{ field: 'CONTENT', start: 0, end: 8 }], 'CONTENT')

    // Assert
    expect(segments).toEqual([
      { text: 'Parolamı', isMatch: true },
      { text: ' unuttum', isMatch: false },
    ])
  })

  it('givenTermBeyondSnippet_whenToHighlightSegments_thenTermIsDropped', () => {
    // Act
    const segments = toHighlightSegments('kısa metin', [{ field: 'CONTENT', start: 5, end: 40 }], 'CONTENT')

    // Assert
    expect(segments).toEqual([{ text: 'kısa metin', isMatch: false }])
  })

  it('givenHeadingTerm_whenHighlightingContent_thenTermIsIgnored', () => {
    // Act
    const segments = toHighlightSegments('Yıllık izin', [{ field: 'HEADING', start: 0, end: 6 }], 'CONTENT')

    // Assert
    expect(segments).toEqual([{ text: 'Yıllık izin', isMatch: false }])
  })

  it('givenOverlappingAndUnsortedTerms_whenToHighlightSegments_thenMergesThem', () => {
    // Act
    const segments = toHighlightSegments(
      'abcdefghij',
      [
        { field: 'CONTENT', start: 6, end: 9 },
        { field: 'CONTENT', start: 1, end: 4 },
        { field: 'CONTENT', start: 3, end: 7 },
      ],
      'CONTENT',
    )

    // Assert
    expect(segments).toEqual([
      { text: 'a', isMatch: false },
      { text: 'bcdefghi', isMatch: true },
      { text: 'j', isMatch: false },
    ])
  })

  it('givenNoTerms_whenToHighlightSegments_thenSingleUnmarkedSegment', () => {
    expect(toHighlightSegments('metin', [], 'CONTENT')).toEqual([{ text: 'metin', isMatch: false }])
  })
})

describe('hitTitle', () => {
  it.each([
    [null, 'Başlıksız parça'],
    [undefined, 'Başlıksız parça'],
    ['Yıllık izin kaç gün?', 'Yıllık izin kaç gün?'],
  ])('givenHeading_whenHitTitle_thenFallsBackToUntitled (%s)', (heading, expected) => {
    expect(hitTitle(heading)).toBe(expected)
  })
})

describe('hitContentSegments', () => {
  it('givenContentLongerThanSnippet_whenHitContentSegments_thenWholeContentWithMatchesAfterSnippetLimit', () => {
    // Arrange
    const content = `${'a '.repeat(150)}masraf beyanı sonu`
    const target = hit({
      content,
      matchedTerms: [
        { field: 'CONTENT', start: 300, end: 306 },
        { field: 'CONTENT', start: 307, end: 313 },
      ],
    })

    // Act
    const segments = hitContentSegments(target)

    // Assert
    expect(content).toHaveLength(318)
    expect(segments.map((segment) => segment.text).join('')).toBe(content)
    expect(segments.filter((segment) => segment.isMatch).map((segment) => segment.text)).toEqual(['masraf', 'beyanı'])
  })

  it('givenTurkishLettersAndEmojiBeforeTerm_whenHitContentSegments_thenMarkIsExactWord', () => {
    // Arrange
    const target = hit({
      content: 'İzin 😀 süresi: yıllık izin',
      matchedTerms: [{ field: 'CONTENT', start: 23, end: 27 }],
    })

    // Act
    const segments = hitContentSegments(target)

    // Assert
    expect(segments.filter((segment) => segment.isMatch)).toEqual([{ text: 'izin', isMatch: true }])
  })

  it('givenHeadingTermOnly_whenHitContentSegments_thenNoMarkInContent', () => {
    // Arrange
    const target = hit({ content: 'Parola bilgisi', matchedTerms: [{ field: 'HEADING', start: 0, end: 6 }] })

    // Act
    const segments = hitContentSegments(target)

    // Assert
    expect(segments).toEqual([{ text: 'Parola bilgisi', isMatch: false }])
  })
})

describe('hitTitleSegments', () => {
  it('givenNullHeading_whenHitTitleSegments_thenUntitledChunk', () => {
    // Act
    const segments = hitTitleSegments(hit({ heading: null }))

    // Assert
    expect(segments).toEqual([{ text: 'Başlıksız parça', isMatch: false }])
  })

  it('givenHeadingTermAndContentTerm_whenHitTitleSegments_thenOnlyHeadingTermMarked', () => {
    // Arrange
    const target = hit({
      heading: 'Masraf beyanı',
      matchedTerms: [
        { field: 'HEADING', start: 0, end: 6 },
        { field: 'CONTENT', start: 7, end: 11 },
      ],
    })

    // Act
    const segments = hitTitleSegments(target)

    // Assert
    expect(segments).toEqual([
      { text: 'Masraf', isMatch: true },
      { text: ' beyanı', isMatch: false },
    ])
  })
})

describe('hitSourceLine', () => {
  it('givenHit_whenHitSourceLine_thenSourceNameChunkNoAndChunkId', () => {
    // Act
    const line = hitSourceLine(hit({ sourceName: 'Atlaskap-Lojistik-Surec-El-Kitabi', chunkNo: 31, chunkId: 1380 }))

    // Assert
    expect(line).toBe('Atlaskap-Lojistik-Surec-El-Kitabi · parça 31 · #1380')
  })
})
