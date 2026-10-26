---
title: Koşullu Modül Yükleme
impact: HIGH
impactDescription: büyük veriyi sadece gerektiğinde yükler
tags: bundle, conditional-loading, lazy-loading
---

## Koşullu Modül Yükleme

Büyük veri veya modülleri sadece bir özellik aktifleştirildiğinde yükle.

**Örnek (animasyon frame'lerini lazy-load etmek):**

```tsx
function AnimationPlayer({ enabled, setEnabled }: { enabled: boolean; setEnabled: React.Dispatch<React.SetStateAction<boolean>> }) {
  const [frames, setFrames] = useState<Frame[] | null>(null)

  useEffect(() => {
    if (enabled && !frames) {
      import('./animation-frames.js')
        .then(mod => setFrames(mod.frames))
        .catch(() => setEnabled(false))
    }
  }, [enabled, frames, setEnabled])

  if (!frames) return <Skeleton />
  return <Canvas frames={frames} />
}
```

Orijinal kuraldaki `typeof window !== 'undefined'` kontrolü SSR bundle'ını küçültmek içindir; SSR olmayan bu Vite SPA'da gerekmez.
