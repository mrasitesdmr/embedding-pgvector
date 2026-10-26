---
title: Boolean Prop Çoğalmasından Kaçın
impact: CRITICAL
impactDescription: bakımı yapılamayan component variant'larını önler
tags: composition, props, architecture
---

## Boolean Prop Çoğalmasından Kaçın

Component davranışını özelleştirmek için `isThread`, `isEditing`, `isDMThread` gibi boolean prop'lar ekleme. Her boolean olası state sayısını ikiye katlar ve bakımı yapılamayan koşullu mantık üretir. Bunun yerine composition kullan.

**Yanlış (boolean prop'lar üstel karmaşıklık yaratır):**

```tsx
function Composer({
  onSubmit,
  isThread,
  channelId,
  isDMThread,
  dmId,
  isEditing,
  isForwarding,
}: Props) {
  return (
    <form>
      <Header />
      <Input />
      {isDMThread ? (
        <AlsoSendToDMField id={dmId} />
      ) : isThread ? (
        <AlsoSendToChannelField id={channelId} />
      ) : null}
      {isEditing ? (
        <EditActions />
      ) : isForwarding ? (
        <ForwardActions />
      ) : (
        <DefaultActions />
      )}
      <Footer onSubmit={onSubmit} />
    </form>
  )
}
```

**Doğru (composition koşulları ortadan kaldırır):**

```tsx
// Channel composer
function ChannelComposer() {
  return (
    <Composer.Frame>
      <Composer.Header />
      <Composer.Input />
      <Composer.Footer>
        <Composer.Attachments />
        <Composer.Formatting />
        <Composer.Emojis />
        <Composer.Submit />
      </Composer.Footer>
    </Composer.Frame>
  )
}

// Thread composer - "kanala da gönder" alanını ekler
function ThreadComposer({ channelId }: { channelId: string }) {
  return (
    <Composer.Frame>
      <Composer.Header />
      <Composer.Input />
      <AlsoSendToChannelField id={channelId} />
      <Composer.Footer>
        <Composer.Formatting />
        <Composer.Emojis />
        <Composer.Submit />
      </Composer.Footer>
    </Composer.Frame>
  )
}

// Edit composer - farklı footer eylemleri
function EditComposer() {
  return (
    <Composer.Frame>
      <Composer.Input />
      <Composer.Footer>
        <Composer.Formatting />
        <Composer.Emojis />
        <Composer.CancelEdit />
        <Composer.SaveEdit />
      </Composer.Footer>
    </Composer.Frame>
  )
}
```

Her variant neyi render ettiği konusunda açıktır. İç parçaları, tek bir monolitik parent'ı paylaşmadan paylaşabiliriz.
