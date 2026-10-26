---
title: Birden Fazla Array İterasyonunu Birleştir
impact: LOW-MEDIUM
impactDescription: iterasyon sayısını azaltır
tags: javascript, arrays, loops, performance
---

## Birden Fazla Array İterasyonunu Birleştir

Birden fazla `.filter()` veya `.map()` çağrısı array'i birden fazla kez dolaşır. Bunları tek bir döngüde birleştir.

**Yanlış (3 iterasyon):**

```typescript
const admins = users.filter(u => u.isAdmin)
const testers = users.filter(u => u.isTester)
const inactive = users.filter(u => !u.isActive)
```

**Doğru (1 iterasyon):**

```typescript
const admins: User[] = []
const testers: User[] = []
const inactive: User[] = []

for (const user of users) {
  if (user.isAdmin) admins.push(user)
  if (user.isTester) testers.push(user)
  if (!user.isActive) inactive.push(user)
}
```
