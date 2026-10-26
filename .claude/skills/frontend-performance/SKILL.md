---
name: frontend-performance
description: Ölçüm odaklı frontend performans iş akışı - önce ölç (Lighthouse, DevTools Performance, React Profiler, web-vitals), darboğazı belirle, tek şeyi düzelt, aynı koşullarda yeniden ölç, gürültü içindeki kazancı revert et, regression'a karşı budget koy. Core Web Vitals (LCP, INP, CLS) eşikleri, semptomdan ölçüme karar ağacı, bundle boyutu, code splitting, re-render ve görsel optimizasyonu. Ekran yavaş açıldığında, etkileşim takıldığında, canlı ingestion ilerlemesi/STOMP güncellemeleri ya da uzun arama sonucu listesi UI'ı yavaşlattığında, bundle büyüdüğünde veya bir performans iyileştirmesinin gerçekten işe yarayıp yaramadığına karar verirken kullan.
license: MIT
metadata:
  origin: addyosmani/agent-skills
  source: https://github.com/addyosmani/agent-skills/tree/main/skills/performance-optimization
  adapted: Frontend'e (React 19 + Vite SPA) daraltıldı; backend bölümleri (N+1, index, connection pool, cache) çıkarıldı — backend için sql-optimization-patterns, jpa-patterns, postgres-patterns.
---

# Frontend Performance Optimization

> **Proje kuralı:** *Neyin* hızlı olduğuna dair kurallar (waterfall, bundle, re-render pattern'leri) `react-best-practices`'tedir; bu skill *ne zaman ve nasıl* optimize edileceğini — ölçüm, karar ve regression koruması — belirler. Yeni bir dependency (ör. `web-vitals`, bundle analyzer) eklemek plan kararıdır (`frontend-conventions` bölüm 1).

## Genel bakış

Optimize etmeden önce ölç. Ölçümsüz performans çalışması tahmindir — ve tahmin, önemli olanı iyileştirmeden karmaşıklık ekleyen erken optimizasyona yol açar. Önce profile et, gerçek darboğazı belirle, düzelt, tekrar ölç. Sadece ölçümlerin önemli olduğunu kanıtladığı şeyi optimize et.

## Ne zaman kullanılır

- Spec'te performans gereksinimi varsa (yükleme süresi budget'ı)
- Kullanıcılar yavaşlık bildiriyorsa
- Core Web Vitals skorları eşiklerin altındaysa
- Bir değişikliğin regression getirdiğinden şüpheleniyorsan
- Büyük veri setleri veya sık güncellenen canlı veri (STOMP) işleyen feature'lar yazıyorsan

**Ne zaman KULLANMA:** Bir sorun olduğuna dair kanıtın yoksa optimize etme. Erken optimizasyon, kazandırdığı performanstan daha pahalıya mal olan karmaşıklık ekler.

## Core Web Vitals hedefleri

| Metrik | İyi | İyileştirme gerekli | Kötü |
|--------|-----|---------------------|------|
| **LCP** (Largest Contentful Paint) | ≤ 2.5s | ≤ 4.0s | > 4.0s |
| **INP** (Interaction to Next Paint) | ≤ 200ms | ≤ 500ms | > 500ms |
| **CLS** (Cumulative Layout Shift) | ≤ 0.1 | ≤ 0.25 | > 0.25 |

Bu proje için en kritik olanlar genellikle **INP** ve **LCP**'dir: arama gönderme, mod değiştirme ve ingestion ilerlemesi gibi canlı güncellemeler sırasında arayüzün takılmaması (INP); uzun konu anlatım sayfalarının ve lab ekranının hızlı açılması (LCP).

## Optimizasyon iş akışı

```
1. MEASURE  → Gerçek veriyle baseline oluştur
2. IDENTIFY → Gerçek darboğazı bul (varsayılanı değil)
3. FIX      → O belirli darboğazı ele al
4. VERIFY   → Tekrar ölç; tut ya da revert et
5. GUARD    → Regression'ı önlemek için budget veya monitoring ekle
```

### Adım 1: Ölç

Birbirini tamamlayan iki yaklaşım — ikisini de kullan:

- **Synthetic (Lighthouse, DevTools Performance sekmesi):** Kontrollü koşullar, tekrarlanabilir. CI'da regression tespiti ve belirli sorunları izole etmek için en iyisi.
- **RUM (`web-vitals` kütüphanesi):** Gerçek koşullardaki gerçek kullanıcı verisi. Bir fix'in kullanıcı deneyimini gerçekten iyileştirdiğini doğrulamak için gerekli.

