<!--
AMAÇ: Claude (ve ekip üyeleri) frontend/ altında bir dosyayı okuyunca ya da düzenleyince yüklenir.
İçerik: harita, komutlar, dosya sahipliği, tuzaklar, iş → skill yönlendirmesi.
Kuralların ayrıntısı frontend-conventions skill'inde. Orada yazanı burada tekrar etme.
Durum: iskelet TASK-003'te kuruldu (Vite react-ts şablonu). Sürümler `package.json` / `package-lock.json`'dadır.
-->

# Frontend (React)

## Teknoloji
- Node 26 (`engines.node: >=26 <27`), npm 11. React 19.3, TypeScript 6.0, Vite 8.3, oxlint 1.89.
- Runtime paketleri: `react-router` 7.18 (declarative `BrowserRouter`), `@tanstack/react-query` 5.104, `react-error-boundary` 6.1, `@fontsource/source-sans-3` 5.3, `@fontsource/jetbrains-mono` 5.3 (yazı tipleri kendi sunucumuzdan; CDN yok).
- Test paketleri: `vitest` 5.0 (jsdom 30), `@vitest/coverage-v8` 5.0, `@testing-library/react` 16.3, `@testing-library/dom` 10.4, `@testing-library/user-event` 14.6, `@testing-library/jest-dom` 7.0, `msw` 3.0, `vitest-axe` 0.1, `@playwright/test` 1.64, `ajv` 8.20, `ajv-formats` 3.0, `yaml` 2.9 (son üçü sadece E2E sözleşme kontrolünde: `e2e/` altında REST cevabını `docs/api/openapi.yaml` şemasına göre doğrulamak için; uygulama kodunda kullanılmaz).
- **Henüz olmayanlar:** `@stomp/stompjs` (canlı güncelleme task'ında), Stryker (mutation testi), form kütüphanesi, global store (Zustand). Skill örnekleri kullansa bile bunları varmış gibi kod yazma; eklemek plan kararıdır.
- Vite dev server `/api`'yi `http://localhost:8080`'e proxy'ler (`vite.config.ts`). `/ws` proxy'si yok (WebSocket henüz yok).
- Üretim build'inde `build.assetsInlineLimit: 0`: hiçbir asset (yazı tipleri dahil) CSS/JS'e `data:` URI olarak gömülmez, `/assets/` altında ayrı dosya olur. nginx CSP'si `font-src 'self'` olduğu için bu ayar kaldırılmaz.
- Vitest config'i `vite.config.ts` içinde (`test` bloğu): jsdom, setup `src/test/setup.ts`, CSS Modules `non-scoped` (testte class adı = CSS'teki ad), `e2e/**` hariç, coverage v8 satır eşiği %80.
- Playwright config'i `playwright.config.ts`: `e2e/`, sadece Chromium, `baseURL = E2E_BASE_URL ?? http://localhost:3000`, `webServer` yok (sistem dışarıdan açık olmalı), `globalSetup: './e2e/global-setup.ts'` (her koşudan önce veriyi sıfırlar).
- Kökteki `*.html` sayfaları (konu anlatımları ve lab demosu) başlangıç içeriğidir. Frontend'e taşınırken içerik ve görsel dil oradan alınır; o dosyalar değiştirilmez.

