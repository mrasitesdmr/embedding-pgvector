---
name: react-testing
description: React Testing Library ve Vitest ile React component testleri, network mocking için MSW, axe ile accessibility assertion'ları ve component testi ile Playwright E2E arasındaki karar sınırı. frontend/ altında React component'leri, hook'lar veya sayfalar için test yazarken ya da düzeltirken kullan.
license: MIT
metadata:
  origin: ECC
  source: https://github.com/affaan-m/ECC/tree/main/skills/react-testing
---

# React Testing

Davranış odaklı component testleri, custom hook testleri, accessibility assertion'ları ve network seviyesinde mocking için kapsamlı React test pattern'leri.

> **Proje kuralı:** Test isimleri `given<Precondition>_when<Action>_then<ExpectedResult>`, `it` (`test` değil), `// Arrange` / `// Act` / `// Assert`, Türkçe UI metniyle sorgu, MSW (`onUnhandledFrame: 'error'`), her `errorCode` için mesaj testi ve dosya konumları (`src/test/`) `frontend-conventions` bölüm 4 ve 11'dedir. Aşağıdaki örnekler bu kurallara göre yazılmıştır; çelişki olursa `frontend-conventions` kazanır.

## Ne zaman devreye girer

- React component'leri, custom hook'lar veya sayfalar için test yazarken
- Test edilmemiş eski component'lere test coverage eklerken
- Enzyme'den veya class component dönemi pattern'lerinden React Testing Library'ye geçerken
- Yeni bir React projesine Vitest kurarken
- Testlerde HTTP request'lerini mock'larken
- Accessibility ihlallerini assert ederken
- Hangi testin RTL'ye, hangisinin Playwright Component Testing'e, hangisinin tam E2E'ye ait olduğuna karar verirken

## Temel ilke

Implementation detaylarını değil, kullanıcının gördüğünü ve yaptığını test et.

Bir test şunları yapmalı:

- Component'i production'daki provider'larla aynı şekilde render etmeli
- Onunla erişilebilir query'ler (role, label) ve `userEvent` üzerinden etkileşime girmeli
- Görünür çıktıyı ve gözlemlenebilir side effect'leri assert etmeli (callback çağrıldı, request gönderildi)

Bir test şunları YAPMAMALI:

- Component state'ini, child'lara geçilen prop'ları ya da hangi hook'ların çağrıldığını incelemek
- React'in kendisini veya framework hook'larını mock'lamak
- Render sayısını ya da kullanıcıyı etkileyenin ötesinde DOM yapısını assert etmek

## Kütüphane seçimi

| Runner | Ne zaman | Not |
|---|---|---|
| **Vitest** | Vite, Remix, modern kurulumlar | Daha hızlı, native ESM, Jest uyumlu API. **Bu projenin seçimi.** |
| **Jest** | Next.js, CRA, köklü repo'lar | Birçok React projesinde varsayılan |
| **Playwright Component Testing** | Gerçek tarayıcı motoru gerekiyorsa | JSDOM gereken özelliği sağlamıyorsa kullan |
| **Cypress Component Testing** | Gerçek tarayıcı, Cypress zaten kullanılıyorsa | Playwright CT'ye alternatif |

Birini seç. Net bir alan ayrımın yoksa aynı repo'da hem RTL + Vitest hem de Playwright CT çalıştırma. Bu projede component testleri Vitest + RTL, tam akışlar Playwright E2E'dir; Playwright CT eklemek plan kararıdır.

## Query önceliği

React Testing Library query'leri üç katmanda sunar; yukarıdan aşağı kullan:

1. **Herkes için erişilebilir**: `getByRole`, `getByLabelText`, `getByPlaceholderText`, `getByText`, `getByDisplayValue`
2. **Semantic**: `getByAltText`, `getByTitle`
3. **Test ID'leri (kaçış kapısı)**: `getByTestId`

```tsx
// En iyisi
screen.getByRole('button', { name: 'Aramayı başlat' })

// Input'lar için uygun
screen.getByLabelText('Arama metni')

// Son çare
screen.getByTestId('search-hit-score')
```

Proje önceliği `getByRole` > `getByLabelText` > `getByText` > `getByTestId`'dir; `getByPlaceholderText` kullanılmaz (placeholder label yerine geçmez).