```bash
# Production build üzerinde ölç — dev server (HMR, unminified) ölçüm için yanıltıcıdır
npm run build && npm run preview
# Chrome DevTools → Lighthouse (Mobile, throttling açık)
# Chrome DevTools → Performance → Record → etkileşimi yap → Stop
# React DevTools → Profiler → Record → "Highlight updates when components render"
```

```ts
// RUM (web-vitals eklenirse — plan kararı)
import { onCLS, onINP, onLCP } from 'web-vitals'

onLCP(console.log)
onINP(console.log)
onCLS(console.log)
```

### Nereden ölçmeye başlamalı

Neyi önce ölçeceğine karar vermek için semptomu kullan:

```
Ne yavaş?
├── İlk sayfa yüklemesi
│   ├── Büyük bundle mı? --> Bundle boyutunu ölç, code splitting'i kontrol et
│   ├── Yavaş sunucu yanıtı mı? --> DevTools Network waterfall'unda TTFB'yi ölç
│   │   └── Bekleme (server) uzun mu? --> Backend'i profile et (sql-optimization-patterns, observability-tracing)
│   └── Render-blocking kaynaklar mı? --> Network waterfall'da bloklayan CSS/JS/font'u kontrol et
├── Etkileşim ağır geliyor
│   ├── Tıklamada UI donuyor mu? --> Main thread'i profile et, long task'lara (>50ms) bak
│   ├── Input gecikmesi mi? --> Re-render'ları, controlled component maliyetini kontrol et
│   └── Animasyon takılıyor mu? --> Layout thrashing'i, forced reflow'ları kontrol et
├── Canlı güncellemeler (STOMP)
│   ├── Her mesajda tüm ekran mı render oluyor? --> React Profiler ile hangi component'lerin render olduğuna bak
│   └── Mesaj sıklığı yüksek mi? --> Mesaj başına state güncellemesi yerine batch'leme/birleştirme
├── Navigasyon sonrası sayfa
│   ├── Veri yükleme mi? --> API yanıt sürelerini ölç, request waterfall'larını kontrol et
│   └── Client rendering mi? --> Component render süresini profile et
└── Backend / API
    └── Bu skill'in kapsamı dışında --> sql-optimization-patterns, jpa-patterns, postgres-patterns
```

### Adım 2: Darboğazı belirle

| Semptom | Olası sebep | İnceleme |
|---------|-------------|----------|
| Yavaş LCP | Büyük görseller, render-blocking kaynaklar, yavaş sunucu, lazy route'un geç yüklenmesi | Network waterfall, görsel boyutları |
| Yüksek CLS | Boyutsuz görseller, geç yüklenen içerik, font kaymaları, yer tutucusuz loading state | Layout shift attribution |
| Kötü INP | Main thread'de ağır JavaScript, büyük DOM güncellemeleri, gereksiz re-render | Performance trace'te long task'lar, React Profiler |
| Yavaş ilk yükleme | Büyük bundle, çok sayıda network isteği | Bundle boyutu, code splitting |

### Adım 3: Darboğazı düzelt

Sadece Adım 2'nin belirlediği tek şeyi düzelt, başka hiçbir şeyi. Aşağıdaki anti-pattern'ler olağan şüphelilerdir; her biri kuralı ve tanıma imzasını verir ve [references/optimization-patterns.md](references/optimization-patterns.md) içindeki çalışılmış bir fix'e bağlanır. İhtiyacın olanı o koda geldiğinde aç, önceden değil. Ayrıntılı kural kataloğu için `react-best-practices`.

