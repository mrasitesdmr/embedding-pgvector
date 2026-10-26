---
title: Göster/Gizle için Activity Component'ini Kullan
impact: MEDIUM
impactDescription: state/DOM'u korur
tags: rendering, activity, visibility, state-preservation
---

## Göster/Gizle için Activity Component'ini Kullan

Görünürlüğü sık değişen pahalı component'lerin state/DOM'unu korumak için React'in `<Activity>` component'ini kullan.

**Kullanım:**

```tsx
import { Activity } from 'react'

function Dropdown({ isOpen }: Props) {
  return (
    <Activity mode={isOpen ? 'visible' : 'hidden'}>
      <ExpensiveMenu />
    </Activity>
  )
}
```

Pahalı re-render'lardan ve state kaybından kaçınır.