Varyantlar:

- `getBy*` - eşleşme yoksa throw eder
- `queryBy*` - `null` döner ("yokluğunu assert et" için kullan)
- `findBy*` - async, Promise döner (async işten sonra görünen elemanlar için kullan)

## `userEvent` ile kullanıcı etkileşimi

```tsx
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryInput } from './QueryInput.tsx'

it('givenQueryWithExtraSpaces_whenSubmitted_thenCallsOnSubmitWithNormalizedQuery', async () => {
  // Arrange
  const user = userEvent.setup()
  const onSubmit = vi.fn()
  render(<QueryInput submitLabel="Aramayı başlat" onSubmit={onSubmit} />)

  // Act
  await user.type(screen.getByLabelText('Arama metni'), '  şifremi   unuttum ')
  await user.click(screen.getByRole('button', { name: 'Aramayı başlat' }))

  // Assert
  expect(onSubmit).toHaveBeenCalledWith({ query: 'şifremi unuttum' })
})
```

- userEvent çağrılarını her zaman `await` et
- Her testte `userEvent.setup()`'ı bir kez çağır, dönen `user`'ı yeniden kullan
- `userEvent` gerçek bir tarayıcı olay dizisini simüle eder; `fireEvent` tek bir sentetik event gönderir. `userEvent`'i tercih et

## Async pattern'ler

```tsx
// Async işten sonra görünen eleman
expect(await screen.findByText('Şifre sıfırlama adımları')).toBeInTheDocument()

// Side effect assertion'ı
await waitFor(() => expect(onSearched).toHaveBeenCalled())

// Kaybolması gereken eleman
await waitForElementToBeRemoved(() => screen.queryByText('Yükleniyor…'))
```

Asla `setTimeout` + assertion kullanma; flaky olur. Yukarıdaki matcher'ları kullan.

## MSW ile network mocking

Mock Service Worker network katmanında mock'lar. Component, hook'lar ve fetch kütüphanesi production'dakiyle birebir aynı davranır.

### Kurulum

```ts
// src/test/handlers.ts
import { http, HttpResponse } from 'msw'

export const handlers = [
  http.get('/api/workspaces/:workspaceId/sources/:sourceId', ({ params }) =>
    HttpResponse.json({
      sourceId: Number(params.sourceId),
      workspaceId: Number(params.workspaceId),
      name: 'Yardım merkezi',
      kind: 'WEB_CRAWL',
      version: null,
      chunkCount: 742,
      status: 'READY',
      syncedAt: '2026-10-12T11:31:40Z',
      fileName: null,
      createdAt: '2026-10-12T11:31:40Z',
      latestJob: null,
    }),
  ),
  http.post('/api/workspaces/:workspaceId/search', () =>
    HttpResponse.json({
      query: 'şifremi unuttum',
      mode: 'VECTOR',
      // ...filterStrategy, efSearch, limit, returnedCount, sayılar ve timings (tam şekil: src/test/fixtures.ts, openapi.yaml)
      hits: [
        { rank: 1, chunkId: 12, chunkNo: 3, sourceId: 301, sourceName: 'Yardım merkezi', heading: 'Şifre sıfırlama',
          content: 'Şifre sıfırlama adımları', matchedBy: ['SEMANTIC'], keywordRank: null, keywordScore: null,
          vectorRank: 1, distance: 0.1269, rrfScore: null, matchedTerms: [] },
      ],
    }),
  ),
]

// src/test/problem.ts — backend'in gerçek hata response'u gibi: Problem Details body + application/problem+json (problem-details-rfc9457)
import { HttpResponse } from 'msw'

export function problem(status: number, errorCode: string) {
  return HttpResponse.json(
    {
      type: `https://embedding-pgvector.example/errors/${errorCode.toLowerCase().replaceAll('_', '-')}`,
      title: 'Error',
      status,
      detail: 'Test error',
      errorCode,
    },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  )
}

// src/test/server.ts
import { setupServer } from 'msw/node'
import { handlers } from './handlers.ts'

export const server = setupServer(...handlers)

// src/test/setup.ts (vite.config.ts → test.setupFiles)
import '@testing-library/jest-dom/vitest'
import { server } from './server.ts'

