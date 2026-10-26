import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { pageOf, searchEvent, searchResult, stats } from '../../../test/fixtures.ts'
import { problem } from '../../../test/problem.ts'
import { renderApp } from '../../../test/renderWithProviders.tsx'
import { server } from '../../../test/server.ts'

describe('SearchEventsPage', () => {
  it('givenStatsAndEvents_whenOpenSearchEvents_thenShowsIndicatorsAndBadges', async () => {
    // Arrange
    server.use(
      http.get('/api/workspaces/:workspaceId/search-events/stats', () =>
        HttpResponse.json(stats({ totalCount: 4, todayCount: 4, last7DaysCount: 4, zeroResultCount: 1, lowResultCount: 1, zeroResultRate: 0.25, lowResultRate: 0.25 })),
      ),
      http.get('/api/workspaces/:workspaceId/search-events', () =>
        HttpResponse.json(
          pageOf([
            searchEvent({ searchEventId: 4, query: 'netflix', resultCount: 0, topChunkId: null, topChunkHeading: null }),
            searchEvent({ searchEventId: 3, query: 'taşınma', mode: 'VECTOR', resultCount: 3, limit: 10 }),
            searchEvent({ searchEventId: 2, query: 'Yıllık izin kaç gün?', resultCount: 10, limit: 10, channel: null }),
            searchEvent({ searchEventId: 1, query: 'eski arama', resultCount: 4, limit: null, mode: 'KEYWORD' }),
          ]),
        ),
      ),
    )

    // Act
    renderApp('/w/42/arama-kayitlari')

    // Assert
    expect(await screen.findByText('netflix')).toBeInTheDocument()
    const indicators = screen.getAllByRole('definition')
    expect(indicators.map((node) => node.textContent?.replace(/\s+/g, ' ').trim())).toEqual([
      '4 arama',
      '%25 son 7 gün',
      '%25 son 7 gün',
    ])
    const rows = screen.getAllByRole('row').slice(1)
    expect(rows.map((row) => within(row).getAllByRole('cell')[2].textContent)).toEqual([
      'netflix',
      'taşınma',
      'Yıllık izin kaç gün?',
      'eski arama',
    ])
    expect(within(rows[0]).getByText('Sonuçsuz')).toHaveClass('bad')
    expect(within(rows[1]).getByText('3')).toHaveClass('run')
    expect(within(rows[2]).getByText('10')).toHaveClass('ok')
    expect(within(rows[3]).getByText('4')).toHaveClass('ok')
    expect(within(rows[0]).getAllByRole('cell')[1]).toHaveTextContent('Temsilci paneli')
    expect(within(rows[2]).getAllByRole('cell')[1]).toHaveTextContent('—')
    expect(within(rows[0]).getAllByRole('cell')[5]).toHaveTextContent('—')
    expect(within(rows[1]).getAllByRole('cell')[3]).toHaveTextContent('Anlam')
    expect(within(rows[0]).getAllByRole('cell')[3]).toHaveTextContent('Hybrid')
    expect(within(rows[1]).getAllByRole('cell')[5]).toHaveTextContent('Yıllık izin kaç gün?')
  })

  it('givenNoEventsInLast7Days_whenOpen_thenRatesShowDash', async () => {
    // Act
    renderApp('/w/42/arama-kayitlari')

    // Assert
    expect(await screen.findByText('Henüz arama kaydı yok')).toBeInTheDocument()
    const indicators = screen.getAllByRole('definition').map((node) => node.textContent?.replace(/\s+/g, ' ').trim())
    expect(indicators).toEqual(['0 arama', '— son 7 gün', '— son 7 gün'])
  })

  it('givenTwoPagesOfEvents_whenClickNext_thenRequestsSecondPage', async () => {
    // Arrange
    const requestedPages: (string | null)[] = []
    server.use(
      http.get('/api/workspaces/:workspaceId/search-events', ({ request }) => {
        const url = new URL(request.url)
        requestedPages.push(`${url.searchParams.get('page')}&${url.searchParams.get('size')}`)
        const page = Number(url.searchParams.get('page'))
        return HttpResponse.json({
          ...pageOf([searchEvent({ searchEventId: page + 1, query: `sayfa ${page + 1} araması` })], page, 20, 25),
        })
      }),
    )
    const user = userEvent.setup()
    renderApp('/w/42/arama-kayitlari')
    expect(await screen.findByText('Sayfa 1 / 2')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Önceki' })).toBeDisabled()

    // Act
    await user.click(screen.getByRole('button', { name: 'Sonraki' }))

    // Assert
    expect(await screen.findByText('Sayfa 2 / 2')).toBeInTheDocument()
    expect(await screen.findByText('sayfa 2 araması')).toBeInTheDocument()
    expect(requestedPages).toEqual(['0&20', '1&20'])
    expect(screen.getByRole('button', { name: 'Sonraki' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Önceki' })).toBeEnabled()
  })

  it('givenSinglePageOfEvents_whenOpen_thenNoPagination', async () => {
    // Arrange
    server.use(http.get('/api/workspaces/:workspaceId/search-events', () => HttpResponse.json(pageOf([searchEvent()]))))

    // Act
    renderApp('/w/42/arama-kayitlari')

    // Assert
    await screen.findByRole('table')
    expect(screen.queryByRole('navigation', { name: 'Sayfalar' })).not.toBeInTheDocument()
  })

  it('givenEventsRequestFails_whenOpen_thenShowsTurkishError', async () => {
    // Arrange
    server.use(http.get('/api/workspaces/:workspaceId/search-events', () => problem(500, 'INTERNAL_ERROR')))

    // Act
    renderApp('/w/42/arama-kayitlari')

    // Assert
    expect(await screen.findByRole('alert')).toHaveTextContent('Sunucuda beklenmeyen bir hata oluştu. Tekrar dene.')
  })

  it('givenSearchCompleted_whenBackOnSearchEvents_thenTabCountAndTableUpdated', async () => {
    // Arrange
    let searched = false
    server.use(
      http.post('/api/workspaces/:workspaceId/search', () => {
        searched = true
        return HttpResponse.json(searchResult())
      }),
      http.get('/api/workspaces/:workspaceId/search-events/stats', () =>
        HttpResponse.json(stats({ totalCount: searched ? 1 : 0 })),
      ),
      http.get('/api/workspaces/:workspaceId/search-events', () =>
        HttpResponse.json(pageOf(searched ? [searchEvent({ query: 'yeni kayıt' })] : [])),
      ),
    )
    const user = userEvent.setup()
    renderApp('/w/42/arama-kayitlari')
    expect(await screen.findByRole('link', { name: /^Arama kayıtları\s*0$/ })).toBeInTheDocument()
    await screen.findByText('Henüz arama kaydı yok')

    // Act
    await user.click(screen.getByRole('link', { name: 'Arama' }))
    await user.type(await screen.findByLabelText('Arama metni'), 'yeni kayıt{Enter}')
    await screen.findByText('2 sonuç')
    await user.click(screen.getByRole('link', { name: /^Arama kayıtları/ }))

    // Assert
    expect(await screen.findByRole('link', { name: /^Arama kayıtları\s*1$/ })).toBeInTheDocument()
    expect(await screen.findByText('yeni kayıt')).toBeInTheDocument()
  })
})
