---
title: State Management'ı UI'dan Ayır
impact: MEDIUM
impactDescription: UI'ı değiştirmeden state implementasyonunu değiştirmeyi sağlar
tags: composition, state, architecture
---

## State Management'ı UI'dan Ayır

State'in nasıl yönetildiğini bilen tek yer provider component olmalıdır. UI component'leri context interface'ini kullanır; state'in useState'ten mi, Zustand'dan mı yoksa bir server sync'ten mi geldiğini bilmezler.

**Yanlış (UI, state implementasyonuna bağımlı):**

```tsx
function ChannelComposer({ channelId }: { channelId: string }) {
  // UI component global state implementasyonunu biliyor
  const state = useGlobalChannelState(channelId)
  const { submit, updateInput } = useChannelSync(channelId)

  return (
    <Composer.Frame>
      <Composer.Input
        value={state.input}
        onChange={(text) => sync.updateInput(text)}
      />
      <Composer.Submit onPress={() => sync.submit()} />
    </Composer.Frame>
  )
}
```

**Doğru (state management provider içinde izole):**

```tsx
// Provider tüm state management ayrıntılarını üstlenir
function ChannelProvider({
  channelId,
  children,
}: {
  channelId: string
  children: React.ReactNode
}) {
  const { state, update, submit } = useGlobalChannel(channelId)
  const inputRef = useRef(null)

  return (
    <Composer.Provider
      state={state}
      actions={{ update, submit }}
      meta={{ inputRef }}
    >
      {children}
    </Composer.Provider>
  )
}

// UI component sadece context interface'ini bilir
function ChannelComposer() {
  return (
    <Composer.Frame>
      <Composer.Header />
      <Composer.Input />
      <Composer.Footer>
        <Composer.Submit />
      </Composer.Footer>
    </Composer.Frame>
  )
}

// Kullanım
function Channel({ channelId }: { channelId: string }) {
  return (
    <ChannelProvider channelId={channelId}>
      <ChannelComposer />
    </ChannelProvider>
  )
}
```

**Farklı provider'lar, aynı UI:**

```tsx
// Geçici formlar için local state
function ForwardMessageProvider({ children }) {
  const [state, setState] = useState(initialState)
  const forwardMessage = useForwardMessage()

  return (
    <Composer.Provider
      state={state}
      actions={{ update: setState, submit: forwardMessage }}
    >
      {children}
    </Composer.Provider>
  )
}

// Kanallar için global senkronize state
function ChannelProvider({ channelId, children }) {
  const { state, update, submit } = useGlobalChannel(channelId)

  return (
    <Composer.Provider state={state} actions={{ update, submit }}>
      {children}
    </Composer.Provider>
  )
}
```

Aynı `Composer.Input` component'i her iki provider ile de çalışır, çünkü implementasyona değil sadece context interface'ine bağımlıdır.
