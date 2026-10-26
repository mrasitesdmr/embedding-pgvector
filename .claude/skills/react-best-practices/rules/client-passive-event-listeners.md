---
title: Scroll Performansı için Passive Event Listener Kullan
impact: MEDIUM
impactDescription: event listener kaynaklı scroll gecikmesini ortadan kaldırır
tags: client, event-listeners, scrolling, performance, touch, wheel
---

## Scroll Performansı için Passive Event Listener Kullan

Anında scroll'u mümkün kılmak için touch ve wheel event listener'larına `{ passive: true }` ekle. Tarayıcılar normalde `preventDefault()` çağrılıp çağrılmadığını görmek için listener'ların bitmesini bekler; bu da scroll gecikmesine yol açar.

**Yanlış:**

```typescript
useEffect(() => {
  const handleTouch = (e: TouchEvent) => console.log(e.touches[0].clientX)
  const handleWheel = (e: WheelEvent) => console.log(e.deltaY)
  
  document.addEventListener('touchstart', handleTouch)
  document.addEventListener('wheel', handleWheel)
  
  return () => {
    document.removeEventListener('touchstart', handleTouch)
    document.removeEventListener('wheel', handleWheel)
  }
}, [])
```

**Doğru:**

```typescript
useEffect(() => {
  const handleTouch = (e: TouchEvent) => console.log(e.touches[0].clientX)
  const handleWheel = (e: WheelEvent) => console.log(e.deltaY)
  
  document.addEventListener('touchstart', handleTouch, { passive: true })
  document.addEventListener('wheel', handleWheel, { passive: true })
  
  return () => {
    document.removeEventListener('touchstart', handleTouch)
    document.removeEventListener('wheel', handleWheel)
  }
}, [])
```

**Passive kullan:** tracking/analytics, logging ve `preventDefault()` çağırmayan her listener için.

**Passive kullanma:** özel swipe gesture'ları, özel zoom kontrolleri ya da `preventDefault()` gerektiren her listener için.
