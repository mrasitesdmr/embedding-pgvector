import { act, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { chunk, BELGELERIM_SOURCES, ingestionJob, pageOf } from '../../../test/fixtures.ts'
import { problem } from '../../../test/problem.ts'
import { currentUrl, renderApp } from '../../../test/renderWithProviders.tsx'
import { server } from '../../../test/server.ts'
import type { IngestionJobResponse, SourceResponse } from '../model/types.ts'

const MANUAL_NAME = 'Atlaskap-Lojistik-Surec-El-Kitabi'

const QUEUED = ingestionJob({ jobId: 7, sourceId: 10002, stage: 'QUEUED' })
const PARSING = ingestionJob({ jobId: 7, sourceId: 10002, stage: 'PARSING', progress: 0.05 })
const CHUNKING = ingestionJob({ jobId: 7, sourceId: 10002, stage: 'CHUNKING', progress: 0.1 })
const EMBEDDING = ingestionJob({
  jobId: 7,
  sourceId: 10002,
  stage: 'EMBEDDING',
  progress: 0.458879,
  embeddedChunks: 96,
  totalChunks: 214,
})
const WRITING = ingestionJob({ jobId: 7, sourceId: 10002, stage: 'WRITING', progress: 0.9, embeddedChunks: 214, totalChunks: 214 })
const DONE = ingestionJob({
  jobId: 7,
  sourceId: 10002,
  stage: 'DONE',
  progress: 1,
  embeddedChunks: 214,
  totalChunks: 214,
  finishedAt: '2026-10-25T10:01:00Z',
})

function manualWith(overrides: Partial<SourceResponse>): SourceResponse {
  return { ...BELGELERIM_SOURCES[1], ...overrides }
}

function sourcesWithManual(manual: SourceResponse): SourceResponse[] {
  return BELGELERIM_SOURCES.map((candidate) => (candidate.sourceId === 10002 ? manual : candidate))
}

const ingestingManual = (job: IngestionJobResponse) => manualWith({ status: 'INGESTING', latestJob: job })
const readyManual = (job: IngestionJobResponse) =>
  manualWith({ status: 'READY', version: '3.2', chunkCount: 214, latestJob: job })

function stubScenario(options: { before: SourceResponse; after: SourceResponse; script: (IngestionJobResponse | 'network-error' | 'not-found')[] }) {
  const state = { jobRequests: 0, listRequests: 0, finished: false }
  server.use(
    http.get('/api/workspaces/:workspaceId/sources', () => {
      state.listRequests += 1
      return HttpResponse.json(pageOf(sourcesWithManual(state.finished ? options.after : options.before)))
    }),
    http.get('/api/workspaces/:workspaceId/sources/10002', () =>
      HttpResponse.json(state.finished ? options.after : options.before),
    ),
    http.get('/api/workspaces/:workspaceId/ingestion-jobs/7', () => {
      const step = options.script[Math.min(state.jobRequests, options.script.length - 1)]
      state.jobRequests += 1
      if (step === 'network-error') {
        return HttpResponse.error()
      }
      if (step === 'not-found') {
        return problem(404, 'INGESTION_JOB_NOT_FOUND')
      }
      if (step.stage === 'DONE' || step.stage === 'FAILED') {
        state.finished = true
      }
      return HttpResponse.json(step)
    }),
  )
  return state
}

async function tick(milliseconds = 1000) {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(milliseconds)
  })
}

function manualRow(): HTMLElement {
  return screen.getByRole('link', { name: MANUAL_NAME }).closest('tr') as HTMLElement
}