beforeAll(() => server.listen({ onUnhandledFrame: 'error' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())
```

Hata response'u her zaman `problem(status, errorCode)` ile üretilir (`http.post(..., () => problem(409, 'SOURCE_LIMIT_REACHED'))`); status ve `application/problem+json` header'ı backend ile aynıdır, `httpClient`'ın gerçek hata yolu test edilir. `VALIDATION_FAILED` için `violations` gereken testte body'yi aynı şekilde elle genişlet.

Mock'lanmamış herhangi bir request testi yüksek sesle düşürsün diye `onUnhandledFrame: 'error'` ayarla; sessizce geçen testler kırmızıdan daha kötüdür. (msw 3'te seçeneğin adı `onUnhandledFrame`'dir; msw 2'deki `onUnhandledRequest` artık yok.)

Handler'lardaki path ve response şekilleri `docs/api/openapi.yaml` sözleşmesiyle birebir aynı olmalı. Hata response'ları için backend'in RFC 9457 Problem Details formatını (`errorCode` alanı dahil) kullan.

### Test bazında override

```tsx
it('givenServerError_whenSourceDetailPageLoads_thenShowsFallbackMessage', async () => {
  // Arrange
  server.use(http.get('/api/workspaces/:workspaceId/sources/:sourceId', () => problem(500, 'INTERNAL_ERROR')))

  // Act
  renderWithProviders(<SourceDetailPage />, { route: '/workspaces/42/sources/301' })

  // Assert
  expect(await screen.findByRole('alert')).toHaveTextContent('Beklenmeyen bir hata oluştu. Tekrar dene.')
})
```

## Provider sarmalama

Provider'ları `src/test/renderWithProviders.tsx` içinde bir kez sar (production'daki `AppProviders` ile aynı provider'lar; tema provider'ı yok, stil CSS Modules ile):

```tsx
// src/test/renderWithProviders.tsx
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, type RenderOptions } from '@testing-library/react'
import type { ReactElement } from 'react'
import { MemoryRouter } from 'react-router'

type RenderWithProvidersOptions = RenderOptions & { route?: string }

export function renderWithProviders(ui: ReactElement, { route = '/', ...options }: RenderWithProvidersOptions = {}) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[route]}>{ui}</MemoryRouter>
    </QueryClientProvider>,
    options,
  )
}
```

Test dosyası `renderWithProviders`'ı göreli path ve uzantıyla, `screen`'i doğrudan `@testing-library/react`'ten import eder (`import { renderWithProviders } from '../../../test/renderWithProviders.tsx'`). Re-export eden `test-utils` barrel'ı açılmaz (`frontend-conventions` bölüm 4).

## Custom hook testi

```tsx
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, renderHook, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'

it('givenInitialCount_whenIncrement_thenCountIncreasesByOne', () => {
  // Arrange
  const { result } = renderHook(() => useCounter(0))

  // Act
  act(() => result.current.increment())

  // Assert
  expect(result.current.count).toBe(1)
})

it('givenExistingSource_whenUseSourceQuery_thenReturnsSource', async () => {
  // Arrange
  // QueryClient wrapper'ın DIŞINDA, test başına bir kez oluşturulur; wrapper içinde oluşturmak her render'da cache'i sıfırlar.
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  )

  // Act
  const { result } = renderHook(() => useSourceQuery(42, 301), { wrapper })

  // Assert
  await waitFor(() => expect(result.current.isSuccess).toBe(true))
  expect(result.current.data?.status).toBe('READY')
})
```

- State değiştiren çağrıları `act` ile sar
- Sadece hook'un public API'si üzerinden test et
- Context kullanan hook'lar için bir `wrapper` geçir

## Accessibility assertion'ları

Projede `vitest-axe` kullanılır (`jest-axe` değil). Matcher bir kez `src/test/setup.ts`'te kaydedilir:

```ts
// src/test/setup.ts
import * as axeMatchers from 'vitest-axe/matchers'
import 'vitest-axe/extend-expect'

expect.extend(axeMatchers)
```

```tsx
import { axe } from 'vitest-axe'

