---
title: Pahalı Türetilmiş Render'lar için useDeferredValue Kullan
impact: MEDIUM
impactDescription: ağır hesaplama sırasında input'u tepkisel tutar
tags: rerender, useDeferredValue, optimization, concurrent
---

## Pahalı Türetilmiş Render'lar için useDeferredValue Kullan

Kullanıcı input'u pahalı hesaplamaları veya render'ları tetikliyorsa input'u tepkisel tutmak için `useDeferredValue` kullan. Ertelenen değer geriden gelir; böylece React input güncellemesine öncelik verir ve pahalı sonucu boşta olduğunda render eder.

**Yanlış (filtreleme sırasında input ağır hissettirir):**

```tsx
function Search({ items }: { items: Item[] }) {
  const [query, setQuery] = useState('')
  const filtered = items.filter(item => fuzzyMatch(item, query))

  return (
    <>
      <input value={query} onChange={e => setQuery(e.target.value)} />
      <ResultsList results={filtered} />
    </>
  )
}
```

**Doğru (input hızlı kalır, sonuçlar hazır olunca render olur):**

```tsx
function Search({ items }: { items: Item[] }) {
  const [query, setQuery] = useState('')
  const deferredQuery = useDeferredValue(query)
  const filtered = useMemo(
    () => items.filter(item => fuzzyMatch(item, deferredQuery)),
    [items, deferredQuery]
  )
  const isStale = query !== deferredQuery

  return (
    <>
      <input value={query} onChange={e => setQuery(e.target.value)} />
      <div style={{ opacity: isStale ? 0.7 : 1 }}>
        <ResultsList results={filtered} />
      </div>
    </>
  )
}
```

**Ne zaman kullanılır:**

- Büyük listeleri filtrelerken/ararken
- Input'a tepki veren pahalı görselleştirmelerde (chart, graph)
- Fark edilir render gecikmesine yol açan her türetilmiş state'te

**Not:** Pahalı hesaplamayı ertelenen değeri dependency olarak alan bir `useMemo` ile sar, aksi halde yine her render'da çalışır.

Referans: [React useDeferredValue](https://react.dev/reference/react/useDeferredValue)
