---
title: Min/Max için Sort Yerine Döngü Kullan
impact: LOW
impactDescription: O(n log n) yerine O(n)
tags: javascript, arrays, performance, sorting, algorithms
---

## Min/Max için Sort Yerine Döngü Kullan

En küçük veya en büyük elemanı bulmak array üzerinden tek bir geçiş gerektirir. Sort etmek israftır ve daha yavaştır.

**Yanlış (O(n log n) - en yeniyi bulmak için sort):**

```typescript
interface Project {
  id: string
  name: string
  updatedAt: number
}

function getLatestProject(projects: Project[]) {
  const sorted = [...projects].sort((a, b) => b.updatedAt - a.updatedAt)
  return sorted[0]
}
```

Sadece maksimum değeri bulmak için tüm array'i sort eder.

**Yanlış (O(n log n) - en eski ve en yeni için sort):**

```typescript
function getOldestAndNewest(projects: Project[]) {
  const sorted = [...projects].sort((a, b) => a.updatedAt - b.updatedAt)
  return { oldest: sorted[0], newest: sorted[sorted.length - 1] }
}
```

Sadece min/max gerekirken yine gereksiz yere sort eder.

**Doğru (O(n) - tek döngü):**

```typescript
function getLatestProject(projects: Project[]) {
  if (projects.length === 0) return null
  
  let latest = projects[0]
  
  for (let i = 1; i < projects.length; i++) {
    if (projects[i].updatedAt > latest.updatedAt) {
      latest = projects[i]
    }
  }
  
  return latest
}

function getOldestAndNewest(projects: Project[]) {
  if (projects.length === 0) return { oldest: null, newest: null }
  
  let oldest = projects[0]
  let newest = projects[0]
  
  for (let i = 1; i < projects.length; i++) {
    if (projects[i].updatedAt < oldest.updatedAt) oldest = projects[i]
    if (projects[i].updatedAt > newest.updatedAt) newest = projects[i]
  }
  
  return { oldest, newest }
}
```

Array üzerinden tek geçiş; kopyalama yok, sort yok.

**Alternatif (küçük array'ler için Math.min/Math.max):**

```typescript
const numbers = [5, 2, 8, 1, 9]
const min = Math.min(...numbers)
const max = Math.max(...numbers)
```

Bu küçük array'lerde çalışır, ama spread operatörünün sınırlamaları nedeniyle çok büyük array'lerde daha yavaş olabilir ya da doğrudan hata fırlatabilir. Maksimum array uzunluğu Chrome 143'te yaklaşık 124000, Safari 18'de 638000'dir; kesin sayılar değişebilir, bkz. [fiddle](https://jsfiddle.net/qw1jabsx/4/). Güvenilirlik için döngü yaklaşımını kullan.
