# Frontend Optimization Pattern'leri

`frontend-performance` skill'inin Adım 3'ünde adı geçen anti-pattern'ler için çalışılmış fix'ler. Skill kuralı ve aranacak imzayı söyler; bu dosya tek bir somut implementation gösterir. Adım 2 darboğazı belirledikten sonra ihtiyacın olan bölümü aç — baştan sona okunmak için yazılmadı.

## İçindekiler

- Optimize edilmemiş görseller
- Gereksiz re-render'lar
- Canlı veri fırtınası
- Büyük bundle
- Bundle'ı ölçmek

## Optimize edilmemiş görseller

```html
<!-- KÖTÜ: Boyut yok, format optimizasyonu yok -->
<img src="/hero.jpg" />

<!-- İYİ: Hero / LCP görseli — art direction + çözünürlük değişimi, yüksek öncelik -->
<!--
  İki teknik birlikte:
  - Art direction (media): breakpoint başına farklı kırpma/kompozisyon
  - Çözünürlük değişimi (srcset + sizes): ekran yoğunluğuna göre doğru dosya boyutu
-->
<picture>
  <!-- Mobil: dikey kırpma (8:10) -->
  <source
    media="(max-width: 767px)"
    srcset="/hero-mobile-400.avif 400w, /hero-mobile-800.avif 800w"
    sizes="100vw"
    width="800"
    height="1000"
    type="image/avif"
  />
  <source
    media="(max-width: 767px)"
    srcset="/hero-mobile-400.webp 400w, /hero-mobile-800.webp 800w"
    sizes="100vw"
    width="800"
    height="1000"
    type="image/webp"
  />
  <!-- Masaüstü: yatay kırpma (2:1) -->
  <source
    srcset="/hero-800.avif 800w, /hero-1200.avif 1200w, /hero-1600.avif 1600w"
    sizes="(max-width: 1200px) 100vw, 1200px"
    width="1200"
    height="600"
    type="image/avif"
  />
  <source
    srcset="/hero-800.webp 800w, /hero-1200.webp 1200w, /hero-1600.webp 1600w"
    sizes="(max-width: 1200px) 100vw, 1200px"
    width="1200"
    height="600"
    type="image/webp"
  />
  <img
    src="/hero-desktop.jpg"
    width="1200"
    height="600"
    fetchpriority="high"
    alt="Görselin Türkçe açıklaması"
  />
</picture>

<!-- İYİ: Ekranın altındaki görsel — lazy load + async decoding -->
<img
  src="/content.webp"
  width="800"
  height="400"
  loading="lazy"
  decoding="async"
  alt="Görselin Türkçe açıklaması"
/>
```

JSX'te attribute adları: `fetchPriority`, `srcSet`, `decoding`, `loading`. Dekoratif görsellerde `alt=""` (`frontend-a11y`).

**CLS için görsel dışı kaynaklar:**
- Veri yüklenirken yer tutucu (skeleton) gerçek içerikle aynı yüksekliği kaplar.
- Web font'larında `font-display: swap` + benzer metrikli fallback; font dosyaları self-host edilir (CSP `font-src 'self'`).
- Hata/uyarı banner'ları içeriği aşağı itmek yerine ayrılmış bir alanda görünür.

## Gereksiz re-render'lar

```tsx
// KÖTÜ: Her render'da yeni nesne oluşturur, child'ları yeniden render ettirir
export function SearchHitList({ hits }: SearchHitListProps) {
  return <ScoreLegend options={{ format: 'percent', digits: 1 }} hits={hits} />
}

// İYİ: Stabil referans
const SCORE_FORMAT = { format: 'percent', digits: 1 } as const

export function SearchHitList({ hits }: SearchHitListProps) {
  return <ScoreLegend options={SCORE_FORMAT} hits={hits} />
}

// Profilin pahalı gösterdiği component için React.memo (ör. vurgulamalı uzun parça metni)
export const SearchHitRow = memo(function SearchHitRow({ hit, terms }: SearchHitRowProps) {
  return <li><HighlightedText text={hit.content} terms={terms} /></li>
})

// Profilin pahalı gösterdiği hesaplama için useMemo (ör. 2D projeksiyon, iki modun sıralama karşılaştırması)
export function RankComparison({ vectorHits, keywordHits }: RankComparisonProps) {
  const overlap = useMemo(() => calculateOverlap(vectorHits, keywordHits), [vectorHits, keywordHits])
  return <p>{overlap.shared} / {overlap.total}</p>
}
```

**Teşhis:** React DevTools Profiler → "Why did this render?" ve "Highlight updates". Bir component prop'u değişmediği halde render oluyorsa sebep genelde parent'ın render içinde oluşturduğu nesne/fonksiyon ya da geniş bir context'tir.

