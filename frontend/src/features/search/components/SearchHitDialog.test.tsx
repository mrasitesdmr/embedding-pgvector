import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { createRef } from 'react'
import { axe } from 'vitest-axe'
import { describe, expect, it, vi } from 'vitest'
import { chunk, hit } from '../../../test/fixtures.ts'
import { problem } from '../../../test/problem.ts'
import { renderWithProviders } from '../../../test/renderWithProviders.tsx'
import { server } from '../../../test/server.ts'
import type { SearchHitResponse } from '../model/types.ts'
import { SearchHitDialog } from './SearchHitDialog.tsx'

const CHUNK_URL = '/api/workspaces/:workspaceId/sources/:sourceId/chunks/:chunkId'

const LONG_CONTENT = [
  `Birinci paragraf ilk satır.\nBirinci paragraf ikinci satır. ${'Yıllık izin süreleri kıdeme göre belirlenir. '.repeat(5)}`,
  `İkinci paragraf. ${'Masraf beyanı süresi on gündür. '.repeat(6)}`,
  'Üçüncü paragraf SON-CÜMLE.',
].join('\n\n')

function renderDialog(target: SearchHitResponse = hit(), onClose = vi.fn()) {
  const view = renderWithProviders(
    <SearchHitDialog workspaceId={42} hit={target} returnFocusRef={createRef<HTMLElement>()} onClose={onClose} />,
    '/w/42/arama',
  )
  return { ...view, onClose }
}

function countChunkRequests(): { count: () => number } {
  let requests = 0
  server.use(
    http.get(CHUNK_URL, ({ params }) => {
      requests += 1
      return HttpResponse.json(chunk(Number(params.chunkId)))
    }),
  )
  return { count: () => requests }
}

