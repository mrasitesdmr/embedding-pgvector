---
title: React 19 API Değişiklikleri
impact: MEDIUM
impactDescription: daha temiz component tanımları ve context kullanımı
tags: react19, refs, context, hooks
---

## React 19 API Değişiklikleri

> **⚠️ Sadece React 19+.** React 18 veya öncesindeysen bunu atla. (Bu proje React 19 kullanıyor.)

React 19'da `ref` artık normal bir prop'tur (`forwardRef` wrapper'ına gerek yok) ve `use()`, `useContext()`'in yerini alır.

**Yanlış (React 19'da forwardRef):**

```tsx
const ComposerInput = forwardRef<TextInput, Props>((props, ref) => {
  return <TextInput ref={ref} {...props} />
})
```

**Doğru (normal bir prop olarak ref):**

```tsx
function ComposerInput({ ref, ...props }: Props & { ref?: React.Ref<TextInput> }) {
  return <TextInput ref={ref} {...props} />
}
```

**Yanlış (React 19'da useContext):**

```tsx
const value = useContext(MyContext)
```

**Doğru (useContext yerine use):**

```tsx
const value = use(MyContext)
```

`use()`, `useContext()`'in aksine koşullu olarak da çağrılabilir.
