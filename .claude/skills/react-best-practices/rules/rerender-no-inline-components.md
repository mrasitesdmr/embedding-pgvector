---
title: Component İçinde Component Tanımlama
impact: HIGH
impactDescription: her render'da remount'u önler
tags: rerender, components, remount, performance
---

## Component İçinde Component Tanımlama

**Etki: HIGH (her render'da remount'u önler)**

Bir component'i başka bir component'in içinde tanımlamak her render'da yeni bir component tipi oluşturur. React her seferinde farklı bir component görür ve onu tamamen remount eder; tüm state ve DOM yok olur.

Geliştiricilerin bunu yapmasının yaygın bir sebebi, prop geçmeden parent değişkenlerine erişmektir. Bunun yerine her zaman prop geç.

**Yanlış (her render'da remount olur):**

```tsx
function UserProfile({ user, theme }) {
  // `theme`'e erişmek için içeride tanımlanmış - KÖTÜ
  const Avatar = () => (
    <img
      src={user.avatarUrl}
      className={theme === 'dark' ? 'avatar-dark' : 'avatar-light'}
    />
  )

  // `user`'a erişmek için içeride tanımlanmış - KÖTÜ
  const Stats = () => (
    <div>
      <span>{user.followers} followers</span>
      <span>{user.posts} posts</span>
    </div>
  )

  return (
    <div>
      <Avatar />
      <Stats />
    </div>
  )
}
```

`UserProfile` her render olduğunda `Avatar` ve `Stats` yeni component tipleridir. React eski instance'ları unmount edip yenilerini mount eder; iç state kaybolur, effect'ler yeniden çalışır ve DOM node'ları yeniden oluşturulur.

**Doğru (bunun yerine prop geç):**

```tsx
function Avatar({ src, theme }: { src: string; theme: string }) {
  return (
    <img
      src={src}
      className={theme === 'dark' ? 'avatar-dark' : 'avatar-light'}
    />
  )
}

function Stats({ followers, posts }: { followers: number; posts: number }) {
  return (
    <div>
      <span>{followers} followers</span>
      <span>{posts} posts</span>
    </div>
  )
}

function UserProfile({ user, theme }) {
  return (
    <div>
      <Avatar src={user.avatarUrl} theme={theme} />
      <Stats followers={user.followers} posts={user.posts} />
    </div>
  )
}
```

**Bu bug'ın belirtileri:**
- Input alanları her tuş vuruşunda focus kaybeder
- Animasyonlar beklenmedik şekilde yeniden başlar
- `useEffect` cleanup/setup her parent render'ında çalışır
- Component içindeki scroll pozisyonu sıfırlanır
