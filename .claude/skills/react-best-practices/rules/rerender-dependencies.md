---
title: Effect Dependency'lerini Daralt
impact: LOW
impactDescription: effect'in yeniden çalışmasını en aza indirir
tags: rerender, useEffect, dependencies, optimization
---

## Effect Dependency'lerini Daralt

Effect'in yeniden çalışmasını en aza indirmek için object yerine primitive dependency'ler belirt.

**Yanlış (user'ın herhangi bir alanı değişince yeniden çalışır):**

```tsx
useEffect(() => {
  console.log(user.id)
}, [user])
```

**Doğru (sadece id değişince yeniden çalışır):**

```tsx
useEffect(() => {
  console.log(user.id)
}, [user.id])
```

**Türetilmiş state için hesaplamayı effect'in dışında yap:**

```tsx
// Yanlış: width=767, 766, 765... için çalışır
useEffect(() => {
  if (width < 768) {
    enableMobileMode()
  }
}, [width])

// Doğru: sadece boolean değiştiğinde çalışır
const isMobile = width < 768
useEffect(() => {
  if (isMobile) {
    enableMobileMode()
  }
}, [isMobile])
```
