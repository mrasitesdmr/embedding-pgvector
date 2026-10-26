---
title: Global Event Listener'ları Tekilleştir
impact: LOW
impactDescription: N component için tek listener
tags: client, event-listeners, subscription, useSyncExternalStore
---

## Global Event Listener'ları Tekilleştir

Aynı global event'i dinleyen birçok component varsa, her instance'ın kendi listener'ını kaydetmesi yerine tek bir module-level listener paylaş. Orijinal Vercel kuralı bunu SWR'ın `useSWRSubscription()`'ı ile gösterir; bu projede SWR olmadığı için (`frontend-conventions` bölüm 1) aynı fikir module-level kayıt + referans sayımıyla uygulanır.

**Yanlış (N instance = N listener):**

```tsx
function useKeyboardShortcut(key: string, callback: () => void) {
  useEffect(() => {
    const handler = (event: KeyboardEvent) => {
      if (event.metaKey && event.key === key) {
        callback()
      }
    }
    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [key, callback])
}
```

`useKeyboardShortcut` hook'u birden fazla kez kullanıldığında her instance yeni bir listener kaydeder.

**Doğru (N instance = 1 listener):**

```tsx
const keyCallbacks = new Map<string, Set<() => void>>()

function handleGlobalKeydown(event: KeyboardEvent) {
  if (event.metaKey) {
    keyCallbacks.get(event.key)?.forEach((callback) => callback())
  }
}

function register(key: string, callback: () => void) {
  if (keyCallbacks.size === 0) {
    window.addEventListener('keydown', handleGlobalKeydown)
  }
  const callbacks = keyCallbacks.get(key) ?? new Set<() => void>()
  callbacks.add(callback)
  keyCallbacks.set(key, callbacks)

  return () => {
    callbacks.delete(callback)
    if (callbacks.size === 0) {
      keyCallbacks.delete(key)
    }
    if (keyCallbacks.size === 0) {
      window.removeEventListener('keydown', handleGlobalKeydown)
    }
  }
}

function useKeyboardShortcut(key: string, callback: () => void) {
  const onShortcut = useEffectEvent(callback)

  useEffect(() => register(key, () => onShortcut()), [key])
}
```

- İlk kayıt listener'ı ekler, son kayıt silinince listener kaldırılır; StrictMode'daki çift mount/unmount da doğru çalışır.
- Callback `useEffectEvent` ile sarıldığı için effect sadece `key` değişince yeniden çalışır (`advanced-effect-event-deps`).
- Paylaşılan değeri **okuyan** (ör. `matchMedia`, online durumu) durumlarda `useSyncExternalStore` kullan.
