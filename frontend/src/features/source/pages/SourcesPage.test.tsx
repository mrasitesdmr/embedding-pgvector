import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { BELGELERIM, pageOf } from '../../../test/fixtures.ts'
import { problem } from '../../../test/problem.ts'
import { currentUrl, renderApp } from '../../../test/renderWithProviders.tsx'
import { server } from '../../../test/server.ts'

describe('SourcesPage', () => {
  it('givenWorkspaceSources_whenOpenSources_thenShowsKindsStatusesFooterAndCreateButton', async () => {
    // Act
    renderApp('/w/42/kaynaklar')

    // Assert
    expect(await screen.findByText('İzin notları')).toBeInTheDocument()
    const breadcrumbs = screen.getByRole('navigation', { name: 'Konum' })
    expect(within(breadcrumbs).getByText('Belgelerim')).toBeInTheDocument()
    expect(within(breadcrumbs).getByText('Bilgi kaynakları')).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('heading', { level: 1, name: 'Bilgi kaynakları' })).toBeInTheDocument()
    expect(screen.getByText('Arama sadece buradaki içerikte yapılır. Her kaynak parçalara bölünür ve embedding\'e çevrilir.')).toBeInTheDocument()
    const rows = screen.getAllByRole('row').slice(1)
    const cells = rows.map((row) => within(row).getAllByRole('cell').map((cell) => cell.textContent))
    expect(cells.map((row) => row.slice(1, 4))).toEqual([
      ['Web sitesi', '21', 'Hazır'],
      ['PDF', '5', 'Hazır'],
      ['CSV', '9', 'İşleniyor'],
      ['Talep geçmişi', '7', 'Başarısız'],
    ])
    expect(cells[1][0]).toBe('Atlaskap-Lojistik-Surec-El-KitabiAtlaskap-Lojistik-Surec-El-Kitabi-v3.1.pdf · v3.1Yeni sürüm yükle')
    expect(cells[0][0]).toBe('İzin notları— · sürüm yok')
    expect(cells[1][4]).toBe('12 Eki 2026 14:31')
    expect(cells[2][4]).toBe('—')
    expect(within(rows[2]).getByText('İşleniyor')).toHaveClass('run')
    expect(within(rows[3]).getByText('Başarısız')).toHaveClass('bad')
    expect(within(rows[0]).getByText('Hazır')).toHaveClass('ok')
    expect(screen.getByText('Toplam 42 parça · embedding modeli bge-m3@1024')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Yeni kaynak ekle' })).toBeEnabled()
    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument()
    expect(document.title).toBe('Bilgi kaynakları · yanıt.')
  })

  it('givenSourceKinds_whenRendered_thenUploadVersionOnlyOnFileSources', async () => {
    // Act
    renderApp('/w/42/kaynaklar')

    // Assert
    await screen.findByText('İzin notları')
    const [webRow, pdfRow, csvRow, ticketRow] = screen.getAllByRole('row').slice(1)
    expect(within(webRow).queryByRole('button', { name: 'Yeni sürüm yükle' })).not.toBeInTheDocument()
    expect(within(pdfRow).getByRole('button', { name: 'Yeni sürüm yükle' })).toBeEnabled()
    expect(within(csvRow).getByRole('button', { name: 'Yeni sürüm yükle' })).toBeDisabled()
    expect(within(ticketRow).queryByRole('button', { name: 'Yeni sürüm yükle' })).not.toBeInTheDocument()
  })

  it('givenSourceRow_whenClickRow_thenOpensDetail', async () => {
    // Arrange
    const user = userEvent.setup()
    renderApp('/w/42/kaynaklar')
    const row = (await screen.findByText('PDF')).closest('tr') as HTMLElement

    // Act
    await user.click(within(row).getByText('PDF'))

    // Assert
    expect(await screen.findByRole('heading', { level: 1, name: 'Atlaskap-Lojistik-Surec-El-Kitabi' })).toBeInTheDocument()
    expect(currentUrl()).toBe('/w/42/kaynaklar/10002')
  })

  it('givenSourceRow_whenClickNameLink_thenOpensDetailOnce', async () => {
    // Arrange
    const user = userEvent.setup()
    renderApp('/w/42/kaynaklar')

    // Act
    await user.click(await screen.findByRole('link', { name: 'İzin notları' }))

    // Assert
    expect(await screen.findByRole('heading', { level: 1, name: 'İzin notları' })).toBeInTheDocument()
    expect(currentUrl()).toBe('/w/42/kaynaklar/10001')
  })

  it('givenWorkspaceWithoutSources_whenOpen_thenShowsEmptyState', async () => {
    // Arrange
    server.use(http.get('/api/workspaces/:workspaceId/sources', () => HttpResponse.json(pageOf([]))))

    // Act
    renderApp('/w/42/kaynaklar')

    // Assert
    expect(await screen.findByText('Bu çalışma alanında henüz kaynak yok')).toBeInTheDocument()
  })

  it('givenWorkspaceWithMixedModels_whenOpen_thenFooterListsAllModels', async () => {
    // Arrange
    server.use(
      http.get('/api/workspaces/42', () =>
        HttpResponse.json({ ...BELGELERIM, embeddingModels: ['aaa@8', 'bge-m3@1024'] }),
      ),
    )

    // Act
    renderApp('/w/42/kaynaklar')

    // Assert
    expect(await screen.findByText('Toplam 42 parça · embedding modeli aaa@8, bge-m3@1024')).toBeInTheDocument()
  })

  it('givenSourcesRequestFails_whenOpen_thenShowsTurkishError', async () => {
    // Arrange
    server.use(http.get('/api/workspaces/:workspaceId/sources', () => problem(500, 'INTERNAL_ERROR')))

    // Act
    renderApp('/w/42/kaynaklar')

    // Assert
    expect(await screen.findByRole('alert')).toHaveTextContent('Sunucuda beklenmeyen bir hata oluştu. Tekrar dene.')
  })
})
