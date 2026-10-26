---
title: State Okumalarını Kullanıldığı Noktaya Ertele
impact: MEDIUM
impactDescription: gereksiz subscription'lardan kaçınır
tags: rerender, searchParams, localStorage, optimization
---

## State Okumalarını Kullanıldığı Noktaya Ertele

Dinamik state'i (searchParams, localStorage) sadece callback'lerin içinde okuyorsan ona subscribe olma.

**Yanlış (tüm searchParams değişikliklerine subscribe olur):**

```tsx
function ShareButton({ chatId }: { chatId: string }) {
  const [searchParams] = useSearchParams()

  const handleShare = () => {
    const ref = searchParams.get('ref')
    shareChat(chatId, { ref })
  }

  return <button onClick={handleShare}>Share</button>
}
```

**Doğru (ihtiyaç anında okur, subscription yok):**

```tsx
function ShareButton({ chatId }: { chatId: string }) {
  const handleShare = () => {
    const params = new URLSearchParams(window.location.search)
    const ref = params.get('ref')
    shareChat(chatId, { ref })
  }

  return <button onClick={handleShare}>Share</button>
}
```
