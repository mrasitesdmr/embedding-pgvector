---
title: Array Karşılaştırmalarında Önce Uzunluğu Kontrol Et
impact: MEDIUM-HIGH
impactDescription: uzunluklar farklıyken pahalı işlemlerden kaçınır
tags: javascript, arrays, performance, optimization, comparison
---

## Array Karşılaştırmalarında Önce Uzunluğu Kontrol Et

Array'leri pahalı işlemlerle (sorting, deep equality, serialization) karşılaştırırken önce uzunlukları kontrol et. Uzunluklar farklıysa array'ler eşit olamaz.

Gerçek uygulamalarda bu optimizasyon, karşılaştırma sık çalışan yollarda (event handler'lar, render döngüleri) çalıştığında özellikle değerlidir.

**Yanlış (pahalı karşılaştırmayı her zaman çalıştırır):**

```typescript
function hasChanges(current: string[], original: string[]) {
  // Uzunluklar farklı olsa bile her zaman sort ve join yapar
  return current.sort().join() !== original.sort().join()
}
```

`current.length` 5 ve `original.length` 100 olsa bile iki O(n log n) sort çalışır. Ayrıca array'leri join etmenin ve string'leri karşılaştırmanın ek yükü vardır.

**Doğru (önce O(1) uzunluk kontrolü):**

```typescript
function hasChanges(current: string[], original: string[]) {
  // Uzunluklar farklıysa erken dön
  if (current.length !== original.length) {
    return true
  }
  // Sadece uzunluklar eşleşince sort et
  const currentSorted = current.toSorted()
  const originalSorted = original.toSorted()
  for (let i = 0; i < currentSorted.length; i++) {
    if (currentSorted[i] !== originalSorted[i]) {
      return true
    }
  }
  return false
}
```

Bu yeni yaklaşım daha verimlidir çünkü:
- Uzunluklar farklıyken array'leri sort ve join etmenin ek yükünden kaçınır
- Join edilmiş string'ler için bellek tüketmez (büyük array'lerde özellikle önemli)
- Orijinal array'leri mutate etmez
- Bir fark bulunduğunda erken döner
