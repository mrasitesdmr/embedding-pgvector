import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { axe } from 'vitest-axe'
import { describe, expect, it } from 'vitest'
import { ingestionJob, source } from '../../../test/fixtures.ts'
import { readMultipart } from '../../../test/multipart.ts'
import type { MultipartParts } from '../../../test/multipart.ts'
import { problem } from '../../../test/problem.ts'
import { currentUrl, renderApp } from '../../../test/renderWithProviders.tsx'
import { server } from '../../../test/server.ts'
import type { IngestionJobResponse, SourceResponse } from '../model/types.ts'

const HANDBOOK = 'Atlaskap-Lojistik-Surec-El-Kitabi'
const HANDBOOK_FILE = `${HANDBOOK}.pdf`

const EMBEDDING_HALF = ingestionJob({
  jobId: 1,
  sourceId: 10001,
  version: null,
  fileName: HANDBOOK_FILE,
  stage: 'EMBEDDING',
  progress: 0.5,
  embeddedChunks: 24,
  totalChunks: 48,
})
const DONE = ingestionJob({
  jobId: 1,
  sourceId: 10001,
  version: null,
  fileName: HANDBOOK_FILE,
  stage: 'DONE',
  progress: 1,
  embeddedChunks: 48,
  totalChunks: 48,
  finishedAt: '2026-10-25T10:01:00Z',
})
const FAILED = ingestionJob({
  jobId: 1,
  sourceId: 10001,
  version: null,
  fileName: HANDBOOK_FILE,
  stage: 'FAILED',
  progress: 0.1,
  errorCode: 'EMBEDDING_UNAVAILABLE',
})

function handbook(overrides: Partial<SourceResponse>): SourceResponse {
  return source({ sourceId: 10001, name: HANDBOOK, kind: 'PDF', fileName: HANDBOOK_FILE, ...overrides })
}

const ingestingHandbook = (job: IngestionJobResponse) =>
  handbook({ status: 'INGESTING', syncedAt: null, chunkCount: 0, latestJob: job })
const readyHandbook = handbook({ status: 'READY', chunkCount: 48, latestJob: DONE })
const failedHandbook = handbook({ status: 'FAILED', syncedAt: null, chunkCount: 0, latestJob: FAILED })

function serveSource(current: () => SourceResponse) {
  server.use(
    http.get('/api/workspaces/:workspaceId/sources/10001', () => HttpResponse.json(current())),
    http.get('/api/workspaces/:workspaceId/sources', () =>
      HttpResponse.json({ content: [current()], page: 0, size: 100, totalElements: 1, totalPages: 1 }),
    ),
    http.get('/api/workspaces/:workspaceId/ingestion-jobs/1', () => {
      const job = current().latestJob
      return job === null ? problem(404, 'INGESTION_JOB_NOT_FOUND') : HttpResponse.json(job)
    }),
  )
}

function trackUploads(response: () => Response = () => HttpResponse.json(EMBEDDING_HALF, { status: 202 })) {
  const received: MultipartParts[] = []
  server.use(
    http.post('/api/workspaces/:workspaceId/sources', async ({ request }) => {
      received.push(await readMultipart(request))
      return response()
    }),
  )
  return received
}

function pdfFile(name = HANDBOOK_FILE): File {
  return new File(['%PDF-1.7 icerik'], name, { type: 'application/pdf' })
}

function newUser() {
  return userEvent.setup({ applyAccept: false })
}