## Klasör yapısı
Feature-first. Ayrıntılı ağaç `frontend-conventions` bölüm 4'te.
```
frontend/
├── e2e/                     Playwright testleri
├── public/                  favicon
└── src/
    ├── main.tsx             font import'ları + AppProviders + App
    ├── index.css            design token'ları (--color-*, --space-*, --radius-*, --text-*), reset
    ├── app/                 App (route'lar), AppProviders, queryClient, WorkspaceLayout, AppHeader, WorkspaceSelect, SectionTabs, NotFoundPage
    ├── features/search/     api/, components/, hooks/, pages/, model/ (Arama + Arama kayıtları)
    ├── features/source/     api/, components/, hooks/, pages/, model/ (Bilgi kaynakları, kaynak detayı, yükleme diyalogları, iş durumu sorgulama)
    ├── shared/              api/ (httpClient, ApiError, errorMessages, pagination), workspace/, format/, components/ (Dialog dahil), toast/, chunk/ (ChunkResponse, getChunk, useChunkQuery, isChunkGoneError; iki feature'ın ortak parça kodu)
    └── test/                Vitest setup, MSW server ve handler'lar (tester)
```
- Feature adları backend paketleriyle aynı.
- Yükleme ve iş durumu kodu `features/source` içindedir (TASK-004 D2); ayrı `features/ingestion` yoktur. İş durumu sorgulaması sayfadan bağımsızdır: `IngestionWatcherProvider` `WorkspaceLayout`'ta kaynak listesindeki aktif `latestJob`'ları 1 sn'de bir sorgular; toast'lar `shared/toast/ToastProvider` ile gösterilir.
- Bir feature başka bir feature'ı import etmez; ortak kod `shared/`'a gider. Çalışma alanı kodu ve adres yardımcıları (`workspacePath`, `sourceDetailPath`) `shared/workspace/`'tadır.
- Adresler: `/w/:workspaceId/arama`, `/w/:workspaceId/arama-kayitlari`, `/w/:workspaceId/kaynaklar`, `/w/:workspaceId/kaynaklar/:sourceId?parca=<chunkId>`, `/w/:workspaceId/belge-yukle?kaynak=<sourceId>` (yükleme sayfası; `kaynak` yüklenen belgenin ilerlemesini izler, sayfa yenilense de kalır). Adres yardımcıları `shared/workspace/workspaceRules.ts`'te (`workspaceHomePath`, `uploadPath`, `homeSection`).
- Açılış: `/` → `/w/42`; `/w/:workspaceId` çalışma alanında kaynak yoksa `belge-yukle`'ye, varsa `arama`'ya yönlendirir (`WorkspaceHomeRedirect`). Çalışma alanı seçicisi sadece 2 ya da daha fazla çalışma alanı varken görünür.
- "Sık aranan" listesi backend'den gelir (`GET …/search-events/popular-queries`, `usePopularQueriesQuery`); sabit öneri listesi yoktur.
- Test dosyası, test ettiği dosyanın yanında durur (`X.test.tsx`).

