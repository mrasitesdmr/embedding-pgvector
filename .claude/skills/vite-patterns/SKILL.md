---
name: vite-patterns
description: Vite build tool pattern'leri - config, plugin'ler, HMR, env variable'lar, proxy kurulumu, dependency pre-bundling, build optimizasyonu, Docker ayarları ve güvenlik tuzakları. frontend/vite.config.ts, .env dosyaları, Vite plugin'leri, dev server proxy'si (backend REST + WebSocket) veya Vite build/HMR hatalarıyla çalışırken kullan.
license: MIT
metadata:
  origin: ECC
  source: https://github.com/affaan-m/ECC/tree/main/skills/vite-patterns
---

# Vite Pattern'leri

Vite 8+ projeleri için build tool ve dev server pattern'leri. Configuration, environment variable'lar, proxy kurulumu, library mode, dependency pre-bundling ve production'daki yaygın tuzakları kapsar.

> **Proje kuralı:** Import'lar göreli path + dosya uzantısıyla yazılır, path alias (`@/...`) ve barrel yoktur, default export yoktur, API/WebSocket adresi kodda sabitlenmez (göreli `/api`, `/ws` + proxy), dev server portu varsayılan 5173'tür (`frontend-conventions` bölüm 4, 5, 7, 13). Aşağıdaki genel örnekler (alias, `port: 3000`, `VITE_API_URL`, library mode, SSR) seçenekleri anlatır; projede bu kurallar geçerlidir.

## Ne zaman kullanılır

- `vite.config.ts` veya `vite.config.js` yapılandırırken
- Environment variable'ları veya `.env` dosyalarını kurarken
- API backend'leri için dev server proxy'si yapılandırırken
- Build çıktısını optimize ederken (chunk'lar, minification, asset'ler)
- `build.lib` ile kütüphane yayınlarken
- Dependency pre-bundling veya CJS/ESM interop sorunlarını giderirken
- HMR, dev server veya build hatalarını debug ederken
- Vite plugin'lerini seçerken ya da sıralarken

## Nasıl çalışır

- **Dev modu** kaynak dosyaları native ESM olarak sunar; bundling yoktur. Dönüşümler her modül isteğinde ihtiyaç anında yapılır; soğuk başlangıcın hızlı ve HMR'ın isabetli olmasının sebebi budur.
- **Build modu**, uygulamayı production için tree-shaking ve code-splitting ile bundle'lamak için Vite 8'de Rolldown kullanır (Vite 5–7 Rollup kullanıyordu). JS minification Oxc Minifier, CSS minification Lightning CSS ile yapılır.
- **JS dönüşümleri** (TS/JSX) Vite 8'de esbuild yerine Oxc ile yapılır; `esbuild` config seçeneği deprecated'dır, yerine `oxc` kullanılır.
- **Dependency pre-bundling**, CJS/UMD dependency'leri Vite 8'de Rolldown ile (önceden esbuild) bir kez ESM'e dönüştürür ve sonucu `node_modules/.vite` altında cache'ler; sonraki başlangıçlar bu işi atlar. `optimizeDeps.esbuildOptions` deprecated'dır, yerine `optimizeDeps.rolldownOptions`.
- **Plugin'ler** dev ve build arasında ortak bir interface paylaşır; aynı plugin nesnesi hem dev server'ın anlık dönüşümlerinde hem de production pipeline'ında çalışır.
- **Environment variable'lar** build sırasında statik olarak koda gömülür (inline). `VITE_` prefix'li değişkenler bundle'da public sabitlere dönüşür; prefix'i olmayan her şey client kodu için görünmezdir.

## Örnekler

### Config yapısı

#### Temel config

```typescript
// vite.config.ts
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      '/ws': { target: 'http://localhost:8080', ws: true },
    },
  },
})
```

Genel projelerde görülen `resolve.alias` (`'@': './src'`) bu projede kullanılmaz; import'lar göreli path + uzantı ile yazılır.

#### Koşullu config

```typescript
// vite.config.ts
import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), ['BACKEND_'])

  return {
    plugins: [react()],
    server: {
      proxy: {
        '/api': { target: env.BACKEND_URL ?? 'http://localhost:8080', changeOrigin: true },
        '/ws': { target: env.BACKEND_URL ?? 'http://localhost:8080', ws: true },
      },
    },
  }
})
```

