---
title: Bağımsız İşlemler için Promise.all()
impact: CRITICAL
impactDescription: 2-10× iyileşme
tags: async, parallelization, promises, waterfalls
---

## Bağımsız İşlemler için Promise.all()

Async işlemlerin birbirine bağımlılığı yoksa onları `Promise.all()` ile eşzamanlı çalıştır.

**Yanlış (sıralı çalışma, 3 round trip):**

```typescript
const user = await fetchUser()
const posts = await fetchPosts()
const comments = await fetchComments()
```

**Doğru (paralel çalışma, 1 round trip):**

```typescript
const [user, posts, comments] = await Promise.all([
  fetchUser(),
  fetchPosts(),
  fetchComments()
])
```