## Komutlar
Hepsi `frontend/` içinde çalıştırılır.
- Bağımlılıkları kur: `npm ci` (`package-lock.json`'a göre; lock dosyasını değiştirmez)
- Dev server: `npm run dev` → http://localhost:5173 (terminali bloklar; arka planda başlat, iş bitince durdur). Backend 8080'de açık olmalı.
- Build + tip kontrolü: `npm run build`
- Lint: `npm run lint` (uyarı bırakılmaz)
- Testler: `npm test` (`vitest run`); izleme modu `npm run test:watch`
- Tek test: `npx vitest run src/features/search/pages/SearchPage.test.tsx -t '<ad>'`
- Coverage: `npm run test:coverage` (satır eşiği %80)
- Uçtan uca: önce bir kez `npx playwright install chromium`, sonra sistem açıkken `npm run test:e2e` (Docker'daki site için varsayılan `http://localhost:3000`). Uçtan uca testler başlarken çalışma alanındaki kaynakları, parçaları, işleri ve arama kayıtlarını siler. Local geliştirmede (dev compose + dev server): `E2E_COMPOSE_FILE=../deployment/docker-compose.dev.yml E2E_BASE_URL=http://localhost:5173 npm run test:e2e`
- Handoff'a `STATUS: DONE` yazmadan önce `build`, `lint` ve testler hatasız geçmeli.

## Dosya sahipliği
| Yol | Sahibi |
|-----|--------|
| `src/**` (testler hariç), `index.html`, `public/` | frontend-coder |
| `package.json`, `package-lock.json`, `vite.config.ts`, `tsconfig*.json`, `.oxlintrc.json`, `.gitignore`, test aracı config'leri | frontend-coder |
| `*.test.ts(x)`, `src/test/**`, `e2e/**` | tester |
| `Dockerfile`, `.dockerignore`, `nginx.conf` | devops |

- Paket ekleme ve kaldırma sadece frontend-coder'ın işidir. Tester'a test paketi gerekiyorsa frontend-coder'a mesaj atar.
- Tester testi geçirmek için uygulama kodunu değiştirmez; bulduğu hatayı raporlar.

## Kurallar
- Bağlayıcı kurallar `frontend-conventions` skill'indedir: dil, yorum yasağı, isimlendirme, API katmanı, `errorCode` → Türkçe mesaj, stil, testler. frontend/ altında kod ya da test yazmadan önce bu skill'i yükle. Başka bir skill'in örneği bununla çelişirse `frontend-conventions` geçerlidir.
- API type'ları ve çağrıları `docs/api/openapi.yaml` ile birebir aynıdır. Sözleşmede olmayan endpoint ya da alan kullanılmaz.
- Kod biçimini düzeltecek bir formatter (Prettier) yok; biçime elle uyulur: 2 boşluk, tek tırnak, noktalı virgül yok, çok satırda sondaki virgül.

## Tuzaklar
Bunlar Vite react-ts şablonunun `tsconfig` ayarlarından gelir; uyulmazsa build ya da lint kırılır.
- `erasableSyntaxOnly` açık: `enum` ve constructor parameter property yazılamaz. Yerine string literal union kullanılır.
- `verbatimModuleSyntax` açık: sadece tip olan import'lar `import type { ... }` ile yazılır.
- Import'lar dosya uzantısıyla yazılır: `./SearchForm.tsx`.
- Lint aracı oxlint'tir (`.oxlintrc.json`: react, typescript, oxc, jsx-a11y eklentileri). ESLint ya da Prettier config'i eklenmez. `jsx-a11y/prefer-tag-over-role` kapalıdır (arama formu `role="search"` kullanır); `no-noninteractive-tabindex` `region` rolüne izin verir (kaydırılabilir sorgu planı).
- Sayfa başlığı React 19'un `<title>` öğesiyle component içinde verilir; çocuğu tek bir string olmalıdır (`{`${name} · yanıt.`}`).
- Modal açıkken sayfa kaydırması `index.css`'teki `:root:has(dialog[open])` kuralıyla kilitlenir; diyaloğun padding'i `.content` sarmalayıcısındadır (dışına tıklama tespiti buna bağlı). Her zaman `isOpen` ile mount edilen bir `Dialog`'da StrictMode açma effect'ini iki kez çalıştırır; ilk cleanup'ın `close` olayı `showModal()`'dan sonra gelir, `handleNativeClose` bu yüzden diyalog yeniden açıksa `onClose` çağırmaz.
- Diyaloglar `shared/components/Dialog.tsx` (native `<dialog>` + `showModal()`) ile açılır. jsdom `showModal`/`close` desteklemez; testte `src/test/setup.ts`'teki taklit gerekir. Dosya alanı olan formlarda `useActionState` kullanılmaz: React form action'ı bitince alanları sıfırlar, backend hatasında seçilen dosya kaybolur (`onSubmit` + mutation kullanılır).
- React 19: `forwardRef` yerine `ref` prop'u, basit formlarda `useActionState`. StrictMode'da effect'ler iki kez çalışır; subscription'lar buna dayanıklı olmalıdır.
- `VITE_` ile başlayan env değişkenleri tarayıcıya gömülür, yani herkese açıktır. Bu değişkenlere gizli bilgi (ör. embedding API anahtarı) konmaz (`frontend-security`).

## Hangi iş → hangi skill
| İş | Skill |
|----|-------|
| Her kod ve test | `frontend-conventions` (zorunlu) |
| Component, hook, state'in yeri | `react-patterns`, `react-composition-patterns` |
| Server state, cache, canlı veri | `react-state-management` |
| Yeni ekran ya da görsel tasarım | `frontend-design`, `web-design-guidelines` |
| Form, modal, klavye, ekran okuyucu | `frontend-a11y` |
| Vite config, proxy, env | `vite-patterns` |
| Kullanıcı girdisi, token, yeni paket | `frontend-security` |
| Performans | `frontend-performance`, `react-best-practices` |
| Component ve hook testi | `react-testing`, `writing-good-tests` |
| Uçtan uca test | `e2e-testing`; çalışan uygulamayı tarayıcıda denemek için `webapp-testing` |
| Review | `code-review-and-quality` |
| Hata ayıklama | `systematic-debugging` |
