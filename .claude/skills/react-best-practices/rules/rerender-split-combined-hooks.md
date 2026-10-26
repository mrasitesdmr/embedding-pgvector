---
title: Birleştirilmiş Hook Hesaplamalarını Ayır
impact: MEDIUM
impactDescription: bağımsız adımların yeniden hesaplanmasından kaçınır
tags: rerender, useMemo, useEffect, dependencies, optimization
---

## Birleştirilmiş Hook Hesaplamalarını Ayır

Bir hook farklı dependency'leri olan birden fazla bağımsız iş içeriyorsa bunları ayrı hook'lara böl. Birleştirilmiş bir hook, herhangi bir dependency değiştiğinde, bazı işler değişen değeri kullanmasa bile tüm işleri yeniden çalıştırır.

**Yanlış (`sortOrder` değişince filtreleme yeniden hesaplanır):**

```tsx
const sortedProducts = useMemo(() => {
  const filtered = products.filter((p) => p.category === category)
  const sorted = filtered.toSorted((a, b) =>
    sortOrder === "asc" ? a.price - b.price : b.price - a.price
  )
  return sorted
}, [products, category, sortOrder])
```

**Doğru (filtreleme sadece products veya category değişince yeniden hesaplanır):**

```tsx
const filteredProducts = useMemo(
  () => products.filter((p) => p.category === category),
  [products, category]
)

const sortedProducts = useMemo(
  () =>
    filteredProducts.toSorted((a, b) =>
      sortOrder === "asc" ? a.price - b.price : b.price - a.price
    ),
  [filteredProducts, sortOrder]
)
```

Bu pattern birbiriyle ilgisiz side effect'leri birleştiren `useEffect`'ler için de geçerlidir:

**Yanlış (herhangi bir dependency değişince iki effect de çalışır):**

```tsx
useEffect(() => {
  analytics.trackPageView(pathname)
  document.title = `${pageTitle} | My App`
}, [pathname, pageTitle])
```

**Doğru (effect'ler bağımsız çalışır):**

```tsx
useEffect(() => {
  analytics.trackPageView(pathname)
}, [pathname])

useEffect(() => {
  document.title = `${pageTitle} | My App`
}, [pageTitle])
```

**Not:** Projende [React Compiler](https://react.dev/learn/react-compiler) etkinse dependency takibini otomatik optimize eder ve bu durumların bazılarını senin yerine halledebilir.
