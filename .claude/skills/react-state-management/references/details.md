# react-state-management — ayrıntılı pattern'ler ve örnekler

> Bu projede sadece Pattern 4 (TanStack Query) geçerlidir; Pattern 1-3 ve 5 genel referanstır (`react-state-management` SKILL.md'deki proje kararı). Projede query modülü `features/<feature>/api/<feature>Queries.ts` dosyasıdır, hook adları `use<Noun>Query` / `use<Verb><Noun>Mutation` kalıbındadır (`frontend-conventions` bölüm 5).

## İçindekiler

- Pattern 1: TypeScript ile Redux Toolkit
- Pattern 2: Slice'lı Zustand (ölçeklenebilir)
- Pattern 3: Atomic state için Jotai
- Pattern 4: Server state için React Query
- Pattern 5: Client + server state'i birleştirme

## Pattern'ler

### Pattern 1: TypeScript ile Redux Toolkit

```typescript
// store/index.ts
import { configureStore } from "@reduxjs/toolkit";
import { TypedUseSelectorHook, useDispatch, useSelector } from "react-redux";
import userReducer from "./slices/userSlice";
import cartReducer from "./slices/cartSlice";

export const store = configureStore({
  reducer: {
    user: userReducer,
    cart: cartReducer,
  },
  middleware: (getDefaultMiddleware) =>
    getDefaultMiddleware({
      serializableCheck: {
        ignoredActions: ["persist/PERSIST"],
      },
    }),
});

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;

// Type'lı hook'lar
export const useAppDispatch: () => AppDispatch = useDispatch;
export const useAppSelector: TypedUseSelectorHook<RootState> = useSelector;
```

```typescript
// store/slices/userSlice.ts
import { createSlice, createAsyncThunk, PayloadAction } from "@reduxjs/toolkit";

interface User {
  id: string;
  email: string;
  name: string;
}

interface UserState {
  current: User | null;
  status: "idle" | "loading" | "succeeded" | "failed";
  error: string | null;
}

const initialState: UserState = {
  current: null,
  status: "idle",
  error: null,
};

export const fetchUser = createAsyncThunk(
  "user/fetchUser",
  async (userId: string, { rejectWithValue }) => {
    try {
      const response = await fetch(`/api/users/${userId}`);
      if (!response.ok) throw new Error("Failed to fetch user");
      return await response.json();
    } catch (error) {
      return rejectWithValue((error as Error).message);
    }
  },
);

const userSlice = createSlice({
  name: "user",
  initialState,
  reducers: {
    setUser: (state, action: PayloadAction<User>) => {
      state.current = action.payload;
      state.status = "succeeded";
    },
    clearUser: (state) => {
      state.current = null;
      state.status = "idle";
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(fetchUser.pending, (state) => {
        state.status = "loading";
        state.error = null;
      })
      .addCase(fetchUser.fulfilled, (state, action) => {
        state.status = "succeeded";
        state.current = action.payload;
      })
      .addCase(fetchUser.rejected, (state, action) => {
        state.status = "failed";
        state.error = action.payload as string;
      });
  },
});

export const { setUser, clearUser } = userSlice.actions;
export default userSlice.reducer;
```

### Pattern 2: Slice'lı Zustand (ölçeklenebilir)

```typescript
// store/slices/createUserSlice.ts
import { StateCreator } from "zustand";

export interface UserSlice {
  user: User | null;
  isAuthenticated: boolean;
  login: (credentials: Credentials) => Promise<void>;
  logout: () => void;
}

export const createUserSlice: StateCreator<
  UserSlice & CartSlice, // Birleşik store tipi
  [],
  [],
  UserSlice
> = (set, get) => ({
  user: null,
  isAuthenticated: false,
  login: async (credentials) => {
    const user = await authApi.login(credentials);
    set({ user, isAuthenticated: true });
  },
  logout: () => {
    set({ user: null, isAuthenticated: false });
    // Diğer slice'lara erişebilir
    // get().clearCart()
  },
});

// store/index.ts
import { create } from "zustand";
import { createUserSlice, UserSlice } from "./slices/createUserSlice";
import { createCartSlice, CartSlice } from "./slices/createCartSlice";

type StoreState = UserSlice & CartSlice;

export const useStore = create<StoreState>()((...args) => ({
  ...createUserSlice(...args),
  ...createCartSlice(...args),
}));

// Seçici subscription'lar (gereksiz re-render'ları önler)
export const useUser = () => useStore((state) => state.user);
export const useCart = () => useStore((state) => state.cart);
```

### Pattern 3: Atomic state için Jotai

```typescript
// atoms/userAtoms.ts
import { atom } from 'jotai'
import { atomWithStorage } from 'jotai/utils'

// Temel atom
export const userAtom = atom<User | null>(null)

// Türetilmiş atom (hesaplanan)
export const isAuthenticatedAtom = atom((get) => get(userAtom) !== null)

// localStorage'da kalıcı atom
export const themeAtom = atomWithStorage<'light' | 'dark'>('theme', 'light')

// Async atom
export const userProfileAtom = atom(async (get) => {
  const user = get(userAtom)
  if (!user) return null
  const response = await fetch(`/api/users/${user.id}/profile`)
  return response.json()
})

// Sadece yazılabilen atom (action)
export const logoutAtom = atom(null, (get, set) => {
  set(userAtom, null)
  set(cartAtom, [])
  localStorage.removeItem('token')
})

// Kullanım
function Profile() {
  const [user] = useAtom(userAtom)
  const [, logout] = useAtom(logoutAtom)
  const [profile] = useAtom(userProfileAtom) // Suspense destekli

  return (
    <Suspense fallback={<Skeleton />}>
      <ProfileContent profile={profile} onLogout={logout} />
    </Suspense>
  )
}
```

### Pattern 4: Server state için React Query

```typescript
// hooks/useUsers.ts
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";

// Query key factory
export const userKeys = {
  all: ["users"] as const,
  lists: () => [...userKeys.all, "list"] as const,
  list: (filters: UserFilters) => [...userKeys.lists(), filters] as const,
  details: () => [...userKeys.all, "detail"] as const,
  detail: (id: string) => [...userKeys.details(), id] as const,
};

// Fetch hook'u
export function useUsers(filters: UserFilters) {
  return useQuery({
    queryKey: userKeys.list(filters),
    queryFn: () => fetchUsers(filters),
    staleTime: 5 * 60 * 1000, // 5 dakika
    gcTime: 30 * 60 * 1000, // 30 dakika (eski adı cacheTime)
  });
}

// Tek kullanıcı hook'u
export function useUser(id: string) {
  return useQuery({
    queryKey: userKeys.detail(id),
    queryFn: () => fetchUser(id),
    enabled: !!id, // id yoksa fetch etme
  });
}

// Optimistic update'li mutation
export function useUpdateUser() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: updateUser,
    onMutate: async (newUser) => {
      // Devam eden refetch'leri iptal et
      await queryClient.cancelQueries({
        queryKey: userKeys.detail(newUser.id),
      });

      // Önceki değerin snapshot'ını al
      const previousUser = queryClient.getQueryData(
        userKeys.detail(newUser.id),
      );

      // Optimistic olarak güncelle
      queryClient.setQueryData(userKeys.detail(newUser.id), newUser);

      return { previousUser };
    },
    onError: (err, newUser, context) => {
      // Hata durumunda geri al
      queryClient.setQueryData(
        userKeys.detail(newUser.id),
        context?.previousUser,
      );
    },
    onSettled: (data, error, variables) => {
      // Mutation sonrası yeniden fetch et
      queryClient.invalidateQueries({
        queryKey: userKeys.detail(variables.id),
      });
    },
  });
}
```

**İş durumu polling'i (proje notu):** İçe alma ilerlemesi WebSocket ile değil, iş durumu sorgulanarak izlenir (TASK-004). İş `sourceKeys.ingestionJob(workspaceId, jobId)` key'indedir; `refetchInterval` iş aktifken 1000 ms, `DONE`/`FAILED` olunca `false` döner. Yükleme mutation'ı dönen işi bu key'e yazar, böylece ilk sorgu beklenmeden ilerleme görünür. WebSocket gelirse mesaj aynı key'e yazılır; ayrı bir `progress` key'i açılmaz (`frontend-conventions` bölüm 8).

Projedeki kalıp (`features/source/api/sourceQueries.ts`, kısaltılmış):

```ts
export const sourceKeys = {
  all: (workspaceId: number) => ['sources', workspaceId] as const,
  list: (workspaceId: number) => [...sourceKeys.all(workspaceId), 'list'] as const,
  detail: (workspaceId: number, sourceId: number) => [...sourceKeys.all(workspaceId), sourceId] as const,
  ingestionJob: (workspaceId: number, jobId: number) => [...sourceKeys.all(workspaceId), 'ingestion-jobs', jobId] as const,
}

export function useSourceQuery(workspaceId: number, sourceId: number | null) {
  return useQuery({
    queryKey: sourceKeys.detail(workspaceId, sourceId ?? 0),
    queryFn: sourceId === null ? skipToken : () => getSource(workspaceId, sourceId),
  })
}

export function useIngestionJobsPolling(workspaceId: number, jobs: IngestionJobResponse[]) {
  return useQueries({
    queries: jobs.map((job) =>
      queryOptions({
        queryKey: sourceKeys.ingestionJob(workspaceId, job.jobId),
        queryFn: () => getIngestionJob(workspaceId, job.jobId),
        initialData: job,
        staleTime: INGESTION_POLL_INTERVAL_MS,
        refetchInterval: (query) =>
          !isClientError(query.state.error) && isJobActive(query.state.data) ? INGESTION_POLL_INTERVAL_MS : false,
        refetchIntervalInBackground: true,
        retry: false,
      }),
    ),
  })
}

export function useCreateSourceMutation(workspaceId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: CreateSourceInput) => createSource(workspaceId, input),
    onSuccess: (job) => {
      queryClient.setQueryData(sourceKeys.ingestionJob(workspaceId, job.jobId), job)
      void queryClient.invalidateQueries({ queryKey: sourceKeys.list(workspaceId) })
      void queryClient.invalidateQueries({ queryKey: workspaceKeys.all })
    },
  })
}

export function useSearchChunksMutation(workspaceId: number) {
  return useMutation({
    mutationFn: (request: SearchChunksRequest) => searchChunks(workspaceId, request),
  })
}
```

- Polling tek bir yerde (`IngestionWatcherProvider`, `WorkspaceLayout`'ta) kurulur; ekranlar aynı key'i istek atmadan okur (`useIngestionJobQuery`, `queryFn: skipToken`). Böylece sayfa değişse de sorgu ve "hazır" toast'ı sürer.
- `refetchIntervalInBackground: true`: sekme arka plandayken de iş bitince toast çıkar. `retry: false` + `isClientError`: iş bulunamazsa (404) sorgu durur ve kaynak listesi yenilenir.
- Yükleme cevabı kaynak değil iştir (`202` + `IngestionJobResponse`); kaynağın yeni hali iş bitince `sourceKeys` invalidate edilerek gelir.
- Arama bir mutation'dır: sonucu `searchChunksMutation.data`'dan okunur, sorgu metnine göre query cache'inde birikmez (her farklı sorgu ayrı bir cache girdisi açardı). Son aramayı sayfa yenilense de göstermek gerekirse bu bir plan kararıdır.

### Pattern 5: Client + server state'i birleştirme

```typescript
// Client state için Zustand
const useUIStore = create<UIState>((set) => ({
  sidebarOpen: true,
  modal: null,
  toggleSidebar: () => set((s) => ({ sidebarOpen: !s.sidebarOpen })),
  openModal: (modal) => set({ modal }),
  closeModal: () => set({ modal: null }),
}))

// Server state için React Query
function Dashboard() {
  const sidebarOpen = useUIStore((s) => s.sidebarOpen)
  const toggleSidebar = useUIStore((s) => s.toggleSidebar)
  const { data: users, isLoading } = useUsers({ active: true })
  const { data: stats } = useStats()

  if (isLoading) return <DashboardSkeleton />

  return (
    <div className={sidebarOpen ? 'with-sidebar' : ''}>
      <Sidebar open={sidebarOpen} onToggle={toggleSidebar} />
      <main>
        <StatsCards stats={stats} />
        <UserTable users={users} />
      </main>
    </div>
  )
}
```
