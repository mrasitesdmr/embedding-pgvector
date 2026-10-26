---
name: e2e-testing
description: Playwright E2E test pattern'leri, Page Object Model, configuration, CI/CD entegrasyonu, artifact yönetimi ve flaky test stratejileri. Playwright testleri yazarken, page object'leri yapılandırırken, dosya yükleme ve içe alma ilerlemesini (sayfa yenilense de devam etmesi dahil) ya da arama akışını uçtan uca test ederken veya CI'da flaky E2E koşularını düzeltirken kullan.
license: MIT
metadata:
  origin: ECC
  source: https://github.com/affaan-m/ECC/tree/main/skills/e2e-testing
---

# E2E Testing Pattern'leri

Stabil, hızlı ve bakımı kolay E2E test suite'leri kurmak için kapsamlı Playwright pattern'leri.

> **Proje kuralı:** Test adları `given<Precondition>_when<Action>_then<ExpectedResult>` (İngilizce, Playwright'ta `test(...)`), gövde `// Arrange` / `// Act` / `// Assert`, locator'lar Türkçe UI metniyle `getByRole` > `getByLabel` > `getByText` > `getByTestId` sırasıyla, sabit bekleme (`waitForTimeout`) ve `networkidle` yok, type-only import'lar `import type`, göreli import'lar uzantılı (`frontend-conventions` bölüm 2, 6, 11).

## Test dosyası organizasyonu

```
frontend/
├── e2e/
│   ├── search.spec.ts           Arama ve sonuçtan kaynak detayına gitme
│   ├── search-events.spec.ts    Arama kayıtları
│   ├── workspace.spec.ts        Çalışma alanı değiştirme, bilinmeyen alan
│   ├── upload.spec.ts           Yeni kaynak, yeni sürüm, ilerleme, geçersiz dosya
│   ├── pages/
│   │   └── SearchPage.ts        Page object'ler (birden fazla spec kullanınca açılır)
│   └── fixtures/                (gerekirse) örnek dosyalar; mevcut örnekler backend/samples/ altında
└── playwright.config.ts
```

## Page Object Model (POM)

```typescript
// e2e/pages/SearchPage.ts
import type { Locator, Page } from '@playwright/test'

export class SearchPage {
  readonly page: Page
  readonly queryInput: Locator
  readonly hits: Locator
  readonly eventsTab: Locator

  constructor(page: Page) {
    this.page = page
    this.queryInput = page.getByLabel('Arama metni')
    this.hits = page.getByRole('list', { name: 'Arama sonuçları' }).getByRole('listitem')
    this.eventsTab = page.getByRole('link', { name: /^Arama kayıtları/ })
  }

  async goto(workspaceId: number) {
    await this.page.goto(`/w/${workspaceId}/arama`)
  }

  async searchWithSuggestion(text: string) {
    await this.page.getByRole('button', { name: text }).click()
  }

  hitWithTitle(title: string): Locator {
    return this.hits.filter({ hasText: title }).first()
  }
}
```

- Locator'lar web-first'tür: her eylemde yeniden çözülür ve actionability (görünür, stabil, etkin) için otomatik bekler; POM'da ekstra `waitFor`/`waitForLoadState` gerekmez.
- `goto` sonrasında `waitForLoadState('networkidle')` kullanılmaz: Playwright dokümantasyonu bunu önermez ve iş durumu polling'i (ya da ileride açık STOMP bağlantısı) olan sayfada ağ hiç boşalmaz. Beklenen elemanı `expect(...)` ile bekle.

## Test yapısı

```typescript
// e2e/search.spec.ts
import { expect, test } from '@playwright/test'
import { SearchPage } from './pages/SearchPage.ts'

const PASSWORD_QUERY = 'Şifremi unuttum, online işlemlere giremiyorum'
const PASSWORD_TITLE = 'Online İşlemler parolanı sıfırlama'

test.describe('Search', () => {
  test('givenDalgaWorkspace_whenHybridSuggestionAndOpenHit_thenSourceDetailHighlightsChunk', async ({ page }) => {
    // Arrange
    const searchPage = new SearchPage(page)
    await searchPage.goto(42)

    // Act
    await searchPage.searchWithSuggestion(PASSWORD_QUERY)
    const passwordHit = searchPage.hitWithTitle(PASSWORD_TITLE)
    await expect(passwordHit).toBeVisible()
    await passwordHit.getByRole('link').click()

    // Assert
    await expect(page).toHaveURL(/\/w\/42\/kaynaklar\/\d+\?parca=\d+$/)
    await expect(page.getByText(/Arama sonucundan açılan parça vurgulandı/)).toBeVisible()
  })
})
```

## İçe alma ilerlemesi: yenileme ve ikinci sekme

İlerleme polling ile izlenir (`frontend-conventions` bölüm 8). Süren işin bilgisi backend'den (`latestJob`) geldiği için sayfa yenilenince ya da adres ikinci bir sekmede açılınca ilerleme kaldığı yerden görünmelidir; E2E bunu doğrular. Aynı context'teki sayfalar `localStorage`'ı (arama ayarları, bölüm 9) ve cookie'leri paylaşır; ayarların sekmeler arasında karışmaması gereken senaryolarda `browser.newContext()` ile ayrı context aç.

```typescript
// e2e/upload.spec.ts
test('givenModemGuide_whenUploadVersionAndReload_thenProgressContinuesAndBecomesReady', async ({ page }) => {
  // Arrange
  await page.goto('/w/42/kaynaklar')
  const row = sourceRow(page, MANUAL_NAME)
  await row.getByRole('button', { name: 'Yeni sürüm yükle' }).click()
  const dialog = page.getByRole('dialog', { name: 'Yeni sürüm yükle' })
  await dialog.getByLabel('Dosya').setInputFiles(SAMPLE_PDF)
  await dialog.getByLabel('Sürüm etiketi (isteğe bağlı)').fill('3.2')

  // Act
  await dialog.getByRole('button', { name: 'Yeni sürümü yükle' }).click()

  // Assert
  await expect(row.getByRole('progressbar')).toBeVisible()
  await expect(row.getByRole('button', { name: 'Yeni sürüm yükle' })).toBeDisabled()
  await page.reload()
  const reloadedRow = sourceRow(page, MANUAL_NAME)
  await expect(reloadedRow.getByRole('progressbar')).toBeVisible()
  // Sabit bekleme yok; gerçek Ollama ile iş uzun sürebileceği için timeout büyük
  await expect(page.getByText(/Dalga T5 modem kullanım kılavuzu v3\.2 hazır · \d+ parça/)).toBeVisible({
    timeout: INGESTION_TIMEOUT_MS,
  })
  await expect(reloadedRow.getByText('Hazır')).toBeVisible()
})
```

- Gerçek Ollama ile iş çoğu zaman 1-2 saniyede biter; "yenilemeden sonra ilerleme görünüyor" adımı bu yüzden işin süresine bağlıdır ve kırılgan olabilir. Kalıcı çözüm yavaşlatılmış bir embedding ortamıdır; o kurulana kadar bu risk raporda yazılır.
- Testler gerçek veriyi değiştirir (yeni sürüm, yeni kaynak). Tekrar koşulabilir olmaları için her koşuda benzersiz kaynak adı kullan ya da çalışma alanındaki boş yere göre davran; temiz başlangıç gerekiyorsa `docker compose ... down -v` + `up` (`deployment/CLAUDE.md`).

## Playwright configuration

Projedeki config (`frontend/playwright.config.ts`):

```typescript
import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  retries: 0,
  workers: 1,
  reporter: 'list',
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:3000',
    trace: 'retain-on-failure',
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
  ],
})
```

> **Proje kuralı:** Sadece Chromium projesi tanımlanır. `webServer` yoktur: testler dışarıdan açılmış sisteme karşı koşar; varsayılan Docker Compose'daki site (`http://localhost:3000`, `deployment/CLAUDE.md`), dev server için `E2E_BASE_URL=http://localhost:5173 npm run test:e2e`. Değişkenin adı her yerde `E2E_BASE_URL`'dir; `BASE_URL` kullanılmaz. Testler aynı veritabanını değiştirdiği için `workers: 1` ve `retries: 0`'dır; kırılgan bir test retry ile gizlenmez.

Uçtan uca testler gerçek backend, PostgreSQL ve Ollama'yı gerektirir; Ollama Mac'te `ollama serve` ile açık olmalıdır (`ollama.md`).

## Flaky test pattern'leri

### Karantina

```typescript
test('givenWorkspaceWithTenSources_whenEleventhSourceAdded_thenShowsSourceLimitMessage', async ({ page }) => {
  test.fixme(true, 'Flaky - Issue #123')
  // test kodu...
})

test('givenSlowEmbeddingService_whenHybridSearch_thenShowsRankedHits', async ({ page }) => {
  test.skip(!!process.env.CI, 'Flaky in CI - Issue #123')
  // test kodu...
})
```

### Flaky'liği tespit etme

```bash
npx playwright test e2e/search/vector-search.spec.ts --repeat-each=10
npx playwright test e2e/search/vector-search.spec.ts --retries=3
```

### Yaygın sebepler ve çözümler

**Eski `page.click(selector)` API'si:**
```typescript
// Kötü: discouraged selector tabanlı API, CSS/test-id'ye bağlı
await page.click('[data-testid="search-button"]')

// İyi: role tabanlı, strict ve her eylemde yeniden çözülen locator
await page.getByRole('button', { name: 'Aramayı başlat' }).click()
```

**Network zamanlaması:**
```typescript
// Kötü: keyfi timeout
await page.waitForTimeout(5000)

// İyi: kullanıcının gördüğü sonucu bekle
await expect(page.getByRole('list', { name: 'Arama sonuçları' }).getByRole('listitem')).toHaveCount(10)

// Gerekirse belirli response'u bekle (eylemden ÖNCE başlat ki kaçmasın)
const searchResponse = page.waitForResponse((response) => response.url().endsWith('/api/workspaces/42/search') && response.request().method() === 'POST')
await page.getByRole('button', { name: 'Aramayı başlat' }).click()
await searchResponse
```

**Animasyon zamanlaması:**
```typescript
// Locator.click() elemanın görünür, stabil (animasyonu bitmiş) ve etkin olmasını zaten bekler;
// ekstra waitFor veya networkidle gerekmez
await page.getByRole('menuitem', { name: 'Kaynağı sil' }).click()
```

## Artifact yönetimi

### Screenshot'lar

```typescript
await page.screenshot({ path: 'artifacts/search-page.png' })
await page.screenshot({ path: 'artifacts/full-page.png', fullPage: true })
await page.getByRole('list', { name: 'Arama sonuçları' }).screenshot({ path: 'artifacts/search-hits.png' })
```

### Trace'ler

Çoğu durumda config'deki `trace: 'on-first-retry'` yeterlidir. Elle trace gerekiyorsa Playwright Tracing API'si context üzerinden kullanılır:

```typescript
await context.tracing.start({ screenshots: true, snapshots: true })
// ... test eylemleri ...
await context.tracing.stop({ path: 'artifacts/trace.zip' })
```

Trace'i `npx playwright show-trace artifacts/trace.zip` ile aç. `browser.startTracing()` farklı bir şeydir: Chromium'a özel, DevTools performans paneli için düşük seviye bir trace üretir; Playwright trace viewer ile açılmaz.

### Video

```typescript
// playwright.config.ts içinde
use: {
  video: 'retain-on-failure',
}
// Videolar outputDir (ör. artifacts/) altına yazılır
```

## CI/CD entegrasyonu

```yaml
# .github/workflows/e2e.yml
name: E2E Tests
on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: frontend
    steps:
      - uses: actions/checkout@v7
      - uses: actions/setup-node@v7
        with:
          node-version: 24
      - run: npm ci
      - run: npx playwright install --with-deps
      - run: npx playwright test
        env:
          E2E_BASE_URL: ${{ vars.STAGING_URL }}
      - uses: actions/upload-artifact@v7
        if: always()
        with:
          name: playwright-report
          path: frontend/playwright-report/
          retention-days: 30
```

`E2E_BASE_URL` sadece testler zaten çalışan bir sisteme karşı koşulacaksa verilir: bu projede devops'un `docker compose` ile açık bıraktığı lokal sistem (http://localhost:3000). Deploy edilmiş bir ortam henüz yoktur. Docker ve ortamlar `deployment/` ile devops agent'ının sorumluluğundadır.

## Test raporu şablonu

```markdown
# E2E Test Raporu

**Tarih:** YYYY-MM-DD HH:MM
**Süre:** Xm Ys
**Durum:** PASSING / FAILING

## Özet
- Toplam: X | Geçen: Y (Z%) | Kalan: A | Flaky: B | Atlanan: C

## Başarısız testler

### test-adı
**Dosya:** `e2e/search/feature.spec.ts:45`
**Hata:** Expected element to be visible
**Screenshot:** artifacts/failed.png
**Önerilen çözüm:** [açıklama]

## Artifact'ler
- HTML Report: playwright-report/index.html
- Screenshot'lar: artifacts/*.png
- Videolar: artifacts/**/*.webm
- Trace'ler: artifacts/**/*.zip
```

## Kritik akış testi

Geri alınamayan ya da gerçek kaynak harcayan akışlarda (kaynağı ve tüm parçalarını kalıcı silme, ücretli embedding API'siyle toplu yeniden içe alma vb.) testi production'a karşı koşturma, onay adımını doğrula ve sonucu backend response'u üzerinden bekle. Locator ve AAA kuralları burada da geçerlidir:

```typescript
test('givenConfirmationDialog_whenAdminConfirmsDeletingSource_thenShowsSourceDeletedMessage', async ({ page }) => {
  test.skip(!!process.env.E2E_BASE_URL, 'Geri alınamaz akış; sadece lokal ortamda koşar')

  // Arrange
  await page.goto('/w/42/kaynaklar/301')
  await page.getByRole('button', { name: 'Kaynağı sil' }).click()
  await expect(page.getByRole('dialog', { name: 'Kaynağı sil' })).toBeVisible()

  // Act
  const deleteResponse = page.waitForResponse(
    (response) => response.url().endsWith('/api/workspaces/42/sources/301') && response.request().method() === 'DELETE',
  )
  await page.getByRole('button', { name: 'Evet, sil' }).click()
  await deleteResponse

  // Assert
  await expect(page.getByRole('status')).toHaveText('Kaynak ve parçaları silindi.')
})
```

(Orijinal skill'deki Wallet/Web3 ve ödeme örnekleri bu projeyle ilgisiz olduğu için çıkarıldı.)

## Git'e girmeyen çıktılar

`playwright-report/`, `artifacts/` (config'teki `outputDir`), `playwright-results.xml` ve `playwright-results.json` üretilen dosyalardır; `frontend/.gitignore`'a eklenir, commit'lenmez.

## İlgili skill'ler

- `react-testing` - component seviyesinde testler ve RTL/Playwright karar sınırı
- `webapp-testing` - çalışan uygulamayı Python Playwright script'iyle hızlıca keşfetme ve doğrulama
