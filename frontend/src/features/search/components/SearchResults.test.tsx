import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { hit, searchResult } from '../../../test/fixtures.ts'
import { currentUrl, renderWithProviders } from '../../../test/renderWithProviders.tsx'
import { SearchResults } from './SearchResults.tsx'

describe('SearchResults', () => {
  it('givenHybridResult_whenRendered_thenShowsRankTitleTagsSnippetAndSourceLine', () => {
    // Act
    renderWithProviders(<SearchResults workspaceId={42} result={searchResult()} />)

    // Assert
    expect(screen.getByRole('heading', { name: '“Yıllık izin kaç gün?”' })).toBeInTheDocument()
    expect(screen.getByText('2 sonuç')).toBeInTheDocument()
    expect(screen.getByText('toplam 57 ms')).toBeInTheDocument()
    const items = within(screen.getByRole('list', { name: 'Arama sonuçları' })).getAllByRole('listitem')
    expect(items).toHaveLength(2)
    expect(within(items[0]).getByText('anlam')).toBeInTheDocument()
    expect(within(items[0]).getByText('kelime')).toBeInTheDocument()
    expect(within(items[0]).getByText('Yıllık', { selector: 'mark' })).toBeInTheDocument()
    expect(within(items[0]).getByText('İzin notları · parça 31 · #1380')).toBeInTheDocument()
    expect(within(items[0]).getByText('1')).toBeInTheDocument()
    expect(within(items[1]).getByText('anlam')).toBeInTheDocument()
    expect(within(items[1]).queryByText('kelime')).not.toBeInTheDocument()
    expect(within(items[1]).queryByText('Yıllık', { selector: 'mark' })).not.toBeInTheDocument()
  })

  it('givenHitWithoutHeading_whenRendered_thenShowsUntitledChunk', () => {
    // Act
    renderWithProviders(
      <SearchResults workspaceId={42} result={searchResult({ hits: [hit({ heading: null, matchedTerms: [] })], returnedCount: 1 })} />,
    )

    // Assert
    expect(screen.getByText('Başlıksız parça')).toBeInTheDocument()
  })

  it('givenHeadingTerm_whenRendered_thenHighlightsHeadingNotContent', () => {
    // Arrange
    const result = searchResult({
      hits: [hit({ heading: 'Parola sıfırlama', content: 'Parola bilgisi', matchedTerms: [{ field: 'HEADING', start: 0, end: 6 }] })],
      returnedCount: 1,
    })

    // Act
    renderWithProviders(<SearchResults workspaceId={42} result={result} />)

    // Assert
    const marks = screen.getAllByText('Parola', { selector: 'mark' })
    expect(marks).toHaveLength(1)
    expect(marks[0].closest('span')?.textContent).toContain('Parola')
  })

  it('givenHit_whenClicked_thenOpensChunkDialogWithoutChangingLocation', async () => {
    // Arrange
    const user = userEvent.setup()
    renderWithProviders(<SearchResults workspaceId={42} result={searchResult()} />, '/w/42/arama')
    const firstHit = within(screen.getByRole('list', { name: 'Arama sonuçları' })).getAllByRole('button')[0]

    // Act
    await user.click(firstHit)

    // Assert
    expect(await screen.findByRole('dialog', { name: 'Yıllık izin kaç gün?' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /Yıllık izin kaç gün?/ })).not.toBeInTheDocument()
    expect(currentUrl()).toBe('/w/42/arama')
  })

  it('givenLongContent_whenRendered_thenSnippetIsCutWithEllipsis', () => {
    // Arrange
    const content = `${'kelime '.repeat(40)}son`
    const result = searchResult({ hits: [hit({ content, matchedTerms: [] })], returnedCount: 1 })

    // Act
    renderWithProviders(<SearchResults workspaceId={42} result={result} />)

    // Assert
    expect(screen.queryByText(content)).not.toBeInTheDocument()
    expect(screen.getByText(/kelime …$/)).toBeInTheDocument()
  })

  it('givenShortVectorBranchWithPostFilter_whenRendered_thenWarnsAndSuggestsIterativeScan', () => {
    // Arrange
    const result = searchResult({ vectorShort: true, vectorCount: 4, limit: 10, filterStrategy: 'POST_FILTER' })

    // Act
    renderWithProviders(<SearchResults workspaceId={42} result={result} />)

    // Assert
    expect(screen.getByText(/istenen 10 sonuçtan sadece 4 tanesini buldu \(kısa kaldı\)/)).toBeInTheDocument()
    expect(screen.getByText(/iterative scan yapıp tekrar dene/)).toBeInTheDocument()
  })

  it('givenShortVectorBranchWithIterativeScan_whenRendered_thenSaysChunksMayBeInsufficient', () => {
    // Arrange
    const result = searchResult({ vectorShort: true, vectorCount: 4, limit: 10, filterStrategy: 'ITERATIVE_SCAN' })

    // Act
    renderWithProviders(<SearchResults workspaceId={42} result={result} />)

    // Assert
    expect(screen.getByText(/yeterli parça olmayabilir/)).toBeInTheDocument()
    expect(screen.queryByText(/kısa kaldı/)).not.toBeInTheDocument()
  })

  it('givenVectorBranchNotShort_whenRendered_thenNoWarning', () => {
    // Act
    renderWithProviders(<SearchResults workspaceId={42} result={searchResult({ vectorShort: false })} />)

    // Assert
    expect(screen.queryByText(/sadece .* tanesini buldu/)).not.toBeInTheDocument()
  })

  it('givenHtmlInContentAndName_whenRendered_thenShownAsText', () => {
    // Arrange
    const result = searchResult({
      hits: [
        hit({
          content: '<img src=x onerror=alert(1)> Yıllık',
          sourceName: '<b>Kaynak</b>',
          matchedTerms: [],
        }),
      ],
      returnedCount: 1,
    })

    // Act
    const { container } = renderWithProviders(<SearchResults workspaceId={42} result={result} />)

    // Assert
    expect(screen.getByText('<img src=x onerror=alert(1)> Yıllık')).toBeInTheDocument()
    expect(screen.getByText(/<b>Kaynak<\/b>/)).toBeInTheDocument()
    expect(container.querySelector('img')).toBeNull()
    expect(container.querySelector('b')).toBeNull()
  })

  it('givenHtmlInQuery_whenRendered_thenQueryShownAsText', () => {
    // Act
    const { container } = renderWithProviders(
      <SearchResults workspaceId={42} result={searchResult({ query: '<script>x</script>', hits: [], returnedCount: 0 })} />,
    )

    // Assert
    expect(screen.getByRole('heading', { name: '“<script>x</script>”' })).toBeInTheDocument()
    expect(container.querySelector('script')).toBeNull()
  })

  it('givenNoHits_whenRendered_thenNoHitListAndNoTechnicalTable', () => {
    // Act
    renderWithProviders(<SearchResults workspaceId={42} result={searchResult({ hits: [], returnedCount: 0 })} />)

    // Assert
    expect(screen.queryByRole('list', { name: 'Arama sonuçları' })).not.toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
    expect(screen.getByText('0 sonuç')).toBeInTheDocument()
  })
})
