---
title: Kritik Olmayan İşleri requestIdleCallback ile Ertele
impact: MEDIUM
impactDescription: arka plan işleri sırasında UI'ı tepkisel tutar
tags: javascript, performance, idle, scheduling, analytics
---

## Kritik Olmayan İşleri requestIdleCallback ile Ertele

**Etki: MEDIUM (arka plan işleri sırasında UI'ı tepkisel tutar)**

Kritik olmayan işleri tarayıcının boşta olduğu zamanlara planlamak için `requestIdleCallback()` kullan. Bu, main thread'i kullanıcı etkileşimleri ve animasyonlar için serbest bırakır; takılmaları (jank) azaltır ve algılanan performansı artırır.

**Yanlış (kullanıcı etkileşimi sırasında main thread'i bloklar):**

```typescript
function handleSearch(query: string) {
  const results = searchItems(query)
  setResults(results)

  // Bunlar main thread'i hemen bloklar
  analytics.track('search', { query })
  saveToRecentSearches(query)
  prefetchTopResults(results.slice(0, 3))
}
```

**Doğru (kritik olmayan işi boşta kalınan zamana erteler):**

```typescript
function handleSearch(query: string) {
  const results = searchItems(query)
  setResults(results)

  // Kritik olmayan işi boşta kalınan zamanlara ertele
  requestIdleCallback(() => {
    analytics.track('search', { query })
  })

  requestIdleCallback(() => {
    saveToRecentSearches(query)
  })

  requestIdleCallback(() => {
    prefetchTopResults(results.slice(0, 3))
  })
}
```

**Mutlaka yapılması gereken iş için timeout ile:**

```typescript
// Tarayıcı meşgul kalsa bile analytics'in 2 saniye içinde gönderilmesini sağla
requestIdleCallback(
  () => analytics.track('page_view', { path: location.pathname }),
  { timeout: 2000 }
)
```

**Büyük işleri parçalara bölmek:**

```typescript
function processLargeDataset(items: Item[]) {
  let index = 0

  function processChunk(deadline: IdleDeadline) {
    // Boş zaman varken elemanları işle (<50ms'lik parçalar hedefle)
    while (index < items.length && deadline.timeRemaining() > 0) {
      processItem(items[index])
      index++
    }

    // Eleman kaldıysa bir sonraki parçayı planla
    if (index < items.length) {
      requestIdleCallback(processChunk)
    }
  }

  requestIdleCallback(processChunk)
}
```

**Desteklemeyen tarayıcılar için fallback ile:**

```typescript
const scheduleIdleWork = window.requestIdleCallback ?? ((cb: () => void) => setTimeout(cb, 1))

scheduleIdleWork(() => {
  // Kritik olmayan iş
})
```

**Ne zaman kullanılır:**

- Analytics ve telemetry
- State'i localStorage/IndexedDB'ye kaydetmek
- Muhtemel sonraki eylemler için kaynakları prefetch etmek
- Acil olmayan veri dönüşümlerini işlemek
- Kritik olmayan özelliklerin lazy initialization'ı

**Ne zaman KULLANILMAZ:**

- Anında geri bildirim gerektiren kullanıcı eylemleri
- Kullanıcının beklediği render güncellemeleri
- Zamana duyarlı işlemler (ör. WebSocket mesajlarını işlemek)
