TASK: TASK-008
# Plan

## Özet
**Mevcut durum:** TASK-001…007 kapalı. Backend 417 test (`./mvnw verify`, TASK-007), frontend 27 dosya / 405 unit test ve 8 Playwright testi (`e2e/document-flow.spec.ts`, TASK-006) geçiyor. Mevcut testler iş kurallarını büyük ölçüde kapsıyor (backend entegrasyon testleri sahte embedding istemcisiyle, frontend MSW ile). Eksik olan, **gerçek çalışan sistemde** (nginx + backend + PostgreSQL/pgvector + gerçek Ollama) üç katmanın (ekran, REST, veritabanı) birlikte doğrulanması:
- E2E testleri veritabanına hiç bakmıyor (`vector_dims`, model etiketi, parça sayısı, iş satırı).
- E2E'deki "sayfa yenilense de ilerleme sürer" testi iş durumunu `page.route` ile taklit ediyor; gerçek içe alma sırasında yenileme denenmemiş.
- Arama kayıtları ve istatistik sayfası, parça detayı (`?parca=`), yeni sürüm sonrası eski parça, hata cevaplarının nginx üzerinden gelişi, Ollama kapalıyken ekran, gerçek eşzamanlı yükleme ve içe alma sırasında backend yeniden başlatma E2E'de yok.

**Yeni durum:** Yeni özellik yok. İş üç fazda yürür:
1. **Doğrulama (tester, Tur 1):** Mevcut bütün testler çalıştırılır (başlangıç sayıları). Aşağıdaki "Şüpheli noktalar" (H1-H8) tek tek doğrulanır. Eksik testler yazılır: backend BT1-BT7, frontend FT1-FT2, E2E ET1-ET8 ve OD1-OD2. Gerçek bir hatayı yakalayan test önce **kırmızı** yazılır; hata bu testle kanıtlanır.
2. **Düzeltme (NEEDS_FIX zinciri):** Bulunan hatalar sorumlu role döner (bkz. "Hata yönetimi ve NEEDS_FIX zinciri"). tester Tur 2'de testlerin yeşile döndüğünü doğrular.
3. **Sistem ve son kontrol:** devops kalıcılık, temiz kurulum, içe alma sırasında yeniden başlatma, README ve `ollama.md` adımlarını çalıştırır. qa her akışı üç katmanda son kez doğrular ve hata listesini denetler.

**Değişen mevcut parçalar (şimdiden):** Sadece `docs/api/openapi.yaml` açıklama ve örnekleri (bkz. "Sözleşme değişiklikleri"). Kod değişikliği yalnızca bulunan hataların düzeltmesi olarak gelir. Yeniden adlandırma yok.

**Bölme:** Önerilmiyor. Doğrulama, düzeltme ve son kontrol aynı hata listesine bağlı; ayrı teslim edilemez.

## Çalışacak roller
| Sıra | Rol | Ne yapar | Ne zaman başlar |
|------|-----|----------|-----------------|
| 0 | lead | Başlangıç kopyasını alır (D14) | Onaydan hemen sonra |
| 1 | frontend-coder (Adım 0) | Sadece soru 1 = A ise: `ajv`, `ajv-formats`, `yaml` dev paketlerini kurar (D6) | Kopyadan sonra |
| 2 | tester (Tur 1) | Başlangıç koşusu, H1-H8, yeni testler, akış matrisi, hata listesi | Adım 0 bitince (soru 1 = B ise kopyadan sonra) |
| 3 | backend-coder, frontend-coder, devops, planner | **Sadece** tester'ın `## Düzeltme` bölümünde adı geçenler, paralel | tester `NEEDS_FIX` yazınca |
| 4 | tester (Tur 2) | Kırmızı testlerin yeşile döndüğünü ve bütün setlerin geçtiğini doğrular | Düzeltmeler bitince |
| 5 | devops | Kalıcılık, temiz kurulum, yeniden başlatma, README ve `ollama.md` | tester `DONE` yazınca |
| 6 | qa | Üç katmanlı son kontrol, sözleşme karşılaştırması, hata listesi denetimi | devops `DONE` yazınca |

