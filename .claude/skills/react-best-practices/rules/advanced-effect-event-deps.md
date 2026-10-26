---
title: Effect Event'leri Dependency Array'lerine Koyma
impact: LOW
impactDescription: gereksiz effect yeniden çalışmalarından ve lint hatalarından kaçınır
tags: advanced, hooks, useEffectEvent, dependencies, effects
---

## Effect Event'leri Dependency Array'lerine Koyma

Effect Event function'larının stabil bir kimliği (identity) yoktur. Kimlikleri bilerek her render'da değişir. `useEffectEvent`'in döndürdüğü function'ı bir `useEffect` dependency array'ine ekleme. Gerçek reaktif değerleri dependency olarak tut ve Effect Event'i effect gövdesinin içinden ya da o effect'in oluşturduğu subscription'ların içinden çağır.

**Yanlış (Effect Event dependency olarak eklenmiş):**

```tsx
import { useEffect, useEffectEvent } from 'react'

function ChatRoom({ roomId, onConnected }: {
  roomId: string
  onConnected: () => void
}) {
  const handleConnected = useEffectEvent(onConnected)

  useEffect(() => {
    const connection = createConnection(roomId)
    connection.on('connected', handleConnected)
    connection.connect()

    return () => connection.disconnect()
  }, [roomId, handleConnected])
}
```

Effect Event'i dependency'lere eklemek effect'in her render'da yeniden çalışmasına yol açar ve React Hooks lint kuralını tetikler.

**Doğru (Effect Event'e değil reaktif değerlere bağımlı ol):**

```tsx
import { useEffect, useEffectEvent } from 'react'

function ChatRoom({ roomId, onConnected }: {
  roomId: string
  onConnected: () => void
}) {
  const handleConnected = useEffectEvent(onConnected)

  useEffect(() => {
    const connection = createConnection(roomId)
    connection.on('connected', handleConnected)
    connection.connect()

    return () => connection.disconnect()
  }, [roomId])
}
```

Bu pattern, kaynak id'sine bağlı STOMP bağlantısı/subscription'ı kuran hook'lar (ör. ingestion ilerlemesi) için doğrudan geçerlidir: dependency olarak sadece `sourceId`'yi tut, callback'leri `useEffectEvent` ile sar.

Referans: [React useEffectEvent: Effect Event in deps](https://react.dev/reference/react/useEffectEvent#effect-event-in-deps)
