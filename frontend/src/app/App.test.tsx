import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { BELGELERIM, DENEME, hit, pageOf, searchResult, stats } from '../test/fixtures.ts'
import { currentUrl, renderApp } from '../test/renderWithProviders.tsx'
import { server } from '../test/server.ts'

describe('App', () => {
  it('givenRootPath_whenAppOpens_thenRedirectsToSearchWithStartPrompt', async () => {
    // Act
    renderApp('/')

    // Assert
    expect(await screen.findByRole('heading', { level: 1, name: 'Bilgi bankası' })).toBeInTheDocument()
    expect(currentUrl()).toBe('/w/42/arama')
    expect(screen.getByText('Belgene bir soru sorarak başla')).toBeInTheDocument()
    expect(await screen.findByRole('combobox', { name: 'Çalışma alanı' })).toHaveDisplayValue(
      'Belgelerim · 4 kaynak · 42 parça',
    )
    expect(await screen.findByRole('link', { name: /^Arama kayıtları\s*0$/ })).toBeInTheDocument()
    expect(document.body.textContent).not.toMatch(/\bRAG\b/)
    expect(document.body.textContent).not.toMatch(/\blab\b/i)
    expect(document.title).toBe('Bilgi bankası · yanıt.')
  })

  it.each([
    ['/', 0, '/w/42/belge-yukle'],
    ['/w/42', 0, '/w/42/belge-yukle'],
    ['/', 1, '/w/42/arama'],
    ['/w/42', 1, '/w/42/arama'],
  ])(
    'givenSourceCount_whenOpenRootOrWorkspaceHome_thenRedirects (%s sourceCount %i)',
    async (path, sourceCount, expectedUrl) => {
      // Arrange
      server.use(
        http.get('/api/workspaces/:workspaceId', () => HttpResponse.json({ ...BELGELERIM, sourceCount })),
      )

      // Act
      renderApp(path)

      // Assert
      await waitFor(() => expect(currentUrl()).toBe(expectedUrl))
    },
  )

  it('givenSingleWorkspace_whenOpenSite_thenNoWorkspaceSelect', async () => {
    // Arrange
    server.use(http.get('/api/workspaces', () => HttpResponse.json(pageOf([BELGELERIM], 0, 100))))

    // Act
    renderApp('/w/42/arama')

    // Assert
    await screen.findByRole('heading', { level: 1, name: 'Bilgi bankası' })
    await screen.findByRole('link', { name: /^Arama kayıtları/ })
    expect(screen.queryByRole('combobox', { name: 'Çalışma alanı' })).not.toBeInTheDocument()
  })

  it('givenTwoWorkspaces_whenOpenSite_thenShowsWorkspaceSelect', async () => {
    // Arrange
    server.use(http.get('/api/workspaces', () => HttpResponse.json(pageOf([DENEME, BELGELERIM], 0, 100))))

    // Act
    renderApp('/w/42/arama')

    // Assert
    expect(await screen.findByRole('combobox', { name: 'Çalışma alanı' })).toBeInTheDocument()
  })

  it('givenWorkspace_whenOpenSearch_thenNavHasUploadLink', async () => {
    // Act
    renderApp('/w/42/arama')

    // Assert
    const nav = await screen.findByRole('navigation', { name: 'Bölümler' })
    expect(within(nav).getByRole('link', { name: 'Belge yükle' })).toHaveAttribute('href', '/w/42/belge-yukle')
  })

  it('givenWorkspaceRootPath_whenOpen_thenRedirectsToSearch', async () => {
    // Act
    renderApp('/w/19')

    // Assert
    expect(await screen.findByRole('heading', { level: 1, name: 'Bilgi bankası' })).toBeInTheDocument()
    expect(currentUrl()).toBe('/w/19/arama')
  })

  it('givenUnknownWorkspaceId_whenOpenSearch_thenShowsWorkspaceNotFound', async () => {
    // Act
    renderApp('/w/999/arama')

    // Assert
    expect(await screen.findByRole('heading', { name: 'Çalışma alanı bulunamadı' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Varsayılan çalışma alanına dön' })).toHaveAttribute('href', '/w/42')
    expect(screen.queryByRole('navigation', { name: 'Bölümler' })).not.toBeInTheDocument()
  })

  it.each(['/w/abc/arama', '/w/0/arama'])(
    'givenInvalidWorkspaceId_whenOpenSearch_thenShowsNotFoundWithoutRequest (%s)',
    async (path) => {
      // Arrange
      const requested: string[] = []
      server.events.on('request:start', ({ request }) => {
        requested.push(new URL(request.url).pathname)
      })

      // Act
      renderApp(path)

      // Assert
      expect(await screen.findByRole('heading', { name: 'Çalışma alanı bulunamadı' })).toBeInTheDocument()
      await screen.findByRole('combobox', { name: 'Çalışma alanı' })
      expect(requested.filter((pathname) => /^\/api\/workspaces\/(abc|0)/.test(pathname))).toEqual([])
      server.events.removeAllListeners()
    },
  )

  it('givenUnknownPath_whenOpen_thenShowsPageNotFound', async () => {
    // Act
    renderApp('/yok')

    // Assert
    expect(await screen.findByRole('heading', { name: 'Sayfa bulunamadı' })).toBeInTheDocument()
  })

  it('givenWorkspaceLoadFails_whenOpen_thenShowsErrorWithRetry', async () => {
    // Arrange
    let calls = 0
    server.use(
      http.get('/api/workspaces/:workspaceId', () => {
        calls += 1
        return calls === 1
          ? HttpResponse.json({ errorCode: 'INTERNAL_ERROR' }, { status: 500 })
          : HttpResponse.json({ workspaceId: 42, name: 'Belgelerim', sourceCount: 4, chunkCount: 42, embeddingModels: [] })
      }),
    )
    const user = userEvent.setup()
    renderApp('/w/42/arama')

    // Act
    const alert = await screen.findByRole('alert')
    await user.click(within(alert).getByRole('button', { name: 'Tekrar dene' }))

    // Assert
    expect(alert).toHaveTextContent('Sunucuda beklenmeyen bir hata oluştu. Tekrar dene.')
    expect(await screen.findByRole('heading', { name: 'Bilgi bankası' })).toBeInTheDocument()
  })

  it('givenSourcesPage_whenSelectOtherWorkspace_thenNavigatesAndListsItsSources', async () => {
    // Arrange
    const user = userEvent.setup()
    renderApp('/w/42/kaynaklar')
    expect(await screen.findByText('İzin notları')).toBeInTheDocument()

    // Act
    await user.selectOptions(screen.getByRole('combobox', { name: 'Çalışma alanı' }), 'Deneme alanı · 3 kaynak · 38 parça')

    // Assert
    expect(await screen.findByText('Deneme notu')).toBeInTheDocument()
    expect(currentUrl()).toBe('/w/19/kaynaklar')
    expect(screen.getByText('Deneme kılavuzu')).toBeInTheDocument()
    expect(screen.getByText('Deneme kayıtları')).toBeInTheDocument()
    expect(screen.queryByText('İzin notları')).not.toBeInTheDocument()
  })

  it('givenSourceDetail_whenSelectOtherWorkspace_thenGoesToSourcesOfNewWorkspace', async () => {
    // Arrange
    const user = userEvent.setup()
    renderApp('/w/42/kaynaklar/10002')
    await screen.findByRole('heading', { level: 1, name: 'Atlaskap-Lojistik-Surec-El-Kitabi' })

    // Act
    await user.selectOptions(screen.getByRole('combobox', { name: 'Çalışma alanı' }), 'Deneme alanı · 3 kaynak · 38 parça')

    // Assert
    await waitFor(() => expect(currentUrl()).toBe('/w/19/kaynaklar'))
  })

  it('givenSearchEventsPage_whenSelectOtherWorkspace_thenStaysOnSearchEvents', async () => {
    // Arrange
    const user = userEvent.setup()
    renderApp('/w/42/arama-kayitlari')
    await screen.findByRole('heading', { level: 1, name: 'Arama kayıtları' })

    // Act
    await user.selectOptions(screen.getByRole('combobox', { name: 'Çalışma alanı' }), 'Deneme alanı · 3 kaynak · 38 parça')

    // Assert
    await waitFor(() => expect(currentUrl()).toBe('/w/19/arama-kayitlari'))
  })

  it('givenSearchResult_whenWorkspaceChanged_thenResultIsCleared', async () => {
    // Arrange
    server.use(
      http.post('/api/workspaces/:workspaceId/search', () =>
        HttpResponse.json(searchResult({ hits: [hit({ heading: 'Özel başlık', matchedTerms: [] })], returnedCount: 1 })),
      ),
    )
    const user = userEvent.setup()
    renderApp('/w/42/arama')
    await user.type(await screen.findByLabelText('Arama metni'), 'Yıllık izin kaç gün?{Enter}')
    expect(await screen.findByText('Özel başlık')).toBeInTheDocument()

    // Act
    await user.selectOptions(screen.getByRole('combobox', { name: 'Çalışma alanı' }), 'Deneme alanı · 3 kaynak · 38 parça')

    // Assert
    expect(await screen.findByText('Belgene bir soru sorarak başla')).toBeInTheDocument()
    expect(currentUrl()).toBe('/w/19/arama')
    expect(screen.queryByText('Özel başlık')).not.toBeInTheDocument()
    expect(screen.getByLabelText('Arama metni')).toHaveValue('')
  })

  it('givenSearchEventsPath_whenRendered_thenTabMarkedCurrentAndKeyboardNavigable', async () => {
    // Arrange
    const user = userEvent.setup()
    renderApp('/w/42/arama-kayitlari')
    const eventsTab = await screen.findByRole('link', { name: /^Arama kayıtları/ })

    // Act
    await user.tab()
    const first = document.activeElement
    await user.tab()
    const second = document.activeElement
    await user.tab()
    const third = document.activeElement
    await user.tab()
    const fourth = document.activeElement
    await user.tab()
    const fifth = document.activeElement

    // Assert
    expect(eventsTab).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('link', { name: 'Arama' })).not.toHaveAttribute('aria-current')
    expect(first).toBe(screen.getByRole('link', { name: 'yanıt.' }))
    expect(second).toBe(screen.getByRole('combobox', { name: 'Çalışma alanı' }))
    expect(third).toBe(screen.getByRole('link', { name: 'Arama' }))
    expect(fourth).toBe(eventsTab)
    expect(fifth).toBe(screen.getByRole('link', { name: 'Bilgi kaynakları' }))
  })

  it('givenSourceDetailPath_whenRendered_thenSourcesTabIsCurrent', async () => {
    // Act
    renderApp('/w/42/kaynaklar/10002')

    // Assert
    expect(await screen.findByRole('link', { name: 'Bilgi kaynakları', current: 'page' })).toBeInTheDocument()
  })

  it('givenSourcesPage_whenClickLogo_thenGoesToSearch', async () => {
    // Arrange
    const user = userEvent.setup()
    renderApp('/w/42/kaynaklar')
    await screen.findByText('İzin notları')

    // Act
    await user.click(screen.getByRole('link', { name: 'yanıt.' }))

    // Assert
    expect(await screen.findByRole('heading', { level: 1, name: 'Bilgi bankası' })).toBeInTheDocument()
    expect(currentUrl()).toBe('/w/42/arama')
  })

  it('givenStatsWithTotal_whenRendered_thenTabShowsFormattedCount', async () => {
    // Arrange
    server.use(http.get('/api/workspaces/:workspaceId/search-events/stats', () => HttpResponse.json(stats({ totalCount: 1410 }))))

    // Act
    renderApp('/w/42/arama')

    // Assert
    expect(await screen.findByRole('link', { name: /^Arama kayıtları\s*1\.410$/ })).toBeInTheDocument()
  })
})