Burada env değeri sadece dev proxy hedefi için config'te okunur (ör. Docker'da `BACKEND_URL=http://backend:8080`). Client koduna gerekmediği için `VITE_` prefix'i verilmez, `loadEnv`'e açık prefix listesi geçilir; `define` ile client koduna API URL'i gömülmez, uygulama kodu göreli `/api` ve `/ws` kullanır.

#### Önemli config seçenekleri

| Key | Varsayılan | Açıklama |
|-----|---------|-------------|
| `root` | `'.'` | Proje kökü (`index.html`'in bulunduğu yer) |
| `base` | `'/'` | Deploy edilen asset'ler için public base path |
| `envPrefix` | `'VITE_'` | Client'a açılan env variable'ların prefix'i |
| `build.outDir` | `'dist'` | Çıktı dizini |
| `build.minify` | `'oxc'` | JS minifier (`'oxc'`, `'terser'` veya `false`; `'esbuild'` deprecated ve ayrıca `esbuild` kurulumu ister) |
| `build.cssMinify` | `'lightningcss'` | CSS minifier |
| `build.sourcemap` | `false` | `true`, `'inline'` veya `'hidden'` |

### Plugin'ler

#### Temel plugin'ler

Plugin ihtiyaçlarının çoğu, bakımı iyi yapılan birkaç paketle karşılanır. Kendi plugin'ini yazmadan önce bunlara bak.

| Plugin | Amaç | Ne zaman kullanılır |
|--------|---------|-------------|
| `@vitejs/plugin-react-swc` | SWC ile React HMR + Fast Refresh | Sadece SWC plugin'i gereken özel durumlar; Vite 8'de `@vitejs/plugin-react` zaten Oxc kullanır |
| `@vitejs/plugin-react` | React HMR + Fast Refresh | create-vite şablonunun varsayılanı; **bu proje bunu kullanıyor**, gerekmedikçe değiştirme |
| `@vitejs/plugin-vue` | Vue 3 SFC desteği | Vue uygulamaları |
| `vite-plugin-checker` | `tsc` + linter'ı HMR overlay'li bir worker thread'de çalıştırır | Build öncesi type-check yapmayan TypeScript uygulamaları - Vite `vite build` sırasında type-check YAPMAZ (bu projede `tsc -b` zaten çalışır; eklemek plan kararıdır) |
| `vite-tsconfig-paths` | `tsconfig.json`'daki `paths` alias'larını uygular | `tsconfig.json`'da zaten alias'ların varsa |
| `vite-plugin-dts` | Library mode'da `.d.ts` dosyaları üretir | TypeScript kütüphaneleri yayınlarken |
| `vite-plugin-svgr` | SVG'leri React component'i olarak import eder | SVG'leri component olarak kullanan React uygulamaları |
| `rollup-plugin-visualizer` | Bundle treemap/sunburst raporu | Periyodik bundle boyutu denetimleri (`enforce: 'post'` kullan) |
| `vite-plugin-pwa` | Sıfır config PWA + Workbox | Offline çalışabilen uygulamalar |

**Kritik uyarı:** `vite build` transpile eder ama type-check YAPMAZ. `vite-plugin-checker` eklemezsen ya da CI'da `tsc --noEmit` çalıştırmazsan type hataları sessizce production'a gider. (Bu projede `npm run build` script'i `tsc -b && vite build` olduğu için build öncesi type-check yapılıyor.)

#### Özel plugin yazmak

Plugin yazmak nadiren gerekir; çoğu ihtiyaç mevcut plugin'lerle karşılanır. Gerektiğinde `vite.config.ts` içinde inline başla, sadece yeniden kullanılıyorsa ayrı dosyaya çıkar.

```typescript
// vite.config.ts — minimal inline plugin
function myPlugin(): Plugin {
  return {
    name: 'my-plugin',                       // zorunlu, benzersiz olmalı
    enforce: 'pre',                           // 'pre' | 'post' (opsiyonel)
    apply: 'build',                           // 'build' | 'serve' (opsiyonel)
    transform(code, id) {
      if (!id.endsWith('.custom')) return
      return { code: transformCustom(code), map: null }
    },
  }
}
```

**Önemli hook'lar:** `transform` (kaynağı değiştir), `resolveId` + `load` (virtual modüller), `transformIndexHtml` (HTML'e enjekte et), `configureServer` (dev middleware ekle), `hotUpdate` (özel HMR - Vite 6 ile geldi, eski `handleHotUpdate` yerine önerilir).

**Virtual modüller** `\0` prefix konvansiyonunu kullanır: `resolveId` `'\0virtual:my-id'` döner ki diğer plugin'ler onu atlasın. Kullanıcı kodu `'virtual:my-id'`'yi import eder.

Plugin API'sinin tamamı için [vite.dev/guide/api-plugin](https://vite.dev/guide/api-plugin)'e bak. Dönüşüm pipeline'ını debug etmek için geliştirme sırasında `vite-plugin-inspect` kullan.

### HMR API

Framework plugin'leri (`@vitejs/plugin-react`, `@vitejs/plugin-vue` vb.) HMR'ı otomatik yönetir. `import.meta.hot`'a doğrudan sadece güncellemeler arasında state koruması gereken özel state store'ları, dev tool'ları veya framework'ten bağımsız utility'ler yazarken uzan.

```typescript
// src/store.ts — vanilla bir modül için manuel HMR
if (import.meta.hot) {
  // State'i güncellemeler arasında koru (.data'yı MUTATE et, asla yeniden atama)
  import.meta.hot.data.count = import.meta.hot.data.count ?? 0

  // Modül değiştirilmeden önce side effect'leri temizle
  import.meta.hot.dispose((data) => clearInterval(data.intervalId))

  // Bu modülün kendi güncellemelerini kabul et
  import.meta.hot.accept()
}
```

Tüm `import.meta.hot` kodu production build'lerinden tree-shake ile çıkarılır; guard'ı kaldırmaya gerek yoktur.

### Environment variable'lar

Vite `.env`, `.env.local`, `.env.[mode]` ve `.env.[mode].local` dosyalarını bu sırayla yükler (sonraki öncekini ezer); `*.local` dosyaları gitignore'dadır ve local secret'lar içindir.

#### Client tarafında erişim

Client koduna sadece `VITE_` prefix'li değişkenler açılır:

```typescript
import.meta.env.VITE_API_URL   // string
import.meta.env.MODE            // 'development' | 'production' | özel
import.meta.env.BASE_URL        // base config değeri
import.meta.env.DEV             // boolean
import.meta.env.PROD            // boolean
import.meta.env.SSR             // boolean
```

#### Config içinde env kullanımı

```typescript
// vite.config.ts — genel örnek; bu projede API adresi client koduna gömülmez (göreli /api, /ws)
import { defineConfig, loadEnv } from 'vite'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd())          // sadece VITE_ prefix'liler (güvenli)
  return {
    define: {
      __API_URL__: JSON.stringify(env.VITE_API_URL),
    },
  }
})
```

### Güvenlik

#### `VITE_` prefix'i bir güvenlik sınırı DEĞİLDİR

`VITE_` prefix'li her değişken **build sırasında client bundle'ına statik olarak gömülür**. Minification, base64 encoding ve source map'leri kapatmak onu gizlemez. Kararlı bir saldırgan gönderilen JavaScript'ten herhangi bir `VITE_` değişkenini çıkarabilir.

**Kural:** `VITE_` değişkenlerine sadece public değerler (API URL'leri, feature flag'ler, public key'ler) girer. Secret'lar (API token'ları, veritabanı URL'leri, private key'ler) bir API'nin ya da serverless function'ın arkasında, server tarafında yaşamak ZORUNDADIR.

#### `loadEnv('')` tuzağı

```typescript
// KÖTÜ: üçüncü argüman olarak '' geçmek TÜM env değişkenlerini (server secret'ları dahil) yükler
// ve onları `define` ile client koduna gömülebilir hale getirir.
const env = loadEnv(mode, process.cwd(), '')

// İYİ: açık prefix listesi
const env = loadEnv(mode, process.cwd(), ['VITE_', 'APP_'])
```

#### Production'da source map'ler

Production source map'leri orijinal kaynak kodunu sızdırır. Bir error tracker'a (Sentry, Bugsnag) yükleyip sonra local'den silmiyorsan kapalı tut:

```typescript
build: {
  sourcemap: false,                                  // varsayılan - böyle kalsın
}
```

#### `.gitignore` kontrol listesi

- `.env.local`, `.env.*.local` - local secret override'ları
- `dist/` - build çıktısı
- `node_modules/.vite` - pre-bundle cache'i (bayat kayıtlar hayalet hatalara yol açar)

### Server proxy

Dev'de frontend (5173) ile backend (Spring Boot, 8080) farklı origin'lerdedir. CORS ayarı yerine proxy kullan; böylece kodda göreli path'ler (`/api/...`, `/ws`) kullanılır ve production'da reverse proxy ile aynı yapı korunur.

```typescript
// vite.config.ts — server.proxy
server: {
  proxy: {
    '/api': {
      target: 'http://localhost:8080',
      changeOrigin: true,                               // virtual-hosted backend'ler için gerekli
    },
    '/ws': {
      target: 'http://localhost:8080',
      ws: true,                                         // WebSocket (STOMP) proxy'si
    },
  },
}
```

`/ws` için `changeOrigin` verilmez: tarayıcının `Origin` header'ı (`http://localhost:5173`) backend'e olduğu gibi gider ve Spring'in `setAllowedOriginPatterns` listesinde bu origin bulunmalıdır (`spring-websocket`).

Backend path'leri `/api` prefix'i içermiyorsa `rewrite: (path) => path.replace(/^\/api/, '')` ekle. WebSocket proxy'si için route config'ine `ws: true` ekle.

### Build optimizasyonu

#### Chunk bölme

Vite 8'de `build.rollupOptions` → `build.rolldownOptions` olarak yeniden adlandırıldı. `output.manualChunks`'ın **object biçimi artık desteklenmiyor**, function biçimi deprecated; yerine Rolldown'un `codeSplitting` seçeneği kullanılır ([Vite migration rehberi](https://vite.dev/guide/migration)). Önce varsayılan bölmeyi ve route bazlı `React.lazy` bölmesini dene (`react-best-practices` / `bundle-dynamic-imports`); elle bölme sadece bundle raporu (`rollup-plugin-visualizer`) gerekçe gösterdiğinde, Rolldown dokümantasyonundaki güncel `codeSplitting` sözdizimiyle yapılır.

### Performans

#### Barrel file'lardan kaçın

Barrel file'lar (bir dizindeki her şeyi yeniden export eden `index.ts`) tek bir sembol import etsen bile Vite'ı yeniden export edilen her dosyayı yüklemeye zorlar. Resmi dokümantasyonun işaret ettiği bir numaralı dev server yavaşlığı budur.

```typescript
// KÖTÜ — tek bir yardımcı import etmek Vite'ı tüm barrel'ı yüklemeye zorlar
import { messageFor } from '../../shared/api'

// İYİ — doğrudan import, sadece o dosya yüklenir
import { messageFor } from '../../shared/api/errorMessages.ts'
```

#### Import uzantılarında açık ol

Her örtük uzantı `resolve.extensions` üzerinden 6'ya kadar dosya sistemi kontrolüne yol açar. Büyük kod tabanlarında bu birikir.

```typescript
// KÖTÜ
import { SearchForm } from './SearchForm'

// İYİ
import { SearchForm } from './SearchForm.tsx'
```

`tsconfig.json`'da `allowImportingTsExtensions` ile `resolve.extensions`'ı sadece gerçekten kullandığın uzantılara daralt.

#### Sık kullanılan route'ları ısıt (warm-up)

`server.warmup.clientFiles`, bilinen sık kullanılan giriş noktalarını tarayıcı istemeden önce dönüştürür; büyük uygulamalarda soğuk yüklemedeki request waterfall'unu ortadan kaldırır.

```typescript
// vite.config.ts
server: {
  warmup: {
    clientFiles: ['./src/main.tsx', './src/features/*/pages/*.tsx'],
  },
}
```

#### Yavaş dev server'ları profilleme

`vite dev` yavaş hissettiriyorsa `vite --profile` ile başla, uygulamayla etkileşime gir, sonra `.cpuprofile` kaydetmek için `p+enter`'a bas. Hangi plugin'lerin zaman yediğini bulmak için dosyayı [Speedscope](https://www.speedscope.app)'ta aç; genellikle topluluk plugin'lerindeki `buildStart`, `config` veya `configResolved` hook'larıdır.

### Library mode

Bir npm paketi yayınlarken `build.lib` kullan. İki tuzak config ayrıntılarından daha önemlidir:

1. **Type'lar üretilmez** - `vite-plugin-dts` ekle ya da ayrıca `tsc --emitDeclarationOnly` çalıştır.
2. **Peer dependency'ler external YAPILMALIDIR** - listelenmemiş peer'lar kütüphanene bundle'lanır ve kullananlarda duplicate-runtime hatalarına yol açar.

```typescript
// vite.config.ts
build: {
  lib: {
    entry: 'src/index.ts',
    formats: ['es', 'cjs'],
    fileName: (format) => `my-lib.${format}.js`,
  },
  rolldownOptions: {
    external: ['react', 'react-dom', 'react/jsx-runtime'],  // her peer dependency
  },
}
```

### SSR external'ları

Çıplak `createServer({ middlewareMode: true })` kurulumları framework yazarlarının alanıdır. Çoğu uygulama bunun yerine Nuxt, Remix, SvelteKit, Astro veya TanStack Start kullanmalıdır. Framework kullanıcısı olarak dokunacağın şey, dependency'ler SSR'da bozulduğunda externals config'idir (bu SPA projesinde gerekmez):

```typescript
// vite.config.ts — ssr seçenekleri
ssr: {
  external: ['node-native-package'],           // SSR bundle'ında require() olarak bırak
  noExternal: ['esm-only-package'],            // SSR çıktısına zorla bundle'la (çoğu SSR hatasını düzeltir)
  target: 'node',                              // 'node' veya 'webworker'
}
```

### Dependency pre-bundling

Vite, CJS/UMD'yi ESM'e dönüştürmek ve request sayısını azaltmak için dependency'leri önceden bundle'lar.

```typescript
// vite.config.ts — optimizeDeps
optimizeDeps: {
  include: [
    'lodash-es',                              // bilinen ağır dependency'leri zorla pre-bundle et
    'cjs-package',                            // interop sorunu çıkaran CJS dependency'ler
    'deep-lib/components/**',                 // derin import'lar için glob
  ],
  exclude: ['local-esm-package'],             // exclude edilirse geçerli ESM olmalı
  force: true,                                // cache'i yok say, yeniden optimize et (geçici debug için)
}
```

### Yaygın tuzaklar

#### Dev, build ile aynı davranmıyor

Dev modu modülleri tek tek (bundle'lamadan, Oxc dönüşümüyle) sunar, build ise Rolldown ile bundle'lar. CJS kütüphaneleri ikisi arasında farklı davranabilir. Deploy etmeden önce her zaman `vite build && vite preview` ile doğrula.

#### Deploy sonrası bayat chunk'lar

Yeni build'ler yeni chunk hash'leri üretir. Aktif oturumu olan kullanıcılar artık var olmayan eski dosya adlarını ister. Vite'ın yerleşik bir çözümü yoktur. Hafifletme yolları:

- Eski `dist/assets/` dosyalarını bir deployment penceresi boyunca yayında tut
- Router'ında dynamic import hatalarını yakala ve sayfayı yeniden yüklemeye zorla

#### Docker ve container'lar

Vite varsayılan olarak `localhost`'a bind olur; bu, container dışından erişilemez:

```typescript
// vite.config.ts — Docker/container kurulumu
server: {
  host: true,                                  // 0.0.0.0'a bind ol
  hmr: { clientPort: 443 },                    // reverse proxy arkasındaysa tarayıcının bağlandığı port
}
```

Container içindeki dev server'da proxy `target`'ı `localhost` değil compose servis adı olmalıdır (ör. `http://backend:8080`); bunu bir env değişkeninden oku.

#### Monorepo dosya erişimi

Vite dosya sunmayı proje köküyle sınırlar. Kök dışındaki paketler engellenir:

```typescript
// vite.config.ts — monorepo dosya erişimi
server: {
  fs: {
    allow: ['..'],                             // üst dizine (workspace kökü) izin ver
  },
}
```

### Anti-pattern'ler

```typescript
// KÖTÜ: envPrefix'i '' yapmak TÜM env değişkenlerini (secret'lar dahil) client'a açar
envPrefix: ''

// KÖTÜ: uygulama kaynak kodunda require()'ın çalıştığını varsaymak — Vite ESM önceliklidir
const lib = require('some-lib')                // bunun yerine import kullan

// KÖTÜ: her node_module'ü kendi chunk'ına bölmek — yüzlerce minik dosya üretir
// (Vite 8'de manualChunks zaten deprecated; codeSplitting ile de bu yapılmaz)

// KÖTÜ: library mode'da peer dependency'leri external yapmamak — duplicate runtime hatalarına yol açar
// rolldownOptions.external olmadan build.lib

// KÖTÜ: deprecated esbuild minifier'ını kullanmak
build: { minify: 'esbuild' }                  // 'oxc' (varsayılan) veya 'terser' kullan

// KÖTÜ: import.meta.hot.data'yı yeniden atayarak değiştirmek
import.meta.hot.data = { count: 0 }           // YANLIŞ: property'leri mutate etmelisin, yeniden atamamalısın
import.meta.hot.data.count = 0                 // DOĞRU
```

**Süreç anti-pattern'leri:**

- **`vite preview` bir production server'ı DEĞİLDİR** - build edilmiş bundle için bir smoke test'tir. `dist/`'i gerçek bir statik host'a (NGINX, Cloudflare Pages, Vercel static) deploy et ya da multi-stage bir Dockerfile kullan.
- **`vite build`'in type-check yapmasını beklemek** - sadece transpile eder. Type hataları sessizce production'a gider. Bu projede `npm run build` önce `tsc -b` çalıştırır; `vite build`'i tek başına çağırma.
- **`@vitejs/plugin-legacy`'yi varsayılan olarak eklemek** - bundle'ları ~%40 şişirir, source-map bundle analizörlerini bozar ve modern tarayıcılardaki kullanıcıların %95+'ı için gereksizdir. Varsayıma değil gerçek analytics'e göre karar ver.
- **`tsconfig.json` path'lerini tekrarlayan 30+ elle yazılmış `resolve.alias` kaydı** - bunun yerine `vite-tsconfig-paths` kullan.
- **Dependency değişikliklerinden sonra bayat `node_modules/.vite` bırakmak** - pre-bundle cache'i hayalet hatalara yol açar. Branch değiştirirken ya da dependency'leri patch'ledikten sonra temizle.

## Hızlı referans

| Pattern | Ne zaman kullanılır |
|---------|-------------|
| `defineConfig` | Her zaman - type inference sağlar |
| `loadEnv(mode, root, ['VITE_'])` | Config'de env değişkenlerine erişim (açık prefix) |
| `vite-plugin-checker` | Build'den önce type-check çalıştırmayan TypeScript uygulamaları (bu projede gerekmez: `npm run build` = `tsc -b && vite build`) |
| `vite-tsconfig-paths` | Elle yazılmış `resolve.alias` yerine |
| `optimizeDeps.include` | Interop sorunu çıkaran CJS dependency'ler |
| `server.proxy` | Dev'de API isteklerini backend'e yönlendirmek |
| `server.host: true` | Docker, container'lar, uzaktan erişim |
| `server.warmup.clientFiles` | Sık kullanılan route'ları önceden dönüştürmek |
| `build.lib` + `external` | npm paketi yayınlamak |
| `codeSplitting` (Rolldown) | Ölçümle gerekçelenen elle chunk bölme (`manualChunks` Vite 8'de deprecated) |
| `vite --profile` | Yavaş dev server'ı debug etmek |
| `vite build && vite preview` | Prod bundle'ını local'de smoke test etmek (prod server DEĞİL) |

## İlgili skill'ler

- `react-patterns` - React component pattern'leri
- `react-best-practices` - bundle ve render performansı kuralları
