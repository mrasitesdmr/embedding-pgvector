---
title: Kritik Olmayan Third-Party Kütüphaneleri Ertele
impact: MEDIUM
impactDescription: ilk render'dan sonra yüklenir
tags: bundle, third-party, analytics, defer
---

## Kritik Olmayan Third-Party Kütüphaneleri Ertele

Analytics, logging ve error tracking kullanıcı etkileşimini bloklamaz. Bunları uygulama ilk render'ını yaptıktan sonra yükle. (Orijinal kural Next.js'te `next/dynamic` + `ssr: false` kullanır; Vite SPA'da `React.lazy` ya da effect içinde `import()` kullanılır.)

**Yanlış (ilk bundle'ı bloklar):**

```tsx
import { Analytics } from 'some-analytics/react'

export function App() {
  return (
    <>
      <Routes />
      <Analytics />
    </>
  )
}
```

**Doğru (ilk render'dan sonra yüklenir):**

```tsx
import { lazy, Suspense } from 'react'

const Analytics = lazy(() =>
  import('some-analytics/react').then(m => ({ default: m.Analytics }))
)

export function App() {
  return (
    <>
      <Routes />
      <Suspense fallback={null}>
        <Analytics />
      </Suspense>
    </>
  )
}
```

UI'ı olmayan SDK'lar için (ör. error tracking init) `useEffect(() => { void import('./monitoring').then(m => m.init()) }, [])` yeterlidir.