**Context bölme:** Sık değişen değer (ör. ingestion ilerleme yüzdesi, `ef_search` slider'ının anlık değeri) ile nadiren değişen değeri (ör. çalışma alanı bilgisi) aynı context'te tutma; sık değişen her güncelleme tüm tüketicileri render eder. Ayrıntı: `react-composition-patterns`, `react-best-practices` (`rerender-*` kuralları).

**Slider ve arama:** `ef_search`/`limit` slider'ı sürüklenirken her değerde arama isteği atılmaz; değer `useDeferredValue` ile gösterilir, arama bırakınca (`onPointerUp`/`onChange` sonrası) ya da "Aramayı başlat" ile yapılır. Her arama ücretli bir embedding çağrısıdır.

## Sık güncellenen veri (iş durumu polling'i)

İçe alma ilerlemesi her saniye sorgulanır (`frontend-conventions` bölüm 8). Her sonucun geniş bir state'i ya da bütün kaynak listesini güncellemesi INP'yi bozar ve gereksiz istek üretir.

```tsx
// KÖTÜ: İlerlemeyi görmek için her saniye bütün kaynak listesini yeniden çek
useQuery({
  queryKey: sourceKeys.list(workspaceId),
  queryFn: () => listSources(workspaceId),
  refetchInterval: 1000,
})

// İYİ: Sadece aktif işleri kendi key'lerinde sorgula; iş bitince sorgu durur,
// liste ve detay sadece o anda bir kez invalidate edilir
useQueries({
  queries: activeJobs.map((job) =>
    queryOptions({
      queryKey: sourceKeys.ingestionJob(workspaceId, job.jobId),
      queryFn: () => getIngestionJob(workspaceId, job.jobId),
      initialData: job,
      refetchInterval: (query) => (isJobActive(query.state.data) ? INGESTION_POLL_INTERVAL_MS : false),
    }),
  ),
})
```

```tsx
// İYİ: Veriyi kullanan en dar component okur; tablo satırı ve sayfa bu veriye abone olmaz
export function IngestionProgress({ sourceName, job }: IngestionProgressProps) {
  const stageText = ingestionStageText(job)
  const percent = progressPercent(job)
  return (
    <div className={styles.progress}>
      <span className={styles.stage}>{stageText}</span>
      <span className={styles.track} role="progressbar" aria-label={`${sourceName} yükleme ilerlemesi`}
        aria-valuemin={0} aria-valuemax={100} aria-valuenow={percent} aria-valuetext={stageText}>
        <span className={styles.fill} style={{ width: `${percent}%` }} />
      </span>
    </div>
  )
}
```

- Satır sadece kendi işini okur (`useIngestionJobQuery`, istek atmaz); bir işin güncellenmesi diğer satırları render etmez.
- WebSocket gelirse aynı ilke geçerlidir: mesaj refetch tetiklemez, aynı `ingestionJob` key'ini hedefli günceller.

- Liste `key`'i stabil bir kimlik olmalı (`chunkId`, `sourceId`), dizi index'i değil.
- Saniyede çok sayıda mesaj geliyorsa her birini state'e yazmak yerine son değeri tut ve `requestAnimationFrame` ile ya da düşük öncelikli güncellemeyle (`startTransition`) yansıt; backend de mesajları batch başına seyreltir (`spring-websocket`).
- Hata/şekil kontrolü için `frontend-security` → gelen veriyi doğrulama.

## Büyük bundle

```tsx
// Vite, paket ESM yayınlıyorsa ve package.json'da `sideEffects: false` ise named import'ları tree-shake eder.
// Import stillerini değiştirmeden önce profile et — asıl kazanç bölme ve lazy loading'den gelir.

// İYİ: Ağır, nadir kullanılan özellik için dynamic import (ör. vektörleri 2D'de gösteren grafik)
// (proje kuralı: default export yok, import'lar göreli path + uzantıyla)
const EmbeddingScatterPlot = lazy(() =>
  import('./EmbeddingScatterPlot.tsx').then((m) => ({ default: m.EmbeddingScatterPlot })),
)

// İYİ: Route seviyesinde code splitting, Suspense ile sarılmış
const SearchPage = lazy(() =>
  import('../features/search/pages/SearchPage.tsx').then((m) => ({ default: m.SearchPage })),
)

export function AppRoutes() {
  return (
    <Suspense fallback={<PageSpinner />}>
      <Routes>
        <Route path="arama" element={<SearchPage />} />
      </Routes>
    </Suspense>
  )
}
```

- Kullanıcının bir sonraki adımda kesin ihtiyaç duyacağı chunk'ı önceden yükle (ör. anlatım sayfasındaki "Lab'da dene" linkine hover/focus'ta `import('./SearchPage.tsx')`) — `react-best-practices` (`bundle-preload`).
- İlk ekranda (anlatım sayfaları) gerekmeyen hiçbir şey ilk chunk'a girmemeli; anlatım sayfaları metin ağırlıklıdır ve LCP'leri hızlı olmalıdır.
- `@stomp/stompjs` ve grafik kütüphanesi gibi sadece lab/kaynak ekranlarında gereken kütüphaneler o route'ların chunk'ında kalmalı.

## Bundle'ı ölçmek

```bash
npm run build
# Vite her chunk için boyut + gzip boyutunu yazar:
# dist/assets/index-abc123.js   142.31 kB │ gzip: 45.80 kB
```

- Baseline olarak `npm run build` çıktısındaki gzip boyutlarını kaydet; değişiklikten sonra aynı komutla karşılaştır.
- Hangi modülün yer kapladığını görmek için bir görselleştirici (`rollup-plugin-visualizer`) geçici olarak çalıştırılabilir; kalıcı devDependency olarak eklemek plan kararıdır.
- Vite'ın "Some chunks are larger than 500 kB" uyarısı bir semptomdur; `build.chunkSizeWarningLimit`'i yükseltmek fix değildir.
