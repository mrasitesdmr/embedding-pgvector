---
title: Uzun Listeler için CSS content-visibility
impact: HIGH
impactDescription: daha hızlı ilk render
tags: rendering, css, content-visibility, long-lists
---

## Uzun Listeler için CSS content-visibility

Ekran dışındaki elemanların render'ını ertelemek için `content-visibility: auto` uygula.

**CSS:**

```css
.message-item {
  content-visibility: auto;
  contain-intrinsic-size: 0 80px;
}
```

**Örnek:**

```tsx
function MessageList({ messages }: { messages: Message[] }) {
  return (
    <div className="overflow-y-auto h-screen">
      {messages.map(msg => (
        <div key={msg.id} className="message-item">
          <Avatar user={msg.author} />
          <div>{msg.content}</div>
        </div>
      ))}
    </div>
  )
}
```

1000 mesaj için tarayıcı, ekran dışındaki ~990 eleman için layout/paint'i atlar (ilk render 10× daha hızlı).
