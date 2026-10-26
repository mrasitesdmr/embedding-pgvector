---
title: Script Tag'lerinde defer veya async Kullan
impact: HIGH
impactDescription: render bloklamayı ortadan kaldırır
tags: rendering, script, defer, async, performance
---

## Script Tag'lerinde defer veya async Kullan

**Etki: HIGH (render bloklamayı ortadan kaldırır)**

`defer` veya `async` olmayan script tag'leri, script indirilip çalıştırılırken HTML parse işlemini bloklar. Bu, First Contentful Paint ve Time to Interactive'i geciktirir.

- **`defer`**: Paralel indirilir, HTML parse bittikten sonra çalışır, çalışma sırasını korur
- **`async`**: Paralel indirilir, hazır olur olmaz çalışır, sıra garantisi yoktur

DOM'a veya başka script'lere bağımlı script'ler için `defer`, analytics gibi bağımsız script'ler için `async` kullan.

Vite notu: Vite'ın `index.html`'e eklediği `<script type="module">` zaten varsayılan olarak defer davranır. Bu kural, `index.html`'e elle eklenen third-party script'ler için geçerlidir.

**Yanlış (render'ı bloklar):**

```html
<head>
  <script src="https://example.com/analytics.js"></script>
  <script src="/scripts/utils.js"></script>
</head>
```

**Doğru (bloklamaz):**

```html
<head>
  <!-- Bağımsız script - async kullan -->
  <script src="https://example.com/analytics.js" async></script>
  <!-- DOM'a bağımlı script - defer kullan -->
  <script src="/scripts/utils.js" defer></script>
</head>
```

Referans: [MDN - Script element](https://developer.mozilla.org/en-US/docs/Web/HTML/Element/script#defer)
