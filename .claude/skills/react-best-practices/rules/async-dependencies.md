---
title: Bağımlılığa Dayalı Paralelleştirme
impact: CRITICAL
impactDescription: 2-10× iyileşme
tags: async, parallelization, dependencies, better-all
---

## Bağımlılığa Dayalı Paralelleştirme

Kısmi bağımlılıkları olan işlemlerde paralelliği en üst düzeye çıkar: her task'ı mümkün olan en erken anda başlat.

> **Proje notu:** `better-all` yeni bir runtime dependency'dir; eklemek plan kararıdır (`frontend-conventions` bölüm 1). Projede aşağıdaki "Ek dependency olmadan alternatif" kullanılır. Ayrıca component içindeki veri çekme TanStack Query hook'larıyla yapılır; bu kural `searchApi.ts` gibi saf async function'lar içindir (ör. hybrid aramada keyword ve vector kolunu paralel başlatmak).

**Yanlış (profile gereksiz yere config'i bekliyor):**

```typescript
const [user, config] = await Promise.all([
  fetchUser(),
  fetchConfig()
])
const profile = await fetchProfile(user.id)
```

**Doğru (config ve profile paralel çalışıyor):**

```typescript
import { all } from 'better-all'

const { user, config, profile } = await all({
  async user() { return fetchUser() },
  async config() { return fetchConfig() },
  async profile() {
    return fetchProfile((await this.$.user).id)
  }
})
```

**Ek dependency olmadan alternatif:**

Önce tüm promise'leri oluşturup en sonda `Promise.all()` da yapabiliriz.

```typescript
const userPromise = fetchUser()
const profilePromise = userPromise.then(user => fetchProfile(user.id))

const [user, config, profile] = await Promise.all([
  userPromise,
  fetchConfig(),
  profilePromise
])
```

Referans: [https://github.com/shuding/better-all](https://github.com/shuding/better-all)
