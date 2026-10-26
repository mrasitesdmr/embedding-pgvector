---
title: Memoize Edilmiş Component'teki Primitive Olmayan Default Değeri Sabite Taşı
impact: MEDIUM
impactDescription: default değer için sabit kullanarak memoization'ı geri kazandırır
tags: rerender, memo, optimization
---

## Memoize Edilmiş Component'teki Primitive Olmayan Default Değeri Sabite Taşı

Memoize edilmiş bir component'in array, function veya object gibi primitive olmayan opsiyonel bir parametresi için default değeri varsa, component'i o parametre olmadan çağırmak memoization'ı bozar. Bunun sebebi her rerender'da yeni değer instance'larının oluşturulması ve bunların `memo()` içindeki strict equality karşılaştırmasından geçememesidir.

Bu sorunu çözmek için default değeri bir sabite çıkar.

**Yanlış (`onClick` her rerender'da farklı değer alır):**

```tsx
const UserAvatar = memo(function UserAvatar({ onClick = () => {} }: { onClick?: () => void }) {
  // ...
})

// Opsiyonel onClick olmadan kullanılıyor
<UserAvatar />
```

**Doğru (stabil default değer):**

```tsx
const NOOP = () => {};

const UserAvatar = memo(function UserAvatar({ onClick = NOOP }: { onClick?: () => void }) {
  // ...
})

// Opsiyonel onClick olmadan kullanılıyor
<UserAvatar />
```
