---
name: react-best-practices
description: Vercel Engineering'in React performans optimizasyonu kuralları (Vite SPA'ya uyarlanmış). React kodu yazarken, review ederken veya refactor ederken optimal performans pattern'lerini sağlamak için kullan. React component'leri, data fetching, bundle optimizasyonu, re-render sorunları veya performans iyileştirmesi içeren işlerde tetiklenir.
license: MIT
metadata:
  origin: vercel-labs/agent-skills
  source: https://github.com/vercel-labs/agent-skills/tree/main/skills/react-best-practices
  version: "1.0.0"
---

# React Best Practices

Vercel tarafından sürdürülen, React uygulamaları için kapsamlı performans optimizasyonu rehberi. Kurallar, otomatik refactoring ve kod üretimine yön vermek için etkiye göre önceliklendirilmiştir.

**Proje kuralı:** Dosya yapısı, isimlendirme, kod biçimi ve teknoloji seçimi `frontend-conventions`'tadır; kural dosyalarındaki genel örnekler (Tailwind class'ları, `@mui`, `lucide-react`, SWR, İngilizce UI metni) kuralı göstermek içindir, projede kullanılmaz.

**Bu projeye uyarlama:** Proje Vite + React 19 SPA'dır (Next.js, SSR, Server Components yok). Bu yüzden orijinaldeki Server-Side Performance kategorisi (`server-*`, 10 kural), `async-api-routes` ve hydration kuralları (`rendering-hydration-*`) çıkarıldı. `next/dynamic` kullanan kurallar `React.lazy` karşılığıyla yeniden yazıldı.

## Ne zaman uygulanır

Bu kurallara şu durumlarda başvur:
- Yeni React component'leri veya sayfaları yazarken
- Data fetching uygularken
- Kodu performans sorunları açısından review ederken
- Mevcut React kodunu refactor ederken
- Bundle boyutunu veya yükleme sürelerini optimize ederken

## Önceliğe göre kural kategorileri

| Öncelik | Kategori | Etki | Prefix |
|----------|----------|--------|--------|
| 1 | Waterfall'ları Ortadan Kaldırma | CRITICAL | `async-` |
| 2 | Bundle Boyutu Optimizasyonu | CRITICAL | `bundle-` |
| 3 | Client-Side Data Fetching | MEDIUM-HIGH | `client-` |
| 4 | Re-render Optimizasyonu | MEDIUM | `rerender-` |
| 5 | Rendering Performansı | MEDIUM | `rendering-` |
| 6 | JavaScript Performansı | LOW-MEDIUM | `js-` |
| 7 | Advanced Pattern'ler | LOW | `advanced-` |

**Kategori açıklamaları:**
- **Waterfall'lar** bir numaralı performans katilidir. Her sıralı `await` tam bir network gecikmesi ekler; bunları ortadan kaldırmak en büyük kazancı sağlar.
- **Bundle boyutu**: İlk bundle'ı küçültmek Time to Interactive ve Largest Contentful Paint'i iyileştirir.
- **Client-side data fetching**: Otomatik deduplication ve verimli fetching pattern'leri gereksiz network request'lerini azaltır.
- **Re-render**: Gereksiz re-render'ları azaltmak boşa giden hesaplamayı en aza indirir ve UI tepkiselliğini artırır.
- **Rendering**: Rendering sürecini optimize etmek tarayıcının yapması gereken işi azaltır.
- **JavaScript**: Sık çalışan yollar (hot path) için mikro optimizasyonlar anlamlı iyileşmelere dönüşebilir.
- **Advanced**: Dikkatli implementasyon gerektiren özel durumlar için ileri pattern'ler.

## Hızlı referans

### 1. Waterfall'ları Ortadan Kaldırma (CRITICAL)

