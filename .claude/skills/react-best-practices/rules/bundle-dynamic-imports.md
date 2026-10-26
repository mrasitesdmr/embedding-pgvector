---
title: Ağır Component'ler için Dynamic Import
impact: CRITICAL
impactDescription: TTI ve LCP'yi doğrudan etkiler
tags: bundle, dynamic-import, code-splitting, react-lazy
---

## Ağır Component'ler için Dynamic Import

İlk render'da gerekmeyen büyük component'leri lazy-load etmek için `React.lazy` + `Suspense` kullan. (Orijinal kural Next.js'in `next/dynamic`'ini kullanır; Vite + React'te karşılığı `React.lazy`'dir. Vite her `import()` için otomatik olarak ayrı bir chunk üretir.)

**Yanlış (Monaco ana chunk ile birlikte bundle edilir, ~300KB):**

```tsx
import { MonacoEditor } from './monaco-editor'

function CodePanel({ code }: { code: string }) {
  return <MonacoEditor value={code} />
}
```

**Doğru (Monaco ihtiyaç anında yüklenir):**

```tsx
import { lazy, Suspense } from 'react'

const MonacoEditor = lazy(() =>
  import('./monaco-editor').then(m => ({ default: m.MonacoEditor }))
)

function CodePanel({ code }: { code: string }) {
  return (
    <Suspense fallback={<EditorSkeleton />}>
      <MonacoEditor value={code} />
    </Suspense>
  )
}
```

Route bazlı code splitting de aynı yöntemle yapılır. Projede default export olmadığı için (`frontend-conventions` bölüm 5) named export `default`'a map'lenir ve import uzantıyla yazılır: `lazy(() => import('../features/search/pages/SearchPage.tsx').then(m => ({ default: m.SearchPage })))`.
