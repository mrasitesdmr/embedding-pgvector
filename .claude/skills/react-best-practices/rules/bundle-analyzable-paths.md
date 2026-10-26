---
title: Statik Olarak Analiz Edilebilir Path'leri Tercih Et
impact: HIGH
impactDescription: istemeden oluşan geniş bundle'lardan ve file trace'lerden kaçınır
tags: bundle, vite, rollup, esbuild, path
---

## Statik Olarak Analiz Edilebilir Path'leri Tercih Et

Build araçları, import ve dosya sistemi path'leri build sırasında belli olduğunda en iyi çalışır. Gerçek path'i bir değişkenin içine gizlersen ya da çok dinamik şekilde birleştirirsen, araç ya olası dosyaların geniş bir kümesini dahil etmek, ya import'u analiz edemediğine dair uyarı vermek ya da güvende kalmak için file tracing'i genişletmek zorunda kalır.

Erişilebilir dosya kümesi dar ve öngörülebilir kalsın diye açık map'leri veya literal path'leri tercih et. `import()` ile modül seçerken de, server/build kodunda dosya okurken de kural aynıdır.

Analiz çok genişlediğinde maliyet gerçektir:
- Daha büyük bundle'lar
- Daha yavaş build'ler
- Daha kötü soğuk başlangıçlar
- Daha fazla bellek kullanımı

### Import path'leri

**Yanlış (bundler neyin import edilebileceğini bilemez):**

```ts
const PAGE_MODULES = {
  home: './pages/home',
  settings: './pages/settings',
} as const

const Page = await import(PAGE_MODULES[pageName])
```

**Doğru (izin verilen modüllerin açık bir map'ini kullan):**

```ts
const PAGE_MODULES = {
  home: () => import('./pages/home'),
  settings: () => import('./pages/settings'),
} as const

const Page = await PAGE_MODULES[pageName]()
```

### Dosya sistemi path'leri (build script'leri, Vite config)

**Yanlış (2 değerli bir enum bile son path'i statik analizden gizler):**

```ts
const baseDir = path.join(process.cwd(), 'content/' + contentKind)
```

**Doğru (her son path'i çağrıldığı yerde literal yap):**

```ts
const baseDir =
  kind === ContentKind.Blog
    ? path.join(process.cwd(), 'content/blog')
    : path.join(process.cwd(), 'content/docs')
```

Referans: [Vite features](https://vite.dev/guide/features.html), [esbuild API](https://esbuild.github.io/api/), [Rollup dynamic import vars](https://www.npmjs.com/package/@rollup/plugin-dynamic-import-vars)
