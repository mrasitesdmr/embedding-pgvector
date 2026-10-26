---
title: Türetilmiş State'e Subscribe Ol
impact: MEDIUM
impactDescription: re-render sıklığını azaltır
tags: rerender, derived-state, media-query, optimization
---

## Türetilmiş State'e Subscribe Ol

Re-render sıklığını azaltmak için sürekli değişen değerler yerine türetilmiş boolean state'e subscribe ol.

**Yanlış (her piksel değişiminde re-render olur):**

```tsx
function Sidebar() {
  const width = useWindowWidth()  // sürekli güncellenir
  const isMobile = width < 768
  return <nav className={isMobile ? 'mobile' : 'desktop'} />
}
```

**Doğru (sadece boolean değişince re-render olur):**

```tsx
function Sidebar() {
  const isMobile = useMediaQuery('(max-width: 767px)')
  return <nav className={isMobile ? 'mobile' : 'desktop'} />
}
```
