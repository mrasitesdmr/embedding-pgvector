---
name: frontend-conventions
description: Embedding ve pgvector projesinin frontend'i için bağlayıcı proje kuralları - kod dili (İngilizce) ve UI metni (Türkçe), yorum politikası, feature-first klasör yapısı, component/hook/dosya/type isimlendirmesi, domain sözlüğü (workspace, source, chunk, search), TypeScript kuralları, API katmanı ve errorCode → Türkçe mesaj eşlemesi, içe alma (ingestion) ilerlemesinin polling ile izlenmesi (WebSocket gelirse STOMP), arama ayarlarının tarayıcıda saklanması, form validation, stil (CSS Modules + design token) ve test isimlendirmesi (given_when_then, AAA). frontend/ altında herhangi bir TypeScript/React kodu veya test yazarken, isimlendirmeye ya da dosyanın nereye konacağına karar verirken, backend'e çağrı eklerken veya kod review yaparken kullan. Diğer skill'lerdeki örneklerle çelişirse bu skill geçerlidir.
---

# Frontend Proje Kuralları

Bu kurallar diğer tüm frontend skill'lerinin (`react-patterns`, `react-testing`, `react-best-practices` vb.) örneklerinden önce gelir. Backend tarafındaki karşılığı `backend-conventions`'tır; ortak kavramlar (domain sözlüğü, `errorCode`, pagination, test isimlendirmesi) iki tarafta aynıdır.

## 1. Teknoloji yığını

