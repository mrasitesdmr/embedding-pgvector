---
title: Açık Koşullu Render Kullan
impact: LOW
impactDescription: 0 veya NaN render edilmesini önler
tags: rendering, conditional, jsx, falsy-values
---

## Açık Koşullu Render Kullan

Koşul `0`, `NaN` ya da render edilen başka falsy değerler olabiliyorsa, koşullu render için `&&` yerine açık ternary operatörleri (`? :`) kullan.

**Yanlış (count 0 olduğunda "0" render eder):**

```tsx
function Badge({ count }: { count: number }) {
  return (
    <div>
      {count && <span className="badge">{count}</span>}
    </div>
  )
}

// count = 0 iken render eder: <div>0</div>
// count = 5 iken render eder: <div><span class="badge">5</span></div>
```

**Doğru (count 0 olduğunda hiçbir şey render etmez):**

```tsx
function Badge({ count }: { count: number }) {
  return (
    <div>
      {count > 0 ? <span className="badge">{count}</span> : null}
    </div>
  )
}

// count = 0 iken render eder: <div></div>
// count = 5 iken render eder: <div><span class="badge">5</span></div>
```
