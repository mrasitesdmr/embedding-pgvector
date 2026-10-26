---
name: react-patterns
description: React 19 pattern'leri - hook disiplini, Suspense + error boundary'ler, form action'ları, data fetching, state management karar ağaçları ve accessibility-first composition. frontend/ altında React component'leri veya custom hook yazarken ya da review ederken, state'in nerede tutulacağına karar verirken veya form/data fetching kurarken kullan.
license: MIT
metadata:
  origin: ECC
  source: https://github.com/affaan-m/ECC/tree/main/skills/react-patterns
---

# React Pattern'leri

Sağlam, erişilebilir ve performanslı component ağaçları kurmak için idiomatik React 19 pattern'leri.

> **Proje kuralı:** Teknoloji seçimi (TanStack Query, `useState`/`useReducer`/Context, React Router, CSS Modules), dosya yapısı, isimlendirme ve kod biçimi `frontend-conventions`'tadır. Bu dosyadaki genel örnekler (SWR, Zustand, Jotai, React Hook Form, çift tırnak/noktalı virgül) seçenekleri anlatır; çelişirse `frontend-conventions` kazanır.

## Ne zaman devreye girer

- React function component'leri, custom hook'lar veya component ağaçları yazarken ya da değiştirirken
- JSX/TSX dosyalarını review ederken
- State yapısını veya component composition'ını tasarlarken
- Class component'leri ya da eski `forwardRef`/`useEffect` ağırlıklı kodu taşırken
- Local state, lifted state, context ve external store arasında seçim yaparken
- React 19 action'ları veya controlled input'larla form yazarken
- TanStack Query / SWR ile data fetching bağlarken

## Temel ilkeler

### 1. Render, props ve state'in saf (pure) bir function'ıdır

```tsx
// İyi: render sırasında türet
function Cart({ items }: { items: CartItem[] }) {
  const total = items.reduce((sum, i) => sum + i.price * i.qty, 0);
  return <span>{formatMoney(total)}</span>;
}

// Kötü: türetilmiş state ayrıca saklanıyor
function Cart({ items }: { items: CartItem[] }) {
  const [total, setTotal] = useState(0);
  useEffect(() => {
    setTotal(items.reduce((sum, i) => sum + i.price * i.qty, 0));
  }, [items]);
  return <span>{formatMoney(total)}</span>;
}
```

`useEffect` içindeki türetilmiş state fazladan bir render döngüsü ekler, senkronizasyonu bozulabilir ve veri akışını gizler.

### 2. Side effect'ler render'ın dışında

Effect'ler, mutation'lar, network çağrıları ve subscription'lar event handler'larda ya da `useEffect` içinde yaşar; asla render gövdesinde değil.

### 3. Inheritance yerine composition

React'te component'ler için inheritance modeli yoktur. `children`, render prop'lar veya component prop'larıyla compose et.

## Hook disiplini

- Sadece top-level'da çağır, asla koşullu çağırma
- Her subscription'ı, interval'ı ve listener'ı cleanup'ta temizle
- Yeni state eskisine bağlıysa functional updater kullan (`setX(prev => prev + 1)`)
- Varsayılan tutum: memoize etme. `useMemo`/`useCallback`'i sadece bir profiler ya da dependency zinciri gerekli olduğunu kanıtladığında ekle
- Genel kural: custom hook'u aynı hook dizisi 2+ component'te tekrarlandığında çıkar. Proje istisnası: backend çağrıları her zaman `use<X>Query` / `use<X>Mutation` hook'larından, STOMP subscription'ları `use<Feature><Event>` hook'larından geçer (tek kullanımda bile; `frontend-conventions` bölüm 7-8)

## State konumu karar ağacı

```
Tek bir component mi kullanıyor?
  -> içinde useState

Parent + birkaç alt component mi kullanıyor?
  -> en yakın ortak üst component'e taşı (lift)

Birbirinden uzak dallarda VE seyrek okunuyor mu (theme, auth, locale)?
  -> React Context

Ağaç boyunca paylaşılan yüksek frekanslı güncellemeler mi?
  -> external store (bu projede sadece Zustand ve sadece planda onaylanırsa)

Server'dan mı türetiliyor? (REST veya STOMP ile gelen canlı veri dahil)
  -> TanStack Query cache'i
```

