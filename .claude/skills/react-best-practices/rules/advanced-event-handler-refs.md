---
title: Event Handler'ları Ref'lerde Sakla
impact: LOW
impactDescription: stabil subscription'lar
tags: advanced, hooks, refs, event-handlers, optimization
---

## Event Handler'ları Ref'lerde Sakla

Callback değiştiğinde yeniden subscribe olmaması gereken effect'lerde kullanılan callback'leri ref'lerde sakla.

**Yanlış (her render'da yeniden subscribe olur):**

```tsx
function useWindowEvent(event: string, handler: (event: Event) => void) {
  useEffect(() => {
    window.addEventListener(event, handler)
    return () => window.removeEventListener(event, handler)
  }, [event, handler])
}
```

**Doğru (stabil subscription):**

```tsx
function useWindowEvent(event: string, handler: (event: Event) => void) {
  const handlerRef = useRef(handler)
  useEffect(() => {
    handlerRef.current = handler
  }, [handler])

  useEffect(() => {
    const listener = (event: Event) => handlerRef.current(event)
    window.addEventListener(event, listener)
    return () => window.removeEventListener(event, listener)
  }, [event])
}
```

**Alternatif: güncel React'teysen `useEffectEvent` kullan (bu proje React 19.2+):**

```tsx
import { useEffectEvent } from 'react'

function useWindowEvent(event: string, handler: (event: Event) => void) {
  const onEvent = useEffectEvent(handler)

  useEffect(() => {
    window.addEventListener(event, onEvent)
    return () => window.removeEventListener(event, onEvent)
  }, [event])
}
```

`useEffectEvent` aynı pattern için daha temiz bir API sunar: döndürdüğü function her zaman handler'ın en güncel versiyonunu çağırır. Dikkat: bu function'ın kimliği **stabil değildir** (her render'da bilerek değişir); bu yüzden dependency array'ine konmaz, başka component'e veya hook'a prop olarak geçilmez, sadece effect içinden çağrılır ya da effect içinde listener olarak kaydedilir (bkz. [advanced-effect-event-deps](advanced-effect-event-deps.md), [React docs](https://react.dev/reference/react/useEffectEvent#caveats)).
