---
title: Dependency Injection için Generic Context Interface'leri Tanımla
impact: HIGH
impactDescription: farklı kullanım senaryolarında dependency injection ile verilebilen state sağlar
tags: composition, context, state, typescript, dependency-injection
---

## Dependency Injection için Generic Context Interface'leri Tanımla

Component context'in için üç parçalı bir **generic interface** tanımla: `state`, `actions` ve `meta`. Bu interface, herhangi bir provider'ın implement edebileceği bir sözleşmedir (contract); böylece aynı UI component'leri tamamen farklı state implementasyonlarıyla çalışabilir.

**Temel ilke:** State'i yukarı taşı, iç parçaları compose et, state'i dependency injection ile verilebilir yap.

**Yanlış (UI belirli bir state implementasyonuna bağımlı):**

```tsx
function ComposerInput() {
  // Belirli bir hook'a sıkı sıkıya bağlı
  const { input, setInput } = useChannelComposerState()
  return <TextInput value={input} onChangeText={setInput} />
}
```

**Doğru (generic interface dependency injection'ı mümkün kılar):**

```tsx
// Herhangi bir provider'ın implement edebileceği GENERIC bir interface tanımla
type ComposerState = {
  input: string
  attachments: Attachment[]
  isSubmitting: boolean
}

type ComposerActions = {
  update: (updater: (state: ComposerState) => ComposerState) => void
  submit: () => void
}

type ComposerMeta = {
  inputRef: React.RefObject<TextInput | null>
}

type ComposerContextValue = {
  state: ComposerState
  actions: ComposerActions
  meta: ComposerMeta
}

const ComposerContext = createContext<ComposerContextValue | null>(null)
```

**UI component'leri implementasyonu değil interface'i kullanır:**

```tsx
function ComposerInput() {
  const {
    state,
    actions: { update },
    meta,
  } = use(ComposerContext)

  // Bu component interface'i implement eden HERHANGİ bir provider ile çalışır
  return (
    <TextInput
      ref={meta.inputRef}
      value={state.input}
      onChangeText={(text) => update((s) => ({ ...s, input: text }))}
    />
  )
}
```

**Farklı provider'lar aynı interface'i implement eder:**

```tsx
// Provider A: Geçici formlar için local state
function ForwardMessageProvider({ children }: { children: React.ReactNode }) {
  const [state, setState] = useState(initialState)
  const inputRef = useRef(null)
  const submit = useForwardMessage()

  return (
    <ComposerContext
      value={{
        state,
        actions: { update: setState, submit },
        meta: { inputRef },
      }}
    >
      {children}
    </ComposerContext>
  )
}

// Provider B: Kanallar için global senkronize state
function ChannelProvider({ channelId, children }: Props) {
  const { state, update, submit } = useGlobalChannel(channelId)
  const inputRef = useRef(null)

  return (
    <ComposerContext
      value={{
        state,
        actions: { update, submit },
        meta: { inputRef },
      }}
    >
      {children}
    </ComposerContext>
  )
}
```

**Aynı compose edilmiş UI ikisiyle de çalışır:**

```tsx
// ForwardMessageProvider ile çalışır (local state)
<ForwardMessageProvider>
  <Composer.Frame>
    <Composer.Input />
    <Composer.Submit />
  </Composer.Frame>
</ForwardMessageProvider>

// ChannelProvider ile çalışır (global senkronize state)
<ChannelProvider channelId="abc">
  <Composer.Frame>
    <Composer.Input />
    <Composer.Submit />
  </Composer.Frame>
</ChannelProvider>
```

**Component'in dışındaki özel UI da state ve actions'a erişebilir:**

Önemli olan görsel iç içelik değil, provider sınırıdır. Paylaşılan state'e ihtiyaç duyan component'lerin `Composer.Frame` içinde olması gerekmez. Sadece provider'ın içinde olmaları yeterlidir.

```tsx
function ForwardMessageDialog() {
  return (
    <ForwardMessageProvider>
      <Dialog>
        {/* Composer UI'ı */}
        <Composer.Frame>
          <Composer.Input placeholder="Add a message, if you'd like." />
          <Composer.Footer>
            <Composer.Formatting />
            <Composer.Emojis />
          </Composer.Footer>
        </Composer.Frame>

        {/* Composer'ın DIŞINDA ama provider'ın İÇİNDE özel UI */}
        <MessagePreview />

        {/* Dialog'un altındaki eylemler */}
        <DialogActions>
          <CancelButton />
          <ForwardButton />
        </DialogActions>
      </Dialog>
    </ForwardMessageProvider>
  )
}

// Bu buton Composer.Frame'in DIŞINDA ama context'i sayesinde yine de submit edebilir!
function ForwardButton() {
  const {
    actions: { submit },
  } = use(ComposerContext)
  return <Button onPress={submit}>Forward</Button>
}

// Bu önizleme Composer.Frame'in DIŞINDA ama composer'ın state'ini okuyabilir!
function MessagePreview() {
  const { state } = use(ComposerContext)
  return <Preview message={state.input} attachments={state.attachments} />
}
```

`ForwardButton` ve `MessagePreview` görsel olarak composer kutusunun içinde değil, ama yine de onun state ve actions'ına erişebilirler. State'i provider'lara taşımanın gücü budur.

UI, birlikte compose ettiğin yeniden kullanılabilir parçalardır. State provider tarafından dependency injection ile verilir. Provider'ı değiştir, UI'ı koru.