- [async-cheap-condition-before-await](rules/async-cheap-condition-before-await.md) - Flag'leri veya remote değerleri await etmeden önce ucuz senkron koşulları kontrol et
- [async-defer-await](rules/async-defer-await.md) - await'i gerçekten kullanıldığı branch'lere taşı
- [async-parallel](rules/async-parallel.md) - Bağımsız işlemler için Promise.all() kullan
- [async-dependencies](rules/async-dependencies.md) - Kısmi bağımlılıklar için better-all kullan
- [async-suspense-boundaries](rules/async-suspense-boundaries.md) - İçeriği aşamalı göstermek için Suspense kullan

### 2. Bundle Boyutu Optimizasyonu (CRITICAL)

- [bundle-barrel-imports](rules/bundle-barrel-imports.md) - Doğrudan import et, barrel file'lardan kaçın
- [bundle-analyzable-paths](rules/bundle-analyzable-paths.md) - Geniş bundle'lardan kaçınmak için statik analiz edilebilir import ve dosya path'lerini tercih et
- [bundle-dynamic-imports](rules/bundle-dynamic-imports.md) - Ağır component'ler için React.lazy kullan
- [bundle-defer-third-party](rules/bundle-defer-third-party.md) - Analytics/logging'i ilk render'dan sonra yükle
- [bundle-conditional](rules/bundle-conditional.md) - Modülleri sadece özellik aktifleşince yükle
- [bundle-preload](rules/bundle-preload.md) - Algılanan hız için hover/focus'ta preload et

### 3. Client-Side Data Fetching (MEDIUM-HIGH)

- [client-swr-dedup](rules/client-swr-dedup.md) - Otomatik request deduplication için TanStack Query kullan (genel kuralda SWR de geçer; projede TanStack Query)
- [client-event-listeners](rules/client-event-listeners.md) - Global event listener'ları tekilleştir
- [client-passive-event-listeners](rules/client-passive-event-listeners.md) - Scroll için passive listener kullan
- [client-localstorage-schema](rules/client-localstorage-schema.md) - localStorage verisini versiyonla ve küçült

### 4. Re-render Optimizasyonu (MEDIUM)

- [rerender-defer-reads](rules/rerender-defer-reads.md) - Sadece callback'lerde kullanılan state'e subscribe olma
- [rerender-memo](rules/rerender-memo.md) - Pahalı işi memoize edilmiş component'lere ayır
- [rerender-memo-with-default-value](rules/rerender-memo-with-default-value.md) - Primitive olmayan default prop'ları sabite taşı
- [rerender-dependencies](rules/rerender-dependencies.md) - Effect'lerde primitive dependency kullan
- [rerender-derived-state](rules/rerender-derived-state.md) - Ham değerlere değil türetilmiş boolean'lara subscribe ol
- [rerender-derived-state-no-effect](rules/rerender-derived-state-no-effect.md) - State'i effect'te değil render sırasında türet
- [rerender-functional-setstate](rules/rerender-functional-setstate.md) - Stabil callback'ler için functional setState kullan
- [rerender-lazy-state-init](rules/rerender-lazy-state-init.md) - Pahalı değerler için useState'e function geçir
- [rerender-simple-expression-in-memo](rules/rerender-simple-expression-in-memo.md) - Basit primitive'ler için memo kullanma
- [rerender-split-combined-hooks](rules/rerender-split-combined-hooks.md) - Bağımsız dependency'leri olan hook'ları ayır
- [rerender-move-effect-to-event](rules/rerender-move-effect-to-event.md) - Etkileşim mantığını event handler'lara koy
- [rerender-transitions](rules/rerender-transitions.md) - Acil olmayan güncellemeler için startTransition kullan
- [rerender-use-deferred-value](rules/rerender-use-deferred-value.md) - Input'u tepkisel tutmak için pahalı render'ları ertele
- [rerender-use-ref-transient-values](rules/rerender-use-ref-transient-values.md) - Geçici ve sık değişen değerler için ref kullan
- [rerender-no-inline-components](rules/rerender-no-inline-components.md) - Component içinde component tanımlama

### 5. Rendering Performansı (MEDIUM)

