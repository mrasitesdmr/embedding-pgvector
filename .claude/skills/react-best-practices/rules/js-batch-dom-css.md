---
title: Layout Thrashing'den Kaçın
impact: MEDIUM
impactDescription: zorunlu senkron layout'ları önler ve performans darboğazlarını azaltır
tags: javascript, dom, css, performance, reflow, layout-thrashing
---

## Layout Thrashing'den Kaçın

Style yazma işlemlerini layout okumalarıyla iç içe yapma. Style değişiklikleri arasında bir layout property'si (`offsetWidth`, `getBoundingClientRect()` veya `getComputedStyle()` gibi) okuduğunda tarayıcı senkron bir reflow tetiklemek zorunda kalır.

**Bu sorun değil (tarayıcı style değişikliklerini toplu işler):**
```typescript
function updateElementStyles(element: HTMLElement) {
  // Her satır style'ı geçersiz kılar, ama tarayıcı yeniden hesaplamayı toplu yapar
  element.style.width = '100px'
  element.style.height = '200px'
  element.style.backgroundColor = 'blue'
  element.style.border = '1px solid black'
}
```

**Yanlış (iç içe okuma ve yazmalar reflow'a zorlar):**
```typescript
function layoutThrashing(element: HTMLElement) {
  element.style.width = '100px'
  const width = element.offsetWidth  // Reflow'a zorlar
  element.style.height = '200px'
  const height = element.offsetHeight  // Bir reflow'a daha zorlar
}
```

**Doğru (yazmaları topla, sonra bir kez oku):**
```typescript
function updateElementStyles(element: HTMLElement) {
  // Tüm yazmaları bir araya topla
  element.style.width = '100px'
  element.style.height = '200px'
  element.style.backgroundColor = 'blue'
  element.style.border = '1px solid black'
  
  // Tüm yazmalar bittikten sonra oku (tek reflow)
  const { width, height } = element.getBoundingClientRect()
}
```

**Doğru (okumaları topla, sonra yaz):**
```typescript
function avoidThrashing(element: HTMLElement) {
  // Okuma fazı - önce tüm layout sorguları
  const rect1 = element.getBoundingClientRect()
  const offsetWidth = element.offsetWidth
  const offsetHeight = element.offsetHeight
  
  // Yazma fazı - sonra tüm style değişiklikleri
  element.style.width = '100px'
  element.style.height = '200px'
}
```

**Daha iyisi: CSS class'ları kullan**
```css
.highlighted-box {
  width: 100px;
  height: 200px;
  background-color: blue;
  border: 1px solid black;
}
```
```typescript
function updateElementStyles(element: HTMLElement) {
  element.classList.add('highlighted-box')
  
  const { width, height } = element.getBoundingClientRect()
}
```

**React örneği:**
```tsx
// Yanlış: style değişikliklerini layout sorgularıyla iç içe yapmak
function Box({ isHighlighted }: { isHighlighted: boolean }) {
  const ref = useRef<HTMLDivElement>(null)
  
  useEffect(() => {
    if (ref.current && isHighlighted) {
      ref.current.style.width = '100px'
      const width = ref.current.offsetWidth // Layout'a zorlar
      ref.current.style.height = '200px'
    }
  }, [isHighlighted])
  
  return <div ref={ref}>Content</div>
}

// Doğru: class'ı aç/kapa
function Box({ isHighlighted }: { isHighlighted: boolean }) {
  return (
    <div className={isHighlighted ? 'highlighted-box' : ''}>
      Content
    </div>
  )
}
```

Mümkün olduğunda inline style yerine CSS class'larını tercih et. CSS dosyaları tarayıcı tarafından cache'lenir; class'lar sorumlulukların daha iyi ayrılmasını sağlar ve bakımı daha kolaydır.

Layout'a zorlayan işlemler hakkında daha fazla bilgi için [bu gist'e](https://gist.github.com/paulirish/5d52fb081b3570c81e3a) ve [CSS Triggers](https://csstriggers.com/)'a bak.
