---
title: SVG Elemanı Yerine SVG Wrapper'ını Animate Et
impact: LOW
impactDescription: donanım hızlandırmayı mümkün kılar
tags: rendering, svg, css, animation, performance
---

## SVG Elemanı Yerine SVG Wrapper'ını Animate Et

Birçok tarayıcıda SVG elemanları üzerindeki CSS3 animasyonları için donanım hızlandırma (hardware acceleration) yoktur. SVG'yi bir `<div>` ile sar ve bunun yerine wrapper'ı animate et.

**Yanlış (SVG'yi doğrudan animate etmek - donanım hızlandırma yok):**

```tsx
function LoadingSpinner() {
  return (
    <svg 
      className="animate-spin"
      width="24" 
      height="24" 
      viewBox="0 0 24 24"
    >
      <circle cx="12" cy="12" r="10" stroke="currentColor" />
    </svg>
  )
}
```

**Doğru (wrapper div'i animate etmek - donanım hızlandırmalı):**

```tsx
function LoadingSpinner() {
  return (
    <div className="animate-spin">
      <svg 
        width="24" 
        height="24" 
        viewBox="0 0 24 24"
      >
        <circle cx="12" cy="12" r="10" stroke="currentColor" />
      </svg>
    </div>
  )
}
```

Bu, tüm CSS transform ve transition'ları için geçerlidir (`transform`, `opacity`, `translate`, `scale`, `rotate`). Wrapper div, tarayıcıların daha akıcı animasyonlar için GPU hızlandırma kullanmasını sağlar.