- Hata bulunmazsa 3. ve 4. adım atlanır: tester Tur 1 doğrudan `STATUS: DONE` yazar, devops başlar.
- **Koşullu rol:** backend-coder ve frontend-coder bu task'ta yeni iş yapmaz; sadece hata düzeltir. Onları lead, tester'ın (ya da devops/qa'nın) `## Düzeltme` bölümüne göre başlatır.
- Atlanan rol yok. devops gerçekten çalışır: Kural 5 ve 6 onun alanı (Docker, README, `ollama.md`).

## Kural eşlemesi
| # | Kural (TASK.md) | Nerede uygulanır | Hangi testle doğrulanır |
|---|-----------------|------------------|-------------------------|
| K1 | Kullanıcının yaptığı her akış (F1-F12, "Akış matrisi") | tester: mevcut + yeni testler; qa Q1 | F1: devops P2, E1. F2: E1, ET1. F3: E2, E5, ET1, ET4. F4: E2, ET2. F5: ET1, ET4. F6: ET3, ET6. F7: E3, E4, ET1, ET5. F8-F9: ET5. F10: E3, E4, ET5. F11-F12: ET6 |
| K2 | Her akış üç katmanda: ekran, REST'in sözleşmeye uyumu, DB (tablolar, `vector_dims`, model, index kullanımı) | E2E destek dosyaları `e2e/support/contract.ts` (D6) ve `e2e/support/database.ts` (D5) | ET1-ET8 ve OD1-OD2'nin her biri en az bir REST ve bir DB assertion'ı taşır; index: ET1 (`vectorPlan`); qa Q1, Q2 |
| K3 | Hata durumları: geçersiz istek, olmayan id, desteklenmeyen/boş dosya, limit, Ollama kapalı; RFC 9457 + `errorCode` + Türkçe mesaj | Mevcut backend ve frontend testleri + yeni testler | ET7 (REST matrisi, nginx üzerinden), ET8 (ekran), OD1, OD2, BT5, BT6, BT7, FT1, FT2, E6 |
| K4 | Eşzamanlılık: aynı anda yükleme, yükleme sürerken arama, yarıda kalan içe alma | Backend testleri + gerçek sistem | ET3, ET4, BT2, BT3, BT4; mevcut `givenNineSourcesAndTwoParallelCreates…`, `givenTwoParallelUploadsToSameSource…`, `givenJobBlockedInEmbedding…`, `givenJobsLeftActive_whenRecoveryRuns…`; devops C5a, C5b |
| K5 | Kalıcılık: `down`/`up` veri kalır, `down -v` temiz, Flyway boş DB'de çalışır | devops | P1, P2, P3; mevcut `CleanInstallIntegrationTest` |
| K6 | README ve `ollama.md` adımları yazıldığı gibi çalışır; soru tablosu beklenen bölümleri döndürür | devops | R1-R5; mevcut E3, E4 (soru tablosu) |
| K7 | Bütün mevcut testler çalıştırılır ve geçer | tester (başlangıç ve son), qa | T-ALL (bkz. "Komut listesi") |
| K8 | Her hata: belirti, tekrar adımı, sorumlu katman, önem; düzeltilir ve testle korunur; sözleşme yanlışsa planner düzeltir | "Hata yönetimi ve NEEDS_FIX zinciri" | qa Q3 |
| S1 | `up --build -d`, 4 servis `healthy`, 3000'de "Henüz belge yüklenmedi" | devops | P2; E1 |
| S2 | Örnek PDF, yenileme, ~24 parça, hepsi 1024 boyut | tester | E2 (taklitli yenileme), ET2 (gerçek yenileme), ET1 (DB) |
| S3 | "Yıllık izin kaç gün?" anlam araması, bölüm ilk sonuçlarda, `search_events`'e yazılır, "Sık aranan"da görünür | tester | ET5 |
| S4 | Ollama kapalıyken yükleme mesajı, kelime araması çalışır | tester | OD1, OD2 (D4, D8) |
| S5 | Boş ya da desteklenmeyen dosya: Türkçe hata, DB'de yarım kayıt yok | tester | E6 (ekran), ET7 (REST + DB), BT6 (metinsiz dosya; karttaki soru 2) |
| AC1 | Her akış için katman, yöntem ve sonuç raporda | tester `03` "Akış matrisi"; qa `05` | qa Q1 |
| AC2 | Hatalar düzeltilmiş, her biri için test var; düzeltilmeyen gerekçeli | `03`, `04`, `05` hata tabloları | qa Q3 |
| AC3 | verify, build, lint, unit, e2e geçer; komut ve sayılar raporda | tester Tur 1/Tur 2, qa | T-ALL, qa Q4 |
| AC4 | `down -v` sonrası temiz açılış ve smoke | devops | P2, P3; qa Q5 |

## Akış matrisi
tester bu tabloyu `03-test-report.md`'ye sonuç sütunuyla kopyalar; qa `05`'te kendi sonucunu ekler. "Mevcut" sütunu bugün var olan testlerdir; "Yeni" bu task'ta yazılanlardır.

| # | Akış | Ekran (UI) kontrolü | REST kontrolü | DB kontrolü | Mevcut | Yeni |
|---|------|---------------------|---------------|-------------|--------|------|
| F1 | Sistemi açma | 3000 açılır, `/` → `/w/42/belge-yukle` | `/actuator/health` UP | `flyway_schema_history` 10 başarılı satır, `vector` 0.8.1 | E1 | P2, P3 |
| F2 | Çalışma alanını görme | Seçici yok (tek alan), "Henüz belge yüklenmedi" | `GET /api/workspaces/42` şemaya uyar, sayılar | `workspaces` tek satır 42 "Belgelerim"; sayılar `count(*)` ile aynı | E1, `WorkspaceIntegrationTest` | ET1 |
| F3 | Belge yükleme (PDF, MD, TXT, CSV) | "Belgeyi yükle" → `?kaynak=` | 202, `Location` = `statusUrl`, gövde `IngestionJobResponse` | `sources` 1 satır `INGESTING`, `ingestion_jobs` 1 satır `QUEUED` | E2, E5, `IngestionIntegrationTest` | ET1, ET4, ET8 |
| F4 | İçe alma ilerlemesi + yenileme | İlerleme çubuğu; yenilemeden sonra değer düşmez | `GET …/ingestion-jobs/{id}` aşamaları sırayla | `embedded_chunks` artar, sonda `= total_chunks` | E2 (taklitli) | ET2 (gerçek) |
| F5 | Parçaların embedding'le yazılması | "Belge hazır · N parça" | `GET …/chunks` şemaya uyar, `embeddingModel` `bge-m3@1024` | `count(*) = N`, `vector_dims = 1024` hepsi, tek model etiketi, `chunk_no` 0..N-1 kesintisiz, `tsv` dolu | `SamplePdfIngestionIntegrationTest` | ET1, ET4 |
| F6 | Yeni sürüm yükleme | "Yeni sürüm yükle" diyaloğu, sürüm etiketi | 202; süren işte 409 `INGESTION_IN_PROGRESS` | Eski parça id'leri silinir, `sources.version` güncellenir, iki iş satırı | `IngestionIntegrationTest` | ET3, ET6 |
| F7 | Kelime / anlam / hybrid arama | Sonuç listesi, mod etiketi | `SearchResultResponse` şemaya uyar; `explain` ile plan | Vektör planında `idx_chunks_embedding` | E3, E4, `SearchIntegrationTest` | ET1, ET5 |
| F8 | Arama kayıtları | `/w/42/arama-kayitlari` tablosu, en yeni üstte | `PageResponseSearchEventResponse` şemaya uyar | `search_events` satırı: `query`, `mode`, `result_count`, `top_chunk_id`, `query_key`, `keyword_count`, `best_distance`, `result_limit` | `SearchIntegrationTest` | ET5 |
| F9 | İstatistikler | Sayaçlar ve oranlar | `SearchEventStatsResponse` | SQL ile sayılan değerler API ile aynı (Europe/Istanbul günü) | `SearchEventStatsIntegrationTest` | ET5 |
| F10 | "Sık aranan" | Liste sırası, tıklayınca arama | `PopularQueriesResponse` | Gruplama `query_key` ile; eşik 0.49 | E3, E4, `PopularQueryIntegrationTest` | ET5 |
| F11 | Belge detay ekranı | Başlık, durum kutuları, "Daha fazla göster" ile bütün parçalar | `SourceResponse`, `PageResponseChunkResponse` | Ekrandaki parça sayısı `chunk_count` ve `count(*)` ile aynı | Frontend unit | ET6 |
| F12 | Parça detayı (`?parca=`) ve eski parça | Vurgu bildirimi; yeni sürümden sonra "Bu parça artık yok…" | `GET …/chunks/{id}` 200, sonra 404 `CHUNK_NOT_FOUND` | Eski `chunk_id` yok; `search_events.top_chunk_heading` korunur | `ChunkIntegrationTest` | ET6 |

## Şüpheli noktalar (tester'ın doğrulayacağı hipotezler)
Kod incelemesinde gördüklerim. Hiçbiri henüz kanıtlanmış hata değildir. tester her biri için sonucu `03`'teki "Hipotez sonuçları" tablosuna yazar: **Hata (B-xx)** ya da **Hata değil** (kanıt: test adı veya komut çıktısı). "Beklenen" sütunu, hata çıkarsa testin doğrulayacağı ve düzeltmenin sağlayacağı davranıştır.

| # | Yer (dosya:satır) | Şüphe | Beklenen | Doğrulayan test | Tahmini önem |
|---|-------------------|-------|----------|-----------------|--------------|
| H1 | `backend/…/ingestion/service/IngestionRecovery.java:32-43`; `IngestionJob.java:143-147`; `IngestionJobRunner.java:71-103` | Kurtarma `ApplicationReadyEvent`'te çalışır; Tomcat istek kabul etmeye bundan önce başlar. Bu arada açılan yeni iş (`QUEUED`) "yarıda kaldı" diye kapatılır, çalışan iş `ensureActive`'e takılıp `INTERNAL_ERROR` logları üretir | Bu süreç içinde kuyruğa verilmiş bir iş kurtarma tarafından kapatılmaz, normal biter (`DONE`). Önceki süreçten kalan iş yine `INGESTION_INTERRUPTED` olur | BT4 + mevcut `givenJobsLeftActive_whenRecoveryRuns…` yeşil kalır | Düşük |
| H2 | `frontend/src/shared/api/httpClient.ts:45-56` | Backend kapalıyken ya da yeniden başlarken nginx 502/504 HTML döner (Vite proxy 500 boş gövde). Problem gövdesi yok → `UNEXPECTED_ERROR` → ekranda "Beklenmeyen bir hata oluştu. Tekrar dene." | 5xx cevabında gövde `errorCode` taşımıyorsa `ApiError(status, 'NETWORK_ERROR')` → "Sunucuya ulaşılamıyor. Bağlantını ve sistemin çalıştığını kontrol et." Problem gövdeli 5xx (ör. 503 `EMBEDDING_UNAVAILABLE`, 500 `INTERNAL_ERROR`) kendi kodunu korur. 4xx gövdesizse `UNEXPECTED_ERROR` kalır | FT1, FT2 | Orta |
| H3 | `frontend/nginx.conf:8` (`client_max_body_size 21m`) | 21 MB'tan büyük gövde site (3000) üzerinden gelince nginx kendi HTML 413'ünü döner; sözleşme 413 `FILE_TOO_LARGE` Problem Details diyor. Ekran 20 MB üstünü zaten göndermediği için sadece API kullanımında görülür | 3000 üzerinden 22 MB dosya → 413, `application/problem+json`, `errorCode` `FILE_TOO_LARGE` | ET7 (satır "22 MB") | Düşük |
| H4 | `backend/…/ingestion/service/IngestionService.java:141-149`; `FileTypeDetector.java:64-74` | Türkçe karakterli dosya adı (`Content-Disposition` içinde ham UTF-8) hiç test edilmemiş; adın bozulması ya da uzantının bulunamaması olası | `Çalışan İzin Notları.md` → iş ve kaynakta `fileName` aynen; boş ad bırakılırsa kaynak adı `Çalışan İzin Notları` | BT1, ET8 | Bilinmiyor |
| H5 | `Chunk.java:28,53-56`; `V4__create_chunks_table.sql:13`; `EmbeddingModelGuard.java:51-57`; `deployment/docker-compose.yml:34-35`; `README.md:30` | Kolon `vector(1024)` sabit. README "model için compose'daki değeri düzenle" diyor. `EMBEDDING_DIMENSIONS` 768 yapılıp 768 boyutlu model seçilirse denetim geçer, yazma ve arama SQL hatasıyla 500 / `INTERNAL_ERROR` olur | Kolondan farklı boyutta vektör hiçbir zaman 500 vermez: arama 409 `EMBEDDING_MODEL_MISMATCH`, içe alma işi `FAILED` + `EMBEDDING_MODEL_MISMATCH`. README/compose notu: boyut 1024 dışına çıkamaz | BT7; devops R1 (doküman) | Düşük |
| H6 | `GlobalExceptionHandler.java:76-85` (framework hataları) | Sayısal olmayan ya da `long`'a sığmayan path id'si (`/sources/abc`, `/workspaces/99999999999999999999`) için davranış test edilmemiş | 400, `application/problem+json`, `errorCode` `BAD_REQUEST` (sözleşmeye bu task'ta yazıldı) | BT5, ET7 | Bilinmiyor |
| H7 | `IngestionJobRunner.java:82-85`; `Source.java:112-114`; `Workspace.java:58-61` | Metni olmayan dosya (yalnızca boşluk içeren `.md`, yalnızca başlık satırı olan `.csv`) 0 bayt olmadığı için kabul edilir; kaynak `FAILED` olarak kalır ve 10 kaynak sınırından yer tutar. Görev "boş dosyada yarım kayıt kalmaz" diyor | Karttaki soru 2'ye bağlı (bkz. "Gözden kaçabilecek durumlar" G4) | BT6 | Soruya bağlı |
| H8 | `frontend/e2e/document-flow.spec.ts:71-129` (test boşluğu) | Yenileme testi iş durumunu `page.route` ile taklit ediyor; gerçek içe alma sırasında yenileme hiç denenmedi | Gerçek, uzun bir içe almada yenilemeden sonra ilerleme kaldığı yerden (daha düşük değil) görünür ve iş biter | ET2 | Test boşluğu |

**Hata sayılmayan, bilinen davranışlar** (tester ve qa bunları hata olarak raporlamaz; gözlem olarak yazabilir):
- Kelime aramasında soru cümlesi sonuç vermeyebilir: `plainto_tsquery` bütün kelimeleri aynı parçada ister ("Yıllık izin kaç gün?" KEYWORD → 0 sonuç, `ollama.md:197-198`). README tablosu varsayılan HYBRID modla doğrulanır.
- PDF parçalarının başlığı "s. N · <sayfanın ilk satırı>"dır (`PdfDocumentParser.java:54-56`); örnek belgede bu, her sayfadaki şirket başlığıdır.
- E2E `global-setup` iş ve arama kaydı sayaçlarını sıfırlamaz; `ollama.md`'deki `ingestion-jobs/1` sadece `down -v` sonrası temiz kurulumda doğrudur.
- Sorgu kuralı 1-500 karakterdir (sözleşme, backend ve frontend aynı). `problem-details-rfc9457` skill'indeki "2-500" ifadesi skill metnidir (bkz. "Açık sorular ve riskler").

## Gözden kaçabilecek durumlar
| # | Durum | Beklenen davranış | Karşılayan test # ya da karttaki soru # |
|---|-------|-------------------|------------------------------------------|
| G1 | Türkçe karakterli dosya adı ve kaynak adı (ç, ğ, ı, İ, ö, ş, ü) | Ad ve dosya adı REST'te, DB'de ve ekranda aynen; boş ad bırakılınca uzantısız dosya adı kullanılır | BT1, ET8 |
| G2 | Aynı adla (büyük/küçük harf ve `İ`/`i` farkıyla) aynı anda iki yükleme | Biri 202, diğeri 409 `SOURCE_NAME_TAKEN`; DB'de tek kaynak, tek iş | BT2 |
| G3 | Backend yeniden başlarken açık sayfada işlem | Ekranda "Sunucuya ulaşılamıyor…" (H2); iş durumu sorgusu durmaz, backend dönünce iş `FAILED` + "iş yarıda kaldı…" görünür | FT1, FT2, devops C5a |
| G4 | Metni olmayan dosya (yalnızca boşluk `.md`, yalnızca başlık `.csv`, görselden oluşan PDF) | Karttaki soru 2. A: kaynak `FAILED` kalır, neden "dosyada metin yok…", "Yeniden dene" ile aynı kaynağa doğru dosya yüklenir. B: metin dosyası ve CSV yüklemede reddedilir, kaynak oluşmaz | Soru 2; BT6 |
| G5 | Yeni sürümden sonra arama sonucundan ya da eski bağlantıdan açılan parça | "Bu parça artık yok; kaynak yeni bir sürüme geçmiş olabilir."; arama kaydındaki başlık korunur | ET6 |

## Kararlar
| # | Karar | Gerekçe | Elenen alternatif ve nedeni |
|---|-------|---------|-----------------------------|
| D1 | backend-coder ve frontend-coder koşullu: sadece `## Düzeltme`'de adları geçerse başlar (frontend-coder ayrıca soru 1 = A ise Adım 0) | Task yeni kod istemiyor; hatasız durumda boşa rol başlatılmaz | Coder'ları baştan başlatmak: yapacak işleri yok, kök kuraldaki "bekleyecek rol önceden başlatılmaz" ile çelişir |
| D2 | tester Tur 1 kapsamlıdır: bütün akışlar, H1-H8 ve yeni testler bitmeden `NEEDS_FIX` yazılmaz; bütün hatalar tek `## Düzeltme` bölümünde toplanır | Kök kural: aynı adım üst üste 2 kez `NEEDS_FIX` dönerse lead durur. Tur 2 sadece düzeltmeleri doğrulamalı | Hata buldukça tek tek bildirmek: zincir defalarca döner, ikinci `NEEDS_FIX`'te iş durur |
| D3 | Hatayı yakalayan test önce kırmızı yazılır ve suite'te kalır; `03`'te "kırmızı (B-xx)" diye işaretlenir. Düzeltmeyle yeşile döner. `@Disabled`, `test.skip`, `test.fixme` ile gizlenmez | Kural 8: her hata için onu yakalayan test. Kırmızı koşu, hatanın gerçek olduğunun kanıtıdır | Düzeltmeden sonra test yazmak: testin hatayı gerçekten yakaladığı kanıtlanmaz |
| D4 | **Ollama kapalı simülasyonu:** Ollama kapatılmaz. tester kendi scratchpad klasöründe geçici bir compose override dosyası açar (içerik: `services.backend.environment.OLLAMA_BASE_URL: http://host.docker.internal:9`), `docker compose -f deployment/docker-compose.yml -f <override> up -d backend` ile backend'i ulaşılamayan adresle yeniden açar, OD1-OD2'yi koşar, sonra `docker compose -f deployment/docker-compose.yml up -d backend` ile geri alır | Kullanıcının bilgisayarındaki Ollama'ya dokunulmaz; repoda dosya değişmez; port 9'da bağlantı hemen reddedilir (gerçek "kapalı" durumu) | Ollama uygulamasını kapatmak: kullanıcının makinesine müdahale. `deployment/docker-compose.yml`'ı düzenlemek: devops dosyası ve geri alınması unutulabilir |
| D5 | **E2E veritabanı kontrolü:** `frontend/e2e/support/database.ts` → `queryRows(sql: string): string[][]` ve `queryValue(sql: string): string`. `docker compose -f <E2E_COMPOSE_FILE ?? deployment/docker-compose.yml> exec -T postgres psql -U embedding -d embedding_pgvector -At -F '\|' -v ON_ERROR_STOP=1 -c <sql>` çalıştırır. `global-setup.ts` aynı yardımcıyı kullanır | `global-setup.ts` aynı yöntemle zaten çalışıyor; yeni paket gerekmez; dev ve tam sistemde aynı | Node PostgreSQL istemcisi (`pg`): yeni dependency ve 5432'ye ikinci bağlantı yolu |
| D6 | **Sözleşme doğrulaması (soru 1 = A):** `frontend/e2e/support/contract.ts` → `expectMatchesSchema(body: unknown, schemaName: string): void`. `docs/api/openapi.yaml`'ı `yaml` ile okur; `Ajv2020` (`strict: false`, `allErrors: true`) + `ajv-formats`; `int32`, `int64`, `float`, `double`, `binary` formatları `addFormat(name, true)` ile kabul edilir; `components.schemas` altında `type: object` ve `properties` taşıyan her şemaya `additionalProperties: false` eklenir (sözleşmede olmayan alan da yakalanır); belge `$id: 'openapi.json'` ile eklenir ve `{ $ref: 'openapi.json#/components/schemas/<schemaName>' }` derlenir. Hata mesajı Ajv hatalarını ve şema adını içerir. **Soru 1 = B ise:** bu dosya yazılmaz; her E2E testi kullandığı alanları ve `required` listesini açıkça doğrular, qa Q2'deki tam karşılaştırma tek güvence olur | Kural 2 her akışta sözleşme uyumunu ister; OpenAPI 3.1 şemaları JSON Schema 2020-12'dir, Ajv bunu doğrudan doğrular. Paketler sadece E2E'de kullanılır, imaja girmez | Backend'de Java şema doğrulayıcı: gerçek sistemi (nginx) değil sahte embedding'li testleri kapsar; OpenAPI 3.1 desteği zayıf |
| D7 | **E2E yerleşimi:** ET1-ET8, `document-flow.spec.ts`'teki mevcut 8 testin **sonuna**, aynı `serial` describe içine, bu sırayla eklenir. OD1-OD2 ayrı dosyada: `e2e/ollama-down.spec.ts`, `test.skip(process.env.E2E_OLLAMA_DOWN !== '1')`. `document-flow.spec.ts` ise `E2E_OLLAMA_DOWN === '1'` iken atlanır. `global-setup.ts` `E2E_OLLAMA_DOWN === '1'` iken sıfırlama yapmaz | Mevcut testler satır sayısı ve "Sık aranan" sırasına bakıyor; yeni testler veri eklediği için sonda olmalı. Çalışma alanı 42 ve 10 kaynak sınırı paylaşılıyor; ayrı dosyalar paralel çalışırsa birbirini bozar. Ollama kapalı koşusu, normal koşunun bıraktığı veriye ihtiyaç duyar | `playwright.config.ts`'te `workers: 1`: frontend-coder dosyası ve dosyalar arası sıra yine garanti olmaz |
| D8 | Ollama kapalıyken yükleme mesajı, ekranda "Son yükleme başarısız: embedding servisine (Ollama) ulaşılamıyor" satırıdır; arama mesajı "Embedding servisine (Ollama) ulaşılamıyor. Ollama'nın çalıştığını kontrol et ya da Kelime yöntemiyle ara." Örnek senaryodaki "Embedding servisine (Ollama) ulaşılamıyor…" ifadesi ikisini de karşılar | Yükleme 202 ile kabul edilir, hata iş üzerinden gelir (`errorMessages.ts:45-53`); arama hatası doğrudan 503'tür. Metinler mevcut ve test edilmiş | Yükleme mesajını arama mesajıyla aynı yapmak: davranış değişikliği, kapsam dışı |
| D9 | **Önem dereceleri:** Kritik: veri kaybı ya da bozulması, sistem açılmıyor, ana akış (yükleme, arama) çalışmıyor. Yüksek: bir akış yanlış sonuç veriyor ya da cevap sözleşmeye aykırı (status, `errorCode`, alan); geçici çözüm yok. Orta: hata durumunda yanlış ya da eksik mesaj, tekrarlanabilir yarış, dokümandaki bir adım çalışmıyor. Düşük: sadece API doğrudan kullanılınca görülür, kozmetik, doküman örneği yanlış | Kural 8 önem ister; roller aynı ölçeği kullanmalı | Serbest değerlendirme: raporlar arasında tutarsız |
| D10 | **Düzeltme politikası:** Kritik, Yüksek ve Orta hataların hepsi bu task'ta düzeltilir. Düşük hata, düzeltmesi tek katmanda ve sözleşmeyi değiştirmeden yapılabiliyorsa düzeltilir; değilse "Düzeltilmedi" listesine gerekçesiyle yazılır. Yeni endpoint, ekran ya da kırıcı sözleşme değişikliği gerektiren düzeltme yapılmaz; listelenir, lead kullanıcıya yeni task önerir | Kabul kriteri "düzeltilmeyen varsa gerekçesiyle" diyor; "yeni özellik yok" kuralı korunur | Her şeyi düzeltmek: kapsam büyür, yeni özelliğe kayar |
| D11 | **Büyük test belgesi testte üretilir**, repoya dosya eklenmez: ET2/ET3 için `Buffer` ile ~500 bölümlü Markdown (her bölüm `## Bölüm <i>` + ~1500 karakterlik Türkçe metin, içinde ayırt edici kelime `zeytinyağı`); devops C5 için aynı biçimde `python3` ile scratchpad'e yazılan dosya | `ingestion.target-chunk-chars=1600` ile bölüm başına ~1 parça → ~500 parça, 6 embedding grubu (96'lık); gerçek Ollama'da EMBEDDING aşaması saniyelerce sürer, yenileme ve yeniden başlatma bu sırada yapılabilir. 20 MB sınırının çok altında | Örnek PDF: ~2,6 sn'de bitiyor, yenileme yarışa döner. Repoya büyük dosya: gereksiz |
| D12 | **Index kullanımı:** Vektör kolu için `explain: true` aramasının `vectorPlan`'ında `idx_chunks_embedding` geçmesi aranır (`search.force-vector-index=true`). Kelime kolu için sadece `idx_chunks_tsv`'nin `pg_indexes`'te var olduğu doğrulanır; kullanılması beklenmez | ~30 satırlık tabloda planlayıcı GIN yerine sıralı taramayı seçebilir; bu hata değildir. Vektör kolunda zorlama ayarı var | Kelime kolunda index kullanımını şart koşmak: küçük veride kararsız test |
| D13 | **Sözleşme düzeltmeleri sürüm artırmaz** (`info.version` 0.5.0 kalır) | Sadece açıklama ve örnek düzeltmesi; path, status, alan ve `errorCode` değişmedi. `OpenApiDocumentationIntegrationTest` sürümü kontrol ediyor; artırmak backend'de gereksiz değişiklik ister | 0.5.1: yapısal değişiklik yokken kod değişikliği gerektirir |
| D14 | **Başlangıç kopyası:** Lead onaydan hemen sonra, hiçbir rol başlamadan şu komutları çalıştırır; qa Q6 diff'i buna göre yapar; kapanışta lead siler (`scratch/` kök `.gitignore`'da):<br>`B=scratch/TASK-008-baseline; mkdir -p $B/backend $B/frontend`<br>`cp -R backend/src backend/pom.xml $B/backend/`<br>`cp -R frontend/src frontend/e2e frontend/package.json frontend/package-lock.json frontend/playwright.config.ts frontend/vite.config.ts frontend/nginx.conf $B/frontend/`<br>`cp -R deployment README.md ollama.md $B/` | Proje git deposu değil (TASK-007 D8 ile aynı gerekçe); qa düzeltmelerin sadece hata için yapıldığını ancak önce/sonra karşılaştırmasıyla görür | `git init`: kullanıcının kararı |
| D15 | Sistem iş sonunda temiz kurulumla (`down -v` + `up --build -d`) açık bırakılır; soru 3 = B ise devops en sonda yedeği geri yükler | AC4 temiz açılışı ister; kullanıcı sonucu hemen deneyebilir | Sistemi kapalı bırakmak: kullanıcı ilk denemede beklemek zorunda kalır |

