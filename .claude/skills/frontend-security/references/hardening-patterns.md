# Frontend Hardening Pattern'leri

`frontend-security` skill'i için kod pattern'leri ve karar yardımcıları. Skill her kuralı söyler; bu dosya her biri için tek bir somut implementation gösterir. İhtiyacın olan bölümü o koda geldiğinde aç — baştan sona okunmak için yazılmadı.

## İçindekiler

- XSS
- Kullanıcı kaynaklı URL'ler
- Tarayıcıda saklama
- Gelen veriyi doğrulama
- CSP ve security header'lar
- Secret yönetimi
- Dependency audit triage
- OWASP Top 10 frontend hızlı referansı

## XSS

```tsx
// KÖTÜ: İçe alınan içeriği HTML olarak render etmek (taranan web sayfası script taşıyabilir)
<p dangerouslySetInnerHTML={{ __html: hit.content }} />

// KÖTÜ: Eşleşen kelimeyi vurgulamak için HTML string üretmek
<p dangerouslySetInnerHTML={{ __html: hit.content.replaceAll(term, `<mark>${term}</mark>`) }} />

// KÖTÜ: Ref ile DOM'a ham HTML yazmak
useEffect(() => {
  if (ref.current) ref.current.innerHTML = source.name
}, [source.name])

// İYİ: React'in otomatik escaping'i
<p>{hit.content}</p>

// İYİ: Vurgulama React elemanlarıyla; metin hiçbir zaman HTML olarak yorumlanmaz
function HighlightedText({ text, terms }: { text: string; terms: string[] }) {
  return <>{splitByTerms(text, terms).map((part, index) => (part.isMatch ? <mark key={index}>{part.text}</mark> : part.text))}</>
}

// İYİ: DOM'a doğrudan yazmak zorunluysa textContent
if (ref.current) ref.current.textContent = source.name
```

`splitByTerms` `model/` altında saf bir function'dır; regex'e kullanıcı girdisi konacaksa özel karakterler escape edilir (aksi halde `(` gibi bir karakter regex'i bozar ya da ReDoS'a açar).

Ham HTML gerçekten kaçınılmazsa (ör. Markdown kaynağının biçimli gösterimi; plan kararı ve yeni dependency gerektirir):

```tsx
import DOMPurify from 'dompurify'

const clean = DOMPurify.sanitize(html, { ALLOWED_TAGS: ['b', 'i', 'em', 'strong', 'code', 'pre', 'ul', 'ol', 'li', 'p'], ALLOWED_ATTR: [] })
<div dangerouslySetInnerHTML={{ __html: clean }} />
```

**Unutulan XSS yolları:**

| Yol | Neden tehlikeli | Önlem |
|-----|-----------------|-------|
| `<a href={userUrl}>` | `javascript:` URL'si tıklanınca kod çalıştırır | Protokol allowlist'i (aşağıda) |
| `<iframe src={userUrl}>` | Aynı sebep + clickjacking | Kullanıcı kaynaklı iframe yok |
| `style={{ background: \`url(${userUrl})\` }}` | CSS injection, veri sızdırma | Kullanıcı verisini CSS'e koyma |
| `document.title = source.name` | Güvenli (metin), ama `innerHTML`'e kopyalanırsa değil | Sadece metin API'leri |
| `eval`, `new Function`, `setTimeout(string)` | String'i kod olarak çalıştırır | Asla kullanma |

## Kullanıcı kaynaklı URL'ler

```ts
const ALLOWED_PROTOCOLS = new Set(['http:', 'https:'])

export function toSafeUrl(raw: string): string | null {
  try {
    const url = new URL(raw, window.location.origin)
    return ALLOWED_PROTOCOLS.has(url.protocol) ? url.toString() : null
  } catch {
    return null
  }
}
```

```tsx
const safeUrl = toSafeUrl(hit.sourceUrl)
{safeUrl ? <a href={safeUrl} rel="noopener noreferrer" target="_blank">Kaynağı aç</a> : null}
```

