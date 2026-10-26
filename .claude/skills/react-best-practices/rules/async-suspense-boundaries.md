---
title: Stratejik Suspense Boundary'leri
impact: HIGH
impactDescription: daha hızlı ilk paint
tags: async, suspense, streaming, layout-shift
---

## Stratejik Suspense Boundary'leri

Tüm sayfayı veri gelene kadar bekletmek yerine, Suspense boundary'leri kullanarak veri yüklenirken çevreleyen UI'ı daha hızlı göster.

> Not: Orijinal kuraldaki `async function` component örneği Server Components içindir. Bu proje bir Vite SPA olduğu için aşağıda client'ta çalışan `use(promise)` biçimi kullanılmıştır.

**Yanlış (çevreleyen UI veri fetch'i yüzünden bloklanıyor):**

```tsx
function Page() {
  const [data, setData] = useState<Data | null>(null)
  useEffect(() => { fetchData().then(setData) }, [])

  if (!data) return <FullPageSpinner /> // Tüm sayfayı blokluyor

  return (
    <div>
      <div>Sidebar</div>
      <div>Header</div>
      <div>
        <DataDisplay data={data} />
      </div>
      <div>Footer</div>
    </div>
  )
}
```

Sadece ortadaki bölüm veriye ihtiyaç duysa da tüm layout veriyi bekliyor.

**Doğru (çevreleyen UI hemen görünür, veri geldiğinde dolar):**

```tsx
function Page({ dataPromise }: { dataPromise: Promise<Data> }) {
  // dataPromise render sırasında OLUŞTURULMAZ; route loader'dan, cache'ten
  // ya da üst component'te bir kez oluşturulup prop olarak gelir.
  return (
    <div>
      <div>Sidebar</div>
      <div>Header</div>
      <Suspense fallback={<Skeleton />}>
        <DataDisplay dataPromise={dataPromise} />
        <DataSummary dataPromise={dataPromise} />
      </Suspense>
      <div>Footer</div>
    </div>
  )
}

function DataDisplay({ dataPromise }: { dataPromise: Promise<Data> }) {
  const data = use(dataPromise) // Promise'i çözer
  return <div>{data.content}</div>
}

function DataSummary({ dataPromise }: { dataPromise: Promise<Data> }) {
  const data = use(dataPromise) // Aynı promise'i yeniden kullanır
  return <div>{data.summary}</div>
}
```

Sidebar, Header ve Footer hemen render olur. İki component aynı promise'i paylaştığı için sadece bir fetch yapılır ve birlikte beklerler.

Client'ta `use(promise)` kullanırken promise'in render'lar arasında sabit kalması gerekir; render içinde `use(fetchData())` her render'da yeni promise oluşturur ve sonsuz suspend'e yol açar. Pratikte bunu TanStack Query'nin `useSuspenseQuery`'si ya da bir route loader'ı yönetir.

**Bu pattern'i ne zaman KULLANMA:**

- Layout kararları için gereken kritik veri (konumlandırmayı etkiliyorsa)
- Ekranın üst kısmındaki SEO-kritik içerik
- Suspense ek yükünün değmeyeceği küçük, hızlı sorgular
- Layout shift'ten (loading → içerik zıplaması) kaçınmak istediğinde

**Trade-off:** Daha hızlı ilk paint'e karşılık olası layout shift. UX önceliklerine göre seç.
