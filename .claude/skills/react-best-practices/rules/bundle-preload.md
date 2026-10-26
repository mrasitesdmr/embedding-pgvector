---
title: Kullanıcı Niyetine Göre Preload
impact: MEDIUM
impactDescription: algılanan gecikmeyi azaltır
tags: bundle, preload, user-intent, hover
---

## Kullanıcı Niyetine Göre Preload

Algılanan gecikmeyi azaltmak için ağır bundle'ları ihtiyaç duyulmadan önce preload et.

**Örnek (hover/focus'ta preload):**

```tsx
function EditorButton({ onClick }: { onClick: () => void }) {
  const preload = () => {
    void import('./monaco-editor')
  }

  return (
    <button
      onMouseEnter={preload}
      onFocus={preload}
      onClick={onClick}
    >
      Open Editor
    </button>
  )
}
```

**Örnek (feature flag açıkken preload):**

```tsx
function FlagsProvider({ children, flags }: Props) {
  useEffect(() => {
    if (flags.editorEnabled) {
      void import('./monaco-editor').then(mod => mod.init())
    }
  }, [flags.editorEnabled])

  return <FlagsContext value={flags}>
    {children}
  </FlagsContext>
}
```

Orijinal kuraldaki `typeof window !== 'undefined'` kontrolü SSR içindir; bu Vite SPA'da gerekmez. Aynı `import()` path'i `React.lazy` ile de kullanılıyorsa tarayıcı modülü bir kez indirir, lazy component hazır olur.
