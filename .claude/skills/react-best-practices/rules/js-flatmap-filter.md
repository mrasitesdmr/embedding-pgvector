---
title: Map ve Filter'ı Tek Geçişte Yapmak için flatMap Kullan
impact: LOW-MEDIUM
impactDescription: ara array'i ortadan kaldırır
tags: javascript, arrays, flatMap, filter, performance
---

## Map ve Filter'ı Tek Geçişte Yapmak için flatMap Kullan

**Etki: LOW-MEDIUM (ara array'i ortadan kaldırır)**

`.map().filter(Boolean)` zinciri bir ara array oluşturur ve iki kez dolaşır. Dönüştürme ve filtrelemeyi tek geçişte yapmak için `.flatMap()` kullan.

**Yanlış (2 iterasyon, ara array):**

```typescript
const userNames = users
  .map(user => user.isActive ? user.name : null)
  .filter(Boolean)
```

**Doğru (1 iterasyon, ara array yok):**

```typescript
const userNames = users.flatMap(user =>
  user.isActive ? [user.name] : []
)
```

**Daha fazla örnek:**

```typescript
// Response'lardan geçerli email'leri çıkar
// Önce
const emails = responses
  .map(r => r.success ? r.data.email : null)
  .filter(Boolean)

// Sonra
const emails = responses.flatMap(r =>
  r.success ? [r.data.email] : []
)

// Parse et ve geçerli sayıları filtrele
// Önce
const numbers = strings
  .map(s => parseInt(s, 10))
  .filter(n => !isNaN(n))

// Sonra
const numbers = strings.flatMap(s => {
  const n = parseInt(s, 10)
  return isNaN(n) ? [] : [n]
})
```

**Ne zaman kullanılır:**
- Bazılarını eleyerek elemanları dönüştürürken
- Bazı input'ların çıktı üretmediği koşullu mapping'de
- Geçersiz input'ların atlanması gereken parse/validation işlemlerinde