- [rendering-animate-svg-wrapper](rules/rendering-animate-svg-wrapper.md) - SVG elemanını değil wrapper div'i animate et
- [rendering-content-visibility](rules/rendering-content-visibility.md) - Uzun listeler için content-visibility kullan
- [rendering-hoist-jsx](rules/rendering-hoist-jsx.md) - Statik JSX'i component'lerin dışına çıkar
- [rendering-svg-precision](rules/rendering-svg-precision.md) - SVG koordinat hassasiyetini düşür
- [rendering-activity](rules/rendering-activity.md) - Göster/gizle için Activity component'ini kullan
- [rendering-conditional-render](rules/rendering-conditional-render.md) - Koşullar için && değil ternary kullan
- [rendering-usetransition-loading](rules/rendering-usetransition-loading.md) - Loading state için useTransition'ı tercih et
- [rendering-resource-hints](rules/rendering-resource-hints.md) - Preload için React DOM resource hint'lerini kullan
- [rendering-script-defer-async](rules/rendering-script-defer-async.md) - Script tag'lerinde defer veya async kullan

### 6. JavaScript Performansı (LOW-MEDIUM)

- [js-batch-dom-css](rules/js-batch-dom-css.md) - CSS değişikliklerini class'lar veya cssText ile grupla
- [js-index-maps](rules/js-index-maps.md) - Tekrarlanan aramalar için Map kur
- [js-cache-property-access](rules/js-cache-property-access.md) - Döngülerde object property'lerini cache'le
- [js-cache-function-results](rules/js-cache-function-results.md) - Function sonuçlarını module-level Map'te cache'le
- [js-cache-storage](rules/js-cache-storage.md) - localStorage/sessionStorage okumalarını cache'le
- [js-combine-iterations](rules/js-combine-iterations.md) - Birden fazla filter/map'i tek döngüde birleştir
- [js-length-check-first](rules/js-length-check-first.md) - Pahalı karşılaştırmadan önce array uzunluğunu kontrol et
- [js-early-exit](rules/js-early-exit.md) - Function'lardan erken dön
- [js-hoist-regexp](rules/js-hoist-regexp.md) - RegExp oluşturmayı döngülerin dışına taşı
- [js-min-max-loop](rules/js-min-max-loop.md) - Min/max için sort yerine döngü kullan
- [js-set-map-lookups](rules/js-set-map-lookups.md) - O(1) arama için Set/Map kullan
- [js-tosorted-immutable](rules/js-tosorted-immutable.md) - Immutability için toSorted() kullan
- [js-flatmap-filter](rules/js-flatmap-filter.md) - Map ve filter'ı tek geçişte yapmak için flatMap kullan
- [js-request-idle-callback](rules/js-request-idle-callback.md) - Kritik olmayan işi tarayıcının boş zamanına ertele

### 7. Advanced Pattern'ler (LOW)

- [advanced-effect-event-deps](rules/advanced-effect-event-deps.md) - `useEffectEvent` sonuçlarını effect dependency'lerine koyma
- [advanced-event-handler-refs](rules/advanced-event-handler-refs.md) - Event handler'ları ref'lerde sakla
- [advanced-init-once](rules/advanced-init-once.md) - Uygulamayı yükleme başına bir kez initialize et
- [advanced-use-latest](rules/advanced-use-latest.md) - Effect'i yeniden çalıştırmadan güncel callback için useEffectEvent

## Nasıl kullanılır

Ayrıntılı açıklamalar ve kod örnekleri için ilgili kural dosyasını oku. Her kural dosyası şunları içerir:
- Neden önemli olduğuna dair kısa açıklama
- Açıklamalı yanlış kod örneği
- Açıklamalı doğru kod örneği
- Ek bağlam ve referanslar

Review yaparken önce CRITICAL kategorilerden başla; LOW kategorilerdeki kuralları sadece ölçülmüş bir sorun ya da sık çalışan bir kod yolu varsa uygula.
