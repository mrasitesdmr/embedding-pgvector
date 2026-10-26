---
title: Effect'i Yeniden Çalıştırmadan Güncel Callback için useEffectEvent
impact: LOW
impactDescription: effect'in yeniden çalışmasını önler
tags: advanced, hooks, useEffectEvent, refs, optimization
---

## Effect'i Yeniden Çalıştırmadan Güncel Callback için useEffectEvent

Callback'lerde en güncel değerlere, onları dependency array'lerine eklemeden eriş. Bu, stale closure'lardan kaçınırken effect'in yeniden çalışmasını önler. `useEffectEvent`'in döndürdüğü function'ın kimliği stabil değildir; dependency'e eklenmez ve sadece effect içinden çağrılır ([advanced-effect-event-deps](advanced-effect-event-deps.md)).

**Yanlış (her callback değişiminde effect yeniden çalışır):**

```tsx
function SearchInput({ onSearch }: { onSearch: (q: string) => void }) {
  const [query, setQuery] = useState('')

  useEffect(() => {
    const timeout = setTimeout(() => onSearch(query), 300)
    return () => clearTimeout(timeout)
  }, [query, onSearch])
}
```

**Doğru (React'in useEffectEvent'ini kullanarak):**

```tsx
import { useEffectEvent } from 'react'

function SearchInput({ onSearch }: { onSearch: (q: string) => void }) {
  const [query, setQuery] = useState('')
  const onSearchEvent = useEffectEvent(onSearch)

  useEffect(() => {
    const timeout = setTimeout(() => onSearchEvent(query), 300)
    return () => clearTimeout(timeout)
  }, [query])
}
```
