---
title: Memoize Edilmiş Component'lere Ayır
impact: MEDIUM
impactDescription: early return'leri mümkün kılar
tags: rerender, memo, useMemo, optimization
---

## Memoize Edilmiş Component'lere Ayır

Hesaplamadan önce early return yapabilmek için pahalı işi memoize edilmiş component'lere ayır.

**Yanlış (loading durumunda bile avatar'ı hesaplar):**

```tsx
function Profile({ user, loading }: Props) {
  const avatar = useMemo(() => {
    const id = computeAvatarId(user)
    return <Avatar id={id} />
  }, [user])

  if (loading) return <Skeleton />
  return <div>{avatar}</div>
}
```

**Doğru (loading durumunda hesaplamayı atlar):**

```tsx
const UserAvatar = memo(function UserAvatar({ user }: { user: User }) {
  const id = useMemo(() => computeAvatarId(user), [user])
  return <Avatar id={id} />
})

function Profile({ user, loading }: Props) {
  if (loading) return <Skeleton />
  return (
    <div>
      <UserAvatar user={user} />
    </div>
  )
}
```

**Not:** Projende [React Compiler](https://react.dev/learn/react-compiler) etkinse `memo()` ve `useMemo()` ile manuel memoization gerekmez. Compiler re-render'ları otomatik optimize eder.
