---
title: Tekrarlanan Function Çağrılarını Cache'le
impact: MEDIUM
impactDescription: gereksiz hesaplamadan kaçınır
tags: javascript, cache, memoization, performance
---

## Tekrarlanan Function Çağrılarını Cache'le

Aynı function render sırasında aynı input'larla tekrar tekrar çağrılıyorsa sonuçları cache'lemek için module-level bir Map kullan.

**Yanlış (gereksiz hesaplama):**

```typescript
function ProjectList({ projects }: { projects: Project[] }) {
  return (
    <div>
      {projects.map(project => {
        // slugify() aynı proje isimleri için 100+ kez çağrılıyor
        const slug = slugify(project.name)
        
        return <ProjectCard key={project.id} slug={slug} />
      })}
    </div>
  )
}
```

**Doğru (cache'lenmiş sonuçlar):**

```typescript
// Module-level cache
const slugifyCache = new Map<string, string>()

function cachedSlugify(text: string): string {
  if (slugifyCache.has(text)) {
    return slugifyCache.get(text)!
  }
  const result = slugify(text)
  slugifyCache.set(text, result)
  return result
}

function ProjectList({ projects }: { projects: Project[] }) {
  return (
    <div>
      {projects.map(project => {
        // Her benzersiz proje ismi için sadece bir kez hesaplanır
        const slug = cachedSlugify(project.name)
        
        return <ProjectCard key={project.id} slug={slug} />
      })}
    </div>
  )
}
```

**Tek değer döndüren function'lar için daha basit pattern:**

```typescript
let isLoggedInCache: boolean | null = null

function isLoggedIn(): boolean {
  if (isLoggedInCache !== null) {
    return isLoggedInCache
  }
  
  isLoggedInCache = document.cookie.includes('auth=')
  return isLoggedInCache
}

// Auth değiştiğinde cache'i temizle
function onAuthChange() {
  isLoggedInCache = null
}
```

Her yerde çalışması için (sadece React component'lerinde değil; utility'lerde, event handler'larda da) hook değil Map kullan.

Referans: [How we made the Vercel Dashboard twice as fast](https://vercel.com/blog/how-we-made-the-vercel-dashboard-twice-as-fast)
