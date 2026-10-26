import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { BELGELERIM_SOURCES, ingestionJob, pageOf, source } from '../../../test/fixtures.ts'
import { readMultipart } from '../../../test/multipart.ts'
import type { MultipartParts } from '../../../test/multipart.ts'
import { problem } from '../../../test/problem.ts'
import { currentUrl, renderApp } from '../../../test/renderWithProviders.tsx'
import { server } from '../../../test/server.ts'
import type { SourceResponse } from '../model/types.ts'

const MARKDOWN = '# Yardım\nŞifre sıfırlama adımları.\n'

function markdownFile(name = 'izin-notlari.md'): File {
  return new File([MARKDOWN], name, { type: 'text/markdown' })
}

function fileWithSize(name: string, size: number): File {
  const file = new File(['x'], name)
  Object.defineProperty(file, 'size', { value: size })
  return file
}

function newUser() {
  return userEvent.setup({ applyAccept: false })
}

function trackCreateRequests() {
  const received: MultipartParts[] = []
  server.use(
    http.post('/api/workspaces/:workspaceId/sources', async ({ request }) => {
      received.push(await readMultipart(request))
      return HttpResponse.json(
        ingestionJob({ jobId: 8, sourceId: 10001, version: null, fileName: 'izin-notlari.md' }),
        { status: 202 },
      )
    }),
  )
  return received
}

async function openDialog(user: ReturnType<typeof newUser>) {
  renderApp('/w/42/kaynaklar')
  await user.click(await screen.findByRole('button', { name: 'Yeni kaynak ekle' }))
  return screen.getByRole('dialog', { name: 'Yeni kaynak ekle' })
}