Çoğu sayfanın context'e ya da global store'a ihtiyacı yoktur. Tekrarlanan lifting acı vermeye başlayana kadar soyutlamaya direnin. Store seçimi ayrıntıları için `react-state-management` skill'ine bak.

## Server / Client Component'ler (RSC)

Bu proje Vite SPA'dır; Server Component kullanılmaz, her component client'ta çalışır ve `"use client"` direktifi gerekmez. Next.js App Router gibi bir RSC ortamına geçilirse sınırlar şunlardır: Server → Client yönünde sadece serialize edilebilir prop'lar veya `children` geçilir; Client → Server yönünde Server Action'lar `<form action={...}>` ile ya da event handler'lardan çağrılır; Client Component dosyasından Server Component `import` edilmez, `children` ile compose edilir.

## Suspense + Error Boundary'ler

```tsx
<ErrorBoundary fallback={<ErrorView />}>
  <Suspense fallback={<UserSkeleton />}>
    <UserDetail id={id} />
  </Suspense>
</ErrorBoundary>
```

- Suspense boundary'lerini route root'una değil veriye yakın yerleştir; içeriği aşamalı göster
- Error Boundary hâlâ bir class API'sidir; hook dostu bir wrapper için `react-error-boundary` kullan
- Bir boundary, child'larının render, lifecycle ve constructor'ları sırasında fırlatılan hataları yakalar; event handler'lardaki veya async koddaki hataları YAKALAMAZ

## Formlar

### React 19 form action'ları (yeni kod için tercih edilen)

```tsx
import { useActionState, useId } from 'react'
import { ApiError } from '../../../shared/api/ApiError.ts'
import { messageFor } from '../../../shared/api/errorMessages.ts'
import { useCreateSourceMutation } from '../api/sourceQueries.ts'
import {
  SOURCE_KIND_LABELS,
  SOURCE_KINDS,
  SOURCE_NAME_MAX_LENGTH,
  SOURCE_NAME_RULE_MESSAGE,
  isSourceKind,
  isValidSourceName,
} from '../model/sourceRules.ts'

type CreateSourceFormState = { formError: string | null; nameError: string | null }

const INITIAL_STATE: CreateSourceFormState = { formError: null, nameError: null }

function toFormState(error: unknown): CreateSourceFormState {
  if (!(error instanceof ApiError)) {
    console.error(error)
    return { ...INITIAL_STATE, formError: messageFor('UNEXPECTED_ERROR') }
  }
  switch (error.errorCode) {
    case 'SOURCE_NAME_TAKEN':
      return { ...INITIAL_STATE, nameError: messageFor(error.errorCode) }
    case 'VALIDATION_FAILED':
      return { ...INITIAL_STATE, nameError: SOURCE_NAME_RULE_MESSAGE }
    default:
      return { ...INITIAL_STATE, formError: messageFor(error.errorCode) }
  }
}

type CreateSourceFormProps = { workspaceId: number }

export function CreateSourceForm({ workspaceId }: CreateSourceFormProps) {
  const createSourceMutation = useCreateSourceMutation(workspaceId)
  const nameId = useId()
  const kindId = useId()
  const nameErrorId = `${nameId}-error`

  async function createSourceAction(_previous: CreateSourceFormState, formData: FormData): Promise<CreateSourceFormState> {
    const name = String(formData.get('name') ?? '').trim()
    const kind = String(formData.get('kind') ?? '')
    if (!isValidSourceName(name)) {
      return { ...INITIAL_STATE, nameError: SOURCE_NAME_RULE_MESSAGE }
    }
    if (!isSourceKind(kind)) {
      return { ...INITIAL_STATE, formError: messageFor('VALIDATION_FAILED') }
    }
    try {
      await createSourceMutation.mutateAsync({ name, kind })
      return INITIAL_STATE
    } catch (error) {
      return toFormState(error)
    }
  }

  const [state, formAction, isPending] = useActionState(createSourceAction, INITIAL_STATE)
  return (
    <form action={formAction} noValidate>
      {state.formError ? <p role="alert">{state.formError}</p> : null}
      <label htmlFor={nameId}>Kaynak adı</label>
      <input
        id={nameId}
        name="name"
        maxLength={SOURCE_NAME_MAX_LENGTH}
        autoComplete="off"
        required
        aria-invalid={state.nameError !== null}
        aria-describedby={state.nameError ? nameErrorId : undefined}
      />
      {state.nameError ? <p id={nameErrorId} role="alert">{state.nameError}</p> : null}
      <label htmlFor={kindId}>Kaynak türü</label>
      <select id={kindId} name="kind" defaultValue="MARKDOWN">
        {SOURCE_KINDS.map((kind) => (
          <option key={kind} value={kind}>{SOURCE_KIND_LABELS[kind]}</option>
        ))}
      </select>
      <button type="submit" disabled={isPending}>{isPending ? 'Ekleniyor…' : 'Kaynağı ekle'}</button>
    </form>
  )
}
```

