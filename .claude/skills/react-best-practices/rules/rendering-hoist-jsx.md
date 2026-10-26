---
title: Statik JSX Elemanlarını Dışarı Taşı
impact: LOW
impactDescription: yeniden oluşturmadan kaçınır
tags: rendering, jsx, static, optimization
---

## Statik JSX Elemanlarını Dışarı Taşı

Yeniden oluşturulmalarını önlemek için statik JSX'i component'lerin dışına çıkar.

**Yanlış (her render'da elemanı yeniden oluşturur):**

```tsx
function LoadingSkeleton() {
  return <div className="animate-pulse h-20 bg-gray-200" />
}

function Container() {
  return (
    <div>
      {loading && <LoadingSkeleton />}
    </div>
  )
}
```

**Doğru (aynı elemanı yeniden kullanır):**

```tsx
const loadingSkeleton = (
  <div className="animate-pulse h-20 bg-gray-200" />
)

function Container() {
  return (
    <div>
      {loading && loadingSkeleton}
    </div>
  )
}
```

Bu, özellikle her render'da yeniden oluşturulması pahalı olabilen büyük ve statik SVG node'ları için faydalıdır.

**Not:** Projende [React Compiler](https://react.dev/learn/react-compiler) etkinse compiler statik JSX elemanlarını otomatik olarak dışarı taşır ve component re-render'larını optimize eder; manuel taşıma gereksiz hale gelir.
