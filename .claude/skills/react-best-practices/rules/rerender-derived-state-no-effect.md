---
title: Türetilmiş State'i Render Sırasında Hesapla
impact: MEDIUM
impactDescription: gereksiz render'lardan ve state kaymasından kaçınır
tags: rerender, derived-state, useEffect, state
---

## Türetilmiş State'i Render Sırasında Hesapla

Bir değer mevcut props/state'ten hesaplanabiliyorsa onu state'te saklama ya da bir effect içinde güncelleme. Ekstra render'lardan ve state kaymasından (state drift) kaçınmak için render sırasında türet. Sadece prop değişikliklerine cevap olarak effect içinde state set etme; bunun yerine türetilmiş değerleri ya da key ile reset'i tercih et.

**Yanlış (gereksiz state ve effect):**

```tsx
function Form() {
  const [firstName, setFirstName] = useState('First')
  const [lastName, setLastName] = useState('Last')
  const [fullName, setFullName] = useState('')

  useEffect(() => {
    setFullName(firstName + ' ' + lastName)
  }, [firstName, lastName])

  return <p>{fullName}</p>
}
```

**Doğru (render sırasında türet):**

```tsx
function Form() {
  const [firstName, setFirstName] = useState('First')
  const [lastName, setLastName] = useState('Last')
  const fullName = firstName + ' ' + lastName

  return <p>{fullName}</p>
}
```

Referanslar: [You Might Not Need an Effect](https://react.dev/learn/you-might-not-need-an-effect)