SPA'da action sıradan bir async function'dır (`"use server"` yoktur). Validation şema kütüphanesiyle (Zod vb.) değil `sourceRules.ts`'teki sabit ve function'larla yapılır; backend çağrısı mutation hook'u üzerinden yapılır. Hangi `errorCode`'un hangi alanın altında, hangisinin form üstünde gösterileceği `frontend-conventions` bölüm 7'deki tablodadır; metinler `errorMessages.ts` ve `sourceRules.ts`'ten gelir (bölüm 7 ve 10). Alanlar uncontrolled'dır; değer yazılırken dönüştürülmesi gereken bir alan olsaydı controlled yapılırdı. Başarılı eklemede kaynak listesinin invalidate edilmesi ve yeni kaynağın cache'e yazılması mutation hook'unun `onSuccess`'indedir (`react-state-management/references/details.md`).

### Controlled input'lar

Değer başka bir UI'ı yönlendiriyorsa, her tuş vuruşunda formatlanıyorsa ya da gerçek zamanlı validation uyguluyorsa controlled kullan.

### Karmaşık formlar

Çok adımlı formlar, dinamik alan dizileri veya alanlar arası validation için bir kütüphane (React Hook Form, TanStack Form) gerekebilir; bu projede form kütüphanesi eklemek plan kararıdır (`frontend-conventions` bölüm 1 ve 10).

## Data fetching karar matrisi

| İhtiyaç | Araç (bu projede) |
|---|---|
| Client-side cache + mutation'lar + invalidation | TanStack Query (SWR genel bir alternatiftir; projede kullanılmaz) |
| Gerçek zamanlı subscription'lar | STOMP (`@stomp/stompjs`), mesajlar TanStack Query cache'ine yazılır |
| Tek seferlik istek | Yine `use<X>Mutation` hook'u; component içinde doğrudan `fetch()` yok |

Uygulama verisi için `useEffect` + `fetch`'ten kaçın; race condition, cache yok, retry yok, Suspense entegrasyonu yok.

## Composition tarifleri

### `children` ile slot

```tsx
<Layout>
  <Header />
  <Main>{content}</Main>
</Layout>
```

### İsimli slot'lar

```tsx
<Page header={<Nav />} sidebar={<Filters />}>
  <Results />
</Page>
```

### Compound component'ler (Context ile paylaşılan state)

```tsx
<Tabs defaultValue="profile">
  <TabsList>
    <TabsTrigger value="profile">Profil</TabsTrigger>
    <TabsTrigger value="settings">Ayarlar</TabsTrigger>
  </TabsList>
  <TabsPanel value="profile"><Profile /></TabsPanel>
  <TabsPanel value="settings"><Settings /></TabsPanel>
</Tabs>
```

Parçalar aynı dosyadan named export edilir (`frontend-conventions` bölüm 5). `Tabs.List` gibi namespace nesnesi export edilmez: oxlint'in `react/only-export-components` kuralı component dosyasından component olmayan bir nesne export edilmesini uyarı olarak verir ve projede lint uyarısı bırakılmaz.

Ayrıntılı composition kuralları için `react-composition-patterns` skill'ine bak.

### Render prop / function-as-child

Parent'ın render edilen çıktıya parametre geçmesi gerektiğinde işe yarar:

```tsx
<DataLoader id={id}>
  {({ data, isLoading }) => isLoading ? <Spinner /> : <UserCard user={data} />}
</DataLoader>
```

Modern alternatif: aynı yapıyı döndüren bir hook (`useData(id)`); genellikle daha temizdir.

## Performans

### `React.memo` gerçekten ne zaman işe yarar

Bir component'i sadece şu durumlarda `React.memo` ile sar:

1. Sık re-render oluyorsa
2. Prop'ları render'lar arasında genellikle aynıysa
3. Render'ı ölçülebilir şekilde pahalıysa

