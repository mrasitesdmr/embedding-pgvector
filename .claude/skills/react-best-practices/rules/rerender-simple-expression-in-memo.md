---
title: Primitive Sonuç Döndüren Basit Bir İfadeyi useMemo ile Sarma
impact: LOW-MEDIUM
impactDescription: her render'da boşa harcanan hesaplama
tags: rerender, useMemo, optimization
---

## Primitive Sonuç Döndüren Basit Bir İfadeyi useMemo ile Sarma

Bir ifade basitse (az sayıda mantıksal veya aritmetik operatör) ve sonuç tipi primitive ise (boolean, number, string), onu `useMemo` ile sarma.
`useMemo`'yu çağırmak ve hook dependency'lerini karşılaştırmak, ifadenin kendisinden daha fazla kaynak tüketebilir.

**Yanlış:**

```tsx
function Header({ user, notifications }: Props) {
  const isLoading = useMemo(() => {
    return user.isLoading || notifications.isLoading
  }, [user.isLoading, notifications.isLoading])

  if (isLoading) return <Skeleton />
  // markup döndür
}
```

**Doğru:**

```tsx
function Header({ user, notifications }: Props) {
  const isLoading = user.isLoading || notifications.isLoading

  if (isLoading) return <Skeleton />
  // markup döndür
}
```
