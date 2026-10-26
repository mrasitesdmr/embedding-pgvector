---
title: Etkileşim Mantığını Event Handler'lara Koy
impact: MEDIUM
impactDescription: effect'in yeniden çalışmasından ve tekrarlanan side effect'lerden kaçınır
tags: rerender, useEffect, events, side-effects, dependencies
---

## Etkileşim Mantığını Event Handler'lara Koy

Bir side effect belirli bir kullanıcı eylemiyle (submit, click, drag) tetikleniyorsa onu o event handler'da çalıştır. Eylemi state + effect olarak modelleme; bu, effect'lerin ilgisiz değişikliklerde yeniden çalışmasına ve eylemin tekrarlanmasına yol açabilir.

**Yanlış (event, state + effect olarak modellenmiş):**

```tsx
function Form() {
  const [submitted, setSubmitted] = useState(false)
  const theme = useContext(ThemeContext)

  useEffect(() => {
    if (submitted) {
      post('/api/register')
      showToast('Registered', theme)
    }
  }, [submitted, theme])

  return <button onClick={() => setSubmitted(true)}>Submit</button>
}
```

**Doğru (handler içinde yap):**

```tsx
function Form() {
  const theme = useContext(ThemeContext)

  function handleSubmit() {
    post('/api/register')
    showToast('Registered', theme)
  }

  return <button onClick={handleSubmit}>Submit</button>
}
```

Referans: [Should this code move to an event handler?](https://react.dev/learn/removing-effect-dependencies#should-this-code-move-to-an-event-handler)
