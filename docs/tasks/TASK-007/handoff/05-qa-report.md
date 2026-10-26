TASK: TASK-007
# QA raporu

## Kullanılan skill'ler
- `code-review-and-quality` (severity ölçeği, review süreci)
- `backend-conventions` bölüm 2 kural kaynağı olarak (plan ve lead'in talimatı); `java-code-review` ve `springboot-verification` gerekmedi: kod davranışı değişmedi, sadece yorum eklendi.

## Kural kontrolü
| # | Kural / Kabul kriteri | Kanıt (kod + test) | Sonuç ✅/❌ |
|---|---|---|---|
| K1/AC-1 | Her üst seviye tipin üstünde Javadoc | V1 çıktısı boş; V2: main 98, test 59, `^/**$` içeren dosya 157, `package-info.java`'da `/**` yok | ✅ |
| K2 | Javadoc biçimi, annotation'lardan önce | V1 (satır `*/` ile bitiyor, ilk bildirim/annotation hemen altında); SourceService'te `/** */` ardından `@Service` görüldü | ✅ |
| K3/AC-2 | Türkçe, en fazla 2 satır | V4 boş (1-2 satır, ≤120 karakter); V6 İngilizce kelime taraması boş; 157 yorum elle okundu, hepsi Türkçe | ✅ |
| K4 | Amaç söyler, sınıf adını tekrar etmez | V5 boş (main + test, kendi adı yok); alan/metot listesi yok (Q1) | ✅ |
| K5 | `@author`/`@since` vb. yok | V6 yasak karakter komutu boş (`@ < > & { }`, `\u`, `*/`) | ✅ |
| K6 | Metot ve nested tiplere yorum yok | V3: dosya başına tek blok, girintili `/**` yok | ✅ |
| K7/AC-3 | Sadece yorum eklendi | V7 (aşağıda) | ✅ |
| K8 | Yorum doğru | Q1/Q2/Q3 (aşağıda) | ✅ |
| AC-4 | `verify` geçer, test sayısı aynı | 417 test, 0 hata; statik test annotation 329 = 329 | ✅ |
| S-1/S-2 | SourceService yorumu, javadoc'ta görünür | D7 cümlesi birebir; `javadoc` çıkış 0, `SourceService.html`'de cümle 1 kez | ✅ |

### V7 (baseline karşılaştırması)
`diff -r -x .DS_Store scratch/TASK-007-baseline/src backend/src`:
- `diff -r` satırı **157**; ekleme hunk'ı **157**; `c`/`d` hunk'ı yok; `<` satırı, `Only in`, `\ No newline` yok (0).
- `>` satırlarının hepsi `/**`, ` * metin` ya da ` */`; toplam **532** (aralık 471-628).
- Main 308 (98 dosya) + test 224 (59 dosya) = 532.
- 35 `package-info.java` diff'te yok. Test annotation sayısı baseline ve güncel: 329 / 329.
- `backend/CLAUDE.md` satır 79: tek ifade "yorum yasağı" → "yorum politikası (her üst seviye tipte Türkçe amaç Javadoc'u, metot yorumu yok)" (D11; `backend/src` dışı olduğu için diff'te görünmez, satır elle okundu).

### Q1: 157 yorumun içerik kontrolü
157 yorumun hepsi (98 main + 59 test) sınıfın gerçek koduyla karşılaştırıldı. Doğruluğu etkileyen uyumsuzluk: **yok**. Önce backend-coder'ın işaretlediği 5 sınıf:

| Sınıf | Karar |
|---|---|
| `EmbeddingModelGuard` | Doğru. "Uyuşmazlıkta 409": `ensureWorkspaceCompatible` ve `ensureDimensions` ikisi de `ConflictException` (`EMBEDDING_MODEL_MISMATCH`) fırlatır. `isWorkspaceCompatible` boolean döner ama yorum onu iddia etmiyor ("denetler"). `warnOnMismatch` açılışta `log.warn` yazar. |
| `SearchProperties` | Doğru, eksik değil yanıltıcı da değil: varsayılan mod/strateji/ef/limit, `rrfK`, istatistik saat dilimi ve sık arama ayarları kayıtta var. `maxScanTuples`/`forceVectorIndex` açıkça sayılmamış ama yorum "hepsi"ni iddia etmiyor. |
| `EmbeddingPgvectorApplication` | Doğru: `@SpringBootApplication` + `@ConfigurationPropertiesScan`; "ayar kayıtları" = `@ConfigurationProperties` record'ları. |
| `ChunkRetrievalService` | Doğru: `retrieveVector`, `retrieveHybrid`, `explainVector` önce `applySearchSettings` çağırır (ef_search, iterative scan, tarama sınırı, sort); hepsi tek `@Transactional(readOnly)` içinde. Kelime araması bu ayarı kullanmaz, yorum da "vektör aramasından önce" diyor. |
| `IngestionJobLifecycleService` | Doğru: metot başına ayrı `@Transactional` ("adım adım"); `completeJob` eski parçaları siler, yenilerini yazar; `failJob` `failIngestion` çağırır. |

Tester'ın raporunda ayrı bir "emin değilim" listesi yok; riskli test yorumlarını da testlerin metot adlarıyla karşılaştırdım (ChunkIntegrationTest 404 senaryoları, PopularQuery "ilk beş", WorkspaceTest "on kaynak", SearchEventStats İstanbul günleri, EmbeddingServiceTest günlüğe metin/vektör yazmama, SourceIntegrationTest paralel oluşturma, SchemaMigration dizi başlangıçları 10001/50, SearchControllerTest'in iki controller'ı, VectorFixtures `unit/mix/random/around`, LegacyDataReset "yeniden çalışınca kaynağı koruma"). Hepsi uyumlu.

### Q2: SourceService
Yorum plandaki D7 cümlesiyle birebir aynı, tek satır; "ekleme", "silme", "limit" iddiası yok. Sınıf gerçekten sadece `listSources` ve `getSource` sunuyor.

### Q3: Örneklem tablosu
| Tür | Dosya | Yorum (özet) | Sınıfın gerçek işi | Uyumlu mu |
|---|---|---|---|---|
| Controller | `SourceController` | Kaynak listeleme, getirme, dosyayla ekleme, yeni sürüm REST uçları | Bu 4 işlem için uçları sunar | ✅ |
| Service | `SourceService` | D7 cümlesi | Kaynak sayfalama ve tek kaynak + son iş | ✅ |
| Repository | `WorkspaceRepository` | Sayılarla okur; ekleme öncesi çalışma alanı satırını kilitler | Özet sorguları + kilitli okuma | ✅ |
| Projection | `WorkspaceSummary` | Çalışma alanını kaynak ve parça sayılarıyla özetleyen sorgu satırı | Aynı sorgunun satırı | ✅ |
| Entity | `Workspace` | Kaynak sınırı ve ad tekilliğini uygular | `addSource` sınır + ad kontrolü | ✅ |
| Enum | `IngestionStage` | Aşamayı ve işin bitmiş sayılıp sayılmadığını belirtir | Aşamalar + `isActive` | ✅ |
| Annotation | `NotEmptyFile` | Boş olmayan dosya doğrulama kuralı | Kısıt annotation'ı | ✅ |
| Request record | `SearchChunksRequest` | İstek gövdesi; arama metni ve kanal adını kırpar, boş kanalı yok sayar | Kompakt kurucu tam bunu yapar | ✅ |
| Response record | `SearchEventStatsResponse` | İstatistikleri taşır; oranları son yedi günden hesaplar | `rate(count, last7DaysCount)` | ✅ |
| Config | `ClockConfig` / `IngestionProperties` | UTC saat sağlar / içe alma ayarlarını okur ve doğrular | `Clock` bean'i / `@ConfigurationProperties` | ✅ |
| Exception | `ServiceUnavailableException` | HTTP 503'e dönüşen servis ulaşılamaz/model yok hataları | `BaseException` alt sınıfı, 503 | ✅ |
| Parser | `CsvDocumentParser` | Her kaydı başlık sütunlarıyla bölüme çevirir; ayırıcıyı ilk satırdan bulur | Ayrıştırıcı bunu yapar | ✅ |
| Unit test | `SourceServiceTest` | Listeleme ve tek kaynak getirme, sahte depolarla | 5 test bu davranışı kapsar | ✅ |
| Entegrasyon testi | `SearchIntegrationTest` | Kelime/vektör/hibrit mod, sıra birleştirme, indeks kullanımı, gerçek PostgreSQL | Test adları bunu doğrular | ✅ |
| Fixture / sahte istemci | `FakeEmbeddingClient`, `PostgresIntegrationTest` | Sabit vektör döndürür / her testte tabloları boşaltır | `@BeforeEach/@AfterEach` TRUNCATE | ✅ |

## Sözleşme uyumu
Değişiklik yok. `docs/api/**`, controller/DTO imzaları ve frontend dokunulmadı; `springdoc` Javadoc okumaz, `OpenApiDocumentationIntegrationTest` `verify` içinde geçti.

## Komutlar (kendi çalıştırdıkların)
| Komut | Sonuç | Raporla tutarlı mı |
|---|---|---|
| V1-V6 (157 dosya, `backend/src`) | Hepsi boş / beklenen sayılar (98, 59, 157) | Evet |
| V7 `diff -r` baseline ↔ `backend/src` | 157 dosya, 157 ekleme hunk'ı, 532 `>` satırı, başka fark yok | Evet |
| V8 `cd backend && ./mvnw verify` | BUILD SUCCESS; `Tests run: 417, Failures: 0, Errors: 0, Skipped: 0`; "All coverage checks have been met." | Evet (02 ve 03 raporlarıyla aynı) |
| V8 statik `@Test` sayımı baseline / güncel | 329 / 329 | Evet |
| V9 `javadoc … -subpackages com.embedding.pgvector` | Çıkış 0; `SourceService.html`'de D7 cümlesi 1 kez | Evet |

`npm` komutları çalıştırılmadı: frontend bu task'ta değişmedi.

## Mutation testi
Çalıştırılmadı (PIT kurulu, `pom.xml`'de var). Gerekçe: `main` ve `test`'te yalnızca yorum eklendi (V7 kanıtı); derlenen bytecode ve test mantığı değişmedi, dolayısıyla mutant kümesi ve skor TASK-006'dakiyle aynıdır. Mutation koşusu burada yeni bilgi üretmez.

## Uçtan uca
Çalıştırılmadı. Gerekçe: frontend ve devops plan dışı, çalışma zamanı davranışı değişmedi. Uygulamanın bu değişiklikten sonra ayağa kalkıp çalıştığı, Spring context'i başlatan entegrasyon testlerinin (417 testin içinde) geçmesiyle kanıtlandı.

## Kod review bulguları
| Severity | Dosya:satır | Bulgu | Sorumlu rol |
|---|---|---|---|
| Nit | `chunk/ChunkIntegrationTest.java` (Javadoc) | "…başka kaynağın, başka çalışma alanının ya da yenilenmiş parça için 404 döner" cümlesi dilbilgisi olarak takılıyor (ek ortak değil). İçerik doğru; ör. "…başka kaynağa ya da başka çalışma alanına ait ve yenilenmiş parçalar için 404 döner." | tester |
| FYI | `config/SearchProperties.java` | Yorum `maxScanTuples` ve `forceVectorIndex` ayarlarını açıkça anmıyor; iddia yanlış değil, durdurmaz. | — |
| FYI | `.claude/skills/*` (lead notu) | Plandaki açık not: eski "yorum yok" ifadeleri lead tarafından düzeltildi; `backend-conventions` bölüm 2'deki `SourceService` örnek cümlesi (ekleme/silme/limit) koda uymuyor (G5). Rol dışı, lead/kullanıcı karar verir. | lead |
| FYI | `scratch/TASK-007-*` | Kapanışta lead'in silmesi gereken geçici dosyalar (baseline, diff, classpath, javadoc, log); qa `scratch/` altına yeni dosya yazmadı, sadece oturum scratchpad'ini kullandı. | lead |

Critical ya da Required bulgu yok.

## Düzeltme
Gerekmiyor. Nit düzeltmesi isteğe bağlı; STATUS'ü etkilemez.

## Düzeltme turu 1
| Madde | Kanıt | Sonuç |
|---|---|---|
| `ChunkIntegrationTest` Javadoc'u (Nit, tester): "Tek parça getirme ucunu gerçek PostgreSQL ile doğrular; başka kaynağa ait parça, başka çalışma alanındaki kaynak, bilinmeyen çalışma alanı ve yenilenmiş parça için 404 döner." | Testteki 4 404 senaryosuyla birebir uyumlu: başka kaynağın parçası, başka çalışma alanının kaynağı, bilinmeyen çalışma alanı, yenilenmiş parça. Dilbilgisi düzeldi. 2 satır, ≤120, Türkçe, tag/yasak karakter yok, kendi adı geçmiyor. | ✅ |
| Baseline diff hâlâ sadece ekleme | `ChunkIntegrationTest` baseline farkı yalnızca 4 satırlık blok. Genel: 157 dosya, 157 ekleme hunk'ı, 532 `>` satırı (hepsi blok satırı), `c`/`d`/`<`/`Only in` yok. | ✅ |
| Biçim taramaları (V1, V4, V6 yasak karakter) | Üçü de boş | ✅ |
| `./mvnw verify` | BUILD SUCCESS, `Tests run: 417, Failures: 0, Errors: 0, Skipped: 0`, JaCoCo geçti | ✅ |

Düzeltme başka bir şeyi bozmadı. Açık madde yok.

STATUS: DONE
