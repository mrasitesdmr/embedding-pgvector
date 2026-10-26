---
title: Geçici Değerler için useRef Kullan
impact: MEDIUM
impactDescription: sık güncellemelerde gereksiz re-render'lardan kaçınır
tags: rerender, useref, state, performance
---

## Geçici Değerler için useRef Kullan

Bir değer sık değişiyorsa ve her güncellemede re-render istemiyorsan (ör. mouse tracker'lar, interval'lar, geçici flag'ler), onu `useState` yerine `useRef` içinde sakla. Component state'ini UI için tut; geçici, DOM'a yakın değerler için ref kullan. Bir ref'i güncellemek re-render tetiklemez.

**Yanlış (her güncellemede render olur):**

```tsx
function Tracker() {
  const [lastX, setLastX] = useState(0)

  useEffect(() => {
    const onMove = (e: MouseEvent) => setLastX(e.clientX)
    window.addEventListener('mousemove', onMove)
    return () => window.removeEventListener('mousemove', onMove)
  }, [])

  return (
    <div
      style={{
        position: 'fixed',
        top: 0,
        left: lastX,
        width: 8,
        height: 8,
        background: 'black',
      }}
    />
  )
}
```

**Doğru (takip için re-render yok):**

```tsx
function Tracker() {
  const lastXRef = useRef(0)
  const dotRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const onMove = (e: MouseEvent) => {
      lastXRef.current = e.clientX
      const node = dotRef.current
      if (node) {
        node.style.transform = `translateX(${e.clientX}px)`
      }
    }
    window.addEventListener('mousemove', onMove)
    return () => window.removeEventListener('mousemove', onMove)
  }, [])

  return (
    <div
      ref={dotRef}
      style={{
        position: 'fixed',
        top: 0,
        left: 0,
        width: 8,
        height: 8,
        background: 'black',
        transform: 'translateX(0px)',
      }}
    />
  )
}
```
