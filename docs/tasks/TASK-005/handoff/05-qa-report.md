TASK: TASK-005
# QA raporu

## Kullanılan skill'ler
`code-review-and-quality`, `java-code-review`, `springboot-verification`, `e2e-testing`. Frontend kodu değişmedi (sadece e2e testi), bu yüzden `frontend-conventions` e2e kuralları (console yok, sabit bekleme yok, retries 0) elle kontrol edildi.

## Kural kontrolü
| # | Kural / Kabul kriteri | Kanıt (kod + test) | Sonuç |
|---|------------------------|--------------------|-------|
| 1 | Tek komutla tam sistem, `.env`/dosya düzenleme yok | `find . -name ".env*"` → boş. `down -v` sonrası `docker compose -f deployment/docker-compose.yml up --build -d` ile 4 servis ayağa kalktı | ✅ |
| 2 | Ayarlar compose içinde düz değer, `.env`/`.env.example` yok | İki compose'ta `${` sayısı 0; `deployment/` altında `.env*` yok | ✅ |
| 3 | 4 servis, sağlık kontrolü, hepsi `healthy` | Temiz kurulumda `postgres`, `backend`, `frontend`, `pgadmin` hepsi `healthy` (~40 sn); 1410 chunk seed edildi | ✅ |
| 4 | Sabit portlar, rehberde yazılı | 3000, 8080, 5050, 5432; `README.md` "Adresler" tablosu | ✅ |
| 5 | Dev compose sadece postgres + pgadmin, `.env` yok | `docker-compose.dev.yml` okundu; ikisi `healthy` | ✅ |
| 6 | Backend `./mvnw spring-boot:run`, frontend `npm run dev`, ortam değişkeni yok | Ortamda `POSTGRES_*`, `DATABASE_URL`, `OLLAMA`, `SEED` değişkeni yok (`env | grep` → 0). Backend `UP`, `:5173` 200, `:5173/api/.../search` (proxy) sonuç döndü | ✅ |
| 7 | Dev veritabanı seed ile dolar | Dev DB'de `chunks` = 1410 | ✅ |
| 8 | Aynı portlar rehberde yazılı | `README.md` "İki kurulum aynı portu kullanır" | ✅ |
| 9-10 | pgAdmin iki kurulumda var; sunucu hazır gelir | Tarayıcıyla (Playwright) giriş yapıldı: Servers altında `embedding-pgvector` elle eklemeden görünüyor; şifre `embedding-local` ile bağlandı. Dev'de de `healthy` | ✅ |
| 11 | 5 tablo ve sorgu | Query Tool'da sorgu çalıştı: `chunks` 1411 (1410 seed + elle yüklediğim "Şifre SSS"), `workspaces` 37, `vector_dims(embedding)` 1024, beş tablonun hepsi `information_schema`'da | ✅ |
| 12 | Ollama Docker'da değil; container ve local backend host Ollama'ya bağlanıyor | Compose'ta Ollama servisi yok, `OLLAMA_BASE_URL=http://host.docker.internal:11434`; tam sistemde ve local'de arama (vektör) çalıştı | ✅ |
| 13 | Ollama kapalıyken sistem başlar, kelime araması çalışır, anlam araması hata verir | Ollama durduruldu, `down -v` + `up --build -d` → 4 servis `healthy`. `KEYWORD` arama 200; `VECTOR` arama 503 `EMBEDDING_UNAVAILABLE`; yükleme 202, iş `FAILED` / `EMBEDDING_UNAVAILABLE` (bugünkü davranış). Seed log'u: `Seed data incomplete, will retry on next start`. Not: temiz DB + Ollama kapalıyken seed olmadığı için kelime araması boş döner (veri yok); veri varken çalışır. README bunu "backend'i yeniden başlat" ile anlatıyor | ✅ |
| 14 | Rehber Türkçe, tek yerde, gereksinimler/Ollama/başlat-durdur-sıfırla/local/adresler | `README.md` okundu ve sırayla uygulandı (aşağıda) | ✅ |
| 15 | Şifreler sadece local; rehberde ve compose'ta yazılı | Compose ilk satırı ve `README.md` "Güvenlik notu" | ✅ |
| 16 | Büyük/küçük harf farkı + Türkçe harfler aynı ad | `Source.toNameKey` (NFC + İ/I/ı → i + `toLowerCase(ROOT)`); `SourceTest` 3 test; çalışan sistemde "yardım MERKEZİ" → 409 | ✅ |
| 17 | "yardım MERKEZİ" → `SOURCE_NAME_TAKEN` | Canlı: `POST /api/workspaces/42/sources` "yardım MERKEZİ", "YARDIM MERKEZI", "Yardım merkezi" → 409 `SOURCE_NAME_TAKEN`; test `SourceIntegrationTest#...Returns409WithoutErrorLog`, `WorkspaceTest` | ✅ |
| 18 | Logda `ERROR` ve kullanıcı verisi yok | Canlı deneme sonrası backend logunda `ERROR` = 0, "merkez" geçen satır = 0; integration testi aynısını doğruluyor | ✅ |
| 19 | Uygulama ve DB aynı kararı verir | `V8` migration: `name_key` NOT NULL + `uk_sources_workspace_id_name_key`; backfill SQL Java ile aynı; `NameKeyMigrationIntegrationTest`, `SourceRepositoryTest` (aynı anahtar DB'de reddedilir) | ✅ |
| 20 | "Yeni sürüm yükle, yenile" testi süreden bağımsız | `upload.spec.ts` (yanıtı tarayıcı tarafında tutma); 10+1 koşuda geçti | ✅ |
| 21 | Takım 10 kez ardışık geçer, retry yok | Ben 10 kez koştum: hepsi `10 passed` (6,6-8,7 sn); `retries: 0` | ✅ |
| 22 | `console.log` yok | `grep -rn "console\." frontend/e2e` → 0 | ✅ |
| AC | Komutlar hatasız | Aşağıdaki tablo | ✅ |
| AC | Türkçe ad için otomatik test: sonuç + logda ERROR yok | `SourceIntegrationTest#givenYardimMerkeziCreatedViaApi_whenCreateSourceWithTurkishUppercaseName_thenReturns409WithoutErrorLog` | ✅ |

## Sözleşme uyumu
`docs/api/` değişmedi. Hata cevabı `errorCode` `SOURCE_NAME_TAKEN` ve status 409, `openapi.yaml` ile aynı; yeni endpoint, alan ya da hata kodu yok. Frontend kodu değişmedi.

## Komutlar (kendi çalıştırdıkların)
| Komut | Sonuç | Raporla tutarlı mı |
|-------|-------|--------------------|
| `cd backend && ./mvnw verify` | BUILD SUCCESS, 399 test, 0 hata/başarısız/atlanan, JaCoCo eşiği geçti | Evet (tester: 399) |
| `cd frontend && npm run build` | Geçti | Evet |
| `cd frontend && npm run lint` | Hata/uyarı yok | Evet |
| `cd frontend && npm test` | 361 test geçti | Evet |
| `E2E_BASE_URL=http://localhost:3000 npm run test:e2e` × 10 (tam sistem, Ollama açık) | 10/10 koşuda `10 passed` | Evet (tester: 10/10 + yük altında 10/10; yük altı koşuyu ben tekrarlamadım) |
| `E2E_BASE_URL=http://localhost:5173 npm run test:e2e` (local dev, 1 koşu) | `10 passed` | — |
| `springboot-verification` statik analiz / OWASP | ATLANDI (SpotBugs/PMD/Checkstyle yapılandırılmamış; NVD anahtarı yok) | — |

## Mutation testi
PIT kurulu: tester `Source`/`Workspace` için 34 mutant, 29 öldü (%85), hayatta kalan yok (raporundan; PIT'i ben tekrar çalıştırmadım). Stryker kurulu değil. Kapsam dışı olarak task'ta mutation açıkları var.

## Uçtan uca
- Tam sistem (3000), temiz volume'lardan sonra: 10 ardışık koşu, hepsi 10/10.
- Local dev (5173): 1 koşu 10/10, yeni sürüm yükleme dahil.
- Elle senaryolar: "Şifremi unuttum, online işlemlere giremiyorum" HYBRID aramasında "Yardım merkezi / Online İşlemler parolanı sıfırlama" ilk sonuç. Türkçe ad varyantları 409. "Şifre SSS" kabul edildi (202).
- Arayüzdeki Türkçe "ad kullanımda" metnini tarayıcıda ayrıca denemedim; API `errorCode` doğrulandı, eşleme önceki task'lardan (TASK-002/004) kalma ve testli.

## README'yi sırayla uygulama (madde 7)
1. Gereksinimler / Ollama: makinede Ollama 0.40.2 ve `bge-m3` vardı; `curl localhost:11434/api/tags` komutu çalışıyor.
2. Tam sistem: `docker compose -f deployment/docker-compose.yml up --build -d` → `ps`'te 4 servis healthy; `down` / `down -v` komutları çalıştı.
3. Local geliştirme: tam sistemi durdur → `docker-compose.dev.yml up -d` (2 servis healthy) → `./mvnw spring-boot:run` → `npm ci && npm run dev` (npm ci'ı yeniden koşmadım, bağımlılıklar kuruluydu) → hepsi README'deki gibi çalıştı.
4. Adresler/giriş bilgileri: pgAdmin ve veritabanı bilgileri doğru.
5. pgAdmin: README adım 2 "Servers → embedding-pgvector hazır gelir" doğru; ama `Servers` düğümü kapalı gelir (çift tıklanır) ve şifre penceresi bağlanırken iki kez çıktı (ikincisi aynı şifreyle geçti). Aşağıda Optional olarak not edildi.

## Kod review bulguları
| Severity | Dosya:satır | Bulgu | Sorumlu rol |
|----------|-------------|-------|-------------|
| Optional | `README.md` pgAdmin bölümü | Giriş sonrası `Servers` düğümü kapalı gelir ve ilk bağlantıda şifre penceresi iki kez çıkabilir (ikinci kez aynı şifre). Rehbere "Servers'a çift tıkla; şifre penceresi tekrar çıkarsa aynı şifreyi gir" notu eklenebilir | devops |
| Optional | `backend/.../Source.java:94-97` | `toNameKey` ile `V8` SQL'i iki ayrı yerde aynı kuralı taşıyor; `NameKeyMigrationIntegrationTest` bunun eşitliğini koruyor. Aksiyon gerekmez | — |
| FYI | `deployment/docker-compose.yml` | Ollama kapalıyken temiz DB'de seed tamamlanmaz (`Seed data incomplete`); Ollama açılınca backend yeniden başlatılmalı. README bunu yazıyor | — |
| FYI | proje | `git` deposu yok, diff yerine handoff + kaynak okuması kullanıldı | — |

Critical ya da Required bulgu yok.

## Düzeltme
Gerek yok.

## Sistemi bıraktığım durum
- Tam sistem (`deployment/docker-compose.yml`, proje `embedding-pgvector`) **AÇIK**, temiz volume'lardan sıfırdan kuruldu, 4 servis healthy, `chunks` = 1410 (E2E ve elle yüklemelerden temiz). Kapatmak için `docker compose -f deployment/docker-compose.yml down` (`-v` ile veri de silinir).
- Local dev compose (`embedding-pgvector-dev`) **KAPALI** ve volume'ları silindi; local backend ve Vite süreçleri durduruldu.
- Ollama **AÇIK** (`ollama serve`, `localhost:11434`, `bge-m3` kurulu).

STATUS: DONE
