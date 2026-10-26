---
title: SVG Hassasiyetini Optimize Et
impact: LOW
impactDescription: dosya boyutunu azaltır
tags: rendering, svg, optimization, svgo
---

## SVG Hassasiyetini Optimize Et

Dosya boyutunu azaltmak için SVG koordinat hassasiyetini düşür. En uygun hassasiyet viewBox boyutuna bağlıdır, ama genel olarak hassasiyeti düşürmek değerlendirilmelidir.

**Yanlış (aşırı hassasiyet):**

```svg
<path d="M 10.293847 20.847362 L 30.938472 40.192837" />
```

**Doğru (1 ondalık basamak):**

```svg
<path d="M 10.3 20.8 L 30.9 40.2" />
```

**SVGO ile otomatikleştir:**

```bash
npx svgo --precision=1 --multipass icon.svg
```