| Konu | Seçim | Not |
|------|-------|-----|
| Build | Vite 8 | `vite-patterns` |
| UI | React 19 | Function component + hook. Class component yok (Error Boundary için `react-error-boundary`) |
| Dil | TypeScript 6, `strict` (TS 6'da varsayılan) | `strict` veya alt bayrakları kapatılmaz |
| Lint | oxlint (`npm run lint`) | Uyarı bırakılmaz |
| Server state | TanStack Query | `useEffect` + `fetch` yok |
| Client state | `useState` / `useReducer` / Context | Global store (Zustand) sadece gerçek ihtiyaçta ve planda onaylanmışsa |
| Routing | React Router (library/declarative mode) | |
| HTTP | Native `fetch`, `shared/api/httpClient.ts` üzerinden | axios yok |
| WebSocket | `@stomp/stompjs`, native WebSocket | SockJS yok; sadece canlı güncelleme gereken task'ta kurulur |
| Stil | CSS Modules + `src/index.css` içinde CSS custom property token'ları | UI kütüphanesi (MUI vb.) ve Tailwind yok |
| Test | Vitest (jsdom) + React Testing Library + user-event + `@testing-library/jest-dom` + MSW + vitest-axe | `react-testing` |
| E2E | Playwright | `e2e-testing` |

Listede olmayan bir runtime dependency (ör. vektörleri 2D'de çizmek için bir grafik kütüphanesi) eklemek plan kararıdır; coder kendi başına eklemez. Henüz kurulmamış bir kütüphane, onu gerektiren ilk task'ta kurulur.

## 2. Dil

- Tüm kod İngilizce: dosya, component, hook, değişken, type, sabit, CSS class adı, test adı, `data-testid`, console mesajı.
- Kullanıcıya gösterilen her metin Türkçe: label, buton, placeholder, hata mesajı, boş durum metni, `aria-label`, sayfa başlığı. Yerleşik teknik terimler (embedding, vector, chunk, recall, HNSW, IVFFlat, RRF) anlatım sayfalarındaki gibi İngilizce bırakılabilir; aynı terim her yerde aynı yazılır.
- Domain sözlüğü backend ile aynıdır: çalışma alanı (kiracı) → `workspace`, bilgi kaynağı → `source`, kaynak türü → `sourceKind`, kaynak durumu → `sourceStatus`, parça → `chunk`, embedding → `embedding`, embedding modeli → `embeddingModel`, içe alma işi → `ingestionJob`, arama sorgusu → `query`, arama modu → `searchMode`, arama sonucu → `searchHit`, benzerlik skoru → `score`, isabet oranı → `recall`, konu anlatım sayfası → `lesson`.
- Backend'in `detail` alanı (İngilizce) kullanıcıya **asla** gösterilmez. Türkçe metin her zaman `errorCode`'dan üretilir (bölüm 7).

### UI metni yazımı

- Sentence case: "Kaynak ekle", "Bilgi bankasında ara" (her kelime büyük harfle değil).
- Buton tam olarak ne olacağını söyler: "Gönder" değil "Aramayı başlat", "Ekle" değil "Kaynağı ekle". Aynı eylem akış boyunca aynı adı taşır ("Kaynağı ekle" butonu → "Kaynak eklendi").
- Hata mesajı ne olduğunu ve gerekiyorsa ne yapılacağını söyler, özür dilemez: "Kaynak bulunamadı. Listeyi yenile."
- Hitap "sen" ile: "Aramak istediğin metni yaz".

## 3. Yorum politikası

- Component, hook ve function üstüne yorum **yazılmaz**: JSDoc (`/** ... */`) yok, `//` ile açıklama yok.
- Kod kendini isimlerle anlatır. Açıklama gerekiyorsa önce yeniden adlandır ya da küçük bir function/component'e ayır.
- Gövde içinde yorum sadece koddan çıkarılamayan bir **neden** için ve tek satır (ör. bir tarayıcı workaround'unun sebebi).
- Testlerdeki `// Arrange`, `// Act`, `// Assert` işaretleri istisnadır (bölüm 11).
- Yorum satırına alınmış kod, `console.log` ve `debugger` bırakılmaz.

## 4. Klasör yapısı (feature-first)

```
frontend/
├── e2e/                         Playwright testleri (e2e-testing)
├── public/
└── src/
    ├── main.tsx                 Giriş noktası; sadece render + provider'lar
    ├── index.css                Design token'ları (:root) ve global reset
    ├── app/
    │   ├── App.tsx              Router tanımı
    │   ├── AppProviders.tsx     QueryClientProvider vb.
    │   └── WorkspaceLayout.tsx  Başlık, sekmeler, ToastProvider; çalışma alanı sayfalarının iskeleti
    ├── features/
    │   ├── search/              Arama ve arama kayıtları
    │   │   ├── api/
    │   │   │   ├── searchApi.ts         REST çağrıları (saf async function'lar)
    │   │   │   └── searchQueries.ts     searchKeys + useXQuery / useXMutation hook'ları
    │   │   ├── components/
    │   │   │   ├── SearchForm.tsx
    │   │   │   ├── SearchForm.module.css
    │   │   │   └── SearchForm.test.tsx
    │   │   ├── hooks/
    │   │   ├── pages/
    │   │   │   ├── SearchPage.tsx
    │   │   │   └── SearchEventsPage.tsx
    │   │   └── model/
    │   │       ├── types.ts             Bu feature'ın DTO type'ları
    │   │       ├── searchRules.ts       Validation sabitleri ve function'ları
    │   │       └── searchSettings.ts    Arama ayarlarının localStorage'da saklanması (bölüm 9)
    │   └── source/              Kaynak listesi, detayı, yükleme diyalogları ve içe alma ilerlemesi
    │       └── (aynı iç yapı)   ör. model/ingestionRules.ts, model/uploadRules.ts, hooks/useSourceIngestion.ts
    ├── shared/
    │   ├── api/                 httpClient.ts, ApiError.ts, errorMessages.ts, pagination.ts
    │   ├── components/          Feature'dan bağımsız UI parçaları (Dialog, ErrorAlert, StatusBadge, Pagination)
    │   ├── format/              Sayı ve tarih biçimleme (tr-TR)
    │   ├── toast/               ToastProvider.tsx, useToast.ts
    │   ├── workspace/           Çalışma alanı type'ları, API'si, useCurrentWorkspace, DEFAULT_WORKSPACE_ID
    │   └── websocket/           (ilk WebSocket task'ında açılır) stompClient.ts, useStompSubscription.ts
    └── test/                    setup.ts, server.ts (MSW), handlers.ts, problem.ts, renderWithProviders.tsx
```

- Feature klasörleri: `search` ve `source`. İçe alma (yükleme diyalogları, ilerleme, iş durumu polling'i) `source` feature'ının içindedir: diyaloglar kaynak ekranlarında açılır ve iş bitince `sourceKeys` invalidate edilir; ayrı bir `ingestion` feature'ı `source`'un type'larını ve key'lerini import edemezdi (TASK-004 D2). Konu anlatım sayfaları kökteki HTML'lerden taşınınca sadece frontend'de olan `lesson` feature'ı açılır.
- Feature'ın iç yapısı her zaman `api/`, `components/`, `hooks/`, `pages/`, `model/`'dir. Feature kökünde dosya durmaz. `model/` backend'deki `domain` paketinin karşılığıdır: type'lar, iş kuralları ve UI'dan bağımsız saf function'lar (ör. skoru yüzdeye çeviren formatter). İhtiyacı olmayan klasör boş olarak açılmaz.
- `shared/` birden fazla feature'ın ya da `app/`'in kullandığı kodu tutar. `errorCode` → Türkçe mesaj `shared/api/errorMessages.ts`'tedir (backend `exception`). WebSocket gelince STOMP kodu `shared/websocket/`'e konur (backend `websocket`).
- Bir feature başka bir feature'ın içinden import etmez. Ortak ihtiyaç `shared/`'a taşınır. `shared/` hiçbir feature'ı import etmez.
- Barrel dosyası (`index.ts` ile re-export) açılmaz; doğrudan dosyadan import et (`react-best-practices` / `bundle-barrel-imports`).
- Import'lar göreli path ile ve dosya uzantısıyla yapılır (`./SearchForm.tsx`, şablondaki `allowImportingTsExtensions` ile uyumlu).
- Test dosyası test ettiği dosyanın yanında durur (`X.test.tsx`). `__tests__` klasörü yok.

## 5. İsimlendirme

### Dosyalar ve export'lar

| Rol | Dosya adı | Export | Örnek |
|-----|-----------|--------|-------|
| Component | `PascalCase.tsx` | Aynı adlı named export | `SearchForm.tsx` → `export function SearchForm` |
| Sayfa | `<Name>Page.tsx` | named | `SearchPage`, `SourceDetailPage` |
| Hook | `use<Name>.ts` | named | `useIngestionProgress.ts` |
| CSS Module | `<Component>.module.css` | - | `SearchForm.module.css` |
| REST modülü | `<feature>Api.ts` | function'lar | `searchApi.ts` |
| Query modülü | `<feature>Queries.ts` | `<feature>Keys` + hook'lar | `searchQueries.ts` |
| Type'lar | `model/types.ts` | named type'lar | |
| Kural/sabit | `model/<feature>Rules.ts` | named | `searchRules.ts` |
| Test | `<Dosya>.test.ts(x)` | - | `searchRules.test.ts` |

- **Default export yok** (named export'lar yeniden adlandırmayı ve arama yapmayı güvenli kılar). `React.lazy` gerektiğinde `lazy(() => import('./SearchPage.tsx').then(m => ({ default: m.SearchPage })))`. Tek istisna, aracın default export beklediği config dosyalarıdır (`vite.config.ts`, `playwright.config.ts`): `export default defineConfig(...)`.
- Dosya başına bir export edilen component. Sadece o dosyada kullanılan küçük yardımcı component'ler aynı dosyada, top-level'da tanımlanabilir (component içinde component tanımlanmaz). İstisna: compound component parçaları (`Tabs`, `TabsList`, `TabsPanel`) tek dosyada birlikte named export edilir (`react-composition-patterns`).

### Kod içi isimler

| Anlam | Kalıp | Örnek |
|-------|-------|-------|
| Component | PascalCase isim | `SearchHitList`, `SourceStatusBadge` |
| Props type'ı | `<Component>Props` | `SearchHitListProps` |
| Callback prop'u | `on<Event>` | `onSearched`, `onSubmit` |
| Handler function'ı | `handle<Event>` | `handleSubmit`, `handleModeChange` |
| Boolean | `is`, `has`, `can`, `should` | `isReady`, `hasHits`, `canSearch` |
| REST function'ı | Backend service metodu ile aynı fiil | `createSource`, `getSource`, `listSources`, `searchChunks` |
| Query hook'u | `use<Noun>Query` | `useSourceQuery(workspaceId, sourceId)` |
| Mutation hook'u | `use<Verb><Noun>Mutation` | `useCreateSourceMutation()`, `useSearchChunksMutation()` |
| Query key factory | `<feature>Keys` | `sourceKeys.detail(workspaceId, sourceId)` |
| Sabit | `UPPER_SNAKE_CASE` | `QUERY_MAX_LENGTH`, `SEARCH_LIMIT_MAX` |
| CSS Module class'ı | camelCase | `styles.hitList`, `styles.isActive` |
| `data-testid` | kebab-case, son çare | `search-hit-score` |

- Kısaltma yok: `source` (`src` değil), `query` (`q` değil), `event` (`e` sadece tek satırlık inline handler'da kabul).
- Koleksiyonlar çoğul (`hits`, `sources`), id'ler `<noun>Id` (`sourceId`, `chunkId`).
- Magic number yok; kural değerleri `<feature>Rules.ts`'te sabit olur.
- Route path'leri `lab.html`'deki adresler gibi Türkçe ve kebab-case'tir; kullanıcının adres çubuğunda gördüğü metin olduğu için bölüm 2'deki "UI metni Türkçe" kuralına girer (TASK-003 D10). Çalışma alanı id'si adrestedir:
  - `/` → `/w/42/arama` yönlendirmesi (`DEFAULT_WORKSPACE_ID`), `/w/:workspaceId` → `arama`
  - `/w/:workspaceId/arama`, `/w/:workspaceId/arama-kayitlari`, `/w/:workspaceId/kaynaklar`, `/w/:workspaceId/kaynaklar/:sourceId` (`?parca=<chunkId>`)
  - Bilinmeyen adres → "Sayfa bulunamadı". Konu anlatım sayfaları taşınınca adresleri aynı biçimde planda belirlenir.
- Route parametreleri ve kod içi isimler yine İngilizcedir (`:workspaceId`, `:sourceId`); sadece sabit path segmentleri Türkçedir.

## 6. TypeScript

- `any` yok. Bilinmeyen veri (`response.json()`, STOMP `message.body`) `unknown` olarak alınır ve tipine daraltılır.
- Non-null assertion (`!`) sadece `main.tsx`'teki `getElementById('root')!` gibi garanti edilen yerlerde.
- `enum` ve constructor parameter property kullanılmaz (`erasableSyntaxOnly` açık). Yerine string literal union:

```ts
export const SEARCH_MODES = ['KEYWORD', 'VECTOR', 'HYBRID'] as const
export type SearchMode = (typeof SEARCH_MODES)[number]
```

- Enum değerleri backend'deki gibi `UPPER_SNAKE_CASE` string'lerdir.
- Object şekilleri ve props için `type` kullanılır (`interface` yok); tutarlılık için tek biçim. Diğer skill'lerdeki `interface` örnekleri projede `type` olarak yazılır.
- Biçim Vite şablonuyla aynıdır: 2 boşluk girinti, tek tırnak, noktalı virgül yok, çok satırlı listelerde sondaki virgül. Diğer skill'lerdeki çift tırnaklı / noktalı virgüllü örnekler bu biçime çevrilerek yazılır.
- Type-only import'lar `import type { SearchResultResponse } from '../model/types.ts'` (`verbatimModuleSyntax` açık).
- API type'ları backend DTO adlarıyla **birebir aynıdır** ve `docs/api/openapi.yaml`'a uyar: `SourceResponse`, `IngestionJobResponse`, `ChunkResponse`, `SearchChunksRequest`, `SearchResultResponse`, `SearchHitResponse`, `SearchEventResponse`, `WorkspaceResponse`. Multipart istekler (dosya yükleme) için frontend'e özel girdi type'ı `<Verb><Noun>Input` adını taşır (`CreateSourceInput`, `UploadSourceVersionInput`) ve API function'ında `FormData`'ya çevrilir. Alan adları camelCase, tarih alanları ISO-8601 `string`.

## 7. API katmanı ve hata gösterimi

### Katmanlar

```
Component  →  use<X>Query / use<X>Mutation (searchQueries.ts)  →  searchApi.ts  →  shared/api/httpClient.ts  →  fetch
```

- Component `fetch`'i ya da `searchApi`'yi doğrudan çağırmaz; query/mutation hook'u kullanır.
- `searchApi.ts` saf async function'lardır, React bilmez; böylece MSW ile ve tek başına test edilebilir.
- Arama bir mutation'dır (`POST .../search`, `backend-conventions` bölüm 3); sonuç ekrandaki son aramayı temsil eder, query cache'inde sorgu metnine göre saklanmaz.
- Path'ler göreli: `/api/workspaces/${workspaceId}/sources`, `/api/workspaces/${workspaceId}/search`. Dev'de Vite proxy, production'da reverse proxy yönlendirir (`vite-patterns`). Base URL kodda sabitlenmez.

### httpClient ve ApiError

```ts
// shared/api/ApiError.ts
export type Violation = { field: string; message: string }

export class ApiError extends Error {
  readonly status: number
  readonly errorCode: string
  readonly violations: Violation[]

  constructor(status: number, errorCode: string, violations: Violation[] = []) {
    super(errorCode)
    this.name = 'ApiError'
    this.status = status
    this.errorCode = errorCode
    this.violations = violations
  }
}
```

```ts
// shared/api/httpClient.ts
const UNEXPECTED_ERROR = 'UNEXPECTED_ERROR'
const NETWORK_ERROR = 'NETWORK_ERROR'

export async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const headers = new Headers(init?.headers)
  const body = init?.body
  if (body !== undefined && body !== null && !(body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  let response: Response
  try {
    response = await fetch(path, { ...init, headers })
  } catch {
    throw new ApiError(0, NETWORK_ERROR)
  }
  if (!response.ok) {
    throw await toApiError(response)
  }
  if (response.status === 204) {
    return undefined as T
  }
  try {
    return (await response.json()) as T
  } catch {
    throw new ApiError(response.status, UNEXPECTED_ERROR)
  }
}
```

- Gövde `FormData` ise `Content-Type` yazılmaz; tarayıcı `multipart/form-data; boundary=…` başlığını kendisi koyar (TASK-004 D10). Dosya yüklemesi için ayrı bir istek function'ı açılmaz, hata eşlemesi tek yerde kalır.

- `toApiError`, `application/problem+json` body'sinden `errorCode` ve `violations`'ı okur; body yoksa, parse edilemezse ya da `errorCode` içermiyorsa `errorCode: 'UNEXPECTED_ERROR'` kullanır. Backend framework hataları için de `errorCode` döner (`BAD_REQUEST`, `METHOD_NOT_ALLOWED`...); bunların Türkçe karşılığı yoksa fallback mesajı gösterilir.
- Backend hata formatı RFC 9457 Problem Details'tir: `type`, `title`, `status`, `detail`, `instance`, `errorCode`, validation'da `violations[]` (bkz. `problem-details-rfc9457`).

### errorCode → Türkçe mesaj

Tüm eşleme tek dosyadadır: `shared/api/errorMessages.ts`. Component içinde hata metni yazılmaz.

```ts
const ERROR_MESSAGES: Record<string, string> = {
  SOURCE_NOT_FOUND: 'Kaynak bulunamadı. Listeyi yenile.',
  SOURCE_LIMIT_REACHED: 'Bu çalışma alanı kaynak sınırına ulaştı. Yeni kaynak eklemeden önce birini sil.',
  SOURCE_NAME_TAKEN: 'Bu ad çalışma alanında başka bir kaynakta kullanılıyor.',
  INGESTION_IN_PROGRESS: 'Kaynak hâlâ işleniyor. Bitince tekrar dene.',
  EMBEDDING_UNAVAILABLE: 'Embedding servisine şu an ulaşılamıyor. Biraz sonra tekrar dene.',
  VALIDATION_FAILED: 'Girdiğin bilgileri kontrol et.',
  NETWORK_ERROR: 'Sunucuya ulaşılamıyor. Bağlantını kontrol et.',
}

const FALLBACK_MESSAGE = 'Beklenmeyen bir hata oluştu. Tekrar dene.'

export function messageFor(errorCode: string): string {
  return ERROR_MESSAGES[errorCode] ?? FALLBACK_MESSAGE
}
```

- Backend'e yeni bir `errorCode` eklendiğinde aynı task içinde buraya da Türkçe karşılığı eklenir; MSW handler'ı ve testi de.
- Bir alana ait hatalar o input'un altında gösterilir ve input'a `aria-describedby` + `aria-invalid` ile bağlanır (`frontend-a11y`):

| `errorCode` | Alan | Metin kaynağı |
|-------------|------|---------------|
| `VALIDATION_FAILED` | `violations[].field` | Alanın kural mesajı (`QUERY_RULE_MESSAGE`, `SOURCE_NAME_RULE_MESSAGE` vb., bölüm 10); backend `message`'ı gösterilmez |
| `SOURCE_NAME_TAKEN` | `name` (kaynak ekleme formunda) | `messageFor('SOURCE_NAME_TAKEN')` |

- Input'a ait olmayan hatalar (ör. `SOURCE_LIMIT_REACHED`, `EMBEDDING_UNAVAILABLE`, `NETWORK_ERROR`, fallback) form üstünde `role="alert"` ile gösterilir.
- Component içinde Türkçe hata metni literal olarak yazılmaz: `errorCode` metinleri `errorMessages.ts`'ten, alan kuralı metinleri `<feature>Rules.ts`'ten gelir.
- Beklenmeyen hatalar `console.error` ile loglanır, kullanıcıya fallback mesajı gösterilir. Stack trace veya `detail` ekrana basılmaz.

### Pagination

Backend `PageResponse<T>` döndürür (`backend-conventions` bölüm 6). Frontend karşılığı `shared/api/pagination.ts`'tedir:

```ts
export type PageResponse<T> = {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export type PageParams = { page?: number; size?: number; sort?: string }
```

- `page` 0'dan başlar; ekranda gösterirken `page + 1`. `size` en fazla 100. `sort` biçimi `field,asc|desc`.
- Query key'e sayfa parametreleri dahil edilir: `sourceKeys.list(workspaceId, { page, size })`.
- Arama sonucu sayfalı değildir; `limit` (k) ile sınırlı düz listedir.

## 8. İçe alma ilerlemesi ve gerçek zamanlı iletişim

### Şu anki yöntem: polling (TASK-004)

Projede WebSocket yoktur. İçe alma ilerlemesi iş durumu sorgulanarak izlenir:

- Süren iş kaynağın `latestJob` alanından öğrenilir. Sayfa yenilense ya da adres yeni sekmede açılsa da ilerleme kaldığı yerden görünür.
- İş `GET /api/workspaces/{workspaceId}/ingestion-jobs/{jobId}` ile `sourceKeys.ingestionJob(workspaceId, jobId)` key'inde sorgulanır (`useIngestionJobsPolling`, `features/source/api/sourceQueries.ts`). `refetchInterval` iş aktifken `INGESTION_POLL_INTERVAL_MS` (1000 ms, `model/ingestionRules.ts`), `DONE` ya da `FAILED` olunca `false` döner; 4xx hatasında da durur. `initialData` olarak `latestJob` verilir, ilk istek beklenmez.
- Tek bir izleyici (`IngestionWatcherProvider`, `WorkspaceLayout`'ta) bütün aktif işleri sorgular. Böylece kullanıcı başka sayfaya geçse de iş bitince toast çıkar. Ekranlar aynı key'i `useIngestionJobQuery` ile istek atmadan okur.
- İş `DONE` olunca kaynak verisi invalidate edilir (liste, detay ve parçalar yeni sürümü gösterir), satır kısa süre vurgulanır ve toast çıkar. `FAILED` olunca sadece liste ve detay yenilenir; toast yoktur, neden satırda "Son yükleme başarısız: <neden>" olarak yazar. Toast sadece bu sekmede aktif görülmüş bir iş bitince çıkar; sayfa açılırken zaten bitmiş bulunan iş için çıkmaz.
- Aynı veri ayrıca `useState`'te ya da ikinci bir store'da tutulmaz.

### WebSocket gelirse (STOMP)

Bu bölüm, canlı güncellemeyi getiren ilk task için hedeftir; planı o task'ın planner'ı yazar ve `docs/api/websocket.md`'yi oluşturur. Canlı güncelleme sadece içe alma ilerlemesi gibi uzun süren işler içindir; arama isteği REST'tir. Sözleşme backend ile ortaktır (`spring-websocket`):

| Yön | Destination |
|-----|-------------|
| İstemci → sunucu | `/app/sources/{sourceId}/<action>` |
| Sunucu → kaynağı izleyenler | `/topic/sources/{sourceId}/<event>` (ör. `/topic/sources/301/ingestion`) |
| Sunucu → tek istemci | `/user/queue/<event>` (ör. `/user/queue/errors`) |

- Bağlantı adresi göreli: `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws`. Dev'de Vite proxy `/ws`'i `ws: true` ile backend'e yönlendirir.
- Heartbeat `10000/10000` (backend ile aynı), `reconnectDelay` 2000 ms.
- Uygulamada tek bir STOMP client vardır (`shared/websocket/stompClient.ts`); component'ler client oluşturmaz.
- Subscription'lar hook içinde kurulur ve cleanup'ta `unsubscribe()` edilir. StrictMode'da effect iki kez çalışır; hook buna dayanıklı olmalı (`react-best-practices` / `advanced-init-once`).
- Effect dependency'si sadece `sourceId`'dir; mesaj callback'leri `useEffectEvent` ile sarılır (`advanced-effect-event-deps`).
- Gelen mesaj `unknown` olarak parse edilir, şekli doğrulanır, sonra polling'in kullandığı **aynı** cache girdisine yazılır. Payload alanları `IngestionJobResponse`'un aynı adlı alanlarıyla aynı tiptir (`backend-conventions` bölüm 3):

```ts
queryClient.setQueryData<IngestionJobResponse>(sourceKeys.ingestionJob(workspaceId, message.jobId), (job) =>
  job ? { ...job, stage: message.stage, progress: message.progress, embeddedChunks: message.embeddedChunks, totalChunks: message.totalChunks } : job,
)
```

- İlerleme için yeni bir query key açılmaz; ayrı key'e yazılan STOMP verisi `useIngestionJobQuery`'yi okuyan ekrana hiç ulaşmaz. Cache'te henüz iş yoksa mesaj atlanır; ekran REST snapshot'ı ile dolar (bağlantı/yeniden bağlanma sırası: önce REST, sonra subscribe — `spring-websocket`). WebSocket gelince polling kaldırılır ya da sadece bağlantı koptuğunda yedek olarak çalışır; hangisi olacağına planner karar verir.
- İş `DONE` olunca `sourceKeys` invalidate edilir (polling'deki gibi).
- Aynı veri ayrıca `useState`'te tutulmaz; Zustand gibi ikinci bir store'a da yazılmaz.
- `/user/queue/errors`'tan gelen `ErrorMessage(errorCode, detail)` aynı `messageFor(errorCode)` ile gösterilir.
- Bağlantı durumu (bağlanıyor / bağlı / koptu) kullanıcıya gösterilir; koptuğunda "Bağlantı koptu, yeniden bağlanılıyor…" gibi `aria-live="polite"` bir mesaj.

## 9. Arama ayarları ve tarayıcıda saklanan veri

- Hesap ve oturum yoktur. Çalışma alanı URL'den gelir (`/w/:workspaceId/...`); varsayılan çalışma alanı `DEFAULT_WORKSPACE_ID` olarak `shared/workspace/workspaceRules.ts`'te durur.
- Kullanıcının arama ayarları (arama modu, filtre stratejisi, `ef_search`, sonuç sayısı vb.) `localStorage`'da tutulur. Sebep: sayfa yenilense de deney aynı ayarlarla sürer; bu veri gizli değildir.
- Erişim sadece `features/search/model/searchSettings.ts` üzerinden (`readSearchSettings()`, `writeSearchSettings(settings)`); key versiyonludur (`emb:search-settings:v1`), okunan değer şekil ve aralık kontrolünden geçer, bozuk alan varsayılana döner, okuma/yazma `try/catch` içindedir (`client-localstorage-schema`). Sadece geçerli değerler yazılır.
- Embedding API anahtarı ya da başka bir gizli bilgi frontend'e hiç gelmez: ne `VITE_` env değişkenine, ne `localStorage`'a, ne de bir response'a. Embedding'i her zaman backend üretir (`frontend-security`).
- Kullanıcının yazdığı sorgu metni log'lanmaz ve analitik/hata raporlarına konmaz.

## 10. Formlar ve validation

- Basit formlar React 19 `useActionState` + native `<form action>` ile yazılır (`react-patterns`). Çok adımlı/dinamik alanlı bir form çıkarsa form kütüphanesi plan kararıdır.
- İstemci tarafı validation sadece kullanıcı deneyimi içindir; doğruluk kaynağı backend'dir. Kurallar backend ile aynı değerleri kullanır ve `<feature>Rules.ts`'te tek yerde durur:

```ts
export const QUERY_MIN_LENGTH = 2
export const QUERY_MAX_LENGTH = 500
export const SEARCH_LIMIT_DEFAULT = 10
export const SEARCH_LIMIT_MAX = 50

export function normalizeQuery(input: string): string {
  return input.trim().replace(/\s+/g, ' ')
}

export const QUERY_RULE_MESSAGE = `Arama metni ${QUERY_MIN_LENGTH}-${QUERY_MAX_LENGTH} karakter olmalı.`

export function isValidQuery(query: string): boolean {
  const normalized = normalizeQuery(query)
  return normalized.length >= QUERY_MIN_LENGTH && normalized.length <= QUERY_MAX_LENGTH
}
```

- Alan kuralının Türkçe mesajı kuralın yanında, aynı dosyadadır (`<FIELD>_RULE_MESSAGE`); hem istemci tarafı kontrol hem backend'in `VALIDATION_FAILED` / `violations[].field` cevabı bu mesajı gösterir.
- Sorgu ve kaynak adı kontrolünden önce `normalizeQuery` / `trim()` uygulanır; sadece boşluktan oluşan değer geçersizdir. Türkçe karakterler (ı, İ, ş, ğ) olduğu gibi gönderilir; büyük/küçük harf dönüşümü gerekiyorsa `toLocaleLowerCase('tr')` kullanılır.
- Submit sırasında buton `disabled` olur ve metni eylemi anlatır ("Aranıyor…").
- Her input'un `<label htmlFor>`'u vardır; placeholder label yerine geçmez (`frontend-a11y`).

## 11. Testler

### İsimlendirme

Backend ile aynı biçim: `given<Precondition>_when<Action>_then<ExpectedResult>`

```ts
describe('SearchForm', () => {
  it('givenEmbeddingServiceDown_whenSearchSubmitted_thenShowsEmbeddingUnavailableMessage', async () => { ... })
  it('givenWhitespaceOnlyQuery_whenSubmitted_thenShowsQueryRuleMessage', async () => { ... })
})
```

- `describe` adı test edilen component/hook/function adıdır.
- `it` kullanılır (`test` değil); tek biçim.

### Gövde yapısı: AAA

```tsx
it('givenEmbeddingServiceDown_whenSearchSubmitted_thenShowsEmbeddingUnavailableMessage', async () => {
  // Arrange
  server.use(http.post('/api/workspaces/:workspaceId/search', () => problem(503, 'EMBEDDING_UNAVAILABLE')))
  const user = userEvent.setup()
  renderWithProviders(<SearchForm workspaceId={42} />)

  // Act
  await user.type(screen.getByLabelText('Arama metni'), 'Şifremi unuttum, online işlemlere giremiyorum')
  await user.click(screen.getByRole('button', { name: 'Aramayı başlat' }))

  // Assert
  expect(await screen.findByRole('alert')).toHaveTextContent('Embedding servisine şu an ulaşılamıyor.')
})
```

- Bölümler `// Arrange`, `// Act`, `// Assert` ile ayrılır; Arrange gerekmiyorsa atlanabilir.
- Query önceliği: `getByRole` > `getByLabelText` > `getByText` > `getByTestId` (`react-testing`). Türkçe UI metinleriyle sorgulanır; bu, metnin de test edilmesini sağlar.
- Network her zaman MSW ile mock'lanır (msw 3: `server.listen({ onUnhandledFrame: 'error' })`); `fetch` veya `searchApi` `vi.mock` ile mock'lanmaz. Handler'lar `docs/api/openapi.yaml`'a uyar; hata response'ları `src/test/problem.ts`'teki `problem(status, errorCode)` ile üretilir: Problem Details body'si + `Content-Type: application/problem+json` + status içeren hazır bir `HttpResponse` döner (`react-testing`).
- Her `errorCode` için en az bir test, kullanıcıya doğru Türkçe mesajın gösterildiğini doğrular.
- Sıralama ve skor gösterimi testlerinde beklenen değer fixture'daki sabit değerlerden elle türetilir (ör. `distance: 0.1269` → "0,1269", `formatScore` tr-TR 4 ondalık); formatter'ın kendisi beklenen değeri üretmek için kullanılmaz.
- Her etkileşimli component için bir `axe` testi (`vitest-axe`).
- Snapshot testi yok. `setTimeout`/sabit bekleme yok; `findBy*` ve `waitFor` kullan.
- Polling içeren ekranlar MSW handler'ının art arda farklı aşamalar dönmesiyle test edilir; beklenen istek sayısı ve iş bitince sorgunun durduğu doğrulanır. WebSocket gelince STOMP içeren hook'lar, `shared/websocket/stompClient.ts` sahte bir implementasyonla değiştirilerek test edilir; gerçek WebSocket akışı E2E'de doğrulanır (`e2e-testing`).
- Playwright E2E testleri (`frontend/e2e/`) aynı isim biçimini kullanır; Playwright API'si gereği `it` yerine `test` / `test.describe` yazılır: `test('givenSeededWorkspace_whenHybridSearch_thenShowsTenRankedHits', ...)`. AAA yorumları E2E'de de kullanılır, sabit bekleme (`waitForTimeout`) yoktur.
- Coverage hedefi: satır bazında en az %80 (`react-testing` / coverage thresholds).

## 12. Stil

- Her component kendi `X.module.css`'ini kullanır. Global CSS sadece `src/index.css`'te: reset, font ve design token'ları.
- Renk, boşluk, radius, font boyutu gibi değerler CSS custom property token'larından gelir (`var(--color-surface)`, `var(--space-3)`); component CSS'inde ham hex değer ya da rastgele px boşluk yazılmaz. Token sistemini oluştururken `frontend-design` skill'ini izle; kökteki HTML sayfalarının görsel dili (font, renk) başlangıç referansıdır.
- Inline `style` sadece runtime'da hesaplanan değerler için (ör. skor çubuğunun genişliği, ingestion ilerleme yüzdesi).
- Mobile-first; hedef en dar ekran 360 px. Kod blokları ve SQL örnekleri dar ekranda yatay kaydırılır, sayfa taşmaz. Görünür focus stili kaldırılmaz (`outline: none` tek başına yasak). `prefers-reduced-motion` desteklenir.
- Şablondan gelen `App.css`, `assets/hero.png`, `react.svg`, `vite.svg` ilk gerçek ekran yazılırken silinir.

## 13. Komutlar

```bash
npm run dev        # Vite dev server (5173), /api backend'e proxy'lenir
npm run build      # tsc -b && vite build (type-check dahil)
npm run lint       # oxlint
npm test           # vitest run
npm run test:e2e   # Playwright, sistem açıkken (ayrıntı frontend/CLAUDE.md)
```

Bir iş bitmeden önce `build`, `lint` ve testler hatasız geçmelidir.

## Kontrol listesi

```
- [ ] Tüm identifier'lar İngilizce, kullanıcıya görünen tüm metinler Türkçe
- [ ] Component/function üstünde yorum yok, commented-out kod, console.log, debugger yok
- [ ] Dosya feature-first yapıda doğru klasörde; feature'lar birbirini import etmiyor; barrel yok
- [ ] Default export yok; isimler bölüm 5'teki kalıplara ve domain sözlüğüne uyuyor
- [ ] any, enum, gereksiz `!` yok; API type'ları backend DTO adlarıyla aynı
- [ ] Backend çağrısı query/mutation hook'u üzerinden; component'te fetch yok
- [ ] Hata metni errorMessages.ts'ten; backend `detail`'i ekranda değil
- [ ] Arama ayarları sadece searchSettings.ts üzerinden; frontend'de gizli anahtar yok, sorgu metni loglanmıyor
- [ ] Polling iş bitince duruyor; (WebSocket varsa) STOMP subscription'ları cleanup'ta kapanıyor, mesajlar aynı query cache girdisine yazılıyor
- [ ] Testler given_when_then isimli, AAA yapılı, MSW ile; her errorCode için mesaj testi var
- [ ] Stil CSS Module + token ile; focus görünür
- [ ] build, lint ve testler geçiyor
```