`React.memo` her render'da bir eşitlik kontrolü ekler. Prop'lar çoğu render'da farklıysa bu kontrol saf ek yüktür.

### Render kaskadlarından kaçınma

- Mümkün olduğunda state'i yukarı değil aşağı taşı
- Context'i böl: her konu için bir context; böylece `themeContext` değişikliği auth kullanıcılarını re-render etmez
- External state kütüphaneleri için `useSyncExternalStore` kullan; güvenli concurrent rendering için gereklidir

### Listeler

- Stabil `key` prop'ları ver (array index değil, kalıcı id; arama sonuçları için `chunkId`, kaynak listesi için `sourceId`)
- Görünür eleman sayısı ~50'yi geçen ve basit olmayan satırlara sahip uzun listeleri `@tanstack/react-virtual` veya `react-window` ile virtualize et

Daha fazla performans kuralı için `react-best-practices` skill'ine bak.

## Accessibility-first composition

- `role` attribute'larına uzanmadan önce her zaman semantic HTML render et (`<button>`, `<a>`, `<nav>`, `<main>`)
- Her etkileşimli eleman klavyeyle erişilebilir olmalı
- Form input'larının label'ı olmalı: `<label htmlFor>` ya da görsel olarak bir icon ile etiketleniyorsa `aria-label`
- Route değişikliklerinde ve modal açılıp kapanırken focus'u yönet
- Component testlerinde `axe` çalıştır (bkz. `react-testing`)
- WCAG kriterleri ve pattern'ler için `frontend-a11y` skill'ine bak

## Routing

Bu skill router'dan bağımsızdır. Yukarıdaki pattern'ler React Router, TanStack Router ve diğerleriyle çalışır. Router'a özel pattern'ler (loader'lar, action'lar, nested layout'lar) router'ın dokümantasyonunu izler; bunlar React çekirdeğinin üstüne eklenen framework konularıdır.

## Kapsam dışı

- **Next.js'e özel konular**: App Router veri yükleme, Route Handler'lar, Middleware, Parallel Route'lar
- **React Native**: Platforma özel pattern'ler ayrı bir skill gerektirecek kadar farklıdır
- **Remix**: Loader/action konvansiyonları Remix dokümantasyonunu izler

## İlgili skill'ler

- `react-best-practices` - Vercel kaynaklı performans kuralları
- `react-composition-patterns` - compound component ve provider pattern'leri
- `react-state-management` - store ve server state seçimi
- `react-testing` - component ve hook testleri
- `frontend-a11y` - accessibility pattern'leri

## Örnekler

### Debounce'lu arama için custom hook

```tsx
function useDebounce<T>(value: T, delay = 300): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const id = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(id);
  }, [value, delay]);
  return debounced;
}

function SearchBox() {
  const [query, setQuery] = useState("");
  const debounced = useDebounce(query, 300);
  const { data } = useQuery({
    queryKey: ["search", debounced],
    queryFn: () => searchApi(debounced),
    enabled: debounced.length > 0,
  });
  return (
    <>
      <input value={query} onChange={(e) => setQuery(e.target.value)} />
      <Results items={data ?? []} />
    </>
  );
}
```

### React 19 `useOptimistic` ile optimistic UI

```tsx
import { useOptimistic } from "react";

export function MessageList({ messages }: { messages: Message[] }) {
  const [optimistic, addOptimistic] = useOptimistic(
    messages,
    (state, newMessage: Message) => [...state, newMessage],
  );

  async function send(formData: FormData) {
    const text = String(formData.get("text"));
    addOptimistic({ id: "pending", text, sender: "me" });
    await saveMessage(text);
  }

  return (
    <>
      <ul>{optimistic.map((m) => <li key={m.id}>{m.text}</li>)}</ul>
      <form action={send}>
        <label htmlFor="message-text">Mesaj</label>
        <input id="message-text" name="text" />
        <button type="submit">Mesajı gönder</button>
      </form>
    </>
  );
}
```

### Render kaskadlarından kaçınmak için context'i bölme

```tsx
// İki context: biri nadiren değişir, diğeri sık
const ThemeContext = createContext<Theme>("light");
const NotificationsContext = createContext<Notification[]>([]);

// Sadece ThemeContext kullanan bir component, notifications değiştiğinde re-render OLMAZ
```