- **Optimize edilmemiş görseller.** Her görsel `width` ve `height` belirtir (CLS). LCP görseli `fetchpriority="high"` alır, modern formatlar (AVIF, WebP) `<picture>` ile, çözünürlük değişimi için `srcset`/`sizes`; ekranın altındaki görseller `loading="lazy"` ve `decoding="async"` alır. [Pattern](references/optimization-patterns.md#optimize-edilmemiş-görseller).
- **Gereksiz re-render'lar.** Render içinde oluşturulan nesne veya fonksiyon her seferinde yeni bir referanstır ve onu alan her child'ı yeniden render eder. Sabitleri dışarı taşı; `React.memo` ve `useMemo`'yu profilin pahalı olduğunu gösterdiği işler için sakla — aşırı kullanım da kendi başına maliyettir. React Compiler etkinse elle memoization çoğu zaman gereksizdir. [Pattern](references/optimization-patterns.md#gereksiz-re-renderlar).
- **Canlı veri fırtınası.** Her STOMP mesajı geniş bir context'i veya üst seviye state'i güncelliyorsa tüm ağaç render olur. Güncellemeyi veriyi kullanan en dar yere indir, TanStack Query cache'ini `setQueryData` ile hedefli güncelle, liste öğelerine stabil `key` ver. [Pattern](references/optimization-patterns.md#canlı-veri-fırtınası).
- **Büyük bundle.** Vite ESM named import'ları zaten tree-shake eder; asıl kazanç route seviyesinde code splitting ve ağır, nadir kullanılan özellikleri `Suspense` arkasında lazy-load etmektir. Import stillerini değiştirmeden önce profile et. [Pattern](references/optimization-patterns.md#büyük-bundle).
- **Uzun listeler.** Yüzlerce öğelik liste (ör. bir kaynağın parçaları ya da 50 sonuçluk arama listesi) her güncellemede tamamen render oluyorsa önce `key`/memoization'ı kontrol et; ölçüm hâlâ yetersiz diyorsa virtualization (yeni dependency → plan kararı).

### Adım 4: Doğrula (tut ya da revert et)

Bir fix, yeniden ölçene kadar bir hipotezdir. Bu adım hayatta kalıp kalmayacağına karar verir.

**Baseline'ı ölçtüğün şekilde yeniden ölç:** aynı komut, aynı koşullar (production build, aynı throttling, aynı cihaz profili), aynı sabit budget (süre, örnek sayısı). Soğuk cache'le alınmış bir baseline'ı sıcak cache'le alınmış bir sonuçla karşılaştırmak değişikliğini değil cache'i ölçer.

**Her seferinde tek şeyi değiştir.** Birlikte gelen üç optimizasyon tek bir sayı üretir ve onu hiçbirine atfedemezsin. Birlikte ship edilmeleri gerekiyorsa önce her birini ayrı ölç.

**Ortalamayı değil gürültüyü yen.** Ölçümü tekrarla ve farkı çalıştırmadan çalıştırmaya değişkenlikle karşılaştır. ±%5 değişkenlik içindeki %3'lük kazanç bir kazanç değil, farklı bir örneklemdir.

Sonra kesin karar ver:

| Baseline'a göre sonuç | Aksiyon |
|---|---|
| Eşiğin ötesinde, testler yeşil | **Tut.** Önce/sonra sayılarını commit mesajına yaz. |
| Gürültü içinde (ölçülebilir değişiklik yok) | **Revert et.** |
| Daha kötü | **Revert et.** |
| İyileşti, ama bir test kırmızıya döndü | **Revert et.** Kazanç kılığına girmiş bir regression. |

**"Nötr" bir revert'tir, tutma değil.** Ekiplerin atladığı adım budur: değişiklik zaten yazılmış, atmak israf gibi geliyor, ölçülmeden giriyor ve codebase hiçbir şey kazandırmamış karmaşıklık biriktiriyor. Tuttuğun kodun bakımını sonsuza kadar yaparsın. Kendi maliyetini çıkarsın.

**Doğruluk metriği kapılar.** Test suite yeşil kalır *ve* sayı hareket eder. Ürünün ihtiyaç duyduğu işi atlayarak kazanan bir "optimizasyon" (bir validation'ı atlamak, taze olması gereken bir şeyi cache'lemek, yük taşıyan bir `await`'i kaldırmak) bir regression'dır, kazanç değil.

#### Revert edilenler dahil her denemeyi kaydet

Revert edilen iş git geçmişinde iz bırakmaz; aynı ölü fikrin bir sonraki çeyrekte yeniden denenmesinin sebebi tam olarak budur. Atılan bir fikrin atılmış kalması için kısa bir kayıt tut:

| Fikir | Baseline → Sonuç | Karar | Neden |
|---|---|---|---|
| Arama sonucu satırını memoize et | INP 240ms → 235ms | revert | Gürültü içinde (±15ms). Darboğaz satırlar değildi. |
| Ingestion ilerlemesi güncellemesini hedefli `setQueryData` yap | INP 240ms → 90ms | tutuldu | Trace'teki long task'lar kayboldu. |
| API origin'ine preconnect | LCP 2.8s → 2.8s | revert | Zaten aynı origin. |

İlgili `handoff/` dosyası veya PR açıklamasındaki bir bölüm yeterlidir. Önemli olan, bir sonraki kişinin (veya agent'ın) deney önermeden önce bunu okuması ve zaten başarısız olmuş birini tekrar çalıştırmamasıdır.

### Adım 5: Regression'a karşı koru

Mevcut her sayıyı değil, kullanıcının gerçekten hissettiği metriği koru — fix'i gerekçelendiren aynı LCP, INP veya diğer ana metrik.

Kullanıcıya dönük yüzeylerde iki tamamlayıcı katman kullan:

- **Synthetic CI gate'i:** Tekrarlanabilir regression'ları merge'den önce bir performans budget'ı ile yakala. Gürültülü ölçümleri tekrarla veya medyan/trend karşılaştır ki normal değişkenlik gate'i flaky bir kontrole çevirmesin.
- **Field monitoring:** RUM verisinde p75'teki anlamlı bir hareket için alarm kur. Sebebi bulmak için attribution'lı `web-vitals` verisini kullan.

Herhangi bir koruma tetiklendiğinde, başka bir fix önermeden önce Adım 1'e dön ve taze bir baseline oluştur.

**Budget'ları belirle ve uygula:**

```
JavaScript bundle: < 200KB gzipped (ilk yükleme)
CSS: < 50KB gzipped
Görseller: < 200KB görsel başına (ekranın üstü)
Font'lar: < 100KB toplam
Time to Interactive: < 3.5s (4G'de)
Lighthouse Performance skoru: ≥ 90
```

**CI'da uygula:**
```bash
# Bundle boyutu: vite build çıktısı her chunk'ın gzip boyutunu yazar
npm run build

# Lighthouse CI (eklenirse)
npx lhci autorun
```

## Yaygın bahaneler

| Bahane | Gerçek |
|---|---|
| "Sonra optimize ederiz" | Performans borcu katlanır. Bariz anti-pattern'leri şimdi düzelt, mikro-optimizasyonları ertele. |
| "Benim makinemde hızlı" | Senin makinen kullanıcınınki değil. Temsili donanım ve ağda (throttling) profile et. |
| "Bu optimizasyon bariz" | Ölçmediysen bilmiyorsun. Önce profile et. |
| "Kullanıcılar 100ms'yi fark etmez" | INP eşiği 200ms; etkileşim gecikmesini kullanıcılar sandığından fazla fark eder. |
| "Framework performansı halleder" | Framework'ler bazı sorunları önler ama büyük bundle'ları veya gereksiz geniş state güncellemelerini düzeltemez. |
| "Her yere `useMemo` koyalım" | Ölçmeden memoization, kazandırmadığı karmaşıklık ve bellek maliyeti ekler. |
| "Pek yardımcı olmadı ama zararı da yok" | Nötr değişiklikler revert edilir. Bakımını sonsuza kadar ödersin, karşılığında hiçbir şey almadın. |
| "Zaten yazdık, tutalım" | Batık maliyet. Ölçüm, değişikliğin yazılmasının ne kadar sürdüğünü umursamaz. |
| "İyileşme bariz, yeniden ölçmeye gerek yok" | O zaman yeniden ölçmek ucuzdur ve kanıtlar. Ölçülmemiş kazançlar, nötr karmaşıklığın yerleşme yoludur. |

## Kırmızı bayraklar

- Gerekçelendiren profiling verisi olmayan optimizasyon
- Dev server üzerinde yapılmış performans ölçümü
- Boyutsuz, lazy loading'siz veya responsive boyutsuz görseller
- İncelenmeden büyüyen bundle boyutu
- Her yerde `React.memo` ve `useMemo` (aşırı kullanmak az kullanmak kadar kötüdür)
- Her STOMP mesajında tüm ekranın yeniden render olması
- Gerekçelendiren bir yeniden ölçüm olmadan tutulan optimizasyonlar
- Tek bir ölçüme toplanmış birden fazla optimizasyon — hiçbir değişikliğe atfedilemez
- Bir testin değiştirilmesini, atlanmasını veya silinmesini gerektiren bir "kazanç"
- İlk deneme kaydedilmediği için birden fazla kez denenen aynı başarısız optimizasyon

## Doğrulama

Performansla ilgili herhangi bir değişiklikten sonra:

- [ ] Önce ve sonra ölçümleri var (spesifik sayılar)
- [ ] Sonuç baseline ile aynı şekilde yeniden ölçüldü (production build, aynı komut, aynı koşullar)
- [ ] İyileşme sadece ortalamayı değil, çalıştırmalar arası değişkenliği de aşıyor
- [ ] Baseline'ı geçemeyen değişiklikler nötr diye tutulmadı, revert edildi
- [ ] Tutulan ve revert edilen tüm denemeler kaydedildi
- [ ] Belirli darboğaz belirlendi ve ele alındı
- [ ] Core Web Vitals "İyi" eşiklerinde
- [ ] Bundle boyutu anlamlı şekilde artmadı
- [ ] Ölçülen kullanıcıya dönük metriğin regression'ı tespit edebilecek bir budget'ı veya monitörü var
- [ ] Mevcut testler hâlâ geçiyor (optimizasyon davranışı bozmadı)
