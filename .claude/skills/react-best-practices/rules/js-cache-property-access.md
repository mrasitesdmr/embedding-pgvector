---
title: Döngülerde Property Erişimini Cache'le
impact: LOW-MEDIUM
impactDescription: lookup sayısını azaltır
tags: javascript, loops, optimization, caching
---

## Döngülerde Property Erişimini Cache'le

Sık çalışan kod yollarında (hot path) object property lookup'larını cache'le.

**Yanlış (3 lookup × N iterasyon):**

```typescript
for (let i = 0; i < arr.length; i++) {
  process(obj.config.settings.value)
}
```

**Doğru (toplam 1 lookup):**

```typescript
const value = obj.config.settings.value
const len = arr.length
for (let i = 0; i < len; i++) {
  process(value)
}
```
