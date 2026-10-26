import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { BELGELERIM_SOURCES, ingestionJob, pageOf } from '../../../test/fixtures.ts'
import { readMultipart } from '../../../test/multipart.ts'
import type { MultipartParts } from '../../../test/multipart.ts'
import { problem } from '../../../test/problem.ts'
import { currentUrl, renderApp } from '../../../test/renderWithProviders.tsx'
import { server } from '../../../test/server.ts'

const PDF_V32 = 'Atlaskap-Lojistik-Surec-El-Kitabi-v3.2.pdf'

function pdfFile(name = PDF_V32, size = 2_516_582): File {
  const file = new File(['%PDF-1.4'], name, { type: 'application/pdf' })
  Object.defineProperty(file, 'size', { value: size })
  return file
}

function newUser() {
  return userEvent.setup({ applyAccept: false })
}

function trackVersionRequests() {
  const received: { sourceId: string; parts: MultipartParts }[] = []
  server.use(
    http.post('/api/workspaces/:workspaceId/sources/:sourceId/versions', async ({ request, params }) => {
      received.push({ sourceId: String(params.sourceId), parts: await readMultipart(request) })
      return HttpResponse.json(ingestionJob({ jobId: 7, sourceId: 10002 }), { status: 202 })
    }),
  )
  return received
}

async function openPdfDialog(user: ReturnType<typeof newUser>) {
  renderApp('/w/42/kaynaklar')
  const row = (await screen.findByRole('link', { name: 'Atlaskap-Lojistik-Surec-El-Kitabi' })).closest('tr') as HTMLElement
  await user.click(within(row).getByRole('button', { name: 'Yeni sürüm yükle' }))
  return { row, dialog: screen.getByRole('dialog', { name: 'Yeni sürüm yükle' }) }
}

