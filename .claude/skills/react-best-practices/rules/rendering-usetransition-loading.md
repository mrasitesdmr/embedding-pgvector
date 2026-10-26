---
title: Manuel Loading State'leri Yerine useTransition Kullan
impact: LOW
impactDescription: re-render'ları azaltır ve kodun anlaşılırlığını artırır
tags: rendering, transitions, useTransition, loading, state
---

## Manuel Loading State'leri Yerine useTransition Kullan

Loading state'leri için manuel `useState` yerine `useTransition` kullan. Bu, yerleşik bir `isPending` state'i sağlar ve transition'ları otomatik yönetir.

**Yanlış (manuel loading state):**

```tsx
function SearchResults() {
  const [query, setQuery] = useState('')
  const [results, setResults] = useState([])
  const [isLoading, setIsLoading] = useState(false)

  const handleSearch = async (value: string) => {
    setIsLoading(true)
    setQuery(value)
    const data = await fetchResults(value)
    setResults(data)
    setIsLoading(false)
  }

  return (
    <>
      <input onChange={(e) => handleSearch(e.target.value)} />
      {isLoading && <Spinner />}
      <ResultsList results={results} />
    </>
  )
}
```

**Doğru (yerleşik pending state'li useTransition):**

```tsx
import { useTransition, useState } from 'react'

function SearchResults() {
  const [query, setQuery] = useState('')
  const [results, setResults] = useState([])
  const [isPending, startTransition] = useTransition()

  const handleSearch = (value: string) => {
    setQuery(value) // Input'u hemen güncelle
    
    startTransition(async () => {
      // Sonuçları fetch et ve güncelle
      const data = await fetchResults(value)
      setResults(data)
    })
  }

  return (
    <>
      <input onChange={(e) => handleSearch(e.target.value)} />
      {isPending && <Spinner />}
      <ResultsList results={results} />
    </>
  )
}
```

**Faydaları:**

- **Otomatik pending state**: `setIsLoading(true/false)`'u manuel yönetmeye gerek yok
- **Hatalara dayanıklılık**: Transition throw etse bile pending state doğru şekilde sıfırlanır
- **Daha iyi tepkisellik**: Güncellemeler sırasında UI tepkisel kalır
- **Kesinti yönetimi**: Yeni transition'lar bekleyenleri otomatik iptal eder

Referans: [useTransition](https://react.dev/reference/react/useTransition)
