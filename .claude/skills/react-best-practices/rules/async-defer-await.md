---
title: Await'i Gerektiği Yere Ertele
impact: HIGH
impactDescription: kullanılmayan kod yollarını bloklamaktan kaçınır
tags: async, await, conditional, optimization
---

## Await'i Gerektiği Yere Ertele

`await` işlemlerini gerçekten kullanıldıkları branch'lere taşı; böylece onlara ihtiyaç duymayan kod yollarını bloklamazsın.

**Yanlış (iki branch'i de bloklar):**

```typescript
async function handleRequest(userId: string, skipProcessing: boolean) {
  const userData = await fetchUserData(userId)
  
  if (skipProcessing) {
    // Hemen döner ama yine de userData'yı bekledi
    return { skipped: true }
  }
  
  // userData'yı sadece bu branch kullanıyor
  return processUserData(userData)
}
```

**Doğru (sadece gerektiğinde bloklar):**

```typescript
async function handleRequest(userId: string, skipProcessing: boolean) {
  if (skipProcessing) {
    // Beklemeden hemen döner
    return { skipped: true }
  }
  
  // Sadece gerektiğinde fetch et
  const userData = await fetchUserData(userId)
  return processUserData(userData)
}
```

**Başka bir örnek (early return optimizasyonu):**

```typescript
// Yanlış: permission'ları her zaman fetch eder
async function updateResource(resourceId: string, userId: string) {
  const permissions = await fetchPermissions(userId)
  const resource = await getResource(resourceId)
  
  if (!resource) {
    return { error: 'Not found' }
  }
  
  if (!permissions.canEdit) {
    return { error: 'Forbidden' }
  }
  
  return await updateResourceData(resource, permissions)
}

// Doğru: sadece gerektiğinde fetch eder
async function updateResource(resourceId: string, userId: string) {
  const resource = await getResource(resourceId)
  
  if (!resource) {
    return { error: 'Not found' }
  }
  
  const permissions = await fetchPermissions(userId)
  
  if (!permissions.canEdit) {
    return { error: 'Forbidden' }
  }
  
  return await updateResourceData(resource, permissions)
}
```

Bu optimizasyon özellikle atlanan branch sık kullanılıyorsa ya da ertelenen işlem pahalıysa değerlidir.

Ucuz bir senkron guard ile birleşen `await getFlag()` (`flag && someCondition`) için [Async Flag'lerden Önce Ucuz Koşulları Kontrol Et](./async-cheap-condition-before-await.md) kuralına bak.
