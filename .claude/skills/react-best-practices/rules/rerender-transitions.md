---
title: Acil Olmayan Güncellemeler için Transition Kullan
impact: MEDIUM
impactDescription: UI'ın tepkiselliğini korur
tags: rerender, transitions, startTransition, performance
---

## Acil Olmayan Güncellemeler için Transition Kullan

UI'ın tepkiselliğini korumak için sık ve acil olmayan state güncellemelerini transition olarak işaretle.

**Yanlış (her scroll'da UI'ı bloklar):**

```tsx
function ScrollTracker() {
  const [scrollY, setScrollY] = useState(0)
  useEffect(() => {
    const handler = () => setScrollY(window.scrollY)
    window.addEventListener('scroll', handler, { passive: true })
    return () => window.removeEventListener('scroll', handler)
  }, [])
}
```

**Doğru (bloklamayan güncellemeler):**

```tsx
import { startTransition } from 'react'

function ScrollTracker() {
  const [scrollY, setScrollY] = useState(0)
  useEffect(() => {
    const handler = () => {
      startTransition(() => setScrollY(window.scrollY))
    }
    window.addEventListener('scroll', handler, { passive: true })
    return () => window.removeEventListener('scroll', handler)
  }, [])
}
```