- `target="_blank"` ile açılan dış bağlantılarda `rel="noopener noreferrer"`.
- Web'den taranan kaynakların URL'leri (`hit.sourceUrl`) taranan sitenin kontrolündedir; her biri `toSafeUrl`'den geçer.
- Route parametreleri (`useParams`) de kullanıcı girdisidir: `workspaceId` ve `sourceId`'yi kullanmadan önce pozitif tam sayı olarak parse et (`Number.isSafeInteger(value) && value > 0`); geçersizse API çağırma, "Kaynak bulunamadı" göster. Hangi çalışma alanına erişilebileceğine backend karar verir.

## Tarayıcıda saklama

Proje kararı: hesap ve token yok; sadece gizli olmayan arama ayarları → `localStorage`, versiyonlu key, tek erişim noktası (`frontend-conventions` bölüm 9).

```ts
const STORAGE_KEY = 'emb:search-settings:v1'

export type SearchSettings = { mode: SearchMode; efSearch: number }

export const DEFAULT_SEARCH_SETTINGS: SearchSettings = { mode: 'HYBRID', efSearch: 40 }

function isSearchSettings(value: unknown): value is SearchSettings {
  if (typeof value !== 'object' || value === null) return false
  const candidate = value as Record<string, unknown>
  return (
    typeof candidate.mode === 'string' &&
    (SEARCH_MODES as readonly string[]).includes(candidate.mode) &&
    typeof candidate.efSearch === 'number' &&
    Number.isInteger(candidate.efSearch) &&
    candidate.efSearch >= EF_SEARCH_MIN &&
    candidate.efSearch <= EF_SEARCH_MAX
  )
}

export function readSearchSettings(): SearchSettings {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return DEFAULT_SEARCH_SETTINGS
    const parsed: unknown = JSON.parse(raw)
    if (isSearchSettings(parsed)) return parsed
    localStorage.removeItem(STORAGE_KEY)
    return DEFAULT_SEARCH_SETTINGS
  } catch {
    return DEFAULT_SEARCH_SETTINGS
  }
}
```

**Neyi nerede saklarız:**

