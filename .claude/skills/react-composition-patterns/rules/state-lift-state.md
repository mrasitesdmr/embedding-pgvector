---
title: State'i Provider Component'lerine Taşı
impact: HIGH
impactDescription: component sınırları dışında state paylaşımını sağlar
tags: composition, state, context, providers
---

## State'i Provider Component'lerine Taşı

State management'ı ayrı provider component'lerine taşı. Bu, ana UI'ın dışındaki kardeş component'lerin prop drilling ya da garip ref'ler olmadan state'e erişip onu değiştirebilmesini sağlar.

**Yanlış (state component içinde hapsolmuş):**

```tsx
function ForwardMessageComposer() {
  const [state, setState] = useState(initialState)
  const forwardMessage = useForwardMessage()

  return (
    <Composer.Frame>
      <Composer.Input />
      <Composer.Footer />
    </Composer.Frame>
  )
}

// Sorun: Bu buton composer state'ine nasıl erişecek?
function ForwardMessageDialog() {
  return (
    <Dialog>
      <ForwardMessageComposer />
      <MessagePreview /> {/* Composer state'ine ihtiyaç duyuyor */}
      <DialogActions>
        <CancelButton />
        <ForwardButton /> {/* submit'i çağırması gerekiyor */}
      </DialogActions>
    </Dialog>
  )
}
```

**Yanlış (state'i yukarı senkronlamak için useEffect):**

```tsx
function ForwardMessageDialog() {
  const [input, setInput] = useState('')
  return (
    <Dialog>
      <ForwardMessageComposer onInputChange={setInput} />
      <MessagePreview input={input} />
    </Dialog>
  )
}

function ForwardMessageComposer({ onInputChange }) {
  const [state, setState] = useState(initialState)
  useEffect(() => {
    onInputChange(state.input) // Her değişiklikte senkronlama 😬
  }, [state.input])
}
```

**Yanlış (submit sırasında state'i ref'ten okumak):**

```tsx
function ForwardMessageDialog() {
  const stateRef = useRef(null)
  return (
    <Dialog>
      <ForwardMessageComposer stateRef={stateRef} />
      <ForwardButton onPress={() => submit(stateRef.current)} />
    </Dialog>
  )
}
```

**Doğru (state provider'a taşınmış):**

```tsx
function ForwardMessageProvider({ children }: { children: React.ReactNode }) {
  const [state, setState] = useState(initialState)
  const forwardMessage = useForwardMessage()
  const inputRef = useRef(null)

  return (
    <Composer.Provider
      state={state}
      actions={{ update: setState, submit: forwardMessage }}
      meta={{ inputRef }}
    >
      {children}
    </Composer.Provider>
  )
}

function ForwardMessageDialog() {
  return (
    <ForwardMessageProvider>
      <Dialog>
        <ForwardMessageComposer />
        <MessagePreview /> {/* Özel component'ler state ve actions'a erişebilir */}
        <DialogActions>
          <CancelButton />
          <ForwardButton /> {/* Özel component'ler state ve actions'a erişebilir */}
        </DialogActions>
      </Dialog>
    </ForwardMessageProvider>
  )
}

function ForwardButton() {
  const { actions } = use(Composer.Context)
  return <Button onPress={actions.submit}>Forward</Button>
}
```

ForwardButton, Composer.Frame'in dışında yaşar ama provider'ın içinde olduğu için submit action'ına yine de erişir. Tek seferlik bir component olsa bile composer'ın state ve actions'ına UI'ın dışından erişebilir.

**Temel çıkarım:** Paylaşılan state'e ihtiyaç duyan component'lerin görsel olarak birbirinin içinde olması gerekmez; sadece aynı provider'ın içinde olmaları yeterlidir.