describe('SearchHitDialog', () => {
  it('givenLongMultiParagraphHit_whenOpened_thenShowsTitleSourceLineAndWholeContent', async () => {
    // Arrange
    const target = hit({
      heading: 'Yıllık izin ve diğer izinler',
      sourceName: 'Atlaskap-Lojistik-Surec-El-Kitabi',
      chunkNo: 31,
      chunkId: 1380,
      content: LONG_CONTENT,
      matchedTerms: [],
    })

    // Act
    renderDialog(target)

    // Assert
    expect(await screen.findByRole('dialog', { name: 'Yıllık izin ve diğer izinler' })).toBeInTheDocument()
    expect(screen.getByText('Atlaskap-Lojistik-Surec-El-Kitabi · parça 31 · #1380')).toBeInTheDocument()
    const region = screen.getByRole('region', { name: 'Parça metni' })
    expect(region.textContent).toBe(LONG_CONTENT)
    expect(region.textContent).not.toContain('…')
    expect(screen.getByRole('button', { name: 'Paneli kapat' })).toHaveFocus()
  })

  it('givenHitWithoutHeading_whenOpened_thenTitleIsUntitledChunk', async () => {
    // Act
    renderDialog(hit({ heading: null, matchedTerms: [] }))

    // Assert
    expect(await screen.findByRole('dialog', { name: 'Başlıksız parça' })).toBeInTheDocument()
  })

  it('givenKeywordHitWithTermsBeyondSnippet_whenOpened_thenMarksAtAllOffsets', async () => {
    // Arrange
    const content = `Masraf ${'x '.repeat(120)}masraf beyanı.`
    const target = hit({
      heading: 'Masraf kuralları',
      content,
      matchedTerms: [
        { field: 'HEADING', start: 0, end: 6 },
        { field: 'CONTENT', start: 0, end: 6 },
        { field: 'CONTENT', start: 247, end: 253 },
        { field: 'CONTENT', start: 254, end: 260 },
      ],
    })

    // Act
    renderDialog(target)

    // Assert
    const region = await screen.findByRole('region', { name: 'Parça metni' })
    expect(Array.from(region.querySelectorAll('mark')).map((mark) => mark.textContent)).toEqual([
      'Masraf',
      'masraf',
      'beyanı',
    ])
    const titleMarks = within(screen.getByRole('dialog')).getByRole('heading', { level: 2 }).querySelectorAll('mark')
    expect(Array.from(titleMarks).map((mark) => mark.textContent)).toEqual(['Masraf'])
  })

  it.each([
    [['SEMANTIC', 'KEYWORD'] as const, true, true],
    [['SEMANTIC'] as const, true, false],
    [['KEYWORD'] as const, false, true],
  ])('givenMatchedBy_%j_whenOpened_thenShowsMatchingTags', async (matchedBy, hasSemantic, hasKeyword) => {
    // Act
    renderDialog(hit({ matchedBy: [...matchedBy], matchedTerms: [] }))

    // Assert
    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).queryByText('anlam') !== null).toBe(hasSemantic)
    expect(within(dialog).queryByText('kelime') !== null).toBe(hasKeyword)
  })

  it('givenHtmlInContent_whenOpened_thenRenderedAsText', async () => {
    // Arrange
    const content = '<img src=x onerror="window.__xss=1"><script>window.__xss=2</script>'

    // Act
    renderDialog(hit({ content, matchedTerms: [] }))

    // Assert
    const dialog = await screen.findByRole('dialog')
    expect(screen.getByRole('region', { name: 'Parça metni' }).textContent).toBe(content)
    expect(dialog.querySelector('img, script')).toBeNull()
    expect((window as unknown as { __xss?: number }).__xss).toBeUndefined()
  })

  it('givenChunkNotFound_whenOpened_thenShowsChunkGoneMessageAndHidesText', async () => {
    // Arrange
    server.use(http.get(CHUNK_URL, () => problem(404, 'CHUNK_NOT_FOUND')))
    const user = userEvent.setup()

    // Act
    const { onClose } = renderDialog()

    // Assert
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Bu parça artık yok; kaynak yeni bir sürüme geçmiş olabilir.',
    )
    expect(screen.queryByRole('region', { name: 'Parça metni' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Kaynakta göster' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Kapat' }))
    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('givenSourceNotFound_whenOpened_thenShowsSourceNotFoundMessage', async () => {
    // Arrange
    server.use(http.get(CHUNK_URL, () => problem(404, 'SOURCE_NOT_FOUND')))

    // Act
    renderDialog()

    // Assert
    expect(await screen.findByRole('alert')).toHaveTextContent('Kaynak bulunamadı.')
    expect(screen.queryByRole('region', { name: 'Parça metni' })).not.toBeInTheDocument()
  })

  it('givenWorkspaceNotFound_whenOpened_thenShowsWorkspaceNotFoundMessage', async () => {
    // Arrange
    server.use(http.get(CHUNK_URL, () => problem(404, 'WORKSPACE_NOT_FOUND')))

    // Act
    renderDialog()

    // Assert
    expect(await screen.findByRole('alert')).toHaveTextContent('Çalışma alanı bulunamadı.')
  })

  it.each([
    ['server error', () => problem(500, 'INTERNAL_ERROR')],
    ['network error', () => HttpResponse.error()],
  ])('givenChunkCheckFails_%s_whenOpened_thenKeepsTextWithoutAlert', async (_name, respond) => {
    // Arrange
    let requests = 0
    server.use(
      http.get(CHUNK_URL, () => {
        requests += 1
        return respond()
      }),
    )
    const target = hit({ content: 'Yıllık izin 14 iş günüdür.', matchedTerms: [] })

    // Act
    renderDialog(target)

    // Assert
    await waitFor(() => expect(requests).toBeGreaterThanOrEqual(1))
    await waitFor(() => expect(screen.getByRole('dialog')).toBeInTheDocument())
    expect(screen.getByRole('region', { name: 'Parça metni' }).textContent).toBe('Yıllık izin 14 iş günüdür.')
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Kaynakta göster' })).toBeInTheDocument()
  })

  it('givenOpenedHit_whenRendered_thenSourceLinkPointsToHighlightedChunkAndChecksExistenceOnce', async () => {
    // Arrange
    const requests = countChunkRequests()

    // Act
    renderDialog(hit({ sourceId: 10001, chunkId: 1380 }))

    // Assert
    expect(await screen.findByRole('link', { name: 'Kaynakta göster' })).toHaveAttribute(
      'href',
      '/w/42/kaynaklar/10001?parca=1380',
    )
    await waitFor(() => expect(requests.count()).toBe(1))
  })

  it('givenChunkUrlOfOtherSource_whenOpened_thenRequestsHitsOwnSourceAndChunk', async () => {
    // Arrange
    let requestedPath = ''
    server.use(
      http.get(CHUNK_URL, ({ request }) => {
        requestedPath = new URL(request.url).pathname
        return HttpResponse.json(chunk(1381))
      }),
    )

    // Act
    renderDialog(hit({ sourceId: 10002, chunkId: 1381 }))

    // Assert
    await waitFor(() => expect(requestedPath).toBe('/api/workspaces/42/sources/10002/chunks/1381'))
  })

  it.each([
    ['Paneli kapat', async (user: ReturnType<typeof userEvent.setup>) => user.click(screen.getByRole('button', { name: 'Paneli kapat' }))],
    ['Kapat', async (user: ReturnType<typeof userEvent.setup>) => user.click(screen.getByRole('button', { name: 'Kapat' }))],
    ['Escape', async (user: ReturnType<typeof userEvent.setup>) => user.keyboard('{Escape}')],
    ['backdrop', async (user: ReturnType<typeof userEvent.setup>) => user.click(screen.getByRole('dialog'))],
  ])('givenOpenedHit_whenClosedBy_%s_thenOnCloseCalledOnce', async (_name, close) => {
    // Arrange
    const user = userEvent.setup()
    const { onClose } = renderDialog()
    await screen.findByRole('region', { name: 'Parça metni' })

    // Act
    await close(user)

    // Assert
    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('givenOpenedHit_whenClickingInsideText_thenDoesNotClose', async () => {
    // Arrange
    const user = userEvent.setup()
    const { onClose } = renderDialog()

    // Act
    await user.click(await screen.findByRole('region', { name: 'Parça metni' }))

    // Assert
    expect(onClose).not.toHaveBeenCalled()
  })

  it.each([
    ['text', undefined],
    ['chunk gone', () => problem(404, 'CHUNK_NOT_FOUND')],
  ])('givenOpenedHit_%s_whenAxeRuns_thenNoViolations', async (_name, respond) => {
    // Arrange
    if (respond !== undefined) {
      server.use(http.get(CHUNK_URL, respond))
    }
    const { baseElement } = renderDialog(hit({ content: 'Yıllık izin 14 iş günüdür.' }))
    if (respond !== undefined) {
      await screen.findByRole('alert')
    } else {
      await screen.findByRole('region', { name: 'Parça metni' })
    }

    // Act
    const results = await axe(baseElement)

    // Assert
    expect(results).toHaveNoViolations()
  })
})
