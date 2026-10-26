---
title: Tekrarlanan Aramalar için Index Map Kur
impact: LOW-MEDIUM
impactDescription: 1M işlemden 2K işleme
tags: javascript, map, indexing, optimization, performance
---

## Tekrarlanan Aramalar için Index Map Kur

Aynı key ile yapılan birden fazla `.find()` çağrısı yerine Map kullanılmalıdır.

**Yanlış (arama başına O(n)):**

```typescript
function processOrders(orders: Order[], users: User[]) {
  return orders.map(order => ({
    ...order,
    user: users.find(u => u.id === order.userId)
  }))
}
```

**Doğru (arama başına O(1)):**

```typescript
function processOrders(orders: Order[], users: User[]) {
  const userById = new Map(users.map(u => [u.id, u]))

  return orders.map(order => ({
    ...order,
    user: userById.get(order.userId)
  }))
}
```

Map'i bir kez kur (O(n)), sonra tüm aramalar O(1) olur.
1000 sipariş × 1000 kullanıcı için: 1M işlem → 2K işlem.