describe('CreateSourceDialog', () => {
  it('givenValidNameAndMarkdown_whenAddSource_thenPostsMultipartClosesDialogAndListsIngestingSource', async () => {
    // Arrange
    const user = newUser()
    const received = trackCreateRequests()
    let created: SourceResponse[] = []
    server.use(
      http.get('/api/workspaces/:workspaceId/sources', () => HttpResponse.json(pageOf([...BELGELERIM_SOURCES, ...created]))),
      http.get('/api/workspaces/:workspaceId/ingestion-jobs/:jobId', () =>
        HttpResponse.json(ingestionJob({ jobId: 8, sourceId: 10001, version: null, stage: 'QUEUED' })),
      ),
      http.post('/api/workspaces/:workspaceId/sources', async ({ request }) => {
        received.push(await readMultipart(request))
        created = [
          source({
            sourceId: 10001,
            name: 'Yardım notları',
            kind: 'MARKDOWN',
            status: 'INGESTING',
            syncedAt: null,
            latestJob: ingestionJob({ jobId: 8, sourceId: 10001, version: null, stage: 'QUEUED' }),
          }),
        ]
        return HttpResponse.json(ingestionJob({ jobId: 8, sourceId: 10001, version: null }), { status: 202 })
      }),
    )
    const dialog = await openDialog(user)
    expect(within(dialog).getByLabelText('Kaynak adı')).toHaveFocus()

    // Act
    await user.type(within(dialog).getByLabelText('Kaynak adı'), '  Yardım notları ')
    await user.upload(within(dialog).getByLabelText('Dosya'), markdownFile())
    await user.click(within(dialog).getByRole('button', { name: 'Kaynağı ekle' }))

    // Assert
    const row = (await screen.findByRole('link', { name: 'Yardım notları' })).closest('tr') as HTMLElement
    expect(received).toEqual([
      {
        fields: { name: 'Yardım notları' },
        files: [{ field: 'file', fileName: 'izin-notlari.md', content: MARKDOWN }],
      },
    ])
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(within(row).getByText('İşleniyor')).toBeInTheDocument()
    expect(within(row).getByText('Sıraya alındı')).toBeInTheDocument()
    expect(currentUrl()).toBe('/w/42/kaynaklar')
  })

  it('givenSelectedFile_whenShown_thenDialogListsFileNameAndTurkishSize', async () => {
    // Arrange
    const user = newUser()
    const dialog = await openDialog(user)

    // Act
    await user.upload(within(dialog).getByLabelText('Dosya'), markdownFile('Şifre notları.markdown'))

    // Assert
    expect(within(dialog).getByText('Şifre notları.markdown')).toBeInTheDocument()
    expect(within(dialog).getByText('41 B')).toBeInTheDocument()
  })

  it.each([
    ['kilavuz.docx', 10, 'Bu dosya türü desteklenmiyor. PDF, TXT, MD, MARKDOWN ya da CSV seç.'],
    ['kilavuz.pdf', 26_214_400, "Dosya 20 MB'tan büyük olamaz."],
    ['bos.txt', 0, 'Dosya boş. İçinde metin olan bir dosya seç.'],
  ])('givenInvalidFile_whenSelected_thenShowsWarningUnderFieldAndSendsNothing (%s)', async (name, size, message) => {
    // Arrange
    const user = newUser()
    const received = trackCreateRequests()
    const dialog = await openDialog(user)
    await user.type(within(dialog).getByLabelText('Kaynak adı'), 'Masraf notları')

    // Act
    await user.upload(within(dialog).getByLabelText('Dosya'), fileWithSize(name, size))
    const alertAfterSelect = within(dialog).getByRole('alert')
    await user.click(within(dialog).getByRole('button', { name: 'Kaynağı ekle' }))

    // Assert
    expect(alertAfterSelect).toHaveTextContent(message)
    expect(within(dialog).getByLabelText('Dosya')).toHaveAttribute('aria-invalid', 'true')
    expect(within(dialog).getByRole('alert')).toHaveTextContent(message)
    expect(received).toHaveLength(0)
    expect(screen.getByRole('dialog', { name: 'Yeni kaynak ekle' })).toBeInTheDocument()
  })

  it('givenWhitespaceName_whenAddSource_thenSendsFileNameWithoutExtensionAndShowsItInNameField', async () => {
    // Arrange
    const user = newUser()
    const received = trackCreateRequests()
    const dialog = await openDialog(user)
    await user.type(within(dialog).getByLabelText('Kaynak adı'), '   ')
    await user.upload(within(dialog).getByLabelText('Dosya'), markdownFile())

    // Act
    await user.click(within(dialog).getByRole('button', { name: 'Kaynağı ekle' }))

    // Assert
    await waitFor(() => expect(received).toHaveLength(1))
    expect(received[0].fields).toEqual({ name: 'izin-notlari' })
  })

  it('givenOneCharacterName_whenAddSource_thenShowsNameRuleFocusesNameAndSendsNothing', async () => {
    // Arrange
    const user = newUser()
    const received = trackCreateRequests()
    const dialog = await openDialog(user)
    await user.type(within(dialog).getByLabelText('Kaynak adı'), 'a')
    await user.upload(within(dialog).getByLabelText('Dosya'), markdownFile())

    // Act
    await user.click(within(dialog).getByRole('button', { name: 'Kaynağı ekle' }))

    // Assert
    expect(within(dialog).getByRole('alert')).toHaveTextContent('Kaynak adı 2-80 karakter olmalı.')
    expect(within(dialog).getByLabelText('Kaynak adı')).toHaveAttribute('aria-invalid', 'true')
    expect(within(dialog).getByLabelText('Kaynak adı')).toHaveFocus()
    expect(received).toHaveLength(0)
  })

  it('givenNothingFilled_whenAddSource_thenAsksOnlyForFileBecauseNameIsOptional', async () => {
    // Arrange
    const user = newUser()
    const received = trackCreateRequests()
    const dialog = await openDialog(user)

    // Act
    await user.click(within(dialog).getByRole('button', { name: 'Kaynağı ekle' }))

    // Assert
    const alerts = within(dialog).getAllByRole('alert').map((alert) => alert.textContent)
    expect(alerts).toEqual(['Yüklenecek dosyayı seç.'])
    expect(received).toHaveLength(0)
  })

  it.each([
    [409, 'SOURCE_NAME_TAKEN', undefined, 'Kaynak adı', 'Bu ad çalışma alanında başka bir kaynakta kullanılıyor.'],
    [
      415,
      'UNSUPPORTED_FILE_ENCODING',
      undefined,
      'Dosya',
      'Metin dosyası UTF-8 olmalı. Dosyayı UTF-8 olarak kaydedip tekrar yükle.',
    ],
    [413, 'FILE_TOO_LARGE', undefined, 'Dosya', "Dosya 20 MB'tan büyük olamaz."],
    [
      400,
      'VALIDATION_FAILED',
      [{ field: 'name', message: 'size must be between 2 and 80' }],
      'Kaynak adı',
      'Kaynak adı 2-80 karakter olmalı.',
    ],
    [409, 'SOURCE_LIMIT_REACHED', undefined, null, 'Bu çalışma alanı 10 kaynak sınırına ulaştı.'],
  ] as const)(
    'givenBackendRejects_whenAddSource_thenShowsTurkishErrorInDialog (%s %s)',
    async (status, errorCode, violations, fieldLabel, message) => {
      // Arrange
      const user = newUser()
      server.use(
        http.post('/api/workspaces/:workspaceId/sources', () =>
          problem(status, errorCode, violations === undefined ? undefined : [...violations]),
        ),
      )
      const dialog = await openDialog(user)
      await user.type(within(dialog).getByLabelText('Kaynak adı'), 'Yardım notları')
      await user.upload(within(dialog).getByLabelText('Dosya'), markdownFile())

      // Act
      await user.click(within(dialog).getByRole('button', { name: 'Kaynağı ekle' }))

      // Assert
      const alert = await within(dialog).findByRole('alert')
      expect(alert).toHaveTextContent(message)
      if (fieldLabel !== null) {
        const field = within(dialog).getByLabelText(fieldLabel)
        expect(field).toHaveAttribute('aria-invalid', 'true')
        expect(field.getAttribute('aria-describedby')?.split(' ')).toContain(alert.id)
      }
      expect(screen.getByRole('dialog', { name: 'Yeni kaynak ekle' })).toBeInTheDocument()
      expect(within(dialog).getByText('izin-notlari.md')).toBeInTheDocument()
      expect(within(dialog).getByLabelText('Kaynak adı')).toHaveValue('Yardım notları')
      expect(document.body.textContent).not.toContain('Embedding service is not reachable')
    },
  )

  it('givenNetworkFailure_whenAddSource_thenShowsConnectionMessage', async () => {
    // Arrange
    const user = newUser()
    server.use(http.post('/api/workspaces/:workspaceId/sources', () => HttpResponse.error()))
    const dialog = await openDialog(user)
    await user.type(within(dialog).getByLabelText('Kaynak adı'), 'Yardım notları')
    await user.upload(within(dialog).getByLabelText('Dosya'), markdownFile())

    // Act
    await user.click(within(dialog).getByRole('button', { name: 'Kaynağı ekle' }))

    // Assert
    expect(await within(dialog).findByRole('alert')).toHaveTextContent(
      'Sunucuya ulaşılamıyor. Bağlantını ve sistemin çalıştığını kontrol et.',
    )
  })

  it('givenUploadInFlight_whenEscapeOrCancelPressed_thenDialogStaysOpenAndButtonsAreDisabled', async () => {
    // Arrange
    const user = newUser()
    let release: () => void = () => {}
    const gate = new Promise<void>((resolve) => {
      release = resolve
    })
    server.use(
      http.post('/api/workspaces/:workspaceId/sources', async () => {
        await gate
        return HttpResponse.json(ingestionJob({ jobId: 8, sourceId: 10001, version: null }), { status: 202 })
      }),
    )
    const dialog = await openDialog(user)
    await user.type(within(dialog).getByLabelText('Kaynak adı'), 'Yardım notları')
    await user.upload(within(dialog).getByLabelText('Dosya'), markdownFile())

    // Act
    await user.click(within(dialog).getByRole('button', { name: 'Kaynağı ekle' }))
    const pendingSubmit = await within(dialog).findByRole('button', { name: 'Yükleniyor…' })
    await user.keyboard('{Escape}')

    // Assert
    expect(pendingSubmit).toBeDisabled()
    expect(within(dialog).getByRole('button', { name: 'Vazgeç' })).toBeDisabled()
    expect(screen.getByRole('dialog', { name: 'Yeni kaynak ekle' })).toBeInTheDocument()
    release()
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })

  it('givenDialogOpenedByButton_whenEscapeOrCancel_thenClosesAndFocusReturnsToButton', async () => {
    // Arrange
    const user = newUser()
    renderApp('/w/42/kaynaklar')
    const opener = await screen.findByRole('button', { name: 'Yeni kaynak ekle' })

    // Act
    await user.click(opener)
    const focusedOnOpen = screen.getByLabelText('Kaynak adı')
    const wasFocused = document.activeElement === focusedOnOpen
    await user.keyboard('{Escape}')
    const closedByEscape = screen.queryByRole('dialog') === null
    const focusAfterEscape = document.activeElement
    await user.click(opener)
    await user.click(screen.getByRole('button', { name: 'Vazgeç' }))

    // Assert
    expect(wasFocused).toBe(true)
    expect(closedByEscape).toBe(true)
    expect(focusAfterEscape).toBe(opener)
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(opener).toHaveFocus()
  })

  it('givenFocusOnLastOrFirstElement_whenTab_thenFocusWrapsInsideDialog', async () => {
    // Arrange
    const user = newUser()
    const dialog = await openDialog(user)
    const submit = within(dialog).getByRole('button', { name: 'Kaynağı ekle' })
    submit.focus()

    // Act
    await user.tab()
    const afterTab = document.activeElement
    await user.tab({ shift: true })
    const afterShiftTab = document.activeElement

    // Assert
    expect(afterTab).toBe(within(dialog).getByLabelText('Kaynak adı'))
    expect(afterShiftTab).toBe(submit)
  })

  it('givenReopenedDialog_whenOpened_thenFormIsReset', async () => {
    // Arrange
    const user = newUser()
    const dialog = await openDialog(user)
    await user.type(within(dialog).getByLabelText('Kaynak adı'), 'Yarım kalan')
    await user.upload(within(dialog).getByLabelText('Dosya'), markdownFile())
    await user.click(within(dialog).getByRole('button', { name: 'Vazgeç' }))

    // Act
    await user.click(screen.getByRole('button', { name: 'Yeni kaynak ekle' }))

    // Assert
    const reopened = screen.getByRole('dialog', { name: 'Yeni kaynak ekle' })
    expect(within(reopened).getByLabelText('Kaynak adı')).toHaveValue('')
    expect(within(reopened).queryByText('izin-notlari.md')).not.toBeInTheDocument()
  })

  it('givenLongName_whenTyping_thenInputLimitsNameToEightyCharacters', async () => {
    // Arrange
    const user = newUser()
    const dialog = await openDialog(user)

    // Act
    await user.type(within(dialog).getByLabelText('Kaynak adı'), 'x'.repeat(90))

    // Assert
    expect(within(dialog).getByLabelText('Kaynak adı')).toHaveValue('x'.repeat(80))
  })
})