## Hata yönetimi ve NEEDS_FIX zinciri
**Hata kaydı.** Hata bulan rol (tester, devops, qa) kendi raporuna şu tabloyu yazar. Numaralar task boyunca tektir: tester B-01'den başlar; devops ve qa önceki raporlardaki en yüksek numaradan devam eder.

| ID | Belirti | Nasıl tekrarlanır (komut ya da adım) | Sorumlu katman ve rol | Önem (D9) | Yakalayan test | Durum |
|----|---------|---------------------------------------|-----------------------|-----------|----------------|-------|

- **Durum değerleri:** `AÇIK` → `DÜZELTİLDİ (tur N)` (düzelten rol yazar) → `DOĞRULANDI` (test yeşil; tester ya da qa yazar). Ya da `DÜZELTİLMEDİ: <gerekçe>` (D10).
- **Sorumlu rol:** `backend/src/main` → backend-coder; `frontend/src` (testler hariç) → frontend-coder; `deployment/**`, Dockerfile'lar, `frontend/nginx.conf`, `README.md`, `ollama.md` → devops; `docs/api/**` → planner. Test dosyasındaki hata tester'ın kendisidir ve zincire girmez.
- **Sözleşme mi kod mu:** Kod sözleşmeden sapıyorsa sorumlu coder'dır. Sözleşme yanlışsa (kodun davranışı doğru, sözleşme metni yanlış) sorumlu planner'dır. Emin olunamıyorsa planner'a sorulur.

