---
title: Function'lardan Erken Dön
impact: LOW-MEDIUM
impactDescription: gereksiz hesaplamadan kaçınır
tags: javascript, functions, optimization, early-return
---

## Function'lardan Erken Dön

Sonuç belli olduğunda gereksiz işlemi atlamak için erken dön (early return).

**Yanlış (cevabı bulduktan sonra bile tüm elemanları işler):**

```typescript
function validateUsers(users: User[]) {
  let hasError = false
  let errorMessage = ''
  
  for (const user of users) {
    if (!user.email) {
      hasError = true
      errorMessage = 'Email required'
    }
    if (!user.name) {
      hasError = true
      errorMessage = 'Name required'
    }
    // Hata bulunduktan sonra bile tüm kullanıcıları kontrol etmeye devam eder
  }
  
  return hasError ? { valid: false, error: errorMessage } : { valid: true }
}
```

**Doğru (ilk hatada hemen döner):**

```typescript
function validateUsers(users: User[]) {
  for (const user of users) {
    if (!user.email) {
      return { valid: false, error: 'Email required' }
    }
    if (!user.name) {
      return { valid: false, error: 'Name required' }
    }
  }

  return { valid: true }
}
```
