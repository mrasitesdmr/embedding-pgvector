import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { chunk, BELGELERIM_SOURCES, pageOf } from '../../../test/fixtures.ts'
import { problem } from '../../../test/problem.ts'
import { renderApp } from '../../../test/renderWithProviders.tsx'
import { server } from '../../../test/server.ts'

const TOTAL_CHUNKS = 45

function useFortyFiveChunks() {
  server.use(
    http.get('/api/workspaces/:workspaceId/sources/10002', () =>
      HttpResponse.json({ ...BELGELERIM_SOURCES[1], chunkCount: TOTAL_CHUNKS }),
    ),
    http.get('/api/workspaces/:workspaceId/sources/:sourceId/chunks', ({ request }) => {
      const page = Number(new URL(request.url).searchParams.get('page') ?? '0')
      const first = page * 20
      const count = Math.min(20, TOTAL_CHUNKS - first)
      const content = Array.from({ length: count }, (_, index) => chunk(1380 + first + index, { chunkNo: first + index }))
      return HttpResponse.json(pageOf(content, page, 20, TOTAL_CHUNKS))
    }),
  )
}

function chunkRows(): HTMLElement[] {
  return screen.getAllByRole('row').slice(1)
}

describe('SourceDetailPage', () => {
  it('givenWebCrawlSource_whenOpenDetail_thenNoUploadVersionButton', async () => {
    // Act
    renderApp('/w/42/kaynaklar/10001')

    // Assert
    expect(await screen.findByRole('heading', { level: 1, name: 'İzin notları' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Yeni sürüm yükle' })).not.toBeInTheDocument()
  })

  it('givenPdfSource_whenOpenDetail_thenShowsInfoBoxesAndFirstTwentyChunks', async () => {
    // Arrange
    useFortyFiveChunks()

    // Act
    renderApp('/w/42/kaynaklar/10002')

    // Assert
    expect(await screen.findByRole('heading', { level: 1, name: 'Atlaskap-Lojistik-Surec-El-Kitabi' })).toBeInTheDocument()
    const breadcrumbs = screen.getByRole('navigation', { name: 'Konum' })
    expect(within(breadcrumbs).getByRole('link', { name: 'Bilgi kaynakları' })).toHaveAttribute('href', '/w/42/kaynaklar')
    expect(within(breadcrumbs).getByText('Atlaskap-Lojistik-Surec-El-Kitabi')).toHaveAttribute('aria-current', 'page')
    await screen.findByText('chunk 1380')
    const boxes = Object.fromEntries(
      Array.from(document.querySelectorAll('dl dt')).map((term) => [term.textContent, term.nextElementSibling?.textContent]),
    )
    expect(boxes).toMatchObject({
      Tür: 'PDF',
      Sürüm: '3.1',
      'Dosya adı': 'Atlaskap-Lojistik-Surec-El-Kitabi-v3.1.pdf',
      Parça: '45',
      'Embedding modeli': 'bge-m3@1024',
      'Son senkron': '12 Eki 2026 14:31',
      Durum: 'Hazır',
    })
    expect(chunkRows()).toHaveLength(20)
    expect(within(chunkRows()[0]).getByText('12')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Yeni sürüm yükle' })).toBeEnabled()
    expect(document.title).toBe('Atlaskap-Lojistik-Surec-El-Kitabi · yanıt.')
  })

  it('givenMoreChunks_whenClickShowMore_thenAppendsNextTwenty', async () => {
    // Arrange
    useFortyFiveChunks()
    const user = userEvent.setup()
    renderApp('/w/42/kaynaklar/10002')
    await screen.findByText('chunk 1380')

    // Act
    await user.click(screen.getByRole('button', { name: 'Daha fazla göster' }))
    await waitFor(() => expect(chunkRows()).toHaveLength(40))
    await user.click(screen.getByRole('button', { name: 'Daha fazla göster' }))

    // Assert
    await waitFor(() => expect(chunkRows()).toHaveLength(45))
    expect(screen.queryByRole('button', { name: 'Daha fazla göster' })).not.toBeInTheDocument()
    expect(screen.getByText('chunk 1424')).toBeInTheDocument()
  })

  it('givenFewChunks_whenOpenDetail_thenNoShowMoreButton', async () => {
    // Act
    renderApp('/w/42/kaynaklar/10002')

    // Assert
    await screen.findByText('chunk 1380')
    expect(screen.queryByRole('button', { name: 'Daha fazla göster' })).not.toBeInTheDocument()
  })

  it('givenLongContentAndMissingHeading_whenOpenDetail_thenPreviewCutAndUntitled', async () => {
    // Arrange
    server.use(
      http.get('/api/workspaces/:workspaceId/sources/:sourceId/chunks', () =>
        HttpResponse.json(pageOf([chunk(7, { heading: null, content: 'a'.repeat(200), tokenCount: 1410 })])),
      ),
    )

    // Act
    renderApp('/w/42/kaynaklar/10002')

    // Assert
    expect(await screen.findByText('Başlıksız parça')).toBeInTheDocument()
    expect(screen.getByText(`${'a'.repeat(160)}…`)).toBeInTheDocument()
    expect(screen.getByText('1.410')).toBeInTheDocument()
  })

  it('givenParcaQuery_whenOpenDetail_thenHighlightedChunkPinnedFirst', async () => {
    // Arrange
    server.use(
      http.get('/api/workspaces/:workspaceId/sources/:sourceId/chunks/:chunkId', () =>
        HttpResponse.json(chunk(1500, { heading: 'Yıllık izin kaç gün?', chunkNo: 31 })),
      ),
    )

    // Act
    renderApp('/w/42/kaynaklar/10002?parca=1500')

    // Assert
    expect(await screen.findByText('Arama sonucundan açılan parça vurgulandı: #1500')).toBeInTheDocument()
    await waitFor(() => expect(chunkRows()[0]).toHaveTextContent('chunk 1500'))
    expect(chunkRows()[0]).toHaveClass('highlighted')
    expect(chunkRows()[1]).not.toHaveClass('highlighted')
  })

  it('givenHighlightedChunkAlsoInList_whenOpenDetail_thenShownOnlyOnce', async () => {
    // Act
    renderApp('/w/42/kaynaklar/10002?parca=1381')

    // Assert
    await waitFor(() => expect(chunkRows()[0]).toHaveTextContent('chunk 1381'))
    expect(screen.getAllByText('chunk 1381')).toHaveLength(1)
  })

  it('givenReplacedChunk_whenOpenDetail_thenShowsChunkGoneNotice', async () => {
    // Arrange
    server.use(http.get('/api/workspaces/:workspaceId/sources/:sourceId/chunks/:chunkId', () => problem(404, 'CHUNK_NOT_FOUND')))

    // Act
    renderApp('/w/42/kaynaklar/10002?parca=999')

    // Assert
    expect(await screen.findByText('Bu parça artık yok; kaynak yeni bir sürüme geçmiş olabilir.')).toBeInTheDocument()
    expect(await screen.findByText('chunk 1380')).toBeInTheDocument()
    expect(chunkRows().every((row) => !row.classList.contains('highlighted'))).toBe(true)
  })

  it('givenInvalidParcaValue_whenOpenDetail_thenNoHighlightNoticeAndNoChunkRequest', async () => {
    // Arrange
    let chunkRequests = 0
    server.use(
      http.get('/api/workspaces/:workspaceId/sources/:sourceId/chunks/:chunkId', () => {
        chunkRequests += 1
        return HttpResponse.json(chunk(1))
      }),
    )

    // Act
    renderApp('/w/42/kaynaklar/10002?parca=abc')

    // Assert
    await screen.findByText('chunk 1380')
    expect(screen.queryByText(/Arama sonucundan açılan parça/)).not.toBeInTheDocument()
    expect(chunkRequests).toBe(0)
  })

  it('givenUnknownSource_whenOpenDetail_thenShowsSourceNotFound', async () => {
    // Act
    renderApp('/w/42/kaynaklar/99999')

    // Assert
    expect(await screen.findByRole('heading', { name: 'Kaynak bulunamadı' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Bilgi kaynaklarına dön' })).toHaveAttribute('href', '/w/42/kaynaklar')
  })

  it('givenNonNumericSourceId_whenOpenDetail_thenShowsSourceNotFound', async () => {
    // Act
    renderApp('/w/42/kaynaklar/abc')

    // Assert
    expect(await screen.findByRole('heading', { name: 'Kaynak bulunamadı' })).toBeInTheDocument()
  })

  it('givenSourceWithoutChunks_whenOpenDetail_thenShowsEmptyMessage', async () => {
    // Arrange
    server.use(http.get('/api/workspaces/:workspaceId/sources/:sourceId/chunks', () => HttpResponse.json(pageOf([]))))

    // Act
    renderApp('/w/42/kaynaklar/10002')

    // Assert
    expect(await screen.findByText('Bu kaynağın henüz parçası yok.')).toBeInTheDocument()
    expect(screen.getByText('Embedding modeli').nextElementSibling).toHaveTextContent('—')
  })

  it('givenServerError_whenOpenDetail_thenShowsTurkishErrorWithRetry', async () => {
    // Arrange
    server.use(http.get('/api/workspaces/:workspaceId/sources/:sourceId', () => problem(500, 'INTERNAL_ERROR')))

    // Act
    renderApp('/w/42/kaynaklar/10002')

    // Assert
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Sunucuda beklenmeyen bir hata oluştu. Tekrar dene.')
    expect(within(alert).getByRole('button', { name: 'Tekrar dene' })).toBeInTheDocument()
  })
})