it('givenSearchForm_whenRendered_thenHasNoAccessibilityViolations', async () => {
  // Arrange
  const { container } = renderWithProviders(<SearchForm workspaceId={42} />)

  // Act
  const results = await axe(container)

  // Assert
  expect(results).toHaveNoViolations()
})
```

`vitest-axe/extend-expect` tip tanımlarını ekler; setup dosyası `tsconfig`'in `include` kapsamında olmalıdır. Matcher tipleri Vitest sürümüyle uyuşmazsa matcher yerine doğrudan `expect(results.violations).toEqual([])` yaz.

Her etkileşimli component için component testlerinde axe çalıştır. Şunları yakalar:

- Form input'larında eksik label'lar
- Geçersiz ARIA kullanımı
- Zayıf renk kontrastı (sınırlı; JSDOM'da gerçek bir CSS motoru yoktur, bu yüzden sadece inline style'larda çalışır; görsel kontrast Playwright'a aittir)
- Görsellerde eksik alt text
- Heading sırası ihlalleri

Daha geniş a11y rehberi için `frontend-a11y` skill'ine bak.

## Snapshot testleri ne zaman KULLANILMAZ

Render edilmiş çıktının snapshot'ları:

- Her stil değişikliğinde kırılır
- Review sırasında düşünülmeden onaylanır
- Davranışı değil implementation detayını (DOM yapısı) test eder

Kabul edilebilir snapshot kullanımları:

- Saf veri serialization function'ları (`formatInvoice(invoice)` -> stabil string)
- Üretilen config dosyaları

Projede component snapshot testi yazılmaz (`frontend-conventions` bölüm 11). Görsel regresyon gerekirse Playwright screenshot karşılaştırması kullanılır; bu bir plan kararıdır.

## Playwright / Cypress'e ne zaman uzanılır

JSDOM (Vitest/Jest'in kullandığı) şunları yapamaz:

- Gerçek layout render etmek (flexbox, grid, viewport query'leri)
- Native tarayıcı animasyonlarını, CSS transition'larını çalıştırmak
- Scroll davranışını, drag-and-drop'u, clipboard'dan yapıştırmayı test etmek
- iframe, popup, download ve cross-origin akışları yönetmek
- Tam DevTools desteğiyle kontrollü bir ortamda gerçek network çalıştırmak

Bunlardan herhangi biri için Playwright Component Testing (gerçek tarayıcıda component testi) ya da tam E2E kullan. Bkz. `e2e-testing` skill'i.

Karar sınırı:

- Bir hook, bir presentational component, mantık içeren bir form -> RTL
- Layout'u önemli olan ya da JSDOM'da olmayan tarayıcı API'lerini kullanan bir component -> Playwright CT
- Birden fazla sayfayı kapsayan tam bir kullanıcı akışı (ör. kaynak ekleyip bir sekmede canlı ingestion ilerlemesini izleme, ardından lab'da arama) -> Playwright E2E

## Coverage hedefleri

Bağlayıcı eşik `frontend-conventions` bölüm 11'dedir: toplamda satır bazında en az %80 (aşağıdaki `thresholds`). Katman tablosu nerede daha fazla test yazılacağını yönlendirmek içindir, ayrı bir eşik değildir.

| Katman | Hedef |
|---|---|
| Saf utility'ler | >=90% |
| Custom hook'lar | >=85% |
| Presentational component'ler | >=80% - satır değil, davranış |
| Container component'ler | >=70% - ana akışlar + hata durumları |
| Sayfalar | E2E ile ayrıca kapsanır; minimum smoke test |

`vite.config.ts` içindeki `test` bloğuyla yapılandır (ayrı `vitest.config.ts` açılmaz):

```ts
// vite.config.ts (Vitest ayrı bir vitest.config.ts olmadan Vite config'ini okur)
/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html', 'lcov'],
      thresholds: {
        lines: 80,
        functions: 80,
        branches: 70,
        statements: 80,
      },
    },
  },
})
```

`coverage.provider: 'v8'` için `@vitest/coverage-v8` dev dependency'si gerekir (Vitest ile aynı sürüm). `globals: true` kullanılıyorsa `tsconfig.app.json`'daki `types`'a `vitest/globals` eklenir; aksi halde `describe`, `it`, `expect`, `vi` her test dosyasında `vitest`'ten import edilir.

## Anti-pattern'ler

- `container.querySelector("...")` - accessibility query'lerini atlar, gerçek kullanıcıların başarısız olacağı yerde testlerin geçmesine izin verir
- Render sayısını assert etmek - implementation detayı
- `vi.mock("react", ...)` - React'i asla mock'lama. Bunun yerine component'i refactor et
- Child component'leri varsayılan olarak mock'lamak - izolasyonu değil entegrasyonu test eder. Sadece child'ın ağır side effect'leri varsa mock'la
- `act()` uyarılarını görmezden gelmek - gerçek bug'lara işaret ederler (unmount sonrası state güncellemesi, eksik async sarmalama)
- Testler arasında mutable state paylaşmak - test sırası değişince flaky olur
- `it.skip()` kaldırıldığında da geçen testler - testin sandığın şeyi assert etmiyor

## TDD iş akışı

```
RED      -> Bir sonraki gereksinim için başarısız bir test yaz
GREEN    -> Testi geçirecek minimum component kodunu yaz
REFACTOR -> Component'i iyileştir, testler yeşil kalsın
REPEAT   -> Sonraki gereksinim
```

Yeni component'ler için:

1. Component'in prop tipini ve imzasını tanımla
2. En basit durum için ilk testi yaz
3. Doğru sebeple başarısız olduğunu doğrula
4. Geçmesine yetecek kadarını implement et
5. Sonraki test senaryosunu ekle
6. Üçüncü benzer test bir pattern ortaya çıkardığında refactor et

## Test komutları

```bash
npx vitest                            # watch
npx vitest run                        # tek seferlik
npx vitest run --coverage             # coverage ile
npx vitest run path/to/file.test.tsx  # tek dosya