describe('UploadPage', () => {
  it('givenBlankName_whenUploadSamplePdf_thenSendsFileNameWithoutExtension', async () => {
    // Arrange
    const user = newUser()
    const received = trackUploads()
    serveSource(() => ingestingHandbook(EMBEDDING_HALF))
    renderApp('/w/42/belge-yukle')

    // Act
    await user.upload(await screen.findByLabelText('Dosya'), pdfFile())
    await user.click(screen.getByRole('button', { name: 'Belgeyi yükle' }))

    // Assert
    await waitFor(() => expect(received).toHaveLength(1))
    expect(received[0].fields).toEqual({ name: HANDBOOK })
    expect(received[0].files.map((file) => file.fileName)).toEqual([HANDBOOK_FILE])
  })

  it('givenFileNameShorterThanTwo_whenUpload_thenShowsNameRuleAndSendsNothing', async () => {
    // Arrange
    const user = newUser()
    const received = trackUploads()
    renderApp('/w/42/belge-yukle')

    // Act
    await user.upload(await screen.findByLabelText('Dosya'), pdfFile('a.pdf'))
    await user.click(screen.getByRole('button', { name: 'Belgeyi yükle' }))

    // Assert
    expect(await screen.findByRole('alert')).toHaveTextContent('Kaynak adı 2-80 karakter olmalı.')
    expect(screen.getByLabelText('Kaynak adı')).toHaveValue('a')
    expect(received).toHaveLength(0)
  })

  it('givenUploadAccepted_whenJobProgressesToDone_thenShowsReadyAndAskLink', async () => {
    // Arrange
    const user = newUser()
    let finished = false
    trackUploads()
    serveSource(() => (finished ? readyHandbook : ingestingHandbook(EMBEDDING_HALF)))
    renderApp('/w/42/belge-yukle')

    // Act
    await user.upload(await screen.findByLabelText('Dosya'), pdfFile())
    await user.click(screen.getByRole('button', { name: 'Belgeyi yükle' }))

    // Assert
    const progress = await screen.findByRole('progressbar')
    expect(progress).toHaveAttribute('aria-valuenow', '50')
    expect(currentUrl()).toBe('/w/42/belge-yukle?kaynak=10001')
    finished = true
    const readyHeading = await screen.findByRole('heading', { name: 'Belge hazır' }, { timeout: 5000 })
    expect(within(readyHeading.parentElement as HTMLElement).getByText(`${HANDBOOK} hazır · 48 parça`)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Soru sor' })).toHaveAttribute('href', '/w/42/arama')
    expect(screen.getByRole('link', { name: 'Başka bir belge yükle' })).toHaveAttribute('href', '/w/42/belge-yukle')
    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument()
  })

  it('givenIngestingSourceInUrl_whenOpenUploadPage_thenShowsProgressImmediately', async () => {
    // Arrange
    serveSource(() => ingestingHandbook(EMBEDDING_HALF))

    // Act
    renderApp('/w/42/belge-yukle?kaynak=10001')

    // Assert
    expect(await screen.findByRole('progressbar')).toHaveAttribute('aria-valuenow', '50')
    expect(screen.getByRole('heading', { name: HANDBOOK })).toBeInTheDocument()
    expect(screen.queryByLabelText('Dosya')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Belgeyi yükle' })).not.toBeInTheDocument()
  })

  it('givenUnknownSourceInUrl_whenOpenUploadPage_thenShowsNotFoundAndNewUploadLink', async () => {
    // Arrange
    server.use(http.get('/api/workspaces/:workspaceId/sources/777', () => problem(404, 'SOURCE_NOT_FOUND')))

    // Act
    renderApp('/w/42/belge-yukle?kaynak=777')

    // Assert
    expect(await screen.findByRole('alert')).toHaveTextContent('Kaynak bulunamadı.')
    expect(screen.getByRole('link', { name: 'Yeni belge yükle' })).toHaveAttribute('href', '/w/42/belge-yukle')
  })

  it('givenNonNumericSourceParam_whenOpenUploadPage_thenShowsForm', async () => {
    // Act
    renderApp('/w/42/belge-yukle?kaynak=abc')

    // Assert
    expect(await screen.findByRole('button', { name: 'Belgeyi yükle' })).toBeInTheDocument()
    expect(screen.getByText('PDF, TXT, MD, MARKDOWN ya da CSV · en fazla 20 MB')).toBeInTheDocument()
  })

  it('givenFailedFirstIngestion_whenRetry_thenUploadsVersionToSameSource', async () => {
    // Arrange
    const user = newUser()
    serveSource(() => failedHandbook)
    const versions: { sourceId: string; parts: MultipartParts }[] = []
    const created = trackUploads()
    server.use(
      http.post('/api/workspaces/:workspaceId/sources/:sourceId/versions', async ({ request, params }) => {
        versions.push({ sourceId: String(params.sourceId), parts: await readMultipart(request) })
        return HttpResponse.json(EMBEDDING_HALF, { status: 202 })
      }),
    )
    renderApp('/w/42/belge-yukle?kaynak=10001')
    expect(await screen.findByRole('heading', { name: 'Belge yüklenemedi' })).toBeInTheDocument()
    expect(screen.getByText('Son yükleme başarısız: embedding servisine (Ollama) ulaşılamıyor')).toBeInTheDocument()

    // Act
    await user.click(screen.getByRole('button', { name: 'Yeniden dene' }))
    const dialog = await screen.findByRole('dialog', { name: 'Yeni sürüm yükle' })
    await user.upload(within(dialog).getByLabelText('Dosya'), pdfFile())
    await user.click(within(dialog).getByRole('button', { name: 'Yeni sürümü yükle' }))

    // Assert
    await waitFor(() => expect(versions).toHaveLength(1))
    expect(versions[0].sourceId).toBe('10001')
    expect(versions[0].parts.files.map((file) => file.fileName)).toEqual([HANDBOOK_FILE])
    expect(created).toHaveLength(0)
  })

  it.each([
    [409, 'SOURCE_NAME_TAKEN', 'Kaynak adı', 'Bu ad çalışma alanında başka bir kaynakta kullanılıyor.'],
    [415, 'UNSUPPORTED_FILE_TYPE', 'Dosya', 'Bu dosya türü desteklenmiyor. PDF, TXT, MD, MARKDOWN ya da CSV seç.'],
    [413, 'FILE_TOO_LARGE', 'Dosya', "Dosya 20 MB'tan büyük olamaz."],
    [409, 'SOURCE_LIMIT_REACHED', null, 'Bu çalışma alanı 10 kaynak sınırına ulaştı.'],
    [
      503,
      'EMBEDDING_UNAVAILABLE',
      null,
      "Embedding servisine (Ollama) ulaşılamıyor. Ollama'nın çalıştığını kontrol et ya da Kelime yöntemiyle ara.",
    ],
  ] as const)('givenUploadError_whenSubmit_thenShowsTurkishMessage (%i %s)', async (status, errorCode, fieldLabel, message) => {
    // Arrange
    const user = newUser()
    trackUploads(() => problem(status, errorCode))
    renderApp('/w/42/belge-yukle')

    // Act
    await user.upload(await screen.findByLabelText('Dosya'), pdfFile())
    await user.click(screen.getByRole('button', { name: 'Belgeyi yükle' }))

    // Assert
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent(message)
    expect(document.body.textContent).not.toContain('Embedding service is not reachable')
    if (fieldLabel !== null) {
      expect(screen.getByLabelText(fieldLabel)).toHaveAttribute('aria-invalid', 'true')
    }
    expect(screen.getByLabelText('Kaynak adı')).toHaveValue(HANDBOOK)
    expect(currentUrl()).toBe('/w/42/belge-yukle')
  })

  it('givenSameFileTwiceWithBlankName_whenSecondUploadRejected_thenNameFieldKeepsDerivedName', async () => {
    // Arrange
    const user = newUser()
    trackUploads(() => problem(409, 'SOURCE_NAME_TAKEN'))
    renderApp('/w/42/belge-yukle')

    // Act
    await user.upload(await screen.findByLabelText('Dosya'), pdfFile())
    await user.click(screen.getByRole('button', { name: 'Belgeyi yükle' }))

    // Assert
    expect(await screen.findByRole('alert')).toHaveTextContent('Bu ad çalışma alanında başka bir kaynakta kullanılıyor.')
    expect(screen.getByLabelText('Kaynak adı')).toHaveValue(HANDBOOK)
  })

  it('givenUploadPageStates_whenRendered_thenHasNoAxeViolations', async () => {
    // Arrange
    serveSource(() => ingestingHandbook(EMBEDDING_HALF))
    const form = renderApp('/w/42/belge-yukle')
    await screen.findByRole('button', { name: 'Belgeyi yükle' })
    const formResults = await axe(form.container)
    form.unmount()
    const progress = renderApp('/w/42/belge-yukle?kaynak=10001')
    await screen.findByRole('progressbar')
    const progressResults = await axe(progress.container)
    progress.unmount()
    serveSource(() => readyHandbook)
    const ready = renderApp('/w/42/belge-yukle?kaynak=10001')
    await screen.findByRole('heading', { name: 'Belge hazır' })
    const readyResults = await axe(ready.container)
    ready.unmount()
    serveSource(() => failedHandbook)
    const failed = renderApp('/w/42/belge-yukle?kaynak=10001')
    await screen.findByRole('heading', { name: 'Belge yüklenemedi' })

    // Act
    const failedResults = await axe(failed.container)

    // Assert
    expect(formResults).toHaveNoViolations()
    expect(progressResults).toHaveNoViolations()
    expect(readyResults).toHaveNoViolations()
    expect(failedResults).toHaveNoViolations()
  })
})
