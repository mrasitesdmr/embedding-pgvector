import { expect, test } from '@playwright/test'
import type { Page } from '@playwright/test'
import { expectMatchesSchema } from './support/contract.ts'
import { queryRows, queryValue } from './support/database.ts'

const HANDBOOK_QUESTION = 'Yıllık izin kaç gün?'
const OLLAMA_SEARCH_MESSAGE = "Embedding servisine (Ollama) ulaşılamıyor. Ollama'nın çalıştığını kontrol et ya da Kelime yöntemiyle ara."

test.skip(process.env.E2E_OLLAMA_DOWN !== '1', 'Backend Ollama erişilemez adresle açıkken E2E_OLLAMA_DOWN=1 ile çalışır')
test.describe.configure({ mode: 'serial' })

function hits(page: Page) {
  return page.getByRole('list', { name: 'Arama sonuçları' }).getByRole('listitem')
}

test.describe('Ollama down', () => {
  test('givenOllamaDown_whenHybridSearch_thenOllamaMessageAndKeywordSearchStillWorks', async ({ page }) => {
    // Arrange
    await page.goto('/w/42/arama')
    const eventsBefore = queryValue('select count(*) from search_events')
    const failed = page.waitForResponse((response) => response.request().method() === 'POST' && response.url().endsWith('/search'))

    // Act
    await page.getByLabel('Arama metni').fill(HANDBOOK_QUESTION)
    await page.getByLabel('Arama metni').press('Enter')
    const failedResponse = await failed
    const problemBody = await failedResponse.json()

    // Assert
    await expect(page.getByRole('alert')).toHaveText(OLLAMA_SEARCH_MESSAGE)
    expect(failedResponse.status()).toBe(503)
    expectMatchesSchema(problemBody, 'ProblemDetail')
    expect(problemBody.errorCode).toBe('EMBEDDING_UNAVAILABLE')
    expect(queryValue('select count(*) from search_events')).toBe(eventsBefore)

    // Act (keyword)
    await page.getByText('Gelişmiş ayarlar').click()
    await page.getByRole('radio', { name: 'Kelime' }).check()
    await page.getByLabel('Arama metni').fill('yıllık izin')
    await page.getByLabel('Arama metni').press('Enter')

    // Assert (keyword)
    await expect(hits(page).first()).toBeVisible()
    const texts = await hits(page).allTextContents()
    expect(texts.some((text) => text.includes('Yıllık izin'))).toBe(true)
    expect(queryRows(`select mode from search_events order by id desc limit 1`)).toEqual([['KEYWORD']])
    expect(Number(queryValue('select count(*) from search_events'))).toBe(Number(eventsBefore) + 1)
  })

  test('givenOllamaDown_whenUploadNewVersionOfMarkdown_thenFailureReasonAndOldChunksKept', async ({ page }) => {
    // Arrange
    const sourceId = Number(queryValue(`select id from sources where name = 'Deneme notu'`))
    const chunkIdsBefore = queryRows(`select id from chunks where source_id = ${sourceId} order by id`)
    await page.goto('/w/42/kaynaklar')
    const row = page.getByRole('row').filter({ has: page.getByRole('link', { name: 'Deneme notu', exact: true }) })
    await row.getByRole('button', { name: 'Yeni sürüm yükle' }).click()
    const dialog = page.getByRole('dialog', { name: 'Yeni sürüm yükle' })
    await dialog.getByLabel('Dosya').setInputFiles({
      name: 'deneme-notu-2.md',
      mimeType: 'text/markdown',
      buffer: Buffer.from('# Deneme notu\nYeni sürüm metni.\n', 'utf-8'),
    })

    // Act
    await dialog.getByRole('button', { name: 'Yeni sürümü yükle' }).click()

    // Assert
    const failure = 'Son yükleme başarısız: embedding servisine (Ollama) ulaşılamıyor'
    await expect(row).toContainText(failure, { timeout: 60_000 })
    const source = await (await page.request.get(`/api/workspaces/42/sources/${sourceId}`)).json()
    expectMatchesSchema(source, 'SourceResponse')
    expect(source.latestJob.stage).toBe('FAILED')
    expect(source.latestJob.errorCode).toBe('EMBEDDING_UNAVAILABLE')
    expect(source.status).toBe('READY')
    expect(queryRows(`select id from chunks where source_id = ${sourceId} order by id`)).toEqual(chunkIdsBefore)
    expect(queryValue(`select chunk_count from sources where id = ${sourceId}`)).toBe(String(chunkIdsBefore.length))
    await page.goto(`/w/42/kaynaklar/${sourceId}`)
    await expect(page.getByText(failure)).toBeVisible()
  })
})
