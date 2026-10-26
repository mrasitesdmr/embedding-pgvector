---
title: Lazy State Initialization Kullan
impact: MEDIUM
impactDescription: her render'da boşa harcanan hesaplama
tags: react, hooks, useState, performance, initialization
---

## Lazy State Initialization Kullan

Pahalı başlangıç değerleri için `useState`'e bir function geçir. Function biçimi olmadan, değer sadece bir kez kullanılsa da initializer her render'da çalışır.

**Yanlış (her render'da çalışır):**

```tsx
function FilteredList({ items }: { items: Item[] }) {
  // buildSearchIndex() initialization'dan sonra bile HER render'da çalışır
  const [searchIndex, setSearchIndex] = useState(buildSearchIndex(items))
  const [query, setQuery] = useState('')
  
  // query değiştiğinde buildSearchIndex gereksiz yere tekrar çalışır
  return <SearchResults index={searchIndex} query={query} />
}

function UserProfile() {
  // JSON.parse her render'da çalışır
  const [settings, setSettings] = useState(
    JSON.parse(localStorage.getItem('settings') || '{}')
  )
  
  return <SettingsForm settings={settings} onChange={setSettings} />
}
```

**Doğru (sadece bir kez çalışır):**

```tsx
function FilteredList({ items }: { items: Item[] }) {
  // buildSearchIndex() SADECE ilk render'da çalışır
  const [searchIndex, setSearchIndex] = useState(() => buildSearchIndex(items))
  const [query, setQuery] = useState('')
  
  return <SearchResults index={searchIndex} query={query} />
}

function UserProfile() {
  // JSON.parse sadece ilk render'da çalışır
  const [settings, setSettings] = useState(() => {
    const stored = localStorage.getItem('settings')
    return stored ? JSON.parse(stored) : {}
  })
  
  return <SettingsForm settings={settings} onChange={setSettings} />
}
```

Başlangıç değerlerini localStorage/sessionStorage'dan hesaplarken, veri yapıları (index'ler, map'ler) kurarken, DOM'dan okurken veya ağır dönüşümler yaparken lazy initialization kullan.

Basit primitive'ler (`useState(0)`), doğrudan referanslar (`useState(props.value)`) veya ucuz literal'ler (`useState({})`) için function biçimi gereksizdir.
