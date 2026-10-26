---
title: Functional setState Güncellemeleri Kullan
impact: MEDIUM
impactDescription: stale closure'ları ve gereksiz callback yeniden oluşturmalarını önler
tags: react, hooks, useState, useCallback, callbacks, closures
---

## Functional setState Güncellemeleri Kullan

State'i mevcut state değerine göre güncellerken state değişkenine doğrudan referans vermek yerine setState'in functional update biçimini kullan. Bu, stale closure'ları önler, gereksiz dependency'leri ortadan kaldırır ve stabil callback referansları oluşturur.

**Yanlış (state'i dependency olarak gerektirir):**

```tsx
function TodoList() {
  const [items, setItems] = useState(initialItems)
  
  // Callback items'a bağlı olmak zorunda, items her değiştiğinde yeniden oluşturulur
  const addItems = useCallback((newItems: Item[]) => {
    setItems([...items, ...newItems])
  }, [items])  // ❌ items dependency'si yeniden oluşturmaya sebep olur
  
  // Dependency unutulursa stale closure riski
  const removeItem = useCallback((id: string) => {
    setItems(items.filter(item => item.id !== id))
  }, [])  // ❌ items dependency'si eksik - eski items'ı kullanacak!
  
  return <ItemsEditor items={items} onAdd={addItems} onRemove={removeItem} />
}
```

İlk callback `items` her değiştiğinde yeniden oluşturulur; bu da child component'lerin gereksiz yere re-render olmasına yol açabilir. İkinci callback'te stale closure bug'ı var; her zaman ilk `items` değerine referans verir.

**Doğru (stabil callback'ler, stale closure yok):**

```tsx
function TodoList() {
  const [items, setItems] = useState(initialItems)
  
  // Stabil callback, asla yeniden oluşturulmaz
  const addItems = useCallback((newItems: Item[]) => {
    setItems(curr => [...curr, ...newItems])
  }, [])  // ✅ Dependency gerekmez
  
  // Her zaman en güncel state'i kullanır, stale closure riski yok
  const removeItem = useCallback((id: string) => {
    setItems(curr => curr.filter(item => item.id !== id))
  }, [])  // ✅ Güvenli ve stabil
  
  return <ItemsEditor items={items} onAdd={addItems} onRemove={removeItem} />
}
```

**Faydaları:**

1. **Stabil callback referansları** - State değiştiğinde callback'lerin yeniden oluşturulması gerekmez
2. **Stale closure yok** - Her zaman en güncel state değeri üzerinde çalışır
3. **Daha az dependency** - Dependency array'lerini sadeleştirir ve memory leak'leri azaltır
4. **Bug'ları önler** - React closure bug'larının en yaygın kaynağını ortadan kaldırır

**Functional update ne zaman kullanılır:**

- Mevcut state değerine bağlı her setState
- State gerektiğinde useCallback/useMemo içinde
- State'e referans veren event handler'lar
- State güncelleyen async işlemler

**Doğrudan güncelleme ne zaman yeterli:**

- State'i statik bir değere set ederken: `setCount(0)`
- State'i sadece props/argümanlardan set ederken: `setName(newName)`
- State önceki değere bağlı değilken

**Not:** Projende [React Compiler](https://react.dev/learn/react-compiler) etkinse compiler bazı durumları otomatik optimize edebilir, ama doğruluk ve stale closure bug'larını önlemek için functional update'ler yine de önerilir.

**Proje notu:** Uzun yaşayan callback'ler (zamanlayıcılar, ileride STOMP subscription'ları) kapanıştaki (closure) eski değeri okursa stale veri üretir. Projede içe alma ilerlemesi `useState`'te değil TanStack Query cache'indedir (`frontend-conventions` bölüm 8); polling sonucu cache'e kendiliğinden yazılır. WebSocket gelirse mesaj sadece ilerleme alanlarını taşıyacağı için updater biçimi kullanılır: `queryClient.setQueryData<IngestionJobResponse>(sourceKeys.ingestionJob(workspaceId, message.jobId), (job) => job ? { ...job, stage: message.stage, progress: message.progress, embeddedChunks: message.embeddedChunks, totalChunks: message.totalChunks } : job)`. Updater her zaman cache'in o anki değerini alır, closure'daki eski değeri değil. Aynı sebeple `IngestionWatcherProvider`'daki "görülen iş" kümeleri `useRef`'te tutulur ve `useEffectEvent` içinden okunur.
