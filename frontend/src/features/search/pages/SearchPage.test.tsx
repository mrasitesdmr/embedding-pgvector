import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import { BELGELERIM, chunk, hit, searchResult } from '../../../test/fixtures.ts'
import { problem } from '../../../test/problem.ts'
import { currentUrl, renderApp } from '../../../test/renderWithProviders.tsx'
import { server } from '../../../test/server.ts'

type Captured = { bodies: unknown[] }

function captureSearchRequests(response: () => Response = () => HttpResponse.json(searchResult())): Captured {
  const captured: Captured = { bodies: [] }
  server.use(
    http.post('/api/workspaces/:workspaceId/search', async ({ request }) => {
      captured.bodies.push(await request.json())
      return response()
    }),
  )
  return captured
}

async function openSearchPage() {
  const user = userEvent.setup()
  renderApp('/w/42/arama')
  const input = await screen.findByLabelText('Arama metni')
  return { user, input }
}

describe('SearchPage', () => {
  it('givenValidQuery_whenPressEnter_thenPostsFullRequestWithAgentChannel', async () => {
    // Arrange
    const captured = captureSearchRequests()
    const { user, input } = await openSearchPage()

    // Act
    await user.type(input, 'Yıllık izin kaç gün?{Enter}')

    // Assert
    expect(await screen.findByText('2 sonuç')).toBeInTheDocument()
    expect(captured.bodies).toEqual([
      {
        query: 'Yıllık izin kaç gün?',
        mode: 'HYBRID',
        filterStrategy: 'ITERATIVE_SCAN',
        efSearch: 40,
        limit: 10,
        channel: 'Temsilci paneli',
        explain: false,
      },
    ])
    expect(screen.getByText(/Belgelerim çalışma alanındaki belgelerde aranır/)).toBeInTheDocument()
  })

  it('givenKeywordMode_whenSearch_thenBodyOmitsVectorSettings', async () => {
    // Arrange
    const captured = captureSearchRequests()
    const { user, input } = await openSearchPage()
    await user.click(screen.getByLabelText('Kelime'))

    // Act
    await user.type(input, 'izin{Enter}')

    // Assert
    await screen.findByText('2 sonuç')
    expect(captured.bodies).toEqual([{ query: 'izin', mode: 'KEYWORD', limit: 10, channel: 'Temsilci paneli' }])
  })

  it('givenWhitespaceQuery_whenTyping_thenSearchButtonDisabledAndNoRequest', async () => {
    // Arrange
    const captured = captureSearchRequests()
    const { user, input } = await openSearchPage()

    // Act
    await user.type(input, '   {Enter}')

    // Assert
    expect(screen.getByRole('button', { name: 'Ara' })).toBeDisabled()
    expect(captured.bodies).toEqual([])
    expect(screen.getByText('Belgene bir soru sorarak başla')).toBeInTheDocument()
  })

  it('givenAnyInput_whenRendered_thenInputLimitedTo500Characters', async () => {
    // Act
    const { input } = await openSearchPage()

    // Assert
    expect(input).toHaveAttribute('maxlength', '500')
  })

  it('givenPendingSearch_whenSubmittingAgain_thenSendsSingleRequest', async () => {
    // Arrange
    let release: () => void = () => {}
    const gate = new Promise<void>((resolve) => {
      release = resolve
    })
    let calls = 0
    server.use(
      http.post('/api/workspaces/:workspaceId/search', async () => {
        calls += 1
        await gate
        return HttpResponse.json(searchResult())
      }),
    )
    const { user, input } = await openSearchPage()

    // Act
    await user.type(input, 'Yıllık izin kaç gün?{Enter}')
    await user.type(input, '{Enter}')

    // Assert
    const button = await screen.findByRole('button', { name: 'Aranıyor…' })
    expect(button).toBeDisabled()
    const results = screen.getByRole('region', { name: 'Sonuçlar' })
    expect(results).toHaveAttribute('aria-busy', 'true')
    expect(within(results).getByText('“Yıllık izin kaç gün?”')).toBeInTheDocument()
    expect(within(results).getByText('aranıyor…')).toBeInTheDocument()
    release()
    expect(await screen.findByRole('button', { name: 'Ara' })).toBeEnabled()
    expect(calls).toBe(1)
  })

  it('givenNoSources_whenOpenSearchPage_thenShowsNoDocumentStateInsteadOfForm', async () => {
    // Arrange
    const popularRequests: string[] = []
    server.use(
      http.get('/api/workspaces/:workspaceId', () => HttpResponse.json({ ...BELGELERIM, sourceCount: 0, chunkCount: 0 })),
      http.get('/api/workspaces/:workspaceId/search-events/popular-queries', ({ request }) => {
        popularRequests.push(request.url)
        return HttpResponse.json({ queries: [] })
      }),
    )

    // Act
    renderApp('/w/42/arama')

    // Assert
    expect(await screen.findByText('Henüz belge yüklenmedi')).toBeInTheDocument()
    expect(screen.getByText('Soru sorabilmek için önce bir belge yükle.')).toBeInTheDocument()
    expect(screen.getAllByRole('link', { name: 'Belge yükle' }).map((link) => link.getAttribute('href'))).toEqual([
      '/w/42/belge-yukle',
      '/w/42/belge-yukle',
    ])
    expect(screen.queryByLabelText('Arama metni')).not.toBeInTheDocument()
    expect(screen.queryByText('Sık aranan:')).not.toBeInTheDocument()
    expect(popularRequests).toEqual([])
  })

  it('givenNoPopularQueries_whenOpenSearchPage_thenNoSuggestionsAndStartPrompt', async () => {
    // Act
    await openSearchPage()

    // Assert
    expect(screen.queryByText('Sık aranan:')).not.toBeInTheDocument()
    expect(screen.getByText('Belgene bir soru sorarak başla')).toBeInTheDocument()
    expect(screen.getByText('Sorunu kendi kelimelerinle yaz; en ilgili parçalar listelenir.')).toBeInTheDocument()
  })

  it('givenPopularQueries_whenClickItem_thenFillsInputAndSearches', async () => {
    // Arrange
    server.use(
      http.get('/api/workspaces/:workspaceId/search-events/popular-queries', () =>
        HttpResponse.json({
          queries: [
            { query: 'parolamı unuttum hesabım kilitlendi', searchCount: 2, lastSearchedAt: '2026-10-25T09:20:11.437Z' },
            { query: 'Yıllık izin kaç gün?', searchCount: 1, lastSearchedAt: '2026-10-25T09:10:00Z' },
          ],
        }),
      ),
    )
    const captured = captureSearchRequests()
    const { user, input } = await openSearchPage()
    const first = await screen.findByRole('button', { name: 'parolamı unuttum hesabım kilitlendi' })

    expect(screen.getByText('Sık arananlardan birini seç ya da kendi sorunu yaz.')).toBeInTheDocument()

    // Act
    const items = screen.getAllByRole('button', { name: /unuttum|izin/ }).map((button) => button.textContent)
    await user.click(screen.getByRole('button', { name: 'Yıllık izin kaç gün?' }))

    // Assert
    expect(first).toBeInTheDocument()
    expect(items).toEqual(['parolamı unuttum hesabım kilitlendi', 'Yıllık izin kaç gün?'])
    await screen.findByText('2 sonuç')
    expect(input).toHaveValue('Yıllık izin kaç gün?')
    expect(captured.bodies).toHaveLength(1)
    expect(captured.bodies[0]).toMatchObject({ query: 'Yıllık izin kaç gün?' })
  })

  it('givenSearchSucceeded_whenResultsShown_thenPopularQueriesRefetchedWithoutReload', async () => {
    // Arrange
    let popularCalls = 0
    server.use(
      http.get('/api/workspaces/:workspaceId/search-events/popular-queries', () => {
        popularCalls += 1
        return HttpResponse.json({
          queries:
            popularCalls === 1
              ? []
              : [{ query: 'Masraf beyanı kaç gün içinde yapılır?', searchCount: 1, lastSearchedAt: '2026-10-25T09:20:11Z' }],
        })
      }),
    )
    captureSearchRequests()
    const { user, input } = await openSearchPage()
    await waitFor(() => expect(popularCalls).toBe(1))

    // Act
    await user.type(input, 'Masraf beyanı kaç gün içinde yapılır?{Enter}')

    // Assert
    expect(await screen.findByRole('button', { name: 'Masraf beyanı kaç gün içinde yapılır?' })).toBeInTheDocument()
    expect(popularCalls).toBe(2)
  })

  it('givenPopularQueriesFail_whenOpenSearchPage_thenHidesSuggestionsAndSearchWorks', async () => {
    // Arrange
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {})
    server.use(http.get('/api/workspaces/:workspaceId/search-events/popular-queries', () => problem(500, 'INTERNAL_ERROR')))
    const captured = captureSearchRequests()
    const { user, input } = await openSearchPage()

    // Act
    await user.type(input, 'izin{Enter}')

    // Assert
    await screen.findByText('2 sonuç')
    expect(screen.queryByText('Sık aranan:')).not.toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(captured.bodies).toHaveLength(1)
    expect(consoleError).toHaveBeenCalled()
    consoleError.mockRestore()
  })

  it('givenDefaultAndChangedSettings_whenRendered_thenInfoLineShowsModeLimitAndModel', async () => {
    // Arrange
    const { user } = await openSearchPage()
    const infoLine = screen.getByRole('list', { name: 'Arama bilgisi' })

    // Assert
    expect(infoLine).toHaveTextContent('Arama: Hybrid (kelime + anlam)')
    expect(infoLine).toHaveTextContent('Sonuç: en fazla 10')
    expect(infoLine).toHaveTextContent('Embedding: bge-m3@1024')

    // Act
    await user.click(screen.getByLabelText('Kelime'))
    const limit = screen.getByLabelText('Sonuç sayısı')
    await user.clear(limit)
    await user.type(limit, '5')

    // Assert
    expect(infoLine).toHaveTextContent('Arama: Kelime')
    expect(infoLine).toHaveTextContent('Sonuç: en fazla 5')
  })

  it('givenKeywordModeNoHits_whenSearch_thenShowsEmptyStateWithSemanticHint', async () => {
    // Arrange
    const query = 'parolamı unuttum hesabım kilitlendi'
    captureSearchRequests(() =>
      HttpResponse.json(searchResult({ query, mode: 'KEYWORD', hits: [], returnedCount: 0, vectorCount: null })),
    )
    const { user, input } = await openSearchPage()
    await user.click(screen.getByLabelText('Kelime'))

    // Act
    await user.type(input, `${query}{Enter}`)

    // Assert
    expect(await screen.findByText(`“${query}” için sonuç bulunamadı`)).toBeInTheDocument()
    expect(screen.getByText('Kelime araması sadece aynı kelimeleri bulur. Anlam ya da Hybrid yöntemiyle tekrar dene.')).toBeInTheDocument()
  })

  it('givenHybridModeNoHits_whenSearch_thenSuggestsOtherWords', async () => {
    // Arrange
    captureSearchRequests(() => HttpResponse.json(searchResult({ hits: [], returnedCount: 0 })))
    const { user, input } = await openSearchPage()

    // Act
    await user.type(input, 'netflix{Enter}')

    // Assert
    expect(await screen.findByText('Farklı kelimelerle aramayı dene.')).toBeInTheDocument()
    expect(screen.queryByText(/Anlam ya da Hybrid yöntemiyle/)).not.toBeInTheDocument()
  })

  it.each([
    [409, 'EMBEDDING_MODEL_MISMATCH', 'Ayarlardaki embedding modeli, bu çalışma alanının parçalarını oluşturan modelden farklı.'],
    [503, 'EMBEDDING_UNAVAILABLE', "Embedding servisine (Ollama) ulaşılamıyor. Ollama'nın çalıştığını kontrol et ya da Kelime yöntemiyle ara."],
    [503, 'EMBEDDING_MODEL_NOT_FOUND', "Embedding modeli Ollama'da yüklü değil. Modeli indir (ollama pull bge-m3) ya da Kelime yöntemiyle ara."],
    [500, 'INTERNAL_ERROR', 'Sunucuda beklenmeyen bir hata oluştu. Tekrar dene.'],
  ])('givenSearchError_whenSearch_thenShowsTurkishMessage (%i %s)', async (status, errorCode, message) => {
    // Arrange
    server.use(http.post('/api/workspaces/:workspaceId/search', () => problem(status, errorCode)))
    const { user, input } = await openSearchPage()

    // Act
    await user.type(input, 'Yıllık izin kaç gün?{Enter}')

    // Assert
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent(message)
    expect(alert).not.toHaveTextContent('Embedding service is not reachable')
  })

  it('givenNetworkFailure_whenSearch_thenShowsUnreachableMessage', async () => {
    // Arrange
    server.use(http.post('/api/workspaces/:workspaceId/search', () => HttpResponse.error()))
    const { user, input } = await openSearchPage()

    // Act
    await user.type(input, 'Yıllık izin kaç gün?{Enter}')

    // Assert
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Sunucuya ulaşılamıyor. Bağlantını ve sistemin çalıştığını kontrol et.',
    )
  })

  it('givenGatewayDown_whenSearchSubmitted_thenShowsServerUnreachableMessage', async () => {
    // Arrange
    server.use(
      http.post(
        '/api/workspaces/:workspaceId/search',
        () => new HttpResponse('<html><body>502 Bad Gateway</body></html>', { status: 502, headers: { 'Content-Type': 'text/html' } }),
      ),
    )
    const { user, input } = await openSearchPage()

    // Act
    await user.type(input, 'Yıllık izin kaç gün?{Enter}')

    // Assert
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Sunucuya ulaşılamıyor. Bağlantını ve sistemin çalıştığını kontrol et.',
    )
  })

  it('givenValidationErrorOnQuery_whenSearch_thenShowsRuleMessageUnderInput', async () => {
    // Arrange
    server.use(
      http.post('/api/workspaces/:workspaceId/search', () =>
        problem(400, 'VALIDATION_FAILED', [{ field: 'query', message: 'size must be between 1 and 500' }]),
      ),
    )
    const { user, input } = await openSearchPage()

    // Act
    await user.type(input, 'izin{Enter}')

    // Assert
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Arama metni 1-500 karakter olmalı.')
    expect(alert).not.toHaveTextContent('size must be')
    expect(input).toHaveAttribute('aria-invalid', 'true')
  })

  it('givenHitsAndSearchEventStats_whenSearchCompletes_thenResultIsKeptAfterVisitingSourceAndGoingBack', async () => {
    // Arrange
    captureSearchRequests(() => HttpResponse.json(searchResult({ hits: [hit()], returnedCount: 1 })))
    const { user, input } = await openSearchPage()
    await user.type(input, 'Yıllık izin kaç gün?{Enter}')
    await user.click(await screen.findByRole('button', { name: /Yıllık izin kaç gün?/ }))

    // Act
    await user.click(await screen.findByRole('link', { name: 'Kaynakta göster' }))
    await screen.findByText(/Arama sonucundan açılan parça vurgulandı/)
    await user.click(screen.getByRole('link', { name: 'Arama' }))

    // Assert
    expect(await screen.findByRole('button', { name: /Yıllık izin kaç gün?/ })).toBeInTheDocument()
    expect(screen.getByLabelText('Arama metni')).toHaveValue('Yıllık izin kaç gün?')
  })

  describe('hit dialog', () => {
    const THREE_HITS = () =>
      searchResult({
        hits: [
          hit({ rank: 1, chunkId: 1380, heading: 'Yıllık izin kaç gün?', matchedTerms: [] }),
          hit({ rank: 2, chunkId: 1381, heading: 'Masraf beyanı', content: 'Masraf beyanı on gün içinde yapılır.', matchedTerms: [] }),
          hit({ rank: 3, chunkId: 1382, heading: 'Seyahat izni', content: 'Seyahat önceden onaylanır.', matchedTerms: [] }),
        ],
        returnedCount: 3,
      })

    async function searchThreeHits() {
      let postCount = 0
      server.use(
        http.post('/api/workspaces/:workspaceId/search', () => {
          postCount += 1
          return HttpResponse.json(THREE_HITS())
        }),
      )
      const { user, input } = await openSearchPage()
      await user.type(input, 'Yıllık izin kaç gün?{Enter}')
      await screen.findByText('3 sonuç')
      return { user, input, posts: () => postCount }
    }

    function hitButtons() {
      return within(screen.getByRole('list', { name: 'Arama sonuçları' })).getAllByRole('button')
    }

    it('givenSearchResults_whenHitOpenedAndClosedWithEscape_thenResultsKeptFocusOnHitAndNoNewSearch', async () => {
      // Arrange
      const { user, input, posts } = await searchThreeHits()
      const namesBefore = hitButtons().map((button) => button.textContent)

      // Act
      await user.click(hitButtons()[0])
      await screen.findByRole('dialog', { name: 'Yıllık izin kaç gün?' })
      await user.keyboard('{Escape}')

      // Assert
      await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
      expect(input).toHaveValue('Yıllık izin kaç gün?')
      expect(hitButtons().map((button) => button.textContent)).toEqual(namesBefore)
      expect(hitButtons()).toHaveLength(3)
      expect(hitButtons()[0]).toHaveFocus()
      expect(posts()).toBe(1)
    })

    it('givenSearchResults_whenSecondHitOpenedWithKeyboard_thenFocusStaysInDialogAndReturns', async () => {
      // Arrange
      const { user } = await searchThreeHits()
      hitButtons()[0].focus()

      // Act
      await user.tab()
      await user.keyboard('{Enter}')
      const dialog = await screen.findByRole('dialog', { name: 'Masraf beyanı' })

      // Assert
      const visited: string[] = []
      for (let step = 0; step < 6; step += 1) {
        await user.tab()
        expect(dialog).toContainElement(document.activeElement as HTMLElement)
        visited.push(document.activeElement?.getAttribute('aria-label') ?? document.activeElement?.textContent ?? '')
      }
      expect(visited).toEqual(['Parça metni', 'Kapat', 'Kaynakta göster', 'Paneli kapat', 'Parça metni', 'Kapat'])
      await user.keyboard('{Escape}')
      await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
      expect(hitButtons()[1]).toHaveFocus()
    })

    it.each(['Paneli kapat', 'Kapat', 'backdrop'])(
      'givenOpenedHit_whenClosedWith_%s_thenResultsStay',
      async (control) => {
        // Arrange
        const { user } = await searchThreeHits()
        await user.click(hitButtons()[1])
        await screen.findByRole('dialog')

        // Act
        await user.click(control === 'backdrop' ? screen.getByRole('dialog') : screen.getByRole('button', { name: control }))

        // Assert
        await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
        expect(hitButtons()).toHaveLength(3)
        expect(hitButtons()[1]).toHaveFocus()
      },
    )

    it('givenOpenedHit_whenSourceLinkClicked_thenSourceDetailWithFullHighlightedChunk', async () => {
      // Arrange
      const fullText = `${'a'.repeat(150)}\n${'b'.repeat(249)}`
      server.use(
        http.get('/api/workspaces/:workspaceId/sources/:sourceId/chunks/:chunkId', () =>
          HttpResponse.json(chunk(1380, { content: fullText, heading: 'Yıllık izin kaç gün?' })),
        ),
        http.get('/api/workspaces/:workspaceId/sources/:sourceId/chunks', () =>
          HttpResponse.json({
            content: [chunk(1380, { content: fullText, heading: 'Yıllık izin kaç gün?' }), chunk(1381, { content: 'c'.repeat(400) })],
            page: 0,
            size: 20,
            totalElements: 2,
            totalPages: 1,
          }),
        ),
      )
      const { user } = await searchThreeHits()
      await user.click(hitButtons()[0])

      // Act
      await user.click(await screen.findByRole('link', { name: 'Kaynakta göster' }))

      // Assert
      await screen.findByText(/Arama sonucundan açılan parça vurgulandı/)
      expect(currentUrl()).toBe('/w/42/kaynaklar/10001?parca=1380')
      const rows = await screen.findAllByRole('row')
      expect(rows[1].querySelector('.fullText')?.textContent).toBe(fullText)
      expect(rows[1].textContent).not.toContain('…')
    })

    it('givenHit_whenDoubleClicked_thenSingleDialog', async () => {
      // Arrange
      const { user } = await searchThreeHits()

      // Act
      await user.dblClick(hitButtons()[0])

      // Assert
      await screen.findByRole('dialog')
      expect(screen.getAllByRole('dialog')).toHaveLength(1)
    })
  })
})
