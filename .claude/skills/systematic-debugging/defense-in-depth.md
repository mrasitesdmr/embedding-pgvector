# Defense-in-Depth Validation

## Genel bakış

Geçersiz veriden kaynaklanan bir bug'ı düzelttiğinde, tek bir yere validation eklemek yeterli gibi gelir. Ama bu tek kontrol farklı code path'ler, refactoring veya mock'lar tarafından atlanabilir.

**Temel ilke:** Verinin geçtiği HER katmanda validate et. Bug'ı yapısal olarak imkansız hale getir.

## Neden birden fazla katman

Tek validation: "Bug'ı düzelttik"
Birden fazla katman: "Bug'ı imkansız hale getirdik"

Farklı katmanlar farklı vakaları yakalar:
- Entry validation çoğu bug'ı yakalar
- Business logic edge case'leri yakalar
- Environment guard'ları context'e özel tehlikeleri önler
- Debug logging, diğer katmanlar başarısız olduğunda yardımcı olur

## Dört katman

### Katman 1: Entry point validation
**Amaç:** Açıkça geçersiz girdiyi API sınırında reddet

```typescript
function createProject(name: string, workingDirectory: string) {
  if (!workingDirectory || workingDirectory.trim() === '') {
    throw new Error('workingDirectory cannot be empty');
  }
  if (!existsSync(workingDirectory)) {
    throw new Error(`workingDirectory does not exist: ${workingDirectory}`);
  }
  if (!statSync(workingDirectory).isDirectory()) {
    throw new Error(`workingDirectory is not a directory: ${workingDirectory}`);
  }
  // ... devam et
}
```

### Katman 2: Business logic validation
**Amaç:** Verinin bu işlem için anlamlı olduğundan emin ol

```typescript
function initializeWorkspace(projectDir: string, sessionId: string) {
  if (!projectDir) {
    throw new Error('projectDir required for workspace initialization');
  }
  // ... devam et
}
```

### Katman 3: Environment guard'ları
**Amaç:** Belirli context'lerde tehlikeli işlemleri önle

```typescript
async function gitInit(directory: string) {
  if (process.env.NODE_ENV === 'test') {
    const normalized = normalize(resolve(directory));
    const tmpDir = normalize(resolve(tmpdir()));

    if (!normalized.startsWith(tmpDir)) {
      throw new Error(
        `Refusing git init outside temp dir during tests: ${directory}`
      );
    }
  }
  // ... devam et
}
```

### Katman 4: Debug instrumentation
**Amaç:** Adli analiz için context yakala

```typescript
async function gitInit(directory: string) {
  const stack = new Error().stack;
  logger.debug('About to git init', {
    directory,
    cwd: process.cwd(),
    stack,
  });
  // ... devam et
}
```

## Bu projede katmanlar

Örnek: "Arama metni" (`query`) akışı

| Katman | Frontend | Backend |
|--------|----------|---------|
| 1. Entry | Form validation (boşluk normalizasyonu, uzunluk) — sadece UX içindir, güvenlik sınırı değildir | Request DTO üzerinde Bean Validation (`@Pattern`, `@Size`) |
| 2. Business | API response'unu parse ederken beklenmeyen şekli reddet | Service katmanında "çalışma alanı var mı, aktif embedding modeli sorgu vektörünün modeliyle aynı mı" kontrolü |
| 3. Environment | — | DB constraint'leri (`UNIQUE`, `CHECK`, `NOT NULL`, `vector(1024)` boyut kontrolü) |
| 4. Debug | — | Structured log + traceId (`observability-tracing`) |

## Pattern'i uygulamak

Bir bug bulduğunda:

1. **Data flow'u izle** — Hatalı değer nereden geliyor? Nerede kullanılıyor?
2. **Tüm checkpoint'leri haritala** — Verinin geçtiği her noktayı listele
3. **Her katmana validation ekle** — Entry, business, environment, debug
4. **Her katmanı test et** — Katman 1'i atlatmayı dene, katman 2'nin yakaladığını doğrula

## Oturumdan örnek

Bug: Boş `projectDir`, kaynak kod içinde `git init` çalışmasına yol açtı

**Data flow:**
1. Test setup → boş string
2. `Project.create(name, '')`
3. `WorkspaceManager.createWorkspace('')`
4. `git init`, `process.cwd()` içinde çalışır

**Eklenen dört katman:**
- Katman 1: `Project.create()` boş olmadığını/var olduğunu/yazılabilir olduğunu validate ediyor
- Katman 2: `WorkspaceManager` projectDir'in boş olmadığını validate ediyor
- Katman 3: `WorktreeManager` testlerde tmpdir dışında git init'i reddediyor
- Katman 4: git init'ten önce stack trace log'lanıyor

**Sonuç:** 1847 testin tamamı geçti, bug reproduce edilemez hale geldi

## Ana çıkarım

Dört katmanın hepsi gerekliydi. Test sırasında her katman diğerlerinin kaçırdığı bug'ları yakaladı:
- Farklı code path'ler entry validation'ı atladı
- Mock'lar business logic kontrollerini atladı
- Farklı platformlardaki edge case'ler environment guard'larına ihtiyaç duydu
- Debug logging yapısal yanlış kullanımı ortaya çıkardı

**Tek bir validation noktasında durma.** Her katmana kontrol ekle.
