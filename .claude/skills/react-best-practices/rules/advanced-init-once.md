---
title: Uygulamayı Mount Başına Değil Bir Kez Initialize Et
impact: LOW-MEDIUM
impactDescription: development'ta tekrarlanan init'ten kaçınır
tags: initialization, useEffect, app-startup, side-effects
---

## Uygulamayı Mount Başına Değil Bir Kez Initialize Et

Uygulama yüklendiğinde bir kez çalışması gereken uygulama çapındaki initialization'ı bir component'in `useEffect([])`'i içine koyma. Component'ler remount olabilir ve effect'ler yeniden çalışır (StrictMode'da development'ta her effect iki kez çalışır). Bunun yerine module-level bir guard ya da entry modülünde (`src/main.tsx`) top-level init kullan.

**Yanlış (dev'de iki kez çalışır, remount'ta yeniden çalışır):**

```tsx
function Comp() {
  useEffect(() => {
    loadFromStorage()
    checkAuthToken()
  }, [])

  // ...
}
```

**Doğru (uygulama yüklemesi başına bir kez):**

```tsx
let didInit = false

function Comp() {
  useEffect(() => {
    if (didInit) return
    didInit = true
    loadFromStorage()
    checkAuthToken()
  }, [])

  // ...
}
```

Not: WebSocket/STOMP bağlantısı gibi temizlenmesi gereken kaynaklar bu kuralın kapsamında değildir; onları effect içinde kur ve cleanup'ta kapat, StrictMode'un çift çalıştırmasına dayanıklı olsunlar.

Referans: [Initializing the application](https://react.dev/learn/you-might-not-need-an-effect#initializing-the-application)
