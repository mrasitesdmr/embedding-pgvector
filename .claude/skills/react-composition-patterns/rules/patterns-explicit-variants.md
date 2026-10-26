---
title: Açık (Explicit) Component Variant'ları Oluştur
impact: MEDIUM
impactDescription: kendini belgeleyen kod, gizli koşul yok
tags: composition, variants, architecture
---

## Açık (Explicit) Component Variant'ları Oluştur

Çok sayıda boolean prop'u olan tek bir component yerine açık variant component'ler oluştur. Her variant ihtiyaç duyduğu parçaları compose eder. Kod kendini belgeler.

**Yanlış (tek component, çok mod):**

```tsx
// Bu component aslında neyi render ediyor?
<Composer
  isThread
  isEditing={false}
  channelId='abc'
  showAttachments
  showFormatting={false}
/>
```

**Doğru (açık variant'lar):**

```tsx
// Neyi render ettiği hemen anlaşılıyor
<ThreadComposer channelId="abc" />

// Ya da
<EditMessageComposer messageId="xyz" />

// Ya da
<ForwardMessageComposer messageId="123" />
```

Her implementasyon benzersiz, açık ve kendi içinde tamdır. Yine de her biri paylaşılan parçaları kullanabilir.

**Implementasyon:**

```tsx
function ThreadComposer({ channelId }: { channelId: string }) {
  return (
    <ThreadProvider channelId={channelId}>
      <Composer.Frame>
        <Composer.Input />
        <AlsoSendToChannelField channelId={channelId} />
        <Composer.Footer>
          <Composer.Formatting />
          <Composer.Emojis />
          <Composer.Submit />
        </Composer.Footer>
      </Composer.Frame>
    </ThreadProvider>
  )
}

function EditMessageComposer({ messageId }: { messageId: string }) {
  return (
    <EditMessageProvider messageId={messageId}>
      <Composer.Frame>
        <Composer.Input />
        <Composer.Footer>
          <Composer.Formatting />
          <Composer.Emojis />
          <Composer.CancelEdit />
          <Composer.SaveEdit />
        </Composer.Footer>
      </Composer.Frame>
    </EditMessageProvider>
  )
}

function ForwardMessageComposer({ messageId }: { messageId: string }) {
  return (
    <ForwardMessageProvider messageId={messageId}>
      <Composer.Frame>
        <Composer.Input placeholder="Add a message, if you'd like." />
        <Composer.Footer>
          <Composer.Formatting />
          <Composer.Emojis />
          <Composer.Mentions />
        </Composer.Footer>
      </Composer.Frame>
    </ForwardMessageProvider>
  )
}
```

Her variant şunlar hakkında açıktır:

- Hangi provider'ı/state'i kullandığı
- Hangi UI elemanlarını içerdiği
- Hangi eylemlerin mevcut olduğu

Akıl yürütülmesi gereken boolean prop kombinasyonları yoktur. İmkânsız state'ler yoktur.
