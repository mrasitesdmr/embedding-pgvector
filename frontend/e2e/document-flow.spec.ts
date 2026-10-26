import { expect, test } from '@playwright/test'
import type { Locator, Page } from '@playwright/test'
import { createHash } from 'node:crypto'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { expectMatchesSchema } from './support/contract.ts'
import { execute, queryRows, queryValue } from './support/database.ts'

const HANDBOOK_NAME = 'Atlaskap-Lojistik-Surec-El-Kitabi'
const SAMPLE_PDF = fileURLToPath(new URL('../../ornek-dokuman/Atlaskap-Lojistik-Surec-El-Kitabi.pdf', import.meta.url))
const INGESTION_TIMEOUT_MS = 180_000
const SITE_PAGES = ['/w/42/belge-yukle', '/w/42/arama', '/w/42/kaynaklar']

const LEAVE_QUESTION = 'Yıllık izin kaç gün?'
const EXPENSE_QUESTION = 'Masraf beyanı kaç gün içinde yapılır?'
const PASSWORD_QUESTION = 'parolamı unuttum hesabım kilitlendi'

function hits(page: Page): Locator {
  return page.getByRole('list', { name: 'Arama sonuçları' }).getByRole('listitem')
}

async function ask(page: Page, question: string) {
  const input = page.getByLabel('Arama metni')
  await input.fill(question)
  await input.press('Enter')
  await expect(page.getByRole('heading', { name: `“${question.trim()}”` })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Ara', exact: true })).toBeEnabled()
  await expect(hits(page).first()).toBeVisible()
}

type SearchHitBody = {
  chunkId: number
  content: string
  matchedTerms: { field: string; start: number; end: number }[]
}

function hitButton(page: Page, index: number): Locator {
  return hits(page).nth(index).getByRole('button')
}

function md5(text: string): string {
  return createHash('md5').update(text, 'utf-8').digest('hex')
}

async function openChunkId(dialog: Locator): Promise<number> {
  const sourceLine = (await dialog.locator('small').innerText()).trim()
  const match = /#(\d+)$/.exec(sourceLine)
  expect(match, `source line "${sourceLine}"`).not.toBeNull()
  return Number(match![1])
}

async function hitIndexWithChunk(page: Page, chunkId: number): Promise<number> {
  const lines = await hits(page).evaluateAll((items) => items.map((item) => item.textContent ?? ''))
  const index = lines.findIndex((text) => text.endsWith(`#${chunkId}`))
  expect(index, `hit with chunk #${chunkId}`).toBeGreaterThanOrEqual(0)
  return index
}

async function popularTexts(page: Page): Promise<string[]> {
  return page.getByRole('list', { name: 'Sık aranan:' }).getByRole('button').allTextContents()
}

async function expectPopular(page: Page, expected: string[]) {
  await expect.poll(() => popularTexts(page)).toEqual(expected)
}

async function expectTopThreeContain(page: Page, text: string) {
  const topThree = await hits(page).evaluateAll((items) => items.slice(0, 3).map((item) => item.textContent ?? ''))
  expect(topThree.some((hitText) => hitText.includes(text))).toBe(true)
}

const WORKSPACE_API = '/api/workspaces/42'
const OLD_SAMPLE_QUESTION_CHUNK_SOURCE = 10001

type JobBody = { jobId: number; sourceId: number; stage: string; statusUrl: string; errorCode: string | null }

let longSourceId = 0

function largeMarkdown(sections: number, marker: string): Buffer {
  const filler = 'Zeytinyağı üretiminde hasat zamanı, sıkım yöntemi ve depolama koşulları kaliteyi belirler. '
  const parts: string[] = []
  for (let index = 0; index < sections; index += 1) {
    parts.push(`## Bölüm ${index} ${marker}\n${filler.repeat(17)}zeytinyağı ${marker} ${index}\n`)
  }
  return Buffer.from(parts.join('\n'), 'utf-8')
}

async function postSource(page: Page, name: string, fileName: string, content: Buffer, mimeType = 'text/markdown') {
  return page.request.post(`${WORKSPACE_API}/sources`, {
    multipart: { name, file: { name: fileName, mimeType, buffer: content } },
  })
}

async function postVersion(page: Page, sourceId: number, content: Buffer, version: string) {
  return page.request.post(`${WORKSPACE_API}/sources/${sourceId}/versions`, {
    multipart: { version, file: { name: 'surum.md', mimeType: 'text/markdown', buffer: content } },
  })
}

async function readJob(page: Page, statusUrl: string): Promise<JobBody> {
  const response = await page.request.get(statusUrl)
  const body = (await response.json()) as JobBody
  expectMatchesSchema(body, 'IngestionJobResponse')
  return body
}

async function acceptedJob(page: Page, response: Awaited<ReturnType<typeof postSource>>): Promise<JobBody> {
  expect(response.status()).toBe(202)
  const body = (await response.json()) as JobBody
  expectMatchesSchema(body, 'IngestionJobResponse')
  expect(response.headers()['location']).toContain(body.statusUrl)
  return body
}

async function waitForJob(page: Page, statusUrl: string, stage: string) {
  await expect.poll(async () => (await readJob(page, statusUrl)).stage, { timeout: INGESTION_TIMEOUT_MS }).toBe(stage)
}

function dbCounts(): string {
  return queryRows(
    `select (select count(*) from sources), (select count(*) from ingestion_jobs), (select count(*) from chunks), (select count(*) from search_events)`,
  )[0].join(',')
}

const KEPT_SOURCE_IDS = `${OLD_SAMPLE_QUESTION_CHUNK_SOURCE}, 10002`

function removeLargeSourcesLeftByEarlierTests() {
  execute(`delete from chunks where source_id not in (${KEPT_SOURCE_IDS})`)
  execute(`delete from ingestion_jobs where source_id not in (${KEPT_SOURCE_IDS})`)
  execute(`delete from sources where id not in (${KEPT_SOURCE_IDS})`)
}

async function openAdvancedAndChoose(page: Page, modeLabel: string) {
  await page.getByText('Gelişmiş ayarlar').click()
  await page.getByRole('radio', { name: modeLabel }).check()
}

test.describe.configure({ mode: 'serial' })
test.skip(process.env.E2E_OLLAMA_DOWN === '1', 'Ollama kapalı koşusunda ollama-down.spec.ts çalışır')

test.describe('Document flow', () => {
  test('givenCleanWorkspace_whenOpenSite_thenUploadPageWithoutSelectorOrDemoText', async ({ page }) => {
    // Act
    await page.goto('/')

    // Assert
    await expect(page).toHaveURL(/\/w\/42\/belge-yukle$/)
    await expect(page.getByRole('heading', { level: 1, name: 'Belge yükle' })).toBeVisible()
    await expect(page.getByRole('combobox', { name: 'Çalışma alanı' })).toHaveCount(0)
    const texts: string[] = [await page.locator('body').innerText()]

    await page.goto('/w/42/arama')
    await expect(page.getByText('Henüz belge yüklenmedi')).toBeVisible()
    await expect(page.getByRole('link', { name: 'Belge yükle' }).last()).toBeVisible()
    await expect(page.getByText('Sık aranan')).toHaveCount(0)
    texts.push(await page.locator('body').innerText())

    await page.goto('/w/42/kaynaklar')
    await expect(page.getByText('Bu çalışma alanında henüz kaynak yok')).toBeVisible()
    texts.push(await page.locator('body').innerText())
    const legacyDemoTerms = ['UG95cmF6', 'RGFsZ2E='].map((encoded) => atob(encoded))
    legacyDemoTerms.push('Şifremi unuttum')
    for (const text of texts) {
      for (const term of legacyDemoTerms) {
        expect(text).not.toContain(term)
      }
    }
  })

  test('givenSamplePdf_whenUploadWithBlankNameAndReload_thenProgressContinuesAndReady', async ({ page }) => {
    // Arrange
    let holdActive = false
    let activeJob: Record<string, unknown> | null = null
    const asIngesting = (row: Record<string, unknown>) =>
      holdActive && activeJob !== null && row.sourceId === activeJob.sourceId
        ? { ...row, status: 'INGESTING', latestJob: activeJob }
        : row
    await page.route(/\/api\/workspaces\/42\/sources(\?.*)?$/, async (route) => {
      if (route.request().method() !== 'GET') {
        await route.fallback()
        return
      }
      const response = await route.fetch()
      const body = (await response.json()) as { content: Record<string, unknown>[] }
      await route.fulfill({ response, json: { ...body, content: body.content.map(asIngesting) } })
    })
    await page.route(/\/api\/workspaces\/42\/sources\/\d+(\?.*)?$/, async (route) => {
      if (route.request().method() !== 'GET') {
        await route.fallback()
        return
      }
      const response = await route.fetch()
      await route.fulfill({ response, json: asIngesting((await response.json()) as Record<string, unknown>) })
    })
    await page.route(/\/api\/workspaces\/42\/ingestion-jobs\/\d+(\?.*)?$/, async (route) => {
      if (holdActive && activeJob !== null) {
        await route.fulfill({ json: activeJob })
        return
      }
      await route.fallback()
    })
    await page.goto('/w/42/belge-yukle')
    await page.getByLabel('Dosya').setInputFiles(SAMPLE_PDF)

    // Act
    const uploadResponse = page.waitForResponse(
      (response) => response.request().method() === 'POST' && /\/api\/workspaces\/42\/sources$/.test(response.url()),
    )
    holdActive = true
    await page.getByRole('button', { name: 'Belgeyi yükle' }).click()
    const accepted = await uploadResponse
    activeJob = (await accepted.json()) as Record<string, unknown>

    // Assert
    expect(accepted.status()).toBe(202)
    await expect(page).toHaveURL(/\/w\/42\/belge-yukle\?kaynak=10001$/)
    await expect(page.getByRole('progressbar')).toBeVisible()
    await page.reload()
    await expect(page.getByRole('progressbar')).toBeVisible()
    await expect(page.getByRole('heading', { name: HANDBOOK_NAME })).toBeVisible()
    holdActive = false
    await expect(page.getByRole('heading', { name: 'Belge hazır' })).toBeVisible({ timeout: INGESTION_TIMEOUT_MS })
    await expect(
      page.getByRole('region', { name: 'Belge yükleme' }).getByText(new RegExp(`^${HANDBOOK_NAME} hazır · \\d+ parça$`)),
    ).toBeVisible()
    await page.getByRole('link', { name: 'Soru sor' }).click()
    await expect(page).toHaveURL(/\/w\/42\/arama$/)
  })

  test('givenReadySample_whenAskThreeQuestions_thenRelevantSectionInTopThree', async ({ page }) => {
    // Arrange
    await page.goto('/w/42/arama')
    await expect(page.getByText('Belgene bir soru sorarak başla')).toBeVisible()
    await expect(page.getByText('Sık aranan')).toHaveCount(0)

    // Act
    await ask(page, LEAVE_QUESTION)
    await expectTopThreeContain(page, 'Yıllık izin')
    await expectPopular(page, [LEAVE_QUESTION])
    await ask(page, EXPENSE_QUESTION)
    await expectTopThreeContain(page, 'Masraf')
    await ask(page, PASSWORD_QUESTION)

    // Assert
    await expectTopThreeContain(page, 'Bilgi teknolojileri destek')
  })

  test('givenPreviousSearches_whenRepeatVariantAndUnrelatedQuery_thenPopularOrderUpdates', async ({ page }) => {
    // Arrange
    await page.goto('/w/42/arama')
    await expectPopular(page, [PASSWORD_QUESTION, EXPENSE_QUESTION, LEAVE_QUESTION])
    await ask(page, '  PAROLAMI unuttum hesabım kilitlendi ')
    await expectPopular(page, ['PAROLAMI unuttum hesabım kilitlendi', EXPENSE_QUESTION, LEAVE_QUESTION])

    // Act
    await ask(page, 'kuantum fiziği')
    await ask(page, 'futbol maçı kaç kaç bitti')

    // Assert
    await expectPopular(page, ['PAROLAMI unuttum hesabım kilitlendi', EXPENSE_QUESTION, LEAVE_QUESTION])
    await page.getByRole('list', { name: 'Sık aranan:' }).getByRole('button', { name: EXPENSE_QUESTION }).click()
    await expect(page.getByLabel('Arama metni')).toHaveValue(EXPENSE_QUESTION)
    await expect(hits(page).first()).toBeVisible()
    await expectPopular(page, [EXPENSE_QUESTION, 'PAROLAMI unuttum hesabım kilitlendi', LEAVE_QUESTION])
  })

  test('givenReadySource_whenOpenRootAndUploadSecondFromMenu_thenSearchFirstAndTwoSources', async ({ page }) => {
    // Arrange
    await page.goto('/')
    await expect(page).toHaveURL(/\/w\/42\/arama$/)

    // Act
    await page.getByRole('navigation', { name: 'Bölümler' }).getByRole('link', { name: 'Belge yükle' }).click()
    await page.getByLabel('Kaynak adı').fill('Deneme notu')
    await page.getByLabel('Dosya').setInputFiles({
      name: 'deneme-notu.md',
      mimeType: 'text/markdown',
      buffer: Buffer.from('# Deneme notu\nKargo teslim süresi 3 iş günüdür.\n', 'utf-8'),
    })
    await page.getByRole('button', { name: 'Belgeyi yükle' }).click()

    // Assert
    await expect(page.getByRole('heading', { name: 'Belge hazır' })).toBeVisible({ timeout: INGESTION_TIMEOUT_MS })
    await page.getByRole('navigation', { name: 'Bölümler' }).getByRole('link', { name: 'Bilgi kaynakları' }).click()
    await expect(page.getByRole('row')).toHaveCount(3)
    await expect(page.getByRole('link', { name: 'Deneme notu', exact: true })).toBeVisible()
    await expect(page.getByRole('link', { name: HANDBOOK_NAME, exact: true })).toBeVisible()
  })

  test('givenUnsupportedAndEmptyFiles_whenSelectedOnUploadPage_thenWarnsAndSendsNoUpload', async ({ page }) => {
    // Arrange
    const uploads: string[] = []
    page.on('request', (request) => {
      if (request.method() === 'POST' && /\/api\/workspaces\/\d+\/sources$/.test(request.url())) {
        uploads.push(request.url())
      }
    })
    await page.goto('/w/42/belge-yukle')

    // Act
    await page.getByLabel('Dosya').setInputFiles({
      name: 'rapor.docx',
      mimeType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
      buffer: Buffer.from('docx'),
    })
    const typeWarning = (await page.getByRole('alert').textContent()) ?? ''
    await page.getByRole('button', { name: 'Belgeyi yükle' }).click()
    await page.getByLabel('Dosya').setInputFiles({ name: 'bos.md', mimeType: 'text/markdown', buffer: Buffer.alloc(0) })
    const emptyWarning = (await page.getByRole('alert').textContent()) ?? ''
    await page.getByRole('button', { name: 'Belgeyi yükle' }).click()

    // Assert
    expect(typeWarning).toBe('Bu dosya türü desteklenmiyor. PDF, TXT, MD, MARKDOWN ya da CSV seç.')
    expect(emptyWarning).toBe('Dosya boş. İçinde metin olan bir dosya seç.')
    expect(uploads).toEqual([])
  })

  test('givenKeyboardOnly_whenOpenUploadVersionDialogAndEscape_thenFocusReturnsToRowButton', async ({ page }) => {
    // Arrange
    await page.goto('/w/42/kaynaklar')
    const row = page.getByRole('row').filter({ has: page.getByRole('link', { name: HANDBOOK_NAME, exact: true }) })
    const rowButton = row.getByRole('button', { name: 'Yeni sürüm yükle' })
    await expect(rowButton).toBeEnabled()
    await rowButton.focus()

    // Act
    await page.keyboard.press('Enter')
    const dialog = page.getByRole('dialog', { name: 'Yeni sürüm yükle' })
    await expect(dialog.getByLabel('Dosya')).toBeFocused()
    await page.keyboard.press('Escape')

    // Assert
    await expect(dialog).toBeHidden()
    await expect(rowButton).toBeFocused()
    await expect(page).toHaveURL(/\/w\/42\/kaynaklar$/)
  })

  test('givenSite_whenOpenUploadSearchSourcesAndDetail_thenNoCspViolationInConsole', async ({ page }) => {
    // Arrange
    const problems: string[] = []
    page.on('console', (message) => {
      if (message.type() === 'error' || message.type() === 'warning') {
        problems.push(message.text())
      }
    })
    page.on('pageerror', (error) => problems.push(error.message))

    // Act
    for (const path of [...SITE_PAGES, '/w/42/kaynaklar/10001']) {
      await page.goto(path)
      await page.waitForLoadState('networkidle')
    }

    // Assert
    expect(problems.filter((text) => /Content Security Policy|violates/i.test(text))).toEqual([])
    expect(problems).toEqual([])
  })

  test('givenReadySamplePdf_whenInspectApiAndDatabase_thenChunksHave1024DimEmbeddingsWithModelTag', async ({ page }) => {
    // Arrange
    const sourceId = OLD_SAMPLE_QUESTION_CHUNK_SOURCE

    // Act
    const workspace = await (await page.request.get(WORKSPACE_API)).json()
    const sourceResponse = await page.request.get(`${WORKSPACE_API}/sources/${sourceId}`)
    const source = await sourceResponse.json()
    const chunkPage = await (await page.request.get(`${WORKSPACE_API}/sources/${sourceId}/chunks?size=100`)).json()
    const plan = await (
      await page.request.post(`${WORKSPACE_API}/search`, {
        data: { query: 'izin süresi', mode: 'VECTOR', explain: true },
      })
    ).json()
    const [count, minDims, maxDims, models, maxChunkNo] = queryRows(
      `select count(*), min(vector_dims(embedding)), max(vector_dims(embedding)), count(distinct embedding_model), max(chunk_no) from chunks where source_id = ${sourceId}`,
    )[0]

    // Assert
    expectMatchesSchema(workspace, 'WorkspaceResponse')
    expectMatchesSchema(source, 'SourceResponse')
    expectMatchesSchema(chunkPage, 'PageResponseChunkResponse')
    expectMatchesSchema(plan, 'SearchResultResponse')
    const chunkTotal = Number(count)
    expect(chunkTotal).toBeGreaterThanOrEqual(22)
    expect([minDims, maxDims, models, Number(maxChunkNo)]).toEqual(['1024', '1024', '1', chunkTotal - 1])
    expect(source.status).toBe('READY')
    expect(source.latestJob.stage).toBe('DONE')
    expect(source.latestJob.progress).toBe(1)
    expect([source.latestJob.embeddedChunks, source.latestJob.totalChunks, source.chunkCount]).toEqual([
      chunkTotal,
      chunkTotal,
      chunkTotal,
    ])
    expect(chunkPage.totalElements).toBe(chunkTotal)
    expect(chunkPage.content.every((chunk: { embeddingModel: string }) => chunk.embeddingModel === 'bge-m3@1024')).toBe(true)
    expect(queryValue(`select count(*) from chunks where tsv is null`)).toBe('0')
    expect(workspace.chunkCount).toBe(Number(queryValue(`select count(*) from chunks where workspace_id = 42`)))
    expect(workspace.sourceCount).toBe(Number(queryValue(`select count(*) from sources where workspace_id = 42`)))
    expect(workspace.embeddingModels).toEqual(['bge-m3@1024'])
    expect(plan.vectorPlan).toContain('idx_chunks_embedding')
    expect(queryValue(`select count(*) from pg_indexes where indexname = 'idx_chunks_tsv'`)).toBe('1')
    await page.goto(`/w/42/kaynaklar/${sourceId}`)
    await expect(page.getByRole('heading', { name: HANDBOOK_NAME })).toBeVisible()
  })

  test('givenLargeMarkdown_whenReloadDuringEmbedding_thenProgressContinuesAndJobDone', async ({ page }) => {
    // Arrange
    await page.goto('/w/42/belge-yukle')
    await page.getByLabel('Kaynak adı').fill('Uzun belge')
    await page.getByLabel('Dosya').setInputFiles({
      name: 'uzun-belge.md',
      mimeType: 'text/markdown',
      buffer: largeMarkdown(500, 'ilk'),
    })

    // Act
    await page.getByRole('button', { name: 'Belgeyi yükle' }).click()
    await expect(page).toHaveURL(/\/w\/42\/belge-yukle\?kaynak=\d+$/)
    longSourceId = Number(new URL(page.url()).searchParams.get('kaynak'))
    const bar = page.getByRole('progressbar')
    await expect(bar).toHaveAttribute('aria-valuetext', /^Embed ediliyor/, { timeout: INGESTION_TIMEOUT_MS })
    const embeddedRows = `select embedded_chunks from ingestion_jobs where source_id = ${longSourceId} order by id desc limit 1`
    await expect.poll(() => Number(queryValue(embeddedRows)), { timeout: INGESTION_TIMEOUT_MS }).toBeGreaterThan(0)
    const before = Number((await bar.getAttribute('aria-valuenow')) ?? '0')
    const embeddedBefore = Number(
      queryValue(`select embedded_chunks from ingestion_jobs where source_id = ${longSourceId} order by id desc limit 1`),
    )
    await page.reload()

    // Assert
    await expect(bar).toBeVisible()
    expect(Number((await bar.getAttribute('aria-valuenow')) ?? '0')).toBeGreaterThanOrEqual(before)
    await expect
      .poll(() => Number(queryValue(`select embedded_chunks from ingestion_jobs where source_id = ${longSourceId} order by id desc limit 1`)), {
        timeout: INGESTION_TIMEOUT_MS,
      })
      .toBeGreaterThan(embeddedBefore)
    await expect(page.getByRole('heading', { name: 'Belge hazır' })).toBeVisible({ timeout: INGESTION_TIMEOUT_MS })
    const [embedded, total, stage] = queryRows(
      `select embedded_chunks, total_chunks, stage from ingestion_jobs where source_id = ${longSourceId} order by id desc limit 1`,
    )[0]
    const chunkCount = queryValue(`select chunk_count from sources where id = ${longSourceId}`)
    expect(stage).toBe('DONE')
    expect([embedded, total, chunkCount]).toEqual([total, total, queryValue(`select count(*) from chunks where source_id = ${longSourceId}`)])
    const source = await (await page.request.get(`${WORKSPACE_API}/sources/${longSourceId}`)).json()
    expectMatchesSchema(source, 'SourceResponse')
    expect(source.latestJob.stage).toBe('DONE')
  })

  test('givenVersionUploadRunning_whenSearch_thenOldChunksUntilDoneThenOnlyNew', async ({ page }) => {
    // Arrange
    const oldIds = queryRows(`select id from chunks where source_id = ${longSourceId}`).map((row) => Number(row[0]))
    const sourceHits = async () => {
      const response = await page.request.post(`${WORKSPACE_API}/search`, {
        data: { query: 'zeytinyağı', mode: 'HYBRID', limit: 50 },
      })
      const body = await response.json()
      expectMatchesSchema(body, 'SearchResultResponse')
      return (body.hits as { sourceId: number; chunkId: number }[])
        .filter((hit) => hit.sourceId === longSourceId)
        .map((hit) => hit.chunkId)
    }
    const job = await acceptedJob(page, await postVersion(page, longSourceId, largeMarkdown(500, 'yeni'), '2'))
    await expect
      .poll(async () => (await readJob(page, job.statusUrl)).stage, { timeout: INGESTION_TIMEOUT_MS })
      .toBe('EMBEDDING')

    // Act
    const second = await postVersion(page, longSourceId, largeMarkdown(2, 'ikinci'), '3')
    const duringHits = await sourceHits()
    const oldCountDuring = Number(queryValue(`select count(*) from chunks where id = any(array[${oldIds.join(',')}])`))
    await waitForJob(page, job.statusUrl, 'DONE')
    const afterHits = await sourceHits()

    // Assert
    expect(second.status()).toBe(409)
    const conflict = await second.json()
    expectMatchesSchema(conflict, 'ProblemDetail')
    expect(conflict.errorCode).toBe('INGESTION_IN_PROGRESS')
    expect(duringHits.length).toBeGreaterThan(0)
    expect(duringHits.every((id) => oldIds.includes(id))).toBe(true)
    expect(oldCountDuring).toBe(oldIds.length)
    expect(afterHits.length).toBeGreaterThan(0)
    expect(afterHits.some((id) => oldIds.includes(id))).toBe(false)
    expect(queryValue(`select count(*) from chunks where id = any(array[${oldIds.join(',')}])`)).toBe('0')
    expect(queryValue(`select version from sources where id = ${longSourceId}`)).toBe('2')
    expect(queryValue(`select count(*) from ingestion_jobs where source_id = ${longSourceId}`)).toBe('2')
    await page.goto(`/w/42/kaynaklar/${longSourceId}`)
    await expect(page.getByRole('heading', { name: 'Uzun belge' })).toBeVisible()
  })

  test('givenThreeFilesPostedAtOnce_whenAllFinished_thenThreeReadySourcesWithOwnChunks', async ({ page }) => {
    // Arrange
    const files = [
      { name: 'Eşzamanlı 1', fileName: 'bir.md', mime: 'text/markdown', content: '# Bir\nbirinci belge metni\n' },
      { name: 'Eşzamanlı 2', fileName: 'iki.txt', mime: 'text/plain', content: '# İki\nikinci belge metni\n# Üç\nüçüncü bölüm\n' },
      { name: 'Eşzamanlı 3', fileName: 'uc.csv', mime: 'text/csv', content: 'kod,açıklama\nA1,ilk satır\nA2,ikinci satır\nA3,üçüncü satır\n' },
    ]

    // Act
    const responses = await Promise.all(
      files.map((file) => postSource(page, file.name, file.fileName, Buffer.from(file.content, 'utf-8'), file.mime)),
    )
    const jobs = await Promise.all(responses.map((response) => acceptedJob(page, response)))
    for (const job of jobs) {
      await waitForJob(page, job.statusUrl, 'DONE')
    }

    // Assert
    expect(new Set(jobs.map((job) => job.sourceId)).size).toBe(3)
    for (const job of jobs) {
      const [chunkCount, ownRows, dims] = queryRows(
        `select s.chunk_count, (select count(*) from chunks c where c.source_id = s.id), (select count(distinct vector_dims(embedding)) || ':' || min(vector_dims(embedding)) from chunks c where c.source_id = s.id) from sources s where s.id = ${job.sourceId}`,
      )[0]
      expect(chunkCount).toBe(ownRows)
      expect(Number(chunkCount)).toBeGreaterThan(0)
      expect(dims).toBe('1:1024')
    }
    await page.goto('/w/42/kaynaklar')
    for (const file of files) {
      await expect(page.getByRole('link', { name: file.name, exact: true })).toBeVisible()
    }
  })

  test('givenVectorSearch_whenOpenSearchEventsPage_thenEventStatsAndPopularMatchDatabase', async ({ page }) => {
    // Arrange
    removeLargeSourcesLeftByEarlierTests()
    await page.goto('/w/42/arama')
    await openAdvancedAndChoose(page, 'Anlam')

    // Act
    await ask(page, LEAVE_QUESTION)
    const [mode, query, queryKey, resultCount, bestDistance, keywordCount] = queryRows(
      `select mode, query, query_key, result_count, best_distance, coalesce(keyword_count::text, 'NULL') from search_events order by id desc limit 1`,
    )[0]
    const popular = await (await page.request.get(`${WORKSPACE_API}/search-events/popular-queries`)).json()
    const events = await (await page.request.get(`${WORKSPACE_API}/search-events?size=5`)).json()
    const stats = await (await page.request.get(`${WORKSPACE_API}/search-events/stats`)).json()

    // Assert
    await expectTopThreeContain(page, 'Yıllık izin')
    expect([mode, query, queryKey, keywordCount]).toEqual(['VECTOR', LEAVE_QUESTION, 'yillik izin kaç gün?', 'NULL'])
    expect(Number(resultCount)).toBeGreaterThanOrEqual(1)
    expect(Number(bestDistance)).toBeLessThanOrEqual(0.49)
    expectMatchesSchema(popular, 'PopularQueriesResponse')
    expectMatchesSchema(events, 'PageResponseSearchEventResponse')
    expectMatchesSchema(stats, 'SearchEventStatsResponse')
    const popularQueries = (popular.queries as { query: string }[]).map((item) => item.query)
    expect(popularQueries).toContain(LEAVE_QUESTION)
    await expectPopular(page, popularQueries)
    expect(events.content[0].query).toBe(LEAVE_QUESTION)
    expect(events.content[0].mode).toBe('VECTOR')
    const todayCount = queryValue(
      `select count(*) from search_events where created_at >= date_trunc('day', now() at time zone 'Europe/Istanbul') at time zone 'Europe/Istanbul'`,
    )
    expect(String(stats.todayCount)).toBe(todayCount)
    await page.goto('/w/42/arama-kayitlari')
    await expect(page.getByRole('row').nth(1)).toContainText(LEAVE_QUESTION)
    await expect(page.locator('dt', { hasText: 'Bugün' }).locator('..')).toContainText(todayCount)
  })

  test('givenSampleSearch_whenHitOpenedAndClosed_thenFullTextShownResultsKeptAndNoNewSearchEvent', async ({ page }) => {
    // Arrange
    await page.goto('/w/42/arama')
    await openAdvancedAndChoose(page, 'Anlam')
    await ask(page, LEAVE_QUESTION)
    const eventsBefore = queryValue('select count(*) from search_events')
    const hitCount = await hits(page).count()
    const dialog = page.getByRole('dialog')

    // Act
    await hitButton(page, 0).click()

    // Assert
    await expect(dialog).toBeVisible()
    await expect(page).toHaveURL(/\/w\/42\/arama$/)
    const chunkId = await openChunkId(dialog)
    const restResponse = await page.request.get(`${WORKSPACE_API}/sources/10001/chunks/${chunkId}`)
    const restChunk = await restResponse.json()
    expectMatchesSchema(restChunk, 'ChunkResponse')
    const region = dialog.getByRole('region', { name: 'Parça metni' })
    const shownText = await region.evaluate((element) => (element as HTMLElement).innerText)
    expect(md5(shownText)).toBe(md5(restChunk.content))
    expect(queryValue(`select md5(content) from chunks where id = ${chunkId}`)).toBe(md5(restChunk.content))
    expect(shownText).not.toContain('…')

    await page.setViewportSize({ width: 360, height: 640 })
    const box = await dialog.boundingBox()
    expect(box).not.toBeNull()
    expect(box!.x).toBeGreaterThanOrEqual(0)
    expect(box!.y).toBeGreaterThanOrEqual(0)
    expect(box!.x + box!.width).toBeLessThanOrEqual(360)
    expect(box!.y + box!.height).toBeLessThanOrEqual(640)
    const metrics = await region.evaluate((element) => ({
      scrollHeight: element.scrollHeight,
      clientHeight: element.clientHeight,
      scrollWidth: element.scrollWidth,
      clientWidth: element.clientWidth,
    }))
    expect(metrics.scrollHeight).toBeGreaterThan(metrics.clientHeight)
    expect(metrics.scrollWidth).toBeLessThanOrEqual(metrics.clientWidth)
    const scrollYBefore = await page.evaluate(() => window.scrollY)
    await page.mouse.move(180, 320)
    await page.mouse.wheel(0, 600)
    await expect.poll(() => region.evaluate((element) => element.scrollTop)).toBeGreaterThan(0)
    expect(await page.evaluate(() => window.scrollY)).toBe(scrollYBefore)
    await page.setViewportSize({ width: 1280, height: 720 })

    await page.keyboard.press('Escape')
    await expect(dialog).toBeHidden()
    await expect(hitButton(page, 0)).toBeFocused()
    await expect(hits(page)).toHaveCount(hitCount)
    await expect(page.getByLabel('Arama metni')).toHaveValue(LEAVE_QUESTION)
    expect(queryValue('select count(*) from search_events')).toBe(eventsBefore)

    await hitButton(page, 0).click()
    await dialog.getByRole('link', { name: 'Kaynakta göster' }).click()
    await expect(page).toHaveURL(`/w/42/kaynaklar/10001?parca=${chunkId}`)
    await expect(page.getByText(`Arama sonucundan açılan parça vurgulandı: #${chunkId}`)).toBeVisible()
    const fullText = page.locator('tbody tr').first().locator('span[class*="fullText"]')
    const sourceText = await fullText.evaluate((element) => (element as HTMLElement).innerText)
    expect(md5(sourceText)).toBe(md5(restChunk.content))

    await page.goBack()
    await expect(hits(page)).toHaveCount(hitCount)
    await expect(dialog).toBeHidden()
    expect(queryValue('select count(*) from search_events')).toBe(eventsBefore)
  })

  test('givenKeywordSearch_whenHitOpened_thenTermsHighlightedBeyondSnippet', async ({ page }) => {
    // Arrange
    await page.goto('/w/42/arama')
    await openAdvancedAndChoose(page, 'Kelime')
    const searchResponse = page.waitForResponse(
      (response) => response.request().method() === 'POST' && /\/api\/workspaces\/42\/search$/.test(response.url()),
    )
    await ask(page, 'masraf beyanı')
    const result = await (await searchResponse).json()
    expectMatchesSchema(result, 'SearchResultResponse')
    const contentTermStarts = (hit: SearchHitBody) =>
      hit.matchedTerms.filter((term) => term.field === 'CONTENT').map((term) => term.start)
    const hitIndex = (result.hits as SearchHitBody[]).findIndex((hit) => contentTermStarts(hit).some((start) => start >= 190))
    expect(hitIndex, 'a hit with a CONTENT term after the snippet limit').toBeGreaterThanOrEqual(0)
    const target = result.hits[hitIndex] as SearchHitBody
    const contentTerms = target.matchedTerms.filter((term) => term.field === 'CONTENT')
    const lastTerm = contentTerms.toSorted((left, right) => left.start - right.start).at(-1)!

    // Act
    await hitButton(page, hitIndex).click()

    // Assert
    const region = page.getByRole('dialog').getByRole('region', { name: 'Parça metni' })
    await expect(region.locator('mark')).toHaveCount(contentTerms.length)
    await expect(region.locator('mark').last()).toHaveText(target.content.slice(lastTerm.start, lastTerm.end))
  })

  test('givenKeyboardOnly_whenSecondHitOpened_thenFocusTrappedAndReturned', async ({ page }) => {
    // Arrange
    await page.goto('/w/42/arama')
    await ask(page, LEAVE_QUESTION)
    await hitButton(page, 0).focus()

    // Act
    await page.keyboard.press('Tab')
    await expect(hitButton(page, 1)).toBeFocused()
    await page.keyboard.press('Enter')

    // Assert
    const dialog = page.getByRole('dialog')
    await expect(dialog).toBeVisible()
    for (let step = 0; step < 8; step += 1) {
      await page.keyboard.press('Tab')
      expect(await page.evaluate(() => document.activeElement?.closest('dialog') !== null)).toBe(true)
    }
    await page.keyboard.press('Escape')
    await expect(dialog).toBeHidden()
    await expect(hitButton(page, 1)).toBeFocused()
  })

  test('givenSearchHitShown_whenSourceGetsNewVersionBeforeOpening_thenPanelShowsChunkGoneMessage', async ({ page }) => {
    // Arrange
    await page.goto('/w/42/arama')
    await ask(page, LEAVE_QUESTION)
    await hitButton(page, 0).click()
    const chunkId = await openChunkId(page.getByRole('dialog'))
    await page.keyboard.press('Escape')
    await expect(page.getByRole('dialog')).toBeHidden()
    await page.goto('/w/42/kaynaklar/10001')
    const more = page.getByRole('button', { name: 'Daha fazla göster' })
    const chunkCount = Number(queryValue(`select chunk_count from sources where id = 10001`))
    await expect(async () => {
      if ((await more.count()) > 0) {
        await more.click({ timeout: 1_000 }).catch(() => undefined)
      }
      await expect(page.locator('tbody tr')).toHaveCount(chunkCount, { timeout: 1_000 })
    }).toPass({ timeout: 30_000 })

    await page.goto('/w/42/arama')
    await ask(page, LEAVE_QUESTION)
    await expect.poll(async () => (await hits(page).count()) > 0).toBe(true)
    const staleHitIndex = await hitIndexWithChunk(page, chunkId)

    // Act
    const upload = await page.request.post(`${WORKSPACE_API}/sources/10001/versions`, {
      multipart: { version: '2026.2', file: { name: 'Atlaskap-Lojistik-Surec-El-Kitabi.pdf', mimeType: 'application/pdf', buffer: readFileSync(SAMPLE_PDF) } },
    })
    const job = await acceptedJob(page, upload)
    await waitForJob(page, job.statusUrl, 'DONE')
    await hitButton(page, staleHitIndex).click()

    // Assert
    const dialog = page.getByRole('dialog')
    await expect(dialog.getByRole('alert')).toHaveText('Bu parça artık yok; kaynak yeni bir sürüme geçmiş olabilir.')
    await expect(dialog.getByRole('region', { name: 'Parça metni' })).toHaveCount(0)
    await expect(dialog.getByRole('link', { name: 'Kaynakta göster' })).toHaveCount(0)
    await dialog.getByRole('button', { name: 'Kapat', exact: true }).click()
    await expect(dialog).toBeHidden()
    await expect(hits(page).first()).toBeVisible()
    await page.goto(`/w/42/kaynaklar/10001?parca=${chunkId}`)
    await expect(page.getByText('Bu parça artık yok; kaynak yeni bir sürüme geçmiş olabilir.')).toBeVisible()
    const gone = await page.request.get(`${WORKSPACE_API}/sources/10001/chunks/${chunkId}`)
    expect(gone.status()).toBe(404)
    const problemBody = await gone.json()
    expectMatchesSchema(problemBody, 'ProblemDetail')
    expect(problemBody.errorCode).toBe('CHUNK_NOT_FOUND')
    expect(queryValue(`select count(*) from chunks where id = ${chunkId}`)).toBe('0')
    expect(queryValue(`select version from sources where id = 10001`)).toBe('2026.2')
    expect(
      queryValue(`select coalesce(top_chunk_heading, '') <> '' from search_events where top_chunk_id = ${chunkId} limit 1`),
    ).toBe('t')
  })

  test('givenInvalidRequestsThroughSite_whenSent_thenProblemDetailsAndNoDatabaseChange', async ({ page }) => {
    // Arrange
    const api = WORKSPACE_API
    const validMd = Buffer.from('# a\nmetin\n', 'utf-8')
    type Row = { label: string; send: () => Promise<Awaited<ReturnType<typeof postSource>>>; status: number; code: string; field?: string }
    const json = (path: string, data: unknown): Row['send'] => () => page.request.post(path, { data })
    const form = (path: string, multipart: Record<string, string | { name: string; mimeType: string; buffer: Buffer }>): Row['send'] =>
      () => page.request.post(path, { multipart })
    const md = { name: 'x.md', mimeType: 'text/markdown', buffer: validMd }
    const rows: Row[] = [
      { label: 'blank query', send: json(`${api}/search`, { query: '   ' }), status: 400, code: 'VALIDATION_FAILED', field: 'query' },
      { label: '501 chars', send: json(`${api}/search`, { query: 'a'.repeat(501) }), status: 400, code: 'VALIDATION_FAILED', field: 'query' },
      { label: 'limit 0', send: json(`${api}/search`, { query: 'izin', limit: 0 }), status: 400, code: 'VALIDATION_FAILED', field: 'limit' },
      { label: 'limit 51', send: json(`${api}/search`, { query: 'izin', limit: 51 }), status: 400, code: 'VALIDATION_FAILED', field: 'limit' },
      { label: 'efSearch 9', send: json(`${api}/search`, { query: 'izin', efSearch: 9 }), status: 400, code: 'VALIDATION_FAILED', field: 'efSearch' },
      { label: 'efSearch 1001', send: json(`${api}/search`, { query: 'izin', efSearch: 1001 }), status: 400, code: 'VALIDATION_FAILED', field: 'efSearch' },
      { label: 'mode FUZZY', send: json(`${api}/search`, { query: 'izin', mode: 'FUZZY' }), status: 400, code: 'VALIDATION_FAILED', field: 'mode' },
      { label: 'channel 61', send: json(`${api}/search`, { query: 'izin', channel: 'c'.repeat(61) }), status: 400, code: 'VALIDATION_FAILED', field: 'channel' },
      {
        label: 'malformed json',
        send: () => page.request.post(`${api}/search`, { data: '{', headers: { 'Content-Type': 'application/json' } }),
        status: 400,
        code: 'BAD_REQUEST',
      },
      { label: 'unknown workspace', send: json('/api/workspaces/999/search', { query: 'izin' }), status: 404, code: 'WORKSPACE_NOT_FOUND' },
      { label: 'unknown source', send: () => page.request.get(`${api}/sources/999999`), status: 404, code: 'SOURCE_NOT_FOUND' },
      { label: 'unknown chunk', send: () => page.request.get(`${api}/sources/10001/chunks/999999999`), status: 404, code: 'CHUNK_NOT_FOUND' },
      { label: 'unknown job', send: () => page.request.get(`${api}/ingestion-jobs/999999`), status: 404, code: 'INGESTION_JOB_NOT_FOUND' },
      { label: 'non numeric id', send: () => page.request.get(`${api}/sources/abc`), status: 400, code: 'BAD_REQUEST' },
      { label: 'page -1', send: () => page.request.get(`${api}/sources?page=-1`), status: 400, code: 'VALIDATION_FAILED', field: 'page' },
      { label: 'size 101', send: () => page.request.get(`${api}/sources?size=101`), status: 400, code: 'VALIDATION_FAILED', field: 'size' },
      { label: 'padded name', send: form(`${api}/sources`, { name: ' a ', file: md }), status: 400, code: 'VALIDATION_FAILED', field: 'name' },
      { label: 'name 81', send: form(`${api}/sources`, { name: 'n'.repeat(81), file: md }), status: 400, code: 'VALIDATION_FAILED', field: 'name' },
      { label: 'no file part', send: form(`${api}/sources`, { name: 'Dosyasız' }), status: 400, code: 'VALIDATION_FAILED', field: 'file' },
      {
        label: 'empty file',
        send: form(`${api}/sources`, { name: 'Boş', file: { name: 'bos.md', mimeType: 'text/markdown', buffer: Buffer.alloc(0) } }),
        status: 400,
        code: 'VALIDATION_FAILED',
        field: 'file',
      },
      {
        label: 'docx',
        send: form(`${api}/sources`, { name: 'Word', file: { name: 'rapor.docx', mimeType: 'application/octet-stream', buffer: Buffer.from('docx') } }),
        status: 415,
        code: 'UNSUPPORTED_FILE_TYPE',
      },
      {
        label: 'fake pdf',
        send: form(`${api}/sources`, { name: 'Sahte', file: { name: 'sahte.pdf', mimeType: 'application/pdf', buffer: Buffer.from('merhaba') } }),
        status: 415,
        code: 'UNSUPPORTED_FILE_TYPE',
      },
      {
        label: 'windows-1254 txt',
        send: form(`${api}/sources`, { name: 'Eski', file: { name: 'eski.txt', mimeType: 'text/plain', buffer: Buffer.from([0x64, 0xfd, 0xf0, 0x65]) } }),
        status: 415,
        code: 'UNSUPPORTED_FILE_ENCODING',
      },
      {
        label: 'version label 41',
        send: form(`${api}/sources/10001/versions`, { version: 'v'.repeat(41), file: md }),
        status: 400,
        code: 'VALIDATION_FAILED',
        field: 'version',
      },
      {
        label: 'csv onto pdf source',
        send: form(`${api}/sources/10001/versions`, { file: { name: 'veri.csv', mimeType: 'text/csv', buffer: Buffer.from('a,b\n1,2\n') } }),
        status: 409,
        code: 'SOURCE_KIND_MISMATCH',
      },
      { label: 'embeddings empty', send: json('/api/embeddings', { text: '' }), status: 400, code: 'VALIDATION_FAILED', field: 'text' },
      { label: 'embeddings 2001', send: json('/api/embeddings', { text: 't'.repeat(2001) }), status: 400, code: 'VALIDATION_FAILED', field: 'text' },
      {
        label: '20 MiB + 1 pdf',
        send: form(`${api}/sources/10001/versions`, {
          file: { name: 'buyuk.pdf', mimeType: 'application/pdf', buffer: Buffer.concat([Buffer.from('%PDF-'), Buffer.alloc(20 * 1024 * 1024 + 1 - 5)]) },
        }),
        status: 413,
        code: 'FILE_TOO_LARGE',
      },
    ]
    const countsBefore = dbCounts()

    // Act
    const failures: string[] = []
    for (const row of rows) {
      const response = await row.send()
      const contentType = response.headers()['content-type'] ?? ''
      const text = await response.text()
      let code = ''
      let fields: string[] = []
      if (contentType.startsWith('application/problem+json')) {
        const body = JSON.parse(text) as { errorCode: string; violations?: { field: string }[] }
        expectMatchesSchema(body, 'ProblemDetail')
        code = body.errorCode
        fields = (body.violations ?? []).map((violation) => violation.field)
      }
      const fieldOk = row.field === undefined || fields.includes(row.field)
      const leaks = /Exception|at com\./.test(text)
      if (response.status() !== row.status || code !== row.code || !fieldOk || leaks) {
        failures.push(`${row.label}: got ${response.status()} ${contentType} code=${code} fields=${fields.join(',')} leaks=${leaks}; want ${row.status} ${row.code} ${row.field ?? ''}`)
      }
    }

    // Assert
    expect(failures).toEqual([])
    expect(dbCounts()).toBe(countsBefore)
  })

  test('givenUploadsOnPage_whenNameTakenOrLimitReached_thenTurkishMessagesAndNoExtraSource', async ({ page }) => {
    // Arrange
    await page.goto('/w/42/belge-yukle')
    await page.getByLabel('Dosya').setInputFiles({
      name: 'Çalışan İzin Notları.md',
      mimeType: 'text/markdown',
      buffer: Buffer.from('# İzin\nÇalışanlar yıllık izin kullanır.\n', 'utf-8'),
    })

    // Act
    await page.getByRole('button', { name: 'Belgeyi yükle' }).click()
    await expect(page.getByRole('heading', { name: 'Belge hazır' })).toBeVisible({ timeout: INGESTION_TIMEOUT_MS })

    // Assert
    expect(queryRows(`select name, file_name from sources where name = 'Çalışan İzin Notları'`)).toEqual([
      ['Çalışan İzin Notları', 'Çalışan İzin Notları.md'],
    ])
    await page.goto('/w/42/kaynaklar')
    await expect(page.getByRole('link', { name: 'Çalışan İzin Notları', exact: true })).toBeVisible()

    // Arrange (b)
    const sourcesBefore = queryValue('select count(*) from sources')
    await page.goto('/w/42/belge-yukle')
    await page.getByLabel('Kaynak adı').fill('DENEME NOTU')
    await page.getByLabel('Dosya').setInputFiles({ name: 'x.md', mimeType: 'text/markdown', buffer: Buffer.from('# x\ny\n') })

    // Act (b)
    await page.getByRole('button', { name: 'Belgeyi yükle' }).click()

    // Assert (b)
    await expect(page.getByText('Bu ad çalışma alanında başka bir kaynakta kullanılıyor.')).toBeVisible()
    expect(queryValue('select count(*) from sources')).toBe(sourcesBefore)

    // Arrange (c)
    const missing = 10 - Number(sourcesBefore)
    for (let index = 0; index < missing; index += 1) {
      const job = await acceptedJob(page, await postSource(page, `Limit ${index}`, 'l.md', Buffer.from(`# L${index}\nmetin\n`)))
      await waitForJob(page, job.statusUrl, 'DONE')
    }
    await page.goto('/w/42/belge-yukle')
    await page.getByLabel('Kaynak adı').fill('On birinci')
    await page.getByLabel('Dosya').setInputFiles({ name: 'z.md', mimeType: 'text/markdown', buffer: Buffer.from('# z\ny\n') })

    // Act (c)
    await page.getByRole('button', { name: 'Belgeyi yükle' }).click()
    const viaRest = await postSource(page, 'On birinci REST', 'z.md', Buffer.from('# z\ny\n'))

    // Assert (c)
    await expect(page.getByRole('alert').filter({ hasText: 'Bu çalışma alanı 10 kaynak sınırına ulaştı.' })).toBeVisible()
    expect(viaRest.status()).toBe(409)
    const limitBody = await viaRest.json()
    expectMatchesSchema(limitBody, 'ProblemDetail')
    expect(limitBody.errorCode).toBe('SOURCE_LIMIT_REACHED')
    expect(queryValue('select count(*) from sources')).toBe('10')

    // Arrange (d)
    const searches: string[] = []
    page.on('request', (request) => {
      if (request.method() === 'POST' && request.url().endsWith('/search')) {
        searches.push(request.url())
      }
    })
    await page.goto('/w/42/arama')
    const eventsBefore = queryValue('select count(*) from search_events')

    // Act (d)
    await page.getByLabel('Arama metni').fill('   ')
    await page.getByLabel('Arama metni').press('Enter')

    // Assert (d)
    await expect(page.getByRole('button', { name: 'Ara', exact: true })).toBeDisabled()
    expect(searches).toEqual([])
    expect(queryValue('select count(*) from search_events')).toBe(eventsBefore)
  })

  test('givenBodyOver21MbThroughSite_whenPostVersion_thenFileTooLargeProblemDetails', async ({ page }) => {
    // Arrange
    const countsBefore = dbCounts()
    const body = Buffer.concat([Buffer.from('%PDF-'), Buffer.alloc(22 * 1024 * 1024 - 5)])

    // Act
    const response = await page.request.post(`${WORKSPACE_API}/sources/10001/versions`, {
      multipart: { file: { name: 'dev.pdf', mimeType: 'application/pdf', buffer: body } },
    })
    const contentType = response.headers()['content-type'] ?? ''
    const text = await response.text()

    // Assert
    expect(response.status()).toBe(413)
    expect(contentType).toMatch(/^application\/problem\+json/)
    const problemBody = JSON.parse(text)
    expectMatchesSchema(problemBody, 'ProblemDetail')
    expect(problemBody.errorCode).toBe('FILE_TOO_LARGE')
    expect(dbCounts()).toBe(countsBefore)
  })

  test('givenOversizedBodyToPathWithEncodedQuote_whenPost_thenProblemDetailsBodyIsValidJson', async ({ page }) => {
    // Arrange
    const countsBefore = dbCounts()
    const body = Buffer.concat([Buffer.from('%PDF-'), Buffer.alloc(22 * 1024 * 1024 - 5)])

    // Act
    const responses = await Promise.all(
      ['x%22y', 'x%5Cy', 'x%0Ay'].map((segment) =>
        page.request.post(`${WORKSPACE_API}/sources/${segment}/versions`, {
          multipart: { file: { name: 'dev.pdf', mimeType: 'application/pdf', buffer: body } },
        }),
      ),
    )
    const texts = await Promise.all(responses.map((response) => response.text()))

    // Assert
    expect(responses.map((response) => response.status())).toEqual([413, 413, 413])
    for (const text of texts) {
      const problemBody = JSON.parse(text)
      expectMatchesSchema(problemBody, 'ProblemDetail')
      expect(problemBody.errorCode).toBe('FILE_TOO_LARGE')
    }
    expect(dbCounts()).toBe(countsBefore)
  })
})