describe('ingestion polling', () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('givenJobAdvancingThroughStages_whenPolled_thenShowsEachStageThenToastHighlightAndStops', async () => {
    // Arrange
    const state = stubScenario({
      before: ingestingManual(QUEUED),
      after: readyManual(DONE),
      script: [PARSING, CHUNKING, EMBEDDING, WRITING, DONE],
    })
    renderApp('/w/42/kaynaklar')
    await screen.findByRole('link', { name: MANUAL_NAME })
    const stageTexts: string[] = [within(manualRow()).getByText('Sıraya alındı').textContent ?? '']
    const requestsAtOpen = state.jobRequests

    // Act
    await tick()
    stageTexts.push((await within(manualRow()).findByText('Dosya okunuyor')).textContent ?? '')
    await tick()
    stageTexts.push((await within(manualRow()).findByText('Parçalara bölünüyor')).textContent ?? '')
    await tick()
    stageTexts.push((await within(manualRow()).findByText('Embed ediliyor · 96/214 parça')).textContent ?? '')
    const embeddingBar = within(manualRow()).getByRole('progressbar')
    const embeddingPercent = embeddingBar.getAttribute('aria-valuenow')
    await tick()
    stageTexts.push((await within(manualRow()).findByText('Yazılıyor')).textContent ?? '')
    await tick()
    const toast = await screen.findByText(`${MANUAL_NAME} v3.2 hazır · 214 parça`)

    // Assert
    expect(requestsAtOpen).toBe(0)
    expect(stageTexts).toEqual([
      'Sıraya alındı',
      'Dosya okunuyor',
      'Parçalara bölünüyor',
      'Embed ediliyor · 96/214 parça',
      'Yazılıyor',
    ])
    expect(embeddingPercent).toBe('46')
    expect(toast.closest('[role="status"]')).not.toBeNull()
    await waitFor(() => expect(manualRow()).toHaveClass('isJustCompleted'))
    await waitFor(() => expect(within(manualRow()).queryByRole('progressbar')).not.toBeInTheDocument())
    expect(within(manualRow()).getByText('Hazır')).toBeInTheDocument()
    expect(within(manualRow()).getByText(/v3\.2/)).toBeInTheDocument()
    expect(manualRow().textContent).toContain('214')
    expect(state.listRequests).toBeGreaterThanOrEqual(2)
    expect(state.jobRequests).toBe(5)
    await tick(3100)
    expect(manualRow()).not.toHaveClass('isJustCompleted')
    expect(screen.getByText(`${MANUAL_NAME} v3.2 hazır · 214 parça`)).toBeInTheDocument()
    await tick(2000)
    expect(screen.queryByText(`${MANUAL_NAME} v3.2 hazır · 214 parça`)).not.toBeInTheDocument()
    expect(state.jobRequests).toBe(5)
  })

  it.each([
    ['NO_TEXT_EXTRACTED', 'dosyada metin yok (sadece görsel içeren PDF ya da sadece başlık satırı olan CSV olabilir)'],
    ['EMBEDDING_UNAVAILABLE', 'embedding servisine (Ollama) ulaşılamıyor'],
    ['EMBEDDING_MODEL_NOT_FOUND', "embedding modeli Ollama'da yüklü değil (ollama pull bge-m3)"],
  ] as const)('givenJobFails_whenPolled_thenShowsFailureReasonInRowAndStopsWithoutToast (%s)', async (errorCode, reason) => {
    // Arrange
    const failed = ingestionJob({ jobId: 7, sourceId: 10002, stage: 'FAILED', progress: 0.1, errorCode })
    const state = stubScenario({
      before: ingestingManual(EMBEDDING),
      after: manualWith({ status: 'READY', latestJob: failed }),
      script: [failed],
    })
    renderApp('/w/42/kaynaklar')
    await screen.findByText('Embed ediliyor · 96/214 parça')

    // Act
    await tick()

    // Assert
    expect(await within(manualRow()).findByText(`Son yükleme başarısız: ${reason}`)).toBeInTheDocument()
    expect(within(manualRow()).queryByRole('progressbar')).not.toBeInTheDocument()
    expect(within(manualRow()).getByText('Hazır')).toBeInTheDocument()
    expect(within(manualRow()).getByRole('button', { name: 'Yeni sürüm yükle' })).toBeEnabled()
    expect(screen.getByRole('status')).toBeEmptyDOMElement()
    expect(state.listRequests).toBeGreaterThanOrEqual(2)
    await tick(3000)
    expect(state.jobRequests).toBe(1)
  })

  it('givenListWithActiveLatestJob_whenPageOpens_thenShowsProgressAndPollsJob', async () => {
    // Arrange
    const state = stubScenario({ before: ingestingManual(EMBEDDING), after: readyManual(DONE), script: [EMBEDDING] })

    // Act
    renderApp('/w/42/kaynaklar')
    const stage = await screen.findByText('Embed ediliyor · 96/214 parça')
    const requestsAtOpen = state.jobRequests
    await tick()

    // Assert
    expect(stage).toBeInTheDocument()
    expect(requestsAtOpen).toBe(0)
    await waitFor(() => expect(state.jobRequests).toBe(1))
  })

  it('givenListWithFinishedLatestJobs_whenPageOpens_thenNoPollingNoToastButFailureShown', async () => {
    // Arrange
    const failed = ingestionJob({ jobId: 9, sourceId: 10003, stage: 'FAILED', errorCode: 'FILE_UNREADABLE' })
    let jobRequests = 0
    server.use(
      http.get('/api/workspaces/:workspaceId/sources', () =>
        HttpResponse.json(
          pageOf(
            BELGELERIM_SOURCES.map((candidate) => {
              if (candidate.sourceId === 10002) return { ...candidate, latestJob: DONE }
              if (candidate.sourceId === 10003) return { ...candidate, status: 'READY' as const, latestJob: failed }
              return candidate
            }),
          ),
        ),
      ),
      http.get('/api/workspaces/:workspaceId/ingestion-jobs/:jobId', () => {
        jobRequests += 1
        return problem(404, 'INGESTION_JOB_NOT_FOUND')
      }),
    )

    // Act
    renderApp('/w/42/kaynaklar')
    const failureLine = await screen.findByText('Son yükleme başarısız: PDF okunamıyor (dosya bozuk ya da parola korumalı olabilir)')
    await tick(3000)

    // Assert
    expect(failureLine.closest('tr')).toHaveTextContent('Masraf kuralları tablosu')
    expect(jobRequests).toBe(0)
    expect(screen.getByRole('status')).toBeEmptyDOMElement()
    expect(manualRow()).not.toHaveClass('isJustCompleted')
    expect(within(manualRow()).queryByRole('progressbar')).not.toBeInTheDocument()
  })

  it('givenActiveJob_whenUserGoesToSearchAndJobFinishes_thenToastOnSearchPageAndSearchWorks', async () => {
    // Arrange
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    stubScenario({ before: ingestingManual(EMBEDDING), after: readyManual(DONE), script: [DONE] })
    renderApp('/w/42/kaynaklar')
    await screen.findByText('Embed ediliyor · 96/214 parça')

    // Act
    await user.click(screen.getByRole('link', { name: 'Arama' }))
    await screen.findByRole('heading', { level: 1, name: 'Bilgi bankası' })
    await tick()
    const toast = await screen.findByText(`${MANUAL_NAME} v3.2 hazır · 214 parça`)
    await user.type(screen.getByLabelText('Arama metni'), 'Yıllık izin kaç gün?{Enter}')

    // Assert
    expect(currentUrl()).toBe('/w/42/arama')
    expect(toast.closest('[role="status"]')).not.toBeNull()
    expect(await screen.findByText('2 sonuç')).toBeInTheDocument()
    expect(screen.getByText('Yıllık izin kaç gün?', { selector: 'h3, h4, strong, b, span, p' })).toBeInTheDocument()
  })

  it('givenPollingNetworkError_whenBackendReturns_thenKeepsLastStageThenShowsInterrupted', async () => {
    // Arrange
    const interrupted = ingestionJob({
      jobId: 7,
      sourceId: 10002,
      stage: 'FAILED',
      progress: 0.458879,
      errorCode: 'INGESTION_INTERRUPTED',
    })
    const state = stubScenario({
      before: ingestingManual(EMBEDDING),
      after: manualWith({ status: 'READY', latestJob: interrupted }),
      script: ['network-error', interrupted],
    })
    renderApp('/w/42/kaynaklar')
    await screen.findByText('Embed ediliyor · 96/214 parça')

    // Act
    await tick()
    await waitFor(() => expect(state.jobRequests).toBe(1))
    const alertCountAfterError = screen.queryAllByRole('alert').length
    const stageAfterError = within(manualRow()).queryByText('Embed ediliyor · 96/214 parça') !== null
    await tick()

    // Assert
    expect(stageAfterError).toBe(true)
    expect(alertCountAfterError).toBe(0)
    expect(
      await within(manualRow()).findByText(
        'Son yükleme başarısız: iş yarıda kaldı (sunucu iş sürerken durdu); dosyayı tekrar yükle',
      ),
    ).toBeInTheDocument()
    expect(state.jobRequests).toBe(2)
  })

  it('givenJobNotFound_whenPolled_thenStopsPollingAndRefreshesList', async () => {
    // Arrange
    const state = stubScenario({
      before: ingestingManual(EMBEDDING),
      after: manualWith({ status: 'READY' }),
      script: ['not-found'],
    })
    renderApp('/w/42/kaynaklar')
    await screen.findByText('Embed ediliyor · 96/214 parça')
    const listRequestsAtOpen = state.listRequests

    // Act
    await tick()
    await waitFor(() => expect(state.listRequests).toBeGreaterThan(listRequestsAtOpen))
    await tick(3000)

    // Assert
    expect(state.jobRequests).toBe(1)
    expect(screen.queryAllByRole('alert')).toHaveLength(0)
  })

  it('givenDetailOpenDuringJob_whenJobDone_thenInfoBoxesAndChunksShowNewVersion', async () => {
    // Arrange
    const state = stubScenario({
      before: ingestingManual(EMBEDDING),
      after: readyManual(DONE),
      script: [EMBEDDING, DONE],
    })
    server.use(
      http.get('/api/workspaces/:workspaceId/sources/:sourceId/chunks', () =>
        HttpResponse.json(
          pageOf(
            state.finished
              ? [chunk(2001, { heading: 'Yeni sürümün ilk başlığı' })]
              : [chunk(1380, { heading: 'Eski sürümün ilk başlığı' })],
          ),
        ),
      ),
    )
    renderApp('/w/42/kaynaklar/10002')
    await screen.findByText('Eski sürümün ilk başlığı')
    const hadProgressBar =
      screen.queryByRole('progressbar', { name: `${MANUAL_NAME} yükleme ilerlemesi` }) !== null
    const boxesBefore = readInfoBoxes()

    // Act
    await tick()
    await tick()

    // Assert
    expect(hadProgressBar).toBe(true)
    expect(boxesBefore).toMatchObject({ Sürüm: '3.1', Parça: '5', Durum: 'İşleniyor' })
    expect(await screen.findByText('Yeni sürümün ilk başlığı')).toBeInTheDocument()
    expect(screen.queryByText('Eski sürümün ilk başlığı')).not.toBeInTheDocument()
    await waitFor(() => expect(readInfoBoxes()).toMatchObject({ Sürüm: '3.2', Parça: '214', Durum: 'Hazır' }))
    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Yeni sürüm yükle' })).toBeEnabled()
    expect(await screen.findByText(`${MANUAL_NAME} v3.2 hazır · 214 parça`)).toBeInTheDocument()
  })
})

function readInfoBoxes(): Record<string, string | null | undefined> {
  return Object.fromEntries(
    Array.from(document.querySelectorAll('dl dt')).map((term) => [term.textContent, term.nextElementSibling?.textContent]),
  )
}
