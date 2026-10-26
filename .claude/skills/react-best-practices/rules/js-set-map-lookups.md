---
title: O(1) Arama için Set/Map Kullan
impact: LOW-MEDIUM
impactDescription: O(n)'den O(1)'e
tags: javascript, set, map, data-structures, performance
---

## O(1) Arama için Set/Map Kullan

Tekrarlanan üyelik kontrolleri için array'leri Set/Map'e dönüştür.

**Yanlış (kontrol başına O(n)):**

```typescript
const allowedIds = ['a', 'b', 'c', ...]
items.filter(item => allowedIds.includes(item.id))
```

**Doğru (kontrol başına O(1)):**

```typescript
const allowedIds = new Set(['a', 'b', 'c', ...])
items.filter(item => allowedIds.has(item.id))
```