**Zincir (tester Tur 1 hata bulursa):**
1. tester `03-test-report.md`'yi `STATUS: NEEDS_FIX` ile bitirir. `## Düzeltme` bölümünde bulguları sorumlu role göre gruplar; her madde: hata ID, kırmızı test adı, beklenen davranış (H tablosundaki "Beklenen" ya da tester'ın gözlemi). Sorumlu rollere ve lead'e mesaj atar.
2. Lead, adı geçen rolleri paralel başlatır:
   - backend-coder ve frontend-coder kendi `02-backend-implementation.md` / `02-frontend-implementation.md` dosyalarını yazar (bu task'ta ilk kez oluşur): her hata için değişen dosya, ne değişti, hangi kırmızı test hedefleniyor, çalıştırdığı komut ve sonucu. Kırmızı testi değiştirmez; test yanlışsa tester'a mesaj atar.
   - devops adı geçerse sadece o hatayı düzeltir ve `04-deploy-report.md`'ye yalnızca `## Düzeltme turu 1` bölümünü yazar (son satır `STATUS: DONE`). Lead bu dosyayı devops'un asıl adımından önce `STATUS: STALE` yapar.
   - planner sözleşmeyi düzeltir, bu plana `## Düzeltme turu N` ekler, etkilenen coder'lara bildirir.
3. Lead `03`'ü `STATUS: STALE` yapar, tester Tur 2'yi başlatır: bütün setler (T-ALL) yeşil, `03`'e `## Düzeltme turu 1` (her hata için `DOĞRULANDI` ve test adı, yeni sayılar). Tur 2'de yeni hata çıkarsa bu ikinci `NEEDS_FIX` olur; lead durur ve kullanıcıya sorar (kök kural).
4. devops asıl adımı, ardından qa.

**Sonraki adımlarda bulunan hata:** devops ya da qa kod hatası bulursa kendi raporunu `NEEDS_FIX` yapar, sorumlu coder düzeltir, zincir coder → tester (yakalayan testi tester yazar; önce kırmızı) → devops → qa olarak yeniden yürür. devops kendi dosyalarındaki (README, `ollama.md`, nginx, compose) hatayı kendisi düzeltir ve aynı raporda kaydeder; bunun için zincir dönmez, ama nginx değişikliği gibi davranış değiştiren her düzeltme bir E2E testiyle korunur (tester'a mesaj atar; bu durumda zincir tester → devops → qa).

**Karta etkisi:** Bir düzeltme kartı değiştiriyorsa (ör. kullanıcıya görünen yeni kural), planner kartı `ONAY: BEKLİYOR` yapar; lead kullanıcıya sorar.

## Sözleşme değişiklikleri
`docs/api/openapi.yaml` (bu planla birlikte yapıldı; `info.version` 0.5.0 kalır, D13):
1. `createSource` → `file` açıklaması: "PDF must start with `%PDF-`" yerine "PDF must contain `%PDF-` within its first 1024 bytes; text files must be UTF-8 (a BOM is allowed) and must not contain NUL bytes." Gerekçe: kod (`FileTypeDetector.java:76-84`) ve test (`FileTypeDetectorTest#givenPdfSignatureWithinFirst1024Bytes_whenDetect_thenAcceptedButBeyondIsRejected`) imzayı ilk 1024 baytta arıyor; bu bilinçli davranış, sözleşme metni yanlıştı.
2. `components.responses.UnsupportedFile` açıklaması: aynı düzeltme ("a `.pdf` without `%PDF-` in its first 1024 bytes").
3. `getWorkspace` örneği `oneDocument` ("After the sample document was ingested"): `chunkCount` 48 → 24. `WorkspaceResponse.chunkCount` ve `SourceResponse.chunkCount` örnekleri 48 → 24. Gerekçe: örnek belge 24 parça üretiyor (README, `ollama.md`).
4. `WorkspaceId`, `SourceId` parametreleri ve `getChunk.chunkId`, `getIngestionJob.jobId`: açıklama eklendi: "A value that is not a 64-bit integer (e.g. `abc`) answers 400 `BAD_REQUEST`; an unknown id answers 404." Sadece açıklama; operasyonlara yeni `400` response'u eklenmedi (framework hatası `problem-details-rfc9457`'de genel tanımlı). qa Q2'de bu satırı status kodu farkı saymaz. H6 bu davranışı doğrular; farklı çıkarsa hata backend'dedir (sözleşme ve skill aynı şeyi söylüyor).

`docs/api/websocket.md`: yok, oluşturulmaz.

## Backend planı
Planlı kod değişikliği yok; backend-coder sadece `## Düzeltme`'de adı geçerse çalışır. Yeni dependency yok.

- **Yüklenecek skill'ler (düzeltme turunda):** `backend-conventions`, `problem-details-rfc9457`, `systematic-debugging`; H1 için `java-concurrency` ve `spring-transactions`; H5 için `postgres-patterns`.
- **H1 çıkarsa önerilen yön:** `IngestionJobRunner` bu süreçte kuyruğa verdiği iş id'lerini bir eşzamanlı küme olarak tutar (`submit` ekler, `run` bitince çıkarır); `IngestionRecovery.recoverInterruptedJobs` bu kümedeki işleri atlar. Böylece kurtarmanın ne zaman çalıştığından bağımsız olur. Eşdeğer başka bir çözüm (kurtarmayı web sunucusu açılmadan önce çalıştırmak) da kabul; ölçüt BT4 ve mevcut kurtarma testleridir.
- **H5 çıkarsa önerilen yön:** `EmbeddingModelGuard.ensureDimensions` ayardaki boyutun yanında kolon boyutunu (`Chunk.EMBEDDING_DIMENSIONS`) da şart koşar; uymayan vektör `ConflictException("EMBEDDING_MODEL_MISMATCH", …)`. Açılışta `embedding.dimensions != 1024` ise `warnOnMismatch` gibi bir uyarı logu yazılır. `backend/CLAUDE.md`'ye "boyut 1024 dışına çıkamaz" notu.
- **H6 çıkarsa:** `GlobalExceptionHandler` tip uyuşmazlığını `BAD_REQUEST` olarak döner (yeni `@ExceptionHandler` gerekiyorsa tek metot; hata başına sınıf açılmaz).
- **Soru 2 = B ise:** planner önce sözleşmeye ret status'unu ve `errorCode`'u yazar ve bu planı günceller (bkz. "Açık sorular ve riskler"). backend-coder kuralı `FileTypeDetector.detect`'te uygular: metin türlerinde (`MARKDOWN`, `CSV`) çözülen metin sadece boşluksa ya da CSV'de başlık satırından başka dolu satır yoksa istek reddedilir, kaynak ve iş oluşmaz. PDF davranışı değişmez.
- Rapor: `handoff/02-backend-implementation.md`; `./mvnw verify` sonucu ve hedeflenen kırmızı testlerin yeşil koşusu.

## Frontend planı
- **Adım 0 (sadece soru 1 = A):** `frontend/package.json` `devDependencies`'e `ajv` (8.x), `ajv-formats` (3.x), `yaml` (2.x) eklenir; `npm install` ile `package-lock.json` güncellenir. Uygulama koduna dokunulmaz. `frontend/CLAUDE.md` "Teknoloji → Test paketleri" satırına üç paket ve "sadece E2E sözleşme kontrolünde" notu. `npm run build`, `npm run lint`, `npm test` geçer. Rapor `handoff/02-frontend-implementation.md` (`STATUS: DONE`); düzeltme turu gelirse aynı dosyaya `## Düzeltme turu N` eklenir.
- **H2 çıkarsa:** `shared/api/httpClient.ts` → `toApiError`: cevap 5xx ve gövdede geçerli `errorCode` yoksa `new ApiError(response.status, NETWORK_ERROR)`; diğer durumlar değişmez. `errorMessages.ts`'e yeni metin gerekmez (`NETWORK_ERROR` mevcut).
- **errorCode → mesaj:** Değişiklik yok. Testlerde beklenen metinler `errorMessages.ts`'teki mevcut metinlerdir.
- **Yüklenecek skill'ler (düzeltme turunda):** `frontend-conventions`, `react-testing`, `systematic-debugging`.

## Test senaryoları
**Başlangıç sayıları:** backend 417 test; frontend 27 dosya / 405 test; E2E 8 test. tester Tur 1'in ilk işi T-ALL'u değişiklik yapmadan çalıştırıp bu sayıları doğrulamaktır; farklıysa ölçülen sayı referans olur ve raporlanır.

**Ortak kurallar:** Backend testleri `backend-conventions` bölüm 5 (given_when_then, AAA, AssertJ, BDDMockito, Testcontainers pgvector imajı). Frontend `frontend-conventions` bölüm 11 (MSW, `problem(status, errorCode)`). E2E `test(...)` + AAA; sabit bekleme yok (`expect.poll`, `toBeVisible({ timeout })`); REST cevabı `page.request` ya da `page.waitForResponse` ile alınır ve D6'ya göre `expectMatchesSchema` ile doğrulanır; DB D5 ile okunur. Her E2E testi en az bir ekran, bir REST ve bir DB assertion'ı taşır (OD dahil).

### Backend (yeni)
| # | Senaryo | Katman | Test adı (given_when_then) | Beklenen sonuç (somut değerle) |
|---|---------|--------|----------------------------|--------------------------------|
| BT1 | Türkçe dosya adı (H4, G1) | integration `ingestion/IngestionIntegrationTest` | `givenTurkishFileName_whenCreateSource_thenFileNameKeptInJobAndSource` | `name` = "Çalışan İzin Notları", dosya `Çalışan İzin Notları.md` (ham UTF-8 `filename`) → 202, `fileName` "Çalışan İzin Notları.md"; iş `DONE` sonrası `GET` kaynak `name` "Çalışan İzin Notları", `fileName` aynı; DB `sources.file_name` ve `ingestion_jobs.file_name` aynı |
| BT2 | Aynı ad aynı anda (G2) | integration `source/SourceIntegrationTest` | `givenTwoParallelCreatesWithSameNameDifferentCase_whenSubmitted_thenOneAcceptedAndOneSourceNameTaken` | "İzin Notları" ve "izin notları" eşzamanlı (`CountDownLatch` ile aynı anda): status kümesi {202, 409}; 409 gövdesi `SOURCE_NAME_TAKEN`; DB `sources` 1, `ingestion_jobs` 1 |
| BT3 | Üç farklı yükleme aynı anda (K4) | integration `IngestionIntegrationTest` | `givenThreeParallelCreatesWithDifferentNames_whenAllFinished_thenThreeReadySourcesWithOwnChunks` | Üç `.md` (1, 2, 3 bölümlü) → üçü 202, farklı `sourceId`; üç iş `DONE`; her kaynağın `chunk_count` = kendi `chunks` satır sayısı (1, 2, 3); başka kaynağa yazılmış parça yok |
| BT4 | Kurtarma süren işi kapatmaz (H1) | integration `IngestionIntegrationTest` | `givenJobSubmittedInThisRun_whenRecoveryRuns_thenJobFinishesDone` | Sahte istemci EMBEDDING'de bekletilir; `ingestionRecovery.recoverInterruptedJobs()` çağrılır; bekletme bırakılır → iş `DONE`, `errorCode` null, kaynak `READY`. Mevcut `givenJobsLeftActive_whenRecoveryRuns…` yeşil kalır |
| BT5 | Framework hataları (H6) | integration `exception/GlobalExceptionHandlerIntegrationTest` (yeni sınıf) | `givenNonNumericOrOverflowingPathId_whenGet_thenReturns400BadRequestProblem` (parametreli) | `/api/workspaces/abc`, `/api/workspaces/42/sources/abc`, `/api/workspaces/42/sources/10001/chunks/abc`, `/api/workspaces/42/ingestion-jobs/abc`, `/api/workspaces/99999999999999999999` → 400, `Content-Type` `application/problem+json`, `errorCode` `BAD_REQUEST`, `type` `…/errors/bad-request`, gövdede `Exception` ve `at com.` yok. Mevcut controller testlerinde olmayan diğer framework durumları (bilinmeyen path 404 `NOT_FOUND`, `DELETE …/sources/10001` 405 `METHOD_NOT_ALLOWED`, `text/plain` arama 415 `UNSUPPORTED_MEDIA_TYPE`) aynı sınıfta ayrı testlerle |
| BT6 | Metinsiz dosya (H7, G4) | integration `IngestionIntegrationTest` | Soru 2 = A: `givenWhitespaceOnlyMarkdownOrHeaderOnlyCsv_whenCreateSource_thenJobFailedNoTextExtractedAndNoChunks` (parametreli). B: `givenWhitespaceOnlyMarkdownOrHeaderOnlyCsv_whenCreateSource_thenRejectedAndNoSourceCreated` | A: `"   \n\n  "` içerikli `bos.md` ve `ad,soyad\n` içerikli `baslik.csv` → 202; iş `FAILED`, `errorCode` `NO_TEXT_EXTRACTED`; kaynak `FAILED`, `chunk_count` 0; DB `chunks` 0. B: planner'ın sözleşmede belirleyeceği status ve `errorCode`; DB `sources` ve `ingestion_jobs` değişmez |
| BT7 | Kolondan farklı boyut (H5) | integration `search/EmbeddingDimensionsIntegrationTest` (yeni sınıf, `embedding.dimensions=768`, sahte istemci 768 boyut döner) | `givenModelReturns768Dimensions_whenVectorSearchAndUpload_thenModelMismatchNotInternalError` | VECTOR arama → 409 `EMBEDDING_MODEL_MISMATCH` (500 değil); `.md` yükleme → iş `FAILED` + `EMBEDDING_MODEL_MISMATCH` (`INTERNAL_ERROR` değil); DB `chunks` 0 |

### Frontend (yeni)
| # | Senaryo | Katman | Test adı | Beklenen sonuç |
|---|---------|--------|----------|----------------|
| FT1 | Ağ geçidi hataları (H2) | unit `shared/api/httpClient.test.ts` | `givenGatewayErrorWithoutProblemBody_whenRequest_thenNetworkErrorWithStatus` (`it.each` 500 boş gövde, 502 HTML, 504 HTML) ve `givenServerErrorWithProblemBody_whenRequest_thenKeepsErrorCode` (503 `EMBEDDING_UNAVAILABLE`, 500 `INTERNAL_ERROR`) | İlki: `ApiError.status` 500/502/504, `errorCode` `NETWORK_ERROR`. İkincisi: kodlar aynen. Mevcut `givenBodyWithoutErrorCode_whenRequest_thenUnexpectedError` 4xx için yeşil kalır |
| FT2 | Backend yeniden başlarken arama (H2, G3) | component `features/search/pages/SearchPage.test.tsx` | `givenGatewayDown_whenSearchSubmitted_thenShowsServerUnreachableMessage` | MSW `POST …/search` → 502, `text/html` → `role="alert"` metni "Sunucuya ulaşılamıyor. Bağlantını ve sistemin çalıştığını kontrol et." |

### E2E (yeni; `document-flow.spec.ts` sonu, bu sırayla, D7)
Ön durum: mevcut 8 test bitti; çalışma alanında 2 kaynak var (örnek PDF 10001, "Deneme notu").

| # | Senaryo | Test adı | Beklenen sonuç |
|---|---------|----------|----------------|
| ET1 | Örnek PDF üç katmanda (F2, F3, F5, F7, K2) | `givenReadySamplePdf_whenInspectApiAndDatabase_thenChunksHave1024DimEmbeddingsWithModelTag` | REST: `GET /api/workspaces/42` (`WorkspaceResponse`), `GET …/sources/10001` (`SourceResponse`; `status` READY, `latestJob.stage` DONE, `progress` 1, `embeddedChunks` = `totalChunks` = `chunkCount` = N), `GET …/chunks?size=100` (`PageResponseChunkResponse`; `totalElements` N, hepsi `embeddingModel` `bge-m3@1024`). DB: `select count(*), min(vector_dims(embedding)), max(vector_dims(embedding)), count(distinct embedding_model), max(chunk_no) from chunks where source_id = 10001` → N, 1024, 1024, 1, N-1; N ≥ 22; `tsv is null` satır 0; `workspaces.chunkCount` API değeri = `select count(*) from chunks where workspace_id = 42`. Index: `POST …/search` `{"query":"Yıllık izin kaç gün?","mode":"VECTOR","explain":true}` → `vectorPlan` `idx_chunks_embedding` içerir; `pg_indexes`'te `idx_chunks_tsv` var. Ekran: `/w/42/kaynaklar/10001` başlıkta "Atlaskap-Lojistik-Surec-El-Kitabi" |
| ET2 | Gerçek içe almada yenileme (F4, H8, S2) | `givenLargeMarkdown_whenReloadDuringEmbedding_thenProgressContinuesAndJobDone` | D11 belgesi, ad "Uzun belge" → `?kaynak=<id>`; ekranda "Embed ediliyor" görününce `progressbar` `aria-valuenow` = v1 ve DB `embedded_chunks` = e1; yenileme; `progressbar` görünür, değer ≥ v1; `expect.poll` ile DB `embedded_chunks` > e1; sonda "Belge hazır"; DB `embedded_chunks = total_chunks = chunk_count = count(*)`; REST iş `DONE` |
| ET3 | Yeni sürüm sürerken arama (F6, K4) | `givenVersionUploadRunning_whenSearch_thenOldChunksUntilDoneThenOnlyNew` | "Uzun belge"nin parça id'leri DB'den alınır (eski küme). `page.request` ile yeni sürüm (`version` "2", farklı ~500 bölümlü içerik, yine `zeytinyağı` içerir) → 202. İş EMBEDDING iken `POST …/search` `{"query":"zeytinyağı","mode":"HYBRID"}` → bu kaynağa ait bütün `chunkId`'ler eski kümede; DB'de eski parça sayısı değişmemiş. İş `DONE` sonra aynı arama → bu kaynağın `chunkId`'leri eski kümede değil; DB eski id'lerden 0 satır; `sources.version` "2"; iki iş satırı. Süren işe ikinci sürüm → 409 `INGESTION_IN_PROGRESS` (`ProblemDetail`) |
| ET4 | Üç yükleme aynı anda, gerçek sistem (F3, F5, K4) | `givenThreeFilesPostedAtOnce_whenAllFinished_thenThreeReadySourcesWithOwnChunks` | `Promise.all` ile üç `POST …/sources` ("Eşzamanlı 1", "Eşzamanlı 2", "Eşzamanlı 3"; küçük `.md`, `.txt`, `.csv`) → üçü 202, farklı `sourceId`; işler `DONE`; DB her kaynağın `chunk_count` = kendi satırı, hepsi `vector_dims` 1024; Kaynaklar ekranında üç ad görünür |
| ET5 | Anlam araması, kayıt, istatistik, "Sık aranan" (S3, F7-F10) | `givenVectorSearch_whenOpenSearchEventsPage_thenEventStatsAndPopularMatchDatabase` | Ekran: Gelişmiş ayarlardan "Anlam" seçilir, "Yıllık izin kaç gün?" → ilk 3 sonuçtan biri "Yıllık izin" içerir. DB en yeni `search_events`: `mode` VECTOR, `query` aynen, `query_key` `yillik izin kaç gün?` (`SearchEvent.toQueryKey`: `ı` → `i`, sonra küçük harf), `result_count` ≥ 1, `best_distance` ≤ 0.49, `keyword_count` null. "Sık aranan" listesinde soru var; REST `PopularQueriesResponse` aynı sırada. `/w/42/arama-kayitlari`: ilk satır bu soru; REST `PageResponseSearchEventResponse`; istatistik kutularındaki bugün / son 7 gün / sonuçsuz sayıları = REST `SearchEventStatsResponse` = DB (`created_at >= date_trunc('day', now() at time zone 'Europe/Istanbul') at time zone 'Europe/Istanbul'` ile sayım) |
| ET6 | Parça detayı ve eski parça (F11, F12, G5) | `givenSearchHitOpened_whenSourceGetsNewVersion_thenChunkNotFoundNoticeAndHeadingKept` | Hybrid "Yıllık izin kaç gün?" → ilk sonuçtan parça açılır → URL `/w/42/kaynaklar/10001?parca=<id>`, bildirim "Arama sonucundan açılan parça vurgulandı: #<id>"; "Daha fazla göster" bitene kadar → satır sayısı = DB `chunk_count`. "Yeni sürüm yükle" diyaloğu, örnek PDF, sürüm "2026.2" → hazır. Aynı adres yenilenir → "Bu parça artık yok; kaynak yeni bir sürüme geçmiş olabilir."; REST `GET …/chunks/<id>` 404 `CHUNK_NOT_FOUND` (`ProblemDetail`); DB `chunks` o id için 0, `sources.version` "2026.2"; arama kayıtlarında o aramanın `topChunkHeading` değeri hâlâ dolu |
| ET7 | Hata cevapları site üzerinden (K3, H3, H6, S5) | `givenInvalidRequestsThroughSite_whenSent_thenProblemDetailsAndNoDatabaseChange` | Tablo güdümlü, `page.request` ile 3000 üzerinden; aşağıdaki "ET7 tablosu". Her satır: status, `content-type` `application/problem+json` ile başlar, `errorCode` ve varsa `violations[].field`, `ProblemDetail` şeması, gövdede `Exception` / `at com.` yok. Tablonun önünde ve sonunda DB `sources`, `ingestion_jobs`, `chunks`, `search_events` sayıları aynı |
| ET8 | Ekranda doğrulama ve çakışma mesajları (K3, G1, H4) | `givenUploadsOnPage_whenNameTakenOrLimitReached_thenTurkishMessagesAndNoExtraSource` | (a) Ad boş, dosya `Çalışan İzin Notları.md` → hazır; Kaynaklar'da ad "Çalışan İzin Notları"; DB `name` ve `file_name` aynen. (b) Ad "DENEME NOTU" + `.md` → ad alanı altında "Bu ad çalışma alanında başka bir kaynakta kullanılıyor."; DB kaynak sayısı değişmez. (c) `page.request` ile kaynak sayısı 10'a tamamlanır (küçük `.md`'ler, `DONE` beklenir); ekrandan 11. yükleme → `role="alert"` "Bu çalışma alanı 10 kaynak sınırına ulaştı."; REST 409 `SOURCE_LIMIT_REACHED`; DB kaynak 10. (d) Arama kutusuna sadece boşluk → "Ara" basılınca `POST …/search` gönderilmez |

**ET7 tablosu** (yol önekleri `/api/workspaces`; `42` örnek çalışma alanı, `10001` örnek PDF, `<md>` ET2'deki "Uzun belge"):

| İstek | Beklenen |
|-------|----------|
| `POST /42/search` `{"query":"   "}` | 400 `VALIDATION_FAILED`, field `query` |
| `POST /42/search` 501 karakterlik `query` | 400 `VALIDATION_FAILED`, `query` |
| `POST /42/search` `limit` 0 ve 51 (iki satır) | 400 `VALIDATION_FAILED`, `limit` |
| `POST /42/search` `efSearch` 9 ve 1001 | 400 `VALIDATION_FAILED`, `efSearch` |
| `POST /42/search` `mode` `"FUZZY"` | 400 `VALIDATION_FAILED`, `mode` |
| `POST /42/search` `channel` 61 karakter | 400 `VALIDATION_FAILED`, `channel` |
| `POST /42/search` gövde `{` | 400 `BAD_REQUEST` |
| `POST /999/search` `{"query":"izin"}` | 404 `WORKSPACE_NOT_FOUND` |
| `GET /42/sources/999999` | 404 `SOURCE_NOT_FOUND` |
| `GET /42/sources/10001/chunks/999999999` | 404 `CHUNK_NOT_FOUND` |
| `GET /42/ingestion-jobs/999999` | 404 `INGESTION_JOB_NOT_FOUND` |
| `GET /42/sources/abc` | 400 `BAD_REQUEST` |
| `GET /42/sources?page=-1` ve `?size=101` | 400 `VALIDATION_FAILED`, `page` / `size` |
| `POST /42/sources` ad `" a "` + geçerli `.md` | 400 `VALIDATION_FAILED`, `name` |
| `POST /42/sources` ad 81 karakter | 400 `VALIDATION_FAILED`, `name` |
| `POST /42/sources` dosya parçası yok | 400 `VALIDATION_FAILED`, `file` |
| `POST /42/sources` 0 baytlık `bos.md` | 400 `VALIDATION_FAILED`, `file` |
| `POST /42/sources` `rapor.docx` | 415 `UNSUPPORTED_FILE_TYPE` |
| `POST /42/sources` `sahte.pdf` (içerik `merhaba`) | 415 `UNSUPPORTED_FILE_TYPE` |
| `POST /42/sources` `eski.txt` (Windows-1254 baytları, ör. `0xFD 0xF0`) | 415 `UNSUPPORTED_FILE_ENCODING` |
| `POST /42/sources/10001/versions` `version` 41 karakter | 400 `VALIDATION_FAILED`, `version` |
| `POST /42/sources/10001/versions` `veri.csv` | 409 `SOURCE_KIND_MISMATCH` |
| `POST /api/embeddings` `{"text":""}` ve 2001 karakter | 400 `VALIDATION_FAILED`, `text` |
| `POST /42/sources/10001/versions` 20 971 521 baytlık `%PDF-` ile başlayan dosya | 413 `FILE_TOO_LARGE` |
| `POST /42/sources/10001/versions` 22 MB dosya (H3) | 413 `FILE_TOO_LARGE`, `application/problem+json` |

Not: limit dolmadan önce çalışır (ET8'den önce); 413 satırları iş açmaz.

### E2E Ollama kapalı (`e2e/ollama-down.spec.ts`, D4, D7)
Ön durum: normal koşu bitti (10 kaynak); backend D4'teki override ile açık.

| # | Senaryo | Test adı | Beklenen sonuç |
|---|---------|----------|----------------|
| OD1 | Arama (S4) | `givenOllamaDown_whenHybridSearch_thenOllamaMessageAndKeywordSearchStillWorks` | Varsayılan modla "Yıllık izin kaç gün?" → `role="alert"` "Embedding servisine (Ollama) ulaşılamıyor. Ollama'nın çalıştığını kontrol et ya da Kelime yöntemiyle ara."; yakalanan cevap 503 `EMBEDDING_UNAVAILABLE` (`ProblemDetail`); DB `search_events` sayısı değişmez. Gelişmiş ayarlarda "Kelime" → "yıllık izin" → sonuç var, biri "Yıllık izin" içerir; DB +1 satır, `mode` KEYWORD |
| OD2 | Yükleme (S4, D8) | `givenOllamaDown_whenUploadNewVersionOfMarkdown_thenFailureReasonAndOldChunksKept` | Kaynaklar → "Deneme notu" → "Yeni sürüm yükle" → küçük `.md` → satırda ve detayda "Son yükleme başarısız: embedding servisine (Ollama) ulaşılamıyor"; REST `latestJob.stage` FAILED, `errorCode` `EMBEDDING_UNAVAILABLE`, kaynak `status` READY; DB kaynağın parça id'leri önceki ile aynı, `chunk_count` aynı |

### Sistem senaryoları (devops; komutlar ve çıktılar `04-deploy-report.md`'ye)
| # | Senaryo | Adımlar | Beklenen sonuç |
|---|---------|---------|----------------|
| P1 | `down` / `up` sonrası veri kalır (K5) | tester'ın bıraktığı sistemde DB sayıları (`sources`, `chunks`, `ingestion_jobs`, `search_events`, `sum(vector_dims(embedding))`) → `down` → `up -d` → 4 servis healthy | Bütün sayılar aynı; Kaynaklar ekranı aynı listeyi gösterir; HYBRID arama 200 |
| P2 | `down -v` sonrası temiz (K5, S1, AC4) | `down -v` → `up --build -d` → healthy süreleri | `workspaces` tek satır (42, "Belgelerim"); diğer dört tablo 0; `select count(*), max(version::int) from flyway_schema_history where success` → 10, 10; `vector` 0.8.1; `http://localhost:3000` → `/w/42/belge-yukle`, `/w/42/arama` "Henüz belge yüklenmedi" |
| P3 | Smoke (AC4) | `deployment/CLAUDE.md` "Smoke test" komutları | health `UP`; 3000 200; `…/sources` `content` boş; pgAdmin `PING` |
| C5a | İlk yüklemede backend yeniden başlatma (K4) | D11 belgesi (`python3` ile scratchpad'e) `curl -F` ile yüklenir → iş `EMBEDDING` olana kadar sorgulanır → `docker compose … restart backend` → healthy | İş `FAILED`, `INGESTION_INTERRUPTED`; kaynak `FAILED`, `chunk_count` 0; DB o kaynağın parçası 0; açık Kaynaklar sayfasında yeniden başlatma sırasında "Sunucuya ulaşılamıyor…" (H2 düzeldiyse), sonra "Son yükleme başarısız: iş yarıda kaldı (sunucu iş sürerken durdu); dosyayı tekrar yükle"; aynı kaynağa yeni sürüm → `DONE` |
| C5b | Sürüm yüklemede backend yeniden başlatma (K4) | Küçük `.md` ile READY kaynak ("Kalıcılık notu"), parça id'leri not edilir → D11 belgesi yeni sürüm → `EMBEDDING` → restart | İş `FAILED` `INGESTION_INTERRUPTED`; kaynak `READY`, `version` ve parça id'leri değişmemiş; arama eski parçayı bulur; yarım yeni parça yok |
| R1 | README "Gereksinimler", "Tam sistem", "Ollama kapalıysa", "Güvenlik notu" | Yazıldığı sırayla | Her komut yazılan sonucu verir; H5 için "boyut 1024 dışına çıkamaz" notu eklenir |
| R2 | README "Örnek belgeyle dene" (K6) | P2 sonrası temiz sistemde 1-6. adımlar ekranda | Yaklaşık 24 parça; tablodaki üç soru varsayılan modda ilgili bölümü ilk 3'te getirir; "kuantum fiziği" sonuç verir ama "Sık aranan"a girmez |
| R3 | README "Local geliştirme" (K6) | Tam sistem `down` → dev compose `up -d` (2 healthy) → `./mvnw spring-boot:run` → `npm ci && npm run dev` → 5173'te örnek belge yükle + ara → `E2E_COMPOSE_FILE=../deployment/docker-compose.dev.yml E2E_BASE_URL=http://localhost:5173 npm run test:e2e` → dev `down` | Hepsi çalışır; dev E2E `document-flow` testleri geçer |
| R4 | `ollama.md` bölüm 4, 5.1-5.6, 6 (K6) | Temiz tam sistemde sırayla | Her çıktı belgedekine uyar (sayılar, aşamalar, hata gövdeleri). Uymayan yer: belge yanlışsa devops düzeltir; kod yanlışsa `NEEDS_FIX` |
| R5 | README "pgAdmin" | Giriş, sunucu, sorgular | Beş tablo; `select count(*) from chunks` → örnek belgeden sonra ~24; `vector_dims` 1024 |
| R6 | Ollama kapalı E2E komutu belgelenir | `deployment/CLAUDE.md` "Komutlar"a D4'teki override yöntemi ve `E2E_OLLAMA_DOWN=1 npx playwright test e2e/ollama-down.spec.ts` | Satır eklendi; komut bir kez çalıştırılıp OD1-OD2 geçti |

Son adım: `down -v` → `up --build -d` → P3 (D15). Soru 3 = B ise devops işe başlarken `pg_dump` ile yedek alır (scratchpad), en sonda geri yükler ve sayıları raporlar.

### qa
| # | Kontrol | Beklenen |
|---|---------|----------|
| Q1 | Akış matrisi F1-F12'yi çalışan sistemde üç katmanda kendisi tekrarlar (ekran: tarayıcı, `webapp-testing`; REST: `curl` 3000 üzerinden; DB: `psql`) | Her satır için yöntem ve sonuç; tester'ın sonucuyla çelişki yok |
| Q2 | `curl -s localhost:8080/v3/api-docs.yaml` ile `docs/api/openapi.yaml`'ı path, method, status, `errorCode`, şema alanları bazında karşılaştırır | Fark yok (açıklama metinleri ve "Sözleşme değişiklikleri" madde 4 hariç) |
| Q3 | Hata tabloları (`03`, `04`, `05`): her hata `DOĞRULANDI` ya da gerekçeli `DÜZELTİLMEDİ`; her `DOĞRULANDI` için yakalayan test suite'te ve adıyla raporda; H1-H8'in hepsinin sonucu yazılı | Eksik yok |
| Q4 | T-ALL'u kendisi yeniden çalıştırır | Hepsi geçer; sayılar tester Tur 2 (ya da Tur 1) ile aynı |
| Q5 | Son durumda sistem temiz kurulumla açık (D15); P3 | Smoke geçer |
| Q6 | D14 kopyasına karşı diff: `diff -ru scratch/TASK-008-baseline/backend/src backend/src`, aynı şekilde `frontend/src`, `frontend/e2e`, `deployment`, `README.md`, `ollama.md`, `frontend/nginx.conf`, `frontend/package.json` | Değişiklikler sadece hata düzeltmeleri, yeni testler, D6 paketleri ve doküman düzeltmeleri; her değişiklik bir B-xx'e ya da test senaryosuna bağlanır; kurallar (`backend-conventions`, `frontend-conventions`) ihlal edilmemiş |

### Komut listesi (T-ALL)
- `cd backend && ./mvnw verify` → BUILD SUCCESS, surefire özeti, JaCoCo "All coverage checks have been met."
- `cd frontend && npm ci && npm run build && npm run lint && npm test && npm run test:coverage` → hepsi hatasız; dosya / test sayısı; satır coverage ≥ %80.
- Tam sistem açıkken: `cd frontend && E2E_BASE_URL=http://localhost:3000 npm run test:e2e` → `document-flow` 16 test geçer, `ollama-down` 2 test atlanır.
- D4 override ile: `cd frontend && E2E_OLLAMA_DOWN=1 E2E_BASE_URL=http://localhost:3000 npx playwright test e2e/ollama-down.spec.ts` → 2 geçer; sonra backend geri alınır ve bir HYBRID arama `curl` ile 200 döner.

## Deployment planı
Yeni servis, port ya da ortam değişkeni yok. Olası değişiklikler: H3 çıkarsa `frontend/nginx.conf` (413 için Problem Details gövdesi), H5 için README ve `deployment/CLAUDE.md` notu, R6 komut satırı, R4'te bulunan doküman düzeltmeleri. Ollama kapalı simülasyonu geçici override dosyasıyla yapılır; repoya girmez.

## Açık sorular ve riskler
- **Kullanıcıya 3 soru** (kartta): (1) sözleşme doğrulaması için üç test paketi, (2) metinsiz dosyanın sonucu, (3) mevcut verinin silinmesi. Soru 2 = B ise planner, kart onayından sonra ve tester başlamadan önce sözleşmeye yeni ret kuralını (status, `errorCode`) ve frontend mesajını ekler; bu planı günceller.
- **Ön koşul:** Docker Desktop ve Ollama (`bge-m3` indirilmiş) açık olmalı. Değilse rol durur, lead'e bildirir; kendisi kurmaya çalışmaz.
- **Risk: süreye bağlı E2E testleri.** ET2, ET3 ve C5 EMBEDDING aşamasını yakalamaya dayanır. Makine çok hızlıysa aşama kaçabilir; bu durumda tester D11'deki bölüm sayısını artırır (en fazla ~2000; dosya yine 20 MB'ın çok altında) ve sayıyı raporlar. Sabit bekleme kullanılmaz.
- **Risk: veri silinir.** E2E her koşuda çalışma alanı 42'yi boşaltır; P2 ve son adım `down -v` yapar; R3 dev ortamındaki veriyi de E2E ile siler (soru 3).
- **Risk: ikinci NEEDS_FIX.** tester Tur 2'de yeni hata çıkarsa lead durur (kök kural). D2 bunu azaltır.
- **Kapsam dışı not (lead/kullanıcı):** Kaynak silme özelliği yok; başarısız kaynaklar 10 sınırında yer tutar ve sadece "Yeniden dene" ile düzeltilebilir. Gerekirse ayrı task.
- **Kapsam dışı not:** PDF parça başlıkları her sayfanın ilk satırı (örnek belgede şirket başlığı). Arama sonuçlarında bölüm adı görünmez ve bu kelimeler her parçanın kelime dizininde yer alır. Hata değil, kalite konusu; ayrı task olabilir.
- **Kapsam dışı not (skill metinleri):** `.claude/skills/problem-details-rfc9457/SKILL.md` tablosu "arama metni 2-500" diyor; sözleşme, backend ve frontend 1-500. `frontend-conventions` bölüm 10 örneği `QUERY_MIN_LENGTH = 2` gösteriyor. `.claude/**` hiçbir rolün değil; lead kullanıcıya sorup düzeltir.

STATUS: DONE