| Veri | Nerede | Neden |
|------|--------|-------|
| Arama ayarları (mod, `ef_search`, sonuç sayısı) | `localStorage` | Gizli değil; yenilemede deney aynı ayarlarla sürer |
| Son arama sonucu | Sadece memory (mutation state) | Sorgu metni hassas olabilir; kalıcı saklamak plan kararıdır |
| Embedding API anahtarı | Hiçbir yerde (frontend'de yok) | Backend ortam değişkeninde |
| Sorgu geçmişi | Saklanmaz | Kişisel veri; eklenirse kapsam ve silme yolu kararı gerekir |

Sorgu metni sızıntısını önleyen kurallar:

```ts
// KÖTÜ
console.log('search', request)                          // log
navigate(`/w/42/arama?q=${encodeURIComponent(query)}`)         // URL (history, Referer, sunucu log'u) — paylaşılabilir arama linki plan kararıdır
reportError(new Error(`Search failed for ${query}`))    // hata raporu

// İYİ: Sorgu sadece backend'e giden isteğin gövdesinde
await request(`/api/workspaces/${workspaceId}/search`, {
  method: 'POST',
  body: JSON.stringify({ query, mode, limit }),
})
```

## Gelen veriyi doğrulama

Projede schema kütüphanesi (zod vb.) yoktur; eklemek plan kararıdır. Kritik payload'lar için type guard yeterlidir:

Projede bugün dışarıdan gelen veri sadece REST yanıtlarıdır; `httpClient` yanıtı sözleşmedeki tipe çevirir ve hata yanıtları `toApiError` ile ayrıştırılır. WebSocket gelirse (canlı ilerleme) mesaj gövdesi `unknown` olarak alınır ve doğrulanır:

```ts
// KÖTÜ: Şekil kontrolü olmadan güven, ayrıca veriyi useState'e kopyala
client.subscribe(`/topic/sources/${sourceId}/ingestion`, (message) => {
  const progress = JSON.parse(message.body) as IngestionProgressMessage
  setProgress(progress)
})

// İYİ: Şekli sözleşmeye göre doğrula, bozuksa yok say ve logla;
// geçerliyse polling'in kullandığı aynı cache girdisine yaz
function isIngestionProgressMessage(value: unknown): value is IngestionProgressMessage {
  if (typeof value !== 'object' || value === null) return false
  const candidate = value as Record<string, unknown>
  return (
    Number.isInteger(candidate.jobId) &&
    typeof candidate.stage === 'string' &&
    (INGESTION_STAGES as readonly string[]).includes(candidate.stage) &&
    typeof candidate.progress === 'number' &&
    Number.isInteger(candidate.embeddedChunks) &&
    (candidate.totalChunks === null || Number.isInteger(candidate.totalChunks))
  )
}

client.subscribe(`/topic/sources/${sourceId}/ingestion`, (message) => {
  let parsed: unknown
  try {
    parsed = JSON.parse(message.body)
  } catch {
    console.error('Unparseable ingestion progress message')
    return
  }
  if (!isIngestionProgressMessage(parsed)) {
    console.error('Unexpected ingestion progress shape')
    return
  }
  const { jobId, stage, progress, embeddedChunks, totalChunks } = parsed
  queryClient.setQueryData<IngestionJobResponse>(sourceKeys.ingestionJob(workspaceId, jobId), (job) =>
    job ? { ...job, stage, progress, embeddedChunks, totalChunks } : job,
  )
})
```

- Type guard sözleşmedeki (`docs/api/openapi.yaml`) alanları birebir kontrol eder; sözleşmede olmayan bir alanı (ör. `embeddingModel`) zorunlu tutmak her geçerli mesajı reddeder. Sözleşme değişince guard da aynı task'ta güncellenir.
- Arama sonucundaki `rank`, `distance`, `rrfScore` gibi değerler sadece gösterim içindir; sıralamayı frontend yeniden hesaplamaz, backend'in döndürdüğü sırayı korur.
- Parça içeriği (`content`, `heading`) içe alınan dosyadan gelir, güvenilmezdir: düz metin olarak render edilir, eşleşen kelime vurgusu `matchedTerms`'ten React öğeleriyle kurulur (`HighlightedText`), `dangerouslySetInnerHTML` kullanılmaz.
- Form girdisi: `maxLength` ve `<feature>Rules.ts` kuralları UX içindir; backend Bean Validation aynı kuralları uygular.

## CSP ve security header'lar

Header'lar production'da statik dosyaları sunan reverse proxy'de eklenir (deployment tarafı). Örnek (nginx):

```nginx
add_header Content-Security-Policy "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self'; connect-src 'self' wss://embedding-pgvector.example.com; frame-ancestors 'none'; base-uri 'self'; form-action 'self'; object-src 'none'" always;
add_header X-Content-Type-Options "nosniff" always;
add_header Referrer-Policy "strict-origin-when-cross-origin" always;
add_header Permissions-Policy "camera=(), microphone=(), geolocation=()" always;
add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
```

**Vite ile CSP uyumu:**

| Konu | Not |
|------|-----|
| `script-src 'self'` | Vite production build'i inline script üretmez; `'unsafe-inline'`/`'unsafe-eval'` gerekmez. Gerekiyormuş gibi görünüyorsa sebebini bul. |
| `style-src` | CSS Modules ayrı `.css` dosyalarına çıkar; inline `style={{...}}` attribute'ları React tarafından DOM API ile set edildiği için `style-src` ihlali değildir. |
| Dev server | HMR inline script ve `ws://localhost` kullanır; dev'de CSP uygulanmaz, production CSP'si dev'e göre gevşetilmez. |
| `frame-ancestors 'none'` | Clickjacking'e karşı; `X-Frame-Options: DENY`'nin modern karşılığı. |

Doğrulama: tarayıcı DevTools > Network > ana doküman > Response Headers; Console'da CSP ihlali olmamalı.

## Secret yönetimi

```
frontend/.env dosyaları:
  ├── .env.example  → Commit'lenir (placeholder değerler)
  ├── .env          → Commit'lenmez
  └── .env.local    → Commit'lenmez

.gitignore içermeli:
  .env
  .env.local
  .env.*.local
```

```ts
// KÖTÜ: VITE_ prefix'i = bundle'a gömülür = public
const apiKey = import.meta.env.VITE_TRANSLATE_API_KEY

// İYİ: Secret gerektiren çağrı backend'den yapılır; frontend sadece kendi backend'ini çağırır
const result = await request<Translation>('/api/translations', { method: 'POST', body })
```

Build çıktısında secret olmadığını kontrol et:

```bash
npm run build && grep -rEi "api[_-]?key|secret|password|token" frontend/dist/assets | head
```

Commit'ten önce:

```bash
git diff --cached | grep -iE "password|secret|api_key|token"
```

**Bir secret commit'lendiyse rotate et.** Satırı silmek veya geçmişi yeniden yazmak yetmez — remote'a ulaştığı an ele geçirilmiş say. Önce key'i iptal edip yeniden üret, sonra geçmişten temizle.

## Dependency audit triage

`npm audit` bilinen advisory'leri raporlar; bir paketin güvenilir olduğunu veya açıklı kodun erişilebilir olduğunu kanıtlamaz.

```
npm audit bir açık raporluyor
├── Severity: critical veya high
│   ├── Açıklı kod runtime bundle'a, build'e veya test/deploy yoluna giriyor mu?
│   │   ├── EVET --> Hemen düzelt (güncelle, patch'le veya paketi değiştir)
│   │   └── HAYIR (bu yolların hiçbirinde kullanılmadığı doğrulandı) --> Yakında düzelt, blocker değil
│   └── Fix var mı?
│       ├── EVET --> Patch'li versiyona güncelle (tek paket, changelog'u oku, test et)
│       └── HAYIR --> Workaround ara, paketi değiştirmeyi düşün veya gözden geçirme tarihiyle kayda al
├── Severity: moderate
│   ├── Production bundle'da mı? --> Sonraki release döngüsünde düzelt
│   └── Sadece dev mi? --> Uygun olduğunda düzelt, backlog'a ekle
└── Severity: low
    └── Düzenli dependency güncellemelerinde takip et ve düzelt
```

**Ana sorular:**
- Açıklı fonksiyon gerçekten senin kod yolunda çağrılıyor mu?
- Paket runtime dependency mi (`dependencies`, bundle'a girer) yoksa sadece dev mi (`devDependencies`)?
- Açık senin bağlamında sömürülebilir mi? (Örn. Node.js server-side bir açık, sadece tarayıcıda çalışan bir pakette genelde erişilemez; ama Vite dev server açıkları geliştirici makinesini etkiler.)

```bash
npm audit --omit=dev          # sadece runtime dependency'ler
npm audit signatures          # registry imzalarını doğrula
npm ls <paket>                # bir transitive paketin neden kurulduğunu gör
```

Bir fix'i ertelediğinde gerekçesini ve gözden geçirme tarihini kaydet.

## OWASP Top 10 frontend hızlı referansı

| OWASP Top 10:2025 | Frontend'deki karşılığı | Bu skill'deki bölüm |
|---|---|---|
| A01 Broken Access Control (SSRF artık bunun altında) | UI'da gizlemek yetki değildir; SSRF backend konusudur (`springboot-security`) | Threat model, kırmızı bayraklar |
| A02 Security Misconfiguration | Eksik CSP/header, açık source map | CSP ve header'lar |
| A03 Software Supply Chain Failures | npm dependency'leri, lockfile, install script'leri, CDN script'leri | Dependency'ler ve supply chain, dependency audit triage |
| A04 Cryptographic Failures | HTTPS/WSS, secret'ı frontend'e koymamak | Tarayıcıda saklama, secret yönetimi |
| A05 Injection (XSS dahil) | `dangerouslySetInnerHTML`, `javascript:` URL | XSS, URL'ler |
| A06 Insecure Design | Threat model yapmadan feature | Süreç: önce threat model |
| A07 Authentication Failures | Projede auth yok; eklenirse token saklama kararı | Tarayıcıda saklama |
| A08 Software or Data Integrity Failures | Doğrulanmamış payload'a güvenmek, imzasız/bütünlüğü kontrol edilmeyen kaynak | Gelen veriyi doğrulama |
| A09 Security Logging and Alerting Failures | Sorgu metnini/PII'yi log'lamak | Tarayıcıda saklama, secret'lar |
| A10 Mishandling of Exceptional Conditions | Hata detayını/stack trace'i göstermek, hatada güvensiz duruma düşmek | Hata gösterimi (`SKILL.md`) |
