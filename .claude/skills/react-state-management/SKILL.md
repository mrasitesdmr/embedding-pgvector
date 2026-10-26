---
name: react-state-management
description: Redux Toolkit, Zustand, Jotai ve React Query (TanStack Query) ile modern React state management. Global state kurarken, server state'i yönetirken, state management çözümleri arasında seçim yaparken veya WebSocket ile gelen canlı veriyi (ör. ingestion ilerlemesi) state'e bağlarken kullan.
license: MIT
metadata:
  origin: wshobson/agents
  source: https://github.com/wshobson/agents/tree/main/plugins/frontend-mobile-development/skills/react-state-management
---

# React State Management

Local component state'ten global store'lara ve server state senkronizasyonuna kadar modern React state management pattern'leri için kapsamlı rehber.

> **Proje kararı (bağlayıcı, `frontend-conventions` bölüm 1):** Server state (REST, polling ile izlenen iş durumu; WebSocket gelirse STOMP ile gelen canlı veri) **TanStack Query**; client state `useState` / `useReducer` / Context. Zustand sadece gerçek ihtiyaçta ve planda onaylanırsa; Redux Toolkit, Jotai, SWR, RTK Query, Formik ve nuqs bu projede kullanılmaz. Aşağıdaki karşılaştırma ve örnekler genel bilgidir; örneklerdeki `interface`, `persist` + `localStorage` ve çift tırnak/noktalı virgül projede kullanılmaz (`type`, `localStorage` sadece `features/search/model/searchSettings.ts` üzerinden ve sadece gizli olmayan arama ayarları için, Vite şablon biçimi).

## Ne zaman kullanılır

- Bir React uygulamasında global state management kurarken
- Redux Toolkit, Zustand ve Jotai arasında seçim yaparken
- Server state'i React Query veya SWR ile yönetirken
- Optimistic update uygularken
- State ile ilgili sorunları debug ederken
- Eski Redux'tan modern pattern'lere geçerken

## Temel kavramlar

### 1. State kategorileri

| Tip              | Açıklama                          | Çözümler                      |
| ---------------- | --------------------------------- | ----------------------------- |
| **Local State**  | Component'e özel, UI state'i       | useState, useReducer          |
| **Global State** | Component'ler arasında paylaşılan | Redux Toolkit, Zustand, Jotai |
| **Server State** | Remote veri, caching              | React Query, SWR, RTK Query   |
| **URL State**    | Route parametreleri, arama        | React Router, nuqs            |
| **Form State**   | Input değerleri, validation       | React Hook Form, Formik       |

Gerçek zamanlı ya da periyodik güncellenen veri ayrı bir kategori değildir; kaynağı server olduğu için server state gibi ele al. Bu projede içe alma ilerlemesi polling ile izlenir: iş `sourceKeys.ingestionJob(workspaceId, jobId)` key'inde `refetchInterval` ile sorgulanır, iş `DONE`/`FAILED` olunca sorgu durur (`useIngestionJobsPolling`, `frontend-conventions` bölüm 8). WebSocket gelirse gelen mesaj aynı key'e yazılır — `queryClient.setQueryData<IngestionJobResponse>(sourceKeys.ingestionJob(workspaceId, message.jobId), (job) => job ? { ...job, stage: message.stage, progress: message.progress, embeddedChunks: message.embeddedChunks, totalChunks: message.totalChunks } : job)`; ilerleme için ayrı query key açılmaz. Aynı veri Zustand'a veya `useState`'e ayrıca yazılmaz.

### 2. Seçim kriterleri

```
Küçük uygulama, basit state → Zustand veya Jotai
Büyük uygulama, karmaşık state → Redux Toolkit
Yoğun server etkileşimi → React Query + hafif client state   ← bu proje
Atomic/ayrıntılı güncellemeler → Jotai
```

## Hızlı başlangıç

### Zustand (en basiti)

```typescript
// store/useStore.ts
import { create } from 'zustand'
import { devtools, persist } from 'zustand/middleware'

interface AppState {
  user: User | null
  theme: 'light' | 'dark'
  setUser: (user: User | null) => void
  toggleTheme: () => void
}

export const useStore = create<AppState>()(
  devtools(
    persist(
      (set) => ({
        user: null,
        theme: 'light',
        setUser: (user) => set({ user }),
        toggleTheme: () => set((state) => ({
          theme: state.theme === 'light' ? 'dark' : 'light'
        })),
      }),
      { name: 'app-storage' }
    )
  )
)

// Component içinde kullanım
function Header() {
  const user = useStore((state) => state.user)
  const theme = useStore((state) => state.theme)
  const toggleTheme = useStore((state) => state.toggleTheme)
  return (
    <header className={theme}>
      {user?.name}
      <button onClick={toggleTheme}>Toggle Theme</button>
    </header>
  )
}
```

Selector'sız `useStore()` çağrısı store'daki her değişiklikte re-render'a yol açar; yukarıdaki gibi ihtiyaç duyulan alanları tek tek seç.

## Ayrıntılı pattern'ler ve örnekler

Ayrıntılı pattern dokümantasyonu [references/details.md](references/details.md) dosyasındadır. Yukarıdaki özet yetersiz kaldığında o dosyayı oku. İçindekiler: Redux Toolkit + TypeScript, slice'lı Zustand, Jotai ile atomic state, server state için React Query (query key factory, optimistic update), client + server state'i birleştirme.

## Best practice'ler

### Yap

- **State'i kullanıldığı yere yakın tut (colocate)** - State'i kullanıldığı yere mümkün olduğunca yakın tut
- **Selector kullan** - Seçici subscription'larla gereksiz re-render'ları önle
- **Veriyi normalize et** - Kolay güncelleme için iç içe yapıları düzleştir
- **Her şeye type ver** - Tam TypeScript kapsamı runtime hatalarını önler
- **Sorumlulukları ayır** - Server state (React Query) ile client state (Zustand) ayrı

### Yapma

- **Her şeyi global yapma** - Her şeyin global state'te olması gerekmez
- **Server state'i kopyalama** - Bırak React Query yönetsin
- **Doğrudan mutate etme** - Her zaman immutable güncellemeler kullan
- **Türetilmiş veriyi saklama** - Bunun yerine hesapla
- **Paradigmaları karıştırma** - Her kategori için tek bir birincil çözüm seç

## Migration rehberleri

### Eski Redux'tan RTK'ya

```typescript
// Önce (eski Redux)
const ADD_TODO = "ADD_TODO";
const addTodo = (text) => ({ type: ADD_TODO, payload: text });
function todosReducer(state = [], action) {
  switch (action.type) {
    case ADD_TODO:
      return [...state, { text: action.payload, completed: false }];
    default:
      return state;
  }
}

// Sonra (Redux Toolkit)
const todosSlice = createSlice({
  name: "todos",
  initialState: [],
  reducers: {
    addTodo: (state, action: PayloadAction<string>) => {
      // Immer "mutation"lara izin verir
      state.push({ text: action.payload, completed: false });
    },
  },
});
```

## İlgili skill'ler

- `react-patterns` - state'in nerede tutulacağına dair karar ağacı
- `react-best-practices` - re-render optimizasyonu kuralları
- `react-composition-patterns` - state'i provider'lara taşıma
