---
title: RegExp Oluşturmayı Dışarı Taşı
impact: LOW-MEDIUM
impactDescription: yeniden oluşturmadan kaçınır
tags: javascript, regexp, optimization, memoization
---

## RegExp Oluşturmayı Dışarı Taşı

Render içinde RegExp oluşturma. Module scope'a taşı ya da `useMemo()` ile memoize et.

**Yanlış (her render'da yeni RegExp):**

```tsx
function Highlighter({ text, query }: Props) {
  const regex = new RegExp(`(${query})`, 'gi')
  const parts = text.split(regex)
  return <>{parts.map((part, i) => ...)}</>
}
```

**Doğru (memoize et ya da dışarı taşı):**

```tsx
const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

function Highlighter({ text, query }: Props) {
  const regex = useMemo(
    () => new RegExp(`(${escapeRegex(query)})`, 'gi'),
    [query]
  )
  const parts = text.split(regex)
  return <>{parts.map((part, i) => ...)}</>
}
```

**Uyarı (global regex'in mutable state'i vardır):**

Global regex (`/g`) mutable bir `lastIndex` state'ine sahiptir:

```typescript
const regex = /foo/g
regex.test('foo')  // true, lastIndex = 3
regex.test('foo')  // false, lastIndex = 0
```
