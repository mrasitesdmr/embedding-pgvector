---
title: Barrel File Import'larından Kaçın
impact: CRITICAL
impactDescription: 200-800ms import maliyeti, yavaş build'ler
tags: bundle, imports, tree-shaking, barrel-files, performance
---

## Barrel File Import'larından Kaçın

Binlerce kullanılmayan modülü yüklememek için barrel file'lar yerine doğrudan kaynak dosyalardan import et. **Barrel file**'lar birden fazla modülü yeniden export eden giriş noktalarıdır (ör. `export * from './module'` yapan bir `index.js`).

Popüler icon ve component kütüphanelerinin giriş dosyasında **10.000'e kadar re-export** olabilir. Birçok React paketi için **sadece import etmek 200-800ms sürer**; bu hem geliştirme hızını hem de production'daki soğuk başlangıcı etkiler.

**Tree-shaking neden yardımcı olmaz:** Bir kütüphane external olarak işaretlendiğinde (bundle edilmediğinde) bundler onu optimize edemez. Tree-shaking'i mümkün kılmak için bundle edersen, tüm modül grafiğini analiz etmek build'leri belirgin şekilde yavaşlatır.

**Vite'ta durum:** Production build'de (Rollup/Rolldown) ESM paketler tree-shake edilir, ama dev server'da barrel import'lar dependency pre-bundling'e ve tarayıcıya giden çok sayıda modül isteğine yol açabilir. Kendi kodunda `src/components/index.ts` gibi barrel'lar açma; Vite'ın HMR sınırlarını da genişletir.

**Yanlış (tüm kütüphaneyi import eder):**

```tsx
import { Check, X, Menu } from 'lucide-react'
// 1.583 modül yükler, dev'de ~2,8s ekstra sürer
// Runtime maliyeti: her soğuk başlangıçta 200-800ms

import { Button, TextField } from '@mui/material'
// 2.225 modül yükler, dev'de ~4,2s ekstra sürer
```

**Doğru - Doğrudan import'lar:**

```tsx
import Button from '@mui/material/Button'
import TextField from '@mui/material/TextField'
// Sadece kullandığını yükler
```

> **TypeScript uyarısı:** Bazı kütüphaneler (özellikle `lucide-react`) derin import path'leri için `.d.ts` dosyası yayınlamaz. `lucide-react/dist/esm/icons/check`'ten import etmek implicit `any` tipine çözülür ve `strict` ya da `noImplicitAny` altında hata verir. Doğrudan import kullanmadan önce kütüphanenin subpath'leri için type export ettiğini doğrula.

<details>
<summary>Next.js'e özel çözüm (bu projede geçerli değil)</summary>

Next.js 13.5+ `next.config.js` içinde `experimental.optimizePackageImports: ['lucide-react', '@mui/material']` ile barrel import'ları build sırasında otomatik olarak doğrudan import'a çevirir.
</details>

Bu optimizasyonlar dev açılışını %15-70, build'leri %28, soğuk başlangıcı %40 hızlandırır ve HMR'ı belirgin şekilde hızlandırır.

Sık etkilenen kütüphaneler: `lucide-react`, `@mui/material`, `@mui/icons-material`, `@tabler/icons-react`, `react-icons`, `@headlessui/react`, `@radix-ui/react-*`, `lodash`, `ramda`, `date-fns`, `rxjs`, `react-use`.

Referans: [How we optimized package imports in Next.js](https://vercel.com/blog/how-we-optimized-package-imports-in-next-js)
