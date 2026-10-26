import { act, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { axe } from 'vitest-axe'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { BELGELERIM, BELGELERIM_SOURCES, ingestionJob, pageOf, searchEvent, searchResult, stats } from './test/fixtures.ts'
import { renderApp } from './test/renderWithProviders.tsx'
import { server } from './test/server.ts'

describe('pages accessibility', () => {
  it('givenSearchPageWithResultsAndOpenPanels_whenRendered_thenHasNoAxeViolations', async () => {
    // Arrange
    server.use(
      http.post('/api/workspaces/:workspaceId/search', () =>
        HttpResponse.json(searchResult({ vectorShort: true, vectorCount: 4, vectorPlan: 'Index Scan' })),
      ),
    )
    const user = userEvent.setup()
    const { container } = renderApp('/w/42/arama')
    await user.type(await screen.findByLabelText('Arama metni'), 'Yıllık izin kaç gün?{Enter}')
    await screen.findByText('2 sonuç')
    await user.click(screen.getByText('Gelişmiş ayarlar'))
    await user.click(screen.getByText('Teknik detay'))

    // Act
    const results = await axe(container)

    // Assert
    expect(results).toHaveNoViolations()
  })

  it('givenEmptySearchPage_whenRendered_thenHasNoAxeViolations', async () => {
    // Arrange
    const { container } = renderApp('/w/42/arama')
    await screen.findByText('Belgene bir soru sorarak başla')

    // Act
    const results = await axe(container)

    // Assert
    expect(results).toHaveNoViolations()
  })

  it('givenNoSourcesState_whenRendered_thenHasNoAxeViolations', async () => {
    // Arrange
    server.use(http.get('/api/workspaces/:workspaceId', () => HttpResponse.json({ ...BELGELERIM, sourceCount: 0, chunkCount: 0 })))
    const { container } = renderApp('/w/42/arama')
    await screen.findByText('Henüz belge yüklenmedi')

    // Act
    const results = await axe(container)

    // Assert
    expect(results).toHaveNoViolations()
  })

  it('givenSearchEventsPage_whenRendered_thenHasNoAxeViolations', async () => {
    // Arrange
    server.use(
      http.get('/api/workspaces/:workspaceId/search-events/stats', () =>
        HttpResponse.json(stats({ totalCount: 2, zeroResultRate: 0.25, lowResultRate: 0.5 })),
      ),
      http.get('/api/workspaces/:workspaceId/search-events', () =>
        HttpResponse.json({ ...pageOf([searchEvent(), searchEvent({ searchEventId: 2, resultCount: 0 })], 0, 20, 40) }),
      ),
    )
    const { container } = renderApp('/w/42/arama-kayitlari')
    await screen.findByRole('table')

    // Act
    const results = await axe(container)

    // Assert
    expect(results).toHaveNoViolations()
  })

  it('givenSourcesPage_whenRendered_thenHasNoAxeViolations', async () => {
    // Arrange
    const { container } = renderApp('/w/42/kaynaklar')
    await screen.findByText('İzin notları')

    // Act
    const results = await axe(container)

    // Assert
    expect(results).toHaveNoViolations()
  })

  it('givenSourceDetailPageWithHighlightedChunk_whenRendered_thenHasNoAxeViolations', async () => {
    // Arrange
    const { container } = renderApp('/w/42/kaynaklar/10002?parca=1381')
    await screen.findByText(/Arama sonucundan açılan parça vurgulandı/)
    await screen.findByText('chunk 1380')

    // Act
    const results = await axe(container)

    // Assert
    expect(results).toHaveNoViolations()
  })

  it('givenNotFoundWorkspace_whenRendered_thenHasNoAxeViolations', async () => {
    // Arrange
    const { container } = renderApp('/w/999/arama')
    await screen.findByRole('heading', { name: 'Çalışma alanı bulunamadı' })

    // Act
    const results = await axe(container)

    // Assert
    expect(results).toHaveNoViolations()
  })

  describe('upload and ingestion states', () => {
    const embedding = ingestionJob({
      jobId: 7,
      sourceId: 10002,
      stage: 'EMBEDDING',
      progress: 0.458879,
      embeddedChunks: 96,
      totalChunks: 214,
    })
    const failed = ingestionJob({ jobId: 9, sourceId: 10003, stage: 'FAILED', errorCode: 'EMBEDDING_UNAVAILABLE' })

    function useBusySources() {
      const sources = BELGELERIM_SOURCES.map((candidate) => {
        if (candidate.sourceId === 10002) return { ...candidate, status: 'INGESTING' as const, latestJob: embedding }
        if (candidate.sourceId === 10003) return { ...candidate, status: 'READY' as const, latestJob: failed }
        return candidate
      })
      server.use(
        http.get('/api/workspaces/:workspaceId/sources', () => HttpResponse.json(pageOf(sources))),
        http.get('/api/workspaces/:workspaceId/sources/10002', () => HttpResponse.json(sources[1])),
        http.get('/api/workspaces/:workspaceId/ingestion-jobs/7', () => HttpResponse.json(embedding)),
      )
    }

    afterEach(() => {
      vi.useRealTimers()
    })

    it('givenCreateDialogWithFileError_whenRendered_thenHasNoAxeViolations', async () => {
      // Arrange
      const user = userEvent.setup({ applyAccept: false })
      const { container } = renderApp('/w/42/kaynaklar')
      await user.click(await screen.findByRole('button', { name: 'Yeni kaynak ekle' }))
      const dialog = screen.getByRole('dialog', { name: 'Yeni kaynak ekle' })
      await user.upload(within(dialog).getByLabelText('Dosya'), new File(['x'], 'kilavuz.docx'))
      await within(dialog).findByRole('alert')

      // Act
      const results = await axe(container)

      // Assert
      expect(results).toHaveNoViolations()
    })

    it('givenUploadVersionDialog_whenRendered_thenHasNoAxeViolations', async () => {
      // Arrange
      const user = userEvent.setup({ applyAccept: false })
      const { container } = renderApp('/w/42/kaynaklar')
      const row = (await screen.findByRole('link', { name: 'Atlaskap-Lojistik-Surec-El-Kitabi' })).closest('tr') as HTMLElement
      await user.click(within(row).getByRole('button', { name: 'Yeni sürüm yükle' }))
      await user.upload(screen.getByLabelText('Dosya'), new File(['x'], 'notlar.md'))

      // Act
      const results = await axe(container)

      // Assert
      expect(results).toHaveNoViolations()
    })

    it('givenRowsWithProgressAndFailure_whenRendered_thenHasNoAxeViolations', async () => {
      // Arrange
      useBusySources()
      const { container } = renderApp('/w/42/kaynaklar')
      await screen.findByText('Embed ediliyor · 96/214 parça')
      await screen.findByText(/Son yükleme başarısız/)

      // Act
      const results = await axe(container)

      // Assert
      expect(results).toHaveNoViolations()
    })

    it('givenDetailWithProgress_whenRendered_thenHasNoAxeViolations', async () => {
      // Arrange
      useBusySources()
      const { container } = renderApp('/w/42/kaynaklar/10002')
      await screen.findByRole('progressbar')

      // Act
      const results = await axe(container)

      // Assert
      expect(results).toHaveNoViolations()
    })

    it('givenVisibleToast_whenRendered_thenHasNoAxeViolations', async () => {
      // Arrange
      vi.useFakeTimers({ shouldAdvanceTime: true })
      const done = ingestionJob({ jobId: 7, sourceId: 10002, stage: 'DONE', progress: 1, embeddedChunks: 214, totalChunks: 214 })
      useBusySources()
      server.use(http.get('/api/workspaces/:workspaceId/ingestion-jobs/7', () => HttpResponse.json(done)))
      const { container } = renderApp('/w/42/kaynaklar')
      await screen.findByText('Embed ediliyor · 96/214 parça')
      await act(async () => {
        await vi.advanceTimersByTimeAsync(1000)
      })
      await screen.findByText(/v3\.2 hazır · 214 parça/)

      // Act
      const results = await axe(container)

      // Assert
      expect(results).toHaveNoViolations()
    })
  })
})