# CI modu
CI=true npx vitest run --coverage
```

## İlgili skill'ler

- `react-patterns` - test edilen component'lerin yazım pattern'leri
- `frontend-a11y` - accessibility kuralları
- `e2e-testing` - Playwright E2E
- `webapp-testing` - çalışan uygulamayı tarayıcıda hızlıca doğrulama

## Örnekler

### MSW ve userEvent ile form gönderimi

```tsx
it('givenWorkspaceAtSourceLimit_whenCreateSubmitted_thenShowsSourceLimitMessage', async () => {
  // Arrange
  server.use(http.post('/api/workspaces/:workspaceId/sources', () => problem(409, 'SOURCE_LIMIT_REACHED')))
  const user = userEvent.setup()
  renderWithProviders(<CreateSourceForm workspaceId={42} />)

  // Act
  await user.type(screen.getByLabelText('Kaynak adı'), 'Fatura SSS')
  await user.click(screen.getByRole('button', { name: 'Kaynağı ekle' }))

  // Assert
  expect(await screen.findByRole('alert')).toHaveTextContent('Bu çalışma alanı kaynak sınırına ulaştı.')
})
```

### Error boundary testi

```tsx
function BrokenComponent(): never {
  throw new Error('boom')
}

it('givenChildThrows_whenRendered_thenShowsFallback', () => {
  // Arrange
  // Beklenen throw için React'in console.error gürültüsünü bastır; spy finally'de geri yüklenir ki başka testlere sızmasın.
  const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {})

  try {
    // Act
    render(
      <ErrorBoundary fallback={<p role="alert">Beklenmeyen bir hata oluştu. Tekrar dene.</p>}>
        <BrokenComponent />
      </ErrorBoundary>,
    )

    // Assert
    expect(screen.getByRole('alert')).toBeInTheDocument()
  } finally {
    errorSpy.mockRestore()
  }
})
```

`ErrorBoundary` `react-error-boundary` paketindendir.

### Suspense boundary testi

```tsx
it('givenSlowSourceQuery_whenRendered_thenShowsLoadingThenSourceName', async () => {
  // Act
  renderWithProviders(
    <Suspense fallback={<p>Yükleniyor…</p>}>
      <SourceDetails workspaceId={42} sourceId={301} />
    </Suspense>,
  )

  // Assert
  expect(screen.getByText('Yükleniyor…')).toBeInTheDocument()
  expect(await screen.findByText('Yardım merkezi')).toBeInTheDocument()
})
```
