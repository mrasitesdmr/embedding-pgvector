---
title: Immutability için sort() Yerine toSorted() Kullan
impact: MEDIUM-HIGH
impactDescription: React state'inde mutation bug'larını önler
tags: javascript, arrays, immutability, react, state, mutation
---

## Immutability için sort() Yerine toSorted() Kullan

`.sort()` array'i yerinde mutate eder; bu da React state ve props ile bug'lara yol açabilir. Mutation olmadan yeni bir sıralı array oluşturmak için `.toSorted()` kullan.

**Yanlış (orijinal array'i mutate eder):**

```typescript
function UserList({ users }: { users: User[] }) {
  // users prop array'ini mutate ediyor!
  const sorted = useMemo(
    () => users.sort((a, b) => a.name.localeCompare(b.name)),
    [users]
  )
  return <div>{sorted.map(renderUser)}</div>
}
```

**Doğru (yeni array oluşturur):**

```typescript
function UserList({ users }: { users: User[] }) {
  // Yeni sıralı array oluşturur, orijinal değişmez
  const sorted = useMemo(
    () => users.toSorted((a, b) => a.name.localeCompare(b.name)),
    [users]
  )
  return <div>{sorted.map(renderUser)}</div>
}
```

**React'te bu neden önemli:**

1. Props/state mutation'ları React'in immutability modelini bozar; React props ve state'in read-only olarak ele alınmasını bekler
2. Stale closure bug'larına yol açar; closure'lar (callback'ler, effect'ler) içinde array'leri mutate etmek beklenmedik davranışlara sebep olabilir

**Tarayıcı desteği (eski tarayıcılar için fallback):**

`.toSorted()` tüm modern tarayıcılarda mevcuttur (Chrome 110+, Safari 16+, Firefox 115+, Node.js 20+). Daha eski ortamlar için spread operatörü kullan:

```typescript
// Eski tarayıcılar için fallback
const sorted = [...items].sort((a, b) => a.value - b.value)
```

**Diğer immutable array metotları:**

- `.toSorted()` - immutable sort
- `.toReversed()` - immutable reverse
- `.toSpliced()` - immutable splice
- `.with()` - immutable eleman değiştirme
