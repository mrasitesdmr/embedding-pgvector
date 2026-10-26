---
title: Storage API Çağrılarını Cache'le
impact: LOW-MEDIUM
impactDescription: pahalı I/O'yu azaltır
tags: javascript, localStorage, storage, caching, performance
---

## Storage API Çağrılarını Cache'le

`localStorage`, `sessionStorage` ve `document.cookie` senkron ve pahalıdır. Okumaları bellekte cache'le.

**Yanlış (her çağrıda storage'dan okur):**

```typescript
function getTheme() {
  return localStorage.getItem('theme') ?? 'light'
}
// 10 kez çağrılırsa = 10 storage okuması
```

**Doğru (Map cache):**

```typescript
const storageCache = new Map<string, string | null>()

function getLocalStorage(key: string) {
  if (!storageCache.has(key)) {
    storageCache.set(key, localStorage.getItem(key))
  }
  return storageCache.get(key)
}

function setLocalStorage(key: string, value: string) {
  localStorage.setItem(key, value)
  storageCache.set(key, value)  // cache'i senkron tut
}
```

Her yerde çalışması için (sadece React component'lerinde değil; utility'lerde, event handler'larda da) hook değil Map kullan.

**Cookie cache'leme:**

```typescript
let cookieCache: Record<string, string> | null = null

function getCookie(name: string) {
  if (!cookieCache) {
    cookieCache = Object.fromEntries(
      document.cookie.split('; ').map(c => c.split('='))
    )
  }
  return cookieCache[name]
}
```

**Önemli (dış değişikliklerde cache'i geçersiz kıl):**

Storage dışarıdan değişebiliyorsa (başka bir sekme, server'ın set ettiği cookie'ler) cache'i geçersiz kıl:

```typescript
window.addEventListener('storage', (e) => {
  if (e.key) storageCache.delete(e.key)
})

document.addEventListener('visibilitychange', () => {
  if (document.visibilityState === 'visible') {
    storageCache.clear()
  }
})
```