describe('UploadVersionDialog', () => {
  it('givenPdfSource_whenUploadVersionWithLabel_thenDialogShowsInfoAndPostsFileAndVersion', async () => {
    // Arrange
    const user = newUser()
    const received = trackVersionRequests()
    const { dialog } = await openPdfDialog(user)

    // Act
    await user.upload(within(dialog).getByLabelText('Dosya'), pdfFile())
    await user.type(within(dialog).getByLabelText('Sürüm etiketi (isteğe bağlı)'), ' 3.2 ')
    const infoBeforeSubmit = dialog.textContent
    await user.click(within(dialog).getByRole('button', { name: 'Yeni sürümü yükle' }))

    // Assert
    expect(infoBeforeSubmit).toContain('Atlaskap-Lojistik-Surec-El-Kitabi · şu an v3.1 · 5 parça')
    expect(infoBeforeSubmit).toContain('Yeni sürüm hazır olana kadar arama mevcut sürümün parçalarını döndürür.')
    expect(infoBeforeSubmit).toContain(PDF_V32)
    expect(infoBeforeSubmit).toContain('2,4 MB')
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(received).toHaveLength(1)
    expect(received[0].sourceId).toBe('10002')
    expect(received[0].parts.fields).toEqual({ version: '3.2' })
    expect(received[0].parts.files.map((file) => [file.field, file.fileName])).toEqual([['file', PDF_V32]])
  })

  it('givenEmptyVersionLabel_whenUploadVersion_thenNoVersionPartIsSent', async () => {
    // Arrange
    const user = newUser()
    const received = trackVersionRequests()
    const { dialog } = await openPdfDialog(user)

    // Act
    await user.upload(within(dialog).getByLabelText('Dosya'), pdfFile())
    await user.type(within(dialog).getByLabelText('Sürüm etiketi (isteğe bağlı)'), '   ')
    await user.click(within(dialog).getByRole('button', { name: 'Yeni sürümü yükle' }))

    // Assert
    await waitFor(() => expect(received).toHaveLength(1))
    expect(received[0].parts.fields).toEqual({})
  })

  it('givenPdfSource_whenSelectMarkdown_thenShowsKindRuleAndSendsNothing', async () => {
    // Arrange
    const user = newUser()
    const received = trackVersionRequests()
    const { dialog } = await openPdfDialog(user)

    // Act
    await user.upload(within(dialog).getByLabelText('Dosya'), new File(['# a'], 'notlar.md'))
    await user.click(within(dialog).getByRole('button', { name: 'Yeni sürümü yükle' }))

    // Assert
    expect(within(dialog).getByLabelText('Dosya')).toHaveAttribute('accept', '.pdf')
    expect(within(dialog).getByRole('alert')).toHaveTextContent('Bu kaynağa sadece PDF dosyası yüklenebilir.')
    expect(received).toHaveLength(0)
  })

  it('givenNoFile_whenSubmit_thenAsksForFileAndSendsNothing', async () => {
    // Arrange
    const user = newUser()
    const received = trackVersionRequests()
    const { dialog } = await openPdfDialog(user)

    // Act
    await user.click(within(dialog).getByRole('button', { name: 'Yeni sürümü yükle' }))

    // Assert
    expect(within(dialog).getByRole('alert')).toHaveTextContent('Yüklenecek dosyayı seç.')
    expect(within(dialog).getByLabelText('Dosya')).toHaveFocus()
    expect(received).toHaveLength(0)
  })

  it('givenInProgressConflict_whenUploadVersion_thenShowsInProgressMessageAndRefreshesList', async () => {
    // Arrange
    const user = newUser()
    let listRequests = 0
    server.use(
      http.get('/api/workspaces/:workspaceId/sources', () => {
        listRequests += 1
        return HttpResponse.json(pageOf(BELGELERIM_SOURCES))
      }),
      http.post('/api/workspaces/:workspaceId/sources/:sourceId/versions', () => problem(409, 'INGESTION_IN_PROGRESS')),
    )
    const { dialog } = await openPdfDialog(user)
    const listRequestsBeforeUpload = listRequests
    await user.upload(within(dialog).getByLabelText('Dosya'), pdfFile())

    // Act
    await user.click(within(dialog).getByRole('button', { name: 'Yeni sürümü yükle' }))

    // Assert
    expect(await within(dialog).findByRole('alert')).toHaveTextContent('Bu kaynağın yüklemesi sürüyor. Bitince tekrar dene.')
    await waitFor(() => expect(listRequests).toBeGreaterThan(listRequestsBeforeUpload))
    expect(screen.getByRole('dialog', { name: 'Yeni sürüm yükle' })).toBeInTheDocument()
  })

  it.each([
    [409, 'SOURCE_KIND_MISMATCH', undefined, 'Dosya', 'Bu kaynağa sadece aynı türde dosya yüklenebilir.'],
    [400, 'VALIDATION_FAILED', [{ field: 'version', message: 'size must be at most 40' }], 'Sürüm etiketi (isteğe bağlı)', 'Sürüm etiketi en fazla 40 karakter olabilir.'],
    [409, 'EMBEDDING_MODEL_MISMATCH', undefined, null, 'Ayarlardaki embedding modeli, bu çalışma alanının parçalarını oluşturan modelden farklı. Anlam ve Hybrid arama ile dosya yükleme bu yüzden yapılamıyor; Kelime yöntemiyle arayabilirsin.'],
  ] as const)(
    'givenBackendRejects_whenUploadVersion_thenShowsTurkishErrorInDialog (%s %s)',
    async (status, errorCode, violations, fieldLabel, message) => {
      // Arrange
      const user = newUser()
      server.use(
        http.post('/api/workspaces/:workspaceId/sources/:sourceId/versions', () =>
          problem(status, errorCode, violations === undefined ? undefined : [...violations]),
        ),
      )
      const { dialog } = await openPdfDialog(user)
      await user.upload(within(dialog).getByLabelText('Dosya'), pdfFile())

      // Act
      await user.click(within(dialog).getByRole('button', { name: 'Yeni sürümü yükle' }))

      // Assert
      const alert = await within(dialog).findByRole('alert')
      expect(alert).toHaveTextContent(message)
      if (fieldLabel !== null) {
        expect(within(dialog).getByLabelText(fieldLabel)).toHaveAttribute('aria-invalid', 'true')
      }
      expect(document.body.textContent).not.toContain('Embedding service is not reachable')
    },
  )

  it('givenDialogOpenedFromRowButton_whenEscapeOrCancel_thenClosesFocusReturnsToRowButtonAndPageStays', async () => {
    // Arrange
    const user = newUser()
    const { row, dialog } = await openPdfDialog(user)
    const rowButton = within(row).getAllByRole('button', { name: 'Yeni sürüm yükle' })[0]
    expect(within(dialog).getByLabelText('Dosya')).toHaveFocus()
    await user.click(within(dialog).getByText('Atlaskap-Lojistik-Surec-El-Kitabi · şu an v3.1 · 5 parça'))

    // Act
    await user.keyboard('{Escape}')
    const focusAfterEscape = document.activeElement
    await user.click(rowButton)
    await user.click(screen.getByRole('button', { name: 'Vazgeç' }))

    // Assert
    expect(focusAfterEscape).toBe(rowButton)
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(rowButton).toHaveFocus()
    expect(currentUrl()).toBe('/w/42/kaynaklar')
  })

  it('givenDialogFields_whenLookedUpByLabel_thenAllFormFieldsHaveLabels', async () => {
    // Arrange
    const user = newUser()

    // Act
    const { dialog } = await openPdfDialog(user)

    // Assert
    expect(within(dialog).getByLabelText('Dosya')).toHaveAttribute('type', 'file')
    expect(within(dialog).getByLabelText('Sürüm etiketi (isteğe bağlı)')).toHaveAttribute('maxlength', '40')
  })

  describe('with an active latest job', () => {
    const embedding = ingestionJob({
      jobId: 7,
      sourceId: 10002,
      stage: 'EMBEDDING',
      progress: 0.458879,
      embeddedChunks: 96,
      totalChunks: 214,
    })

    function useActiveJob() {
      const withJob = BELGELERIM_SOURCES.map((candidate) =>
        candidate.sourceId === 10002 ? { ...candidate, status: 'INGESTING' as const, latestJob: embedding } : candidate,
      )
      server.use(
        http.get('/api/workspaces/:workspaceId/sources', () => HttpResponse.json(pageOf(withJob))),
        http.get('/api/workspaces/:workspaceId/sources/10002', () => HttpResponse.json(withJob[1])),
        http.get('/api/workspaces/:workspaceId/ingestion-jobs/7', () => HttpResponse.json(embedding)),
      )
    }

    it('givenActiveLatestJob_whenRenderedInList_thenUploadVersionDisabledAndBadgeIngesting', async () => {
      // Arrange
      useActiveJob()

      // Act
      renderApp('/w/42/kaynaklar')

      // Assert
      const row = (await screen.findByRole('link', { name: 'Atlaskap-Lojistik-Surec-El-Kitabi' })).closest('tr') as HTMLElement
      const button = within(row).getByRole('button', { name: 'Yeni sürüm yükle' })
      expect(button).toBeDisabled()
      expect(button).toHaveAttribute('title', 'Yükleme sürüyor')
      expect(within(row).getByText('İşleniyor')).toBeInTheDocument()
      expect(within(row).getByText('Embed ediliyor · 96/214 parça')).toBeInTheDocument()
    })

    it('givenActiveLatestJob_whenRenderedInDetail_thenUploadVersionDisabledAndProgressShown', async () => {
      // Arrange
      useActiveJob()

      // Act
      renderApp('/w/42/kaynaklar/10002')

      // Assert
      const button = await screen.findByRole('button', { name: 'Yeni sürüm yükle' })
      expect(button).toBeDisabled()
      expect(screen.getByText('İşleniyor')).toBeInTheDocument()
      expect(
        screen.getByRole('progressbar', { name: 'Atlaskap-Lojistik-Surec-El-Kitabi yükleme ilerlemesi' }),
      ).toHaveAttribute('aria-valuenow', '46')
    })
  })
})
