---
title: Render Prop Yerine Children ile Compose Etmeyi Tercih Et
impact: MEDIUM
impactDescription: daha temiz composition, daha iyi okunabilirlik
tags: composition, children, render-props
---

## Render Prop Yerine Children Tercih Et

Composition için `renderX` prop'ları yerine `children` kullan. Children daha okunabilirdir, doğal şekilde compose olur ve callback imzalarını anlamayı gerektirmez.

**Yanlış (render prop'lar):**

```tsx
function Composer({
  renderHeader,
  renderFooter,
  renderActions,
}: {
  renderHeader?: () => React.ReactNode
  renderFooter?: () => React.ReactNode
  renderActions?: () => React.ReactNode
}) {
  return (
    <form>
      {renderHeader?.()}
      <Input />
      {renderFooter ? renderFooter() : <DefaultFooter />}
      {renderActions?.()}
    </form>
  )
}

// Kullanımı hantal ve esnek değil
return (
  <Composer
    renderHeader={() => <CustomHeader />}
    renderFooter={() => (
      <>
        <Formatting />
        <Emojis />
      </>
    )}
    renderActions={() => <SubmitButton />}
  />
)
```

**Doğru (children ile compound component'ler):**

```tsx
function ComposerFrame({ children }: { children: React.ReactNode }) {
  return <form>{children}</form>
}

function ComposerFooter({ children }: { children: React.ReactNode }) {
  return <footer className='flex'>{children}</footer>
}

// Kullanımı esnek
return (
  <Composer.Frame>
    <CustomHeader />
    <Composer.Input />
    <Composer.Footer>
      <Composer.Formatting />
      <Composer.Emojis />
      <SubmitButton />
    </Composer.Footer>
  </Composer.Frame>
)
```

**Render prop'lar ne zaman uygundur:**

```tsx
// Veriyi geri iletmen gerektiğinde render prop'lar iyi çalışır
<List
  data={items}
  renderItem={({ item, index }) => <Item item={item} index={index} />}
/>
```

Parent'ın child'a veri ya da state sağlaması gerektiğinde render prop kullan. Statik yapı compose ederken children kullan.
