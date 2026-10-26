---
title: React DOM Resource Hint'lerini Kullan
impact: HIGH
impactDescription: kritik kaynakların yüklenme süresini azaltır
tags: rendering, preload, preconnect, prefetch, resource-hints
---

## React DOM Resource Hint'lerini Kullan

**Etki: HIGH (kritik kaynakların yüklenme süresini azaltır)**

React DOM, tarayıcıya ihtiyaç duyacağı kaynaklar hakkında ipucu vermek için API'ler sağlar. Server component'lerde özellikle faydalıdırlar, ama SPA'da da client render sırasında çağrılabilirler.

- **`prefetchDNS(href)`**: Bağlanmayı beklediğin bir domain için DNS çöz
- **`preconnect(href)`**: Bir server'a bağlantı kur (DNS + TCP + TLS)
- **`preload(href, options)`**: Yakında kullanacağın bir kaynağı (stylesheet, font, script, image) fetch et
- **`preloadModule(href)`**: Yakında kullanacağın bir ES modülünü fetch et
- **`preinit(href, options)`**: Bir stylesheet veya script'i fetch et ve çalıştır
- **`preinitModule(href)`**: Bir ES modülünü fetch et ve çalıştır

**Örnek (third-party API'lere preconnect):**

```tsx
import { preconnect, prefetchDNS } from 'react-dom'

export function App() {
  prefetchDNS('https://analytics.example.com')
  preconnect('https://api.example.com')

  return <main>{/* içerik */}</main>
}
```

**Örnek (kritik font ve stilleri preload etmek):**

```tsx
import { preload, preinit } from 'react-dom'

export function App() {
  // Font dosyasını preload et
  preload('/fonts/inter.woff2', { as: 'font', type: 'font/woff2', crossOrigin: 'anonymous' })

  // Kritik stylesheet'i hemen fetch edip uygula
  preinit('/styles/critical.css', { as: 'style' })

  return <main>{/* içerik */}</main>
}
```

**Örnek (code-split route'lar için modül preload):**

```tsx
import { preloadModule } from 'react-dom'

function Navigation() {
  const preloadDashboard = () => {
    preloadModule('/dashboard.js', { as: 'script' })
  }

  return (
    <nav>
      <a href="/dashboard" onMouseEnter={preloadDashboard}>
        Dashboard
      </a>
    </nav>
  )
}
```

Vite'ta build sonrası chunk adları hash'lidir; route modüllerini preload etmek için path yerine `() => import('./pages/X')` çağrısını tetiklemek (bkz. `bundle-preload`) daha güvenilirdir.

**Hangisi ne zaman:**

| API | Kullanım durumu |
|-----|----------|
| `prefetchDNS` | Daha sonra bağlanacağın third-party domain'ler |
| `preconnect` | Hemen fetch yapacağın API'ler veya CDN'ler |
| `preload` | Mevcut sayfa için gereken kritik kaynaklar |
| `preloadModule` | Muhtemel bir sonraki navigasyon için JS modülleri |
| `preinit` | Erken çalışması gereken stylesheet/script'ler |
| `preinitModule` | Erken çalışması gereken ES modülleri |

Referans: [React DOM Resource Preloading APIs](https://react.dev/reference/react-dom#resource-preloading-apis)
