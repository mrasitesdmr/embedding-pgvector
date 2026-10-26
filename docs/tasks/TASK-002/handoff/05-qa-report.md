TASK: TASK-002
# QA raporu

Her şey kendi çalıştırmalarımla doğrulandı: çalışan Docker sistemi (postgres + backend, healthy, localhost:8080, gerçek lokal Ollama bge-m3), `./mvnw verify` ve PIT. Raporlardaki sonuçlara güvenilmedi.

## Kullanılan skill'ler
`code-review-and-quality`, `java-code-review`. Backend değiştiği için `springboot-verification` kontrol listesi (build, test, coverage, log/secret taraması) elle uygulandı.

## Kural kontrolü
Komut sütunundaki sayılar benim çalıştırmalarımdır. Kod kanıtı = sınıf, test kanıtı = tester'ın test adı (`verify`: 345 test geçti).

| # | Kural / Kabul kriteri | Kanıt (kod + test + kendi denemem) | Sonuç |
|---|---|---|---|
| K1 | Çalışma alanı izolasyonu | Her SQL `workspace_id = :workspaceId`. Test: `SearchIntegrationTest#givenTwoWorkspacesWithSimilarChunks`. Denedim: Kıyı Net (19) `Şifremi unuttum` limit=50: keyword `[]`, vector ve hybrid kaynakları sadece `[1191,1192,1193]` | ✅ |
| K2 | Alan listesi/tekil | `GET /api/workspaces` → 37 alan, toplam 1.410 parça, 112 kaynak. `/workspaces/42` → 4 kaynak, 42 parça | ✅ |
| K3 | 404 ayrı errorCode | `/workspaces/999` `WORKSPACE_NOT_FOUND`; `/19/sources/301` `SOURCE_NOT_FOUND`; `/19/ingestion-jobs/1` `INGESTION_JOB_NOT_FOUND`; `/42/sources/9999/chunks` `SOURCE_NOT_FOUND`. Hepsi 404 `application/problem+json` | ✅ |
| K4 | Kaynak listesi alanları | `SourceResponse`. 42/sources: ad, tür, sürüm, `chunkCount`, `status`, `syncedAt`, `fileName` dönüyor | ✅ |
| K5 | Dosya türleri, uzantı+içerik | `FileTypeDetector` + `FileTypeDetectorTest`. Denedim: `.docx` 415 `UNSUPPORTED_FILE_TYPE`; `.pdf` uzantılı düz metin 415; NUL baytlı `.csv` 415; Windows-1254 `.txt` 415 `UNSUPPORTED_FILE_ENCODING` | ✅ |
| K6 | 20 MB / boş dosya | 25 MB PDF → 413 `FILE_TOO_LARGE` (`title="Content Too Large"`); 0 bayt → 400 `VALIDATION_FAILED` `field=file` | ✅ |
| K7 | 202 + iş no + adres | Yeni sürüm → `HTTP/1.1 202`, `Location: /api/workspaces/42/ingestion-jobs/1`, gövdede `jobId`, `statusUrl`. Yeni kaynak (md, csv) de 202 | ✅ |
| K8 | İş bitmeden 2. yükleme 409 | İş `QUEUED` iken ikinci yükleme → 409 `INGESTION_IN_PROGRESS`. DB garantisi: `uk_ingestion_jobs_source_id_active` kısmi unique index (`WHERE stage <> ALL('DONE','FAILED')`, `\d ingestion_jobs` ile gördüm) + `PESSIMISTIC_WRITE` kaynak kilidi. Test: `givenTwoParallelUploadsToSameSource` | ✅ |
| K9 | Aşamalar, ilerleme, zaman | İş durumunda `stage/progress/embeddedChunks/totalChunks/startedAt/finishedAt/errorCode`. Dosya küçük olduğu için (~360 ms) `PARSING…EMBEDDING` anlık görülmedi; log'da `Ingestion stage changed … stage=PARSING` görünüyor (kartın "Kısa aşamalar" kararı) | ✅ |
| K10 | Parçalama | `PdfDocumentParser`, `MarkdownDocumentParser`, `CsvDocumentParser`, `TextChunker` + testleri. Denedim: v3.2 PDF → 12 parça, başlık `s. 6 · Wi-Fi 6 ve kanal genişliği (yeni)`; CSV 5 parça, başlık `TS-104`, içerik `Kod: …\nAçıklama: …\nÇözüm: …`, `tokenCount` 31, `chunkNo` 0'dan | ✅ |
| K11 | 96'lık toplu embed | `IngestionJobRunnerTest#given214Chunks_…` (214 → 96/96/22). Gerçek 96+ parçalı dosya yüklemedim (örnek dosyalar 12 parçaya kadar) | ✅ (testle) |
| K12 | Tek transaction, commit'e kadar eski parçalar | `IngestionJobLifecycleService.completeJob` tek `@Transactional`: kilit → sil → ekle → `completeIngestion` → `job.complete`. Denedim: iş sürerken 20 ms aralıkla parça id'lerini yoklayan script: yalnızca iki durum görüldü, `{1452..1463}` → `{1464..1475}` (ara durum yok, 12+12 karışımı yok). Log: `replacedChunks=5 newChunks=12` | ✅ |
| K13 | Model etiketi | Tüm 1.417 parça `bge-m3@1024`, `vector_dims=1024` (psql) | ✅ |
| K14 | Ollama yok / metin yok | Backend'i `OLLAMA_BASE_URL=http://127.0.0.1:9` ile ayrı container'da (8081) çalıştırdım: 302'ye yükleme → iş `FAILED`, `errorCode=EMBEDDING_UNAVAILABLE`, `totalChunks=12`; kaynak 302 `READY`, eski sürümüyle (`3.4`, 12 parça), keyword arama eski parçaları buluyor; vector arama 503 `EMBEDDING_UNAVAILABLE`. Yeni kaynak ilk iş başarısız → `FAILED`, `chunkCount=0`. FAILED kaynağa sonra geçerli dosya → `READY`. Backend çökmedi. `NO_TEXT_EXTRACTED`: `IngestionIntegrationTest#givenPdfWithoutText` (kendim denemedim) | ✅ |
| K15 | Parça listesi | `/sources/302/chunks?size=100` → `chunkNo` sırasıyla, `embeddingModel`, `tokenCount`, sayfalı | ✅ |
| K16 | Arama parametreleri | Varsayılanlar `search.default-*` yapılandırmasından. `{"query":"  ","limit":51,"efSearch":5,"mode":"x"}` → 400 `VALIDATION_FAILED` + `violations[]` (efSearch, query …) | ✅ |
| K17 | Keyword FTS | `plainto_tsquery('turkish')`, `ts_rank_cd`, GIN `idx_chunks_tsv`; keyword'de `embeddingMs=null` (embed yok) | ✅ |
| K18 | Vektör kolu, `SET LOCAL` | `set_config(...,true)` yalnız `@Transactional(readOnly)` içinde. Test: `givenSearchWithEf100`. Gerçek veride aşağıdaki sayılar | ✅ |
| K19 | Hybrid RRF | tek SQL, `FULL OUTER JOIN`; keyword boşken sonuç vektörle aynı (aşağıda) | ✅ |
| K20 | Yanıt içeriği, plan | Genel + süreler + hit alanları + `matchedTerms` + `vectorPlan` (EXPLAIN ANALYZE, BUFFERS) hepsi geliyor | ✅ |
| K21 | Model koruması | `EMBEDDING_MODEL=embeddinggemma` ile ayrı container: açılış WARN `Embedding model mismatch configuredModel=embeddinggemma@1024 storedModel=bge-m3@1024 workspaceCount=37`, uygulama açıldı. keyword 200; vector ve hybrid 409 `EMBEDDING_MODEL_MISMATCH`; yeni sürüm ve yeni kaynak yükleme 409 `EMBEDDING_MODEL_MISMATCH` | ✅ |
| K22 | Başlangıç verisi | 37 alan, 112 kaynak, 1.410 parça; Dalga 42 (%2,98); 301-304; Dalga id 42; deploy sonrası restart: `loadedSources=0 skippedSources=112` (devops çıktısı, log'da duruyor). Ollama kapalıyken seed: `SeedDataLoaderIntegrationTest` (kendim denemedim) | ✅ |
| K23 | 3 önerilen arama | Aşağıda gerçek sayılarla | ✅ |
| K24 | Arama kayıtları | `GET /42/search-events` yeniden eskiye: `resultCount`, `topChunkId`, `topChunkHeading`, `totalMs`, `channel`. Sonuçsuz keyword araması kaydı `resultCount=0` ile listede. Hata dönen (409) aramalar kaydedilmiyor (kartın kararı) | ✅ |
| K25 | Kayıt yazılamazsa arama döner | `SearchServiceTest` (`DataAccessException` stub'ı). Kendim denemedim | ✅ (testle) |
| K26 | Flyway, vector, index'ler | psql: `vector 0.8.1`, `vector(1024)`, `idx_chunks_embedding hnsw (vector_cosine_ops) m=16 ef_construction=64`, `idx_chunks_tsv gin`, `idx_chunks_workspace_id btree`, `tsv` generated always stored | ✅ |
| K27 | RFC 9457 + errorCode, içerik sızmıyor | Denenen tüm hatalar `application/problem+json`, `type/title/status/detail/instance/errorCode`. `detail` Ollama/PG/dosya içeriği taşımıyor | ✅ |
| K28 | Loglar | Container log'unda `Ingestion job started/stage/batch/finished`, `Search completed`, `Short vector branch` WARN, `Search returned no results` WARN, mismatch WARN var. Arama metni ve vektör için grep (`Taşınıyorum`, `Şifremi`, `[-0.0`) → 0 sonuç; sadece `queryLength` | ✅ |
| K29 | `POST /api/embeddings` | 200, `bge-m3`, 1024, 1024 elemanlı vektör, 23 ms | ✅ |
| K30 | Docker | `ps`: postgres ve backend `Up (healthy)`. `.env` yokken compose başlamıyor (`required variable POSTGRES_PASSWORD is missing`, deploy raporu; `:?` ifadesini compose dosyasında gördüm). Container kullanıcısı `USER app` (Dockerfile; deploy raporu uid=999) | ✅ |
| K31 | `ollama.md` | Aşağıda; `channel` anlatımı yanlış | ❌ |

### Onay kartı "Bittiğini nasıl anlayacaksın" (13 madde) — gerçek sistemde

| # | Komut / senaryo | Beklenen | Gerçek | Sonuç |
|---|---|---|---|---|
| 1 | `compose ps`; `GET /workspaces/42/sources` | iki servis healthy, Dalga 4 kaynak READY | ikisi `Up (healthy)`; 301 (21), 302 (5), 303 (1), 304 (15), hepsi `READY`; toplam 42 parça | ✅ |
| 2 | `POST /42/search` `Şifremi unuttum, online işlemlere giremiyorum` `keyword` | 0 sonuç | `returnedCount=0 keywordCount=0` | ✅ |
| 2b | Aynısı `hybrid` | "Online İşlemler parolanı sıfırlama" ilk 10'da, semantic | 1. sıra `Online İşlemler parolanı sıfırlama` (chunk 1369), `matchedBy=[SEMANTIC]`, `vectorRank=1`, `distance=0,3431`, `rrfScore=0,01639`; `keywordCount=0 vectorCount=10` | ✅ |
| 3 | `TS-999 hata kodu` `hybrid` | TS-999 parçaları kelime kolunda da bulunduğu için önde | İlk 4 sonuç `[KEYWORD,SEMANTIC]`: 1403 (kw 1, v 1, rrf 0,03279), 1373 (kw 3, v 2, 0,032), 1395 (kw 2, v 3, 0,032), 1374 (kw 4, v 4, 0,03125). 5-10. sıra sadece `SEMANTIC` (rrf ≤ 0,01538). `keywordCount=4`, `matchedTerms` dolu | ✅ |
| 4a | `Taşınıyorum, internetimi yeni eve nasıl aldırırım?` `vector`, `post_filter`, `efSearch=40` | 10'dan az, `vectorShort`, WARN | `returnedCount=4 vectorCount=4 vectorShort=true`; log: `WARN Short vector branch workspaceId=42 requested=10 returned=4 efSearch=40 filterStrategy=POST_FILTER`. `hybrid`+`post_filter` de 4 (`keywordCount=0`) | ✅ |
| 4b | Aynısı `iterative_scan` | 10 | `vectorCount=10 vectorShort=false` | ✅ |
| 4c | `explain=true` | HNSW index taraması + filtreyle elenen satır | `Index Scan using idx_chunks_embedding on chunks`, `Filter: (workspace_id = '42'::bigint)`, `Rows Removed by Filter: 36`, `rows=4` (40 aday, 4 kaldı) | ✅ |
| 5 | v3.2 PDF'i 302'ye | 202; iş DONE; kaynak 3.2 + yeni sayı; yeni parçalar aramada, eskiler yok | 202 + `Location`; iş 1 `DONE 12/12`; 302 `3.2`, 12 parça, `READY`; eski id'lerden (<1411) 302'de kalan 0 (psql); `Wi-Fi 6 kanal genişliği 80 MHz` hybrid 1. sıra `s. 6 · Wi-Fi 6 ve kanal genişliği (yeni)` `[KEYWORD,SEMANTIC]` | ✅ |
| 6 | İş sürerken ikinci yükleme | 409 | 409 `INGESTION_IN_PROGRESS` | ✅ |
| 7 | Ollama'sız backend ile yükleme | iş FAILED, kaynak eski sürümle READY, eski parçalar aranır | K14'teki gibi | ✅ |
| 8 | `.docx`, 25 MB | 415, 413 | 415 `UNSUPPORTED_FILE_TYPE`, 413 `FILE_TOO_LARGE` | ✅ |
| 9 | `EMBEDDING_MODEL=embeddinggemma` | açılışta WARN; vector/hybrid hata; keyword çalışır | K21'deki gibi | ✅ |
| 10 | Kıyı Net `Şifremi unuttum` | sadece Kıyı Net | K1: sadece 1191-1193 | ✅ |
| 11 | Arama kayıtları | sonuç sayısı + ilk sonuç başlığı; sonuçsuz 0 | `resultCount`, `topChunkHeading` var; sonuçsuz kayıt 0 | ✅ |
| 12 | Testler gerçek DB + Ollama'sız | geçer | `verify` 345 test geçti (Testcontainers); tester `OLLAMA_BASE_URL=http://127.0.0.1:9` ile de koştu | ✅ |
| 13 | `ollama.md` komutları sırayla | anlatılan sonuçlar | Bölüm 5.1-5.5 ve 6 çalıştırıldı. Sayılar/çıktılar tutuyor, **`channel` anlatımı yanlış** (Bulgu 1) | ❌ |

TASK örnek senaryolarının tamamı yukarıda. "Sonuçsuz arama kaydı 0" ve "ilk sonuç başlığı" `search-events` çıktısında görüldü.

## Sözleşme uyumu
- Çalışan backend'in `/v3/api-docs.yaml` çıktısı ile `docs/api/openapi.yaml` `$ref`'ler çözülerek karşılaştırıldı (11 operation): path, method, `operationId`, parametreler (ad/yer/required), request body content-type, response status ve content-type, şema adları, `properties`, `required`, tipler → **fark yok**; `info.version` ikisinde de 0.2.0.
- Tek fark gösterim biçimi: sözleşmede `errorCode`/`filterStrategy` enum'larına `null` da yazılı (OpenAPI 3.1 nullable enum), springdoc `null`'ı enum'a yazmıyor. İstemci davranışı aynı; **FYI**.
- Denenen hata durumları: 400 (`VALIDATION_FAILED` + `violations`), 404 ×3 kod, 409 (`INGESTION_IN_PROGRESS`, `SOURCE_KIND_MISMATCH`, `SOURCE_NAME_TAKEN`, `EMBEDDING_MODEL_MISMATCH`), 413, 415 (`UNSUPPORTED_FILE_TYPE`, `UNSUPPORTED_FILE_ENCODING`, `UNSUPPORTED_MEDIA_TYPE`), 503 (`EMBEDDING_UNAVAILABLE`). Hepsi RFC 9457, `errorCode` sözleşmedeki ile aynı.

## Komutlar (kendi çalıştırdıklarım)
| Komut | Sonuç | Raporla tutarlı mı |
|---|---|---|
| `cd backend && ./mvnw verify` | BUILD SUCCESS, **345 test, 0 hata, 0 atlanan**, JaCoCo check geçti | ✅ tester: 345 |
| `docker compose -f deployment/docker-compose.yml ps` | postgres ve backend `Up (healthy)` | ✅ |
| `curl localhost:8080/actuator/health` | `{"status":"UP",...}` | ✅ |
| PIT (aşağıda) | 233 mutant, 151 öldürüldü | tester'dan farklı kapsam |
| `npm` komutları | Frontend yok (planda atlandı) | — |

## Mutation testi
Araç PIT 1.30.1. Tester'ın tam koşusu zaman aşımına düşmüştü; ben entegrasyon testlerini hariç tutarak (`-DexcludedTestClasses='*IntegrationTest'`) bu sınıflarla çalıştırdım: `search.service.*`, `ingestion.service.*`, `ingestion.chunking.*`. Süre 28 sn.
- 233 mutant, **151 öldürüldü (%65)**, 65'i "kapsama yok" (`IngestionService` 30, `IngestionJobLifecycleService` 17, `ChunkRetrievalService` 16: bu sınıfların testleri entegrasyon testi, bu koşuda dışarıda), **test gücü %90** (kapsanan mutantlar içinde 151/168).
- Sınıf bazında: `SearchService` 41/41, `ChunkDraft` 3/3, `IngestionRecovery` 1/1, `IngestionJobRunner` 36/40 (+1 kapsamsız), `KeywordHighlighter` 12/13, `VectorLiteral` 6/7, `TextChunker` 50/65 (12 hayatta, 2 zaman aşımı).
- Hayatta kalanlar: `TextChunker` iç sınırları (`hardSplitIndex` 106/107/111 negated conditional, `assemble` 129-130, `overlap` 145/148, `units` 73, `whitespaceSeparator` 115, `length` 161), `IngestionJobRunner` 121/140 (süre ve ortalama token: yalnız log), `KeywordHighlighter` 32, `VectorLiteral` 9 (StringBuilder kapasitesi, denk mutant). `hardSplitIndex` 3 mutantı tek karakter kenar durumu; `writing-good-tests` açısından küçük bir boşluk (Optional).
- Entegrasyon testlerine bağlı 3 servis için asıl kanıt `verify` içindeki entegrasyon testleri ve benim gerçek sistem denemelerim (K8, K12, K14).
- Eşik tanımlı değil.

## Uçtan uca
Frontend olmadığı için Playwright yok. Uçtan uca = yukarıdaki curl senaryoları, gerçek Ollama bge-m3 ve gerçek PostgreSQL ile. Ayrıca ek olarak denenenler:
- Aynı kaynağa v3.2'yi tekrar tekrar yüklerken parça id'si izlemesiyle tek-transaction doğrulaması (K12).
- Ayrı container'larda (port 8081, aynı compose ağı, aynı DB) `OLLAMA_BASE_URL=http://127.0.0.1:9` ve `EMBEDDING_MODEL=embeddinggemma` ile iki farklı backend. Compose dosyasına ve çalışan `deployment-backend-1`'e dokunulmadı; iki geçici container (`qa-be`) silindi.
- FAILED yeni kaynağa (10002) sonradan geçerli dosya yüklendi → `READY` (3 parça); CSV ile yeni kaynak (10003) → `READY` (5 parça).

### Sistemin son hali
- `deployment-backend-1` ve `deployment-postgres-1` açık, orijinal env, healthy. Geçici `qa-be` container'ı silindi.
- Test için oluşturduğum kaynaklar `QaYeni` (10002) ve `QaCsv` (10003) ve onların parçaları/işleri veritabanından **silindi** (rehberdeki sayılar bozulmasın diye; 112 kaynak, 1.417 parça).
- **Kalan değişiklikler:** kaynak 302 artık sürüm `3.4`, 12 parça (seed'deki 3.1/5 parça değil); 302'ye 4 iş (`ingestion_jobs` 1-4: 2 `DONE`, 2 `FAILED`) ve 8 arama kaydı. Seed'e dönmek için `docker compose -f deployment/docker-compose.yml down -v` + `up --build -d` gerekir (rehber bölüm 5.1 bunu söylüyor).

## Kod review bulguları
| Severity | Dosya:satır | Bulgu | Sorumlu rol |
|---|---|---|---|
| Required | `ollama.md` bölüm 5.4 (`channel` açıklaması, `Q=` örneği ve "Aynı vektör aramasını sadece 'Yardım merkezi' kaynağıyla (`channel`) sınırla" cümlesi) | Rehber `channel`'ı "kaynak adı filtresi" olarak anlatıyor. Sözleşme ve kod (TASK Kural 16) `channel`'ı sadece arama kaydına yazılan etiket olarak tanımlıyor; sonuçları **sınırlamıyor**. Denedim: `"channel":"Yardım merkezi"` ile `iterative_scan` sonuçlarında 4 farklı kaynak çıkıyor (Yardım merkezi, Dalga T5 kılavuzu, Çözülmüş destek talepleri, …). `post_filter` ile görülen "4 sonuç" filtre kaynağından değil, çalışma alanı filtresinden (`workspace_id = 42`, 40 adaydan 4'ü) geliyor. Kullanıcı yanlış neden öğrenir. Aynı yanlış anlatım `04-deploy-report.md` smoke test satırında ("`filterStrategy` denemesi (`channel=Yardım merkezi`)") ve `Rows Removed by Filter` yorumunda da var | devops |
| Optional | `TextChunker` (satır 73, 106-111, 115, 129-130, 145-148, 161) | PIT'te 12 hayatta kalan mutant, çoğu `hardSplitIndex` ve örtüşme iç sınırları. Gözlemlenebilir davranış testlerle kapalı; sert bölme (boşluksuz uzun metin) için tek karakterlik sınır testi eklenebilir | tester |
| Optional | `IngestionService.createSource` / `Workspace.addSource` | Ad tekilliği `toLowerCase(Locale.ROOT)` ile entity'de, `lower(name)` ile DB'de. Türkçe `İ` içeren ad (`yardım MERKEZİ`) entity kontrolünü geçip DB unique index'inde patlıyor; sonuç yine doğru 409 `SOURCE_NAME_TAKEN`, ama Hibernate `org.hibernate.orm.jdbc.error` ERROR logu iki satır yazıyor ve bağlı parametreler arasında kaynak adı da var. Arama metni/parça içeriği sızmıyor, kural ihlali değil; log gürültüsü | backend-coder |
| FYI | `/swagger-ui.html`, `/v3/api-docs` | Springdoc "production'da kapat" uyarısı veriyor; bu task'ta kimlik doğrulama kapsam dışı, Swagger UI kartta isteniyor | — |
| FYI | `openapi.yaml` ↔ springdoc | Nullable enum'larda `null` gösterimi farkı (yukarıda) | planner (sözleşme) |
| FYI | `04-deploy-report.md`, `ollama.md` 5.4 | Rehberdeki "dönen=4/10" sayıları ve `Rows Removed by Filter: 35` temiz seed için; ben 302 değişmiş (12 parça) sistemde 36 gördüm. Rehberde "sayılar değişir" notu zaten var | — |

### Gözlenen iyi uygulamalar
- Veri erişimi tamamen JPA: `grep -rniE "JdbcClient|JdbcTemplate|starter-jdbc|jdbc\.core|DuplicateKey" pom.xml src/main` → sonuç yok. Entity'ler setter'sız, ilişkiler LAZY, `ddl-auto=validate`, `open-in-view=false`.
- Tek transaction (`completeJob`), 409'un DB seviyesinde garantisi (kısmi unique index + kilit), `set_config(..., true)` ile transaction'a yerel ayarlar.
- Native sorgular `:param` ile bağlı, string birleştirmesi yalnız sabit SQL parçalarında; kullanıcı girdisi SQL'e girmiyor (sorgu vektörü `CAST(:queryEmbedding AS vector)` parametresi).
- Secret'lar: `Dockerfile`'da şifre yok, `docker history` ve imaj katmanlarında `password` geçmiyor (0); compose şifreyi `${POSTGRES_PASSWORD:?…}` ile `.env`'den alıyor, `.env` `.gitignore`'da. Container non-root (`USER app`). (`docker inspect` env'de şifre çalışma zamanı değişkeni olarak var, beklenen.)
- Arama metni, parça içeriği, vektör loglarda yok (grep 0).

## Düzeltme
**devops** (`ollama.md`, `handoff/04-deploy-report.md`):
1. `ollama.md` bölüm 5.4: `channel`'ı "kaynak adı filtresi" diye anlatan cümleyi ve "sadece 'Yardım merkezi' kaynağıyla sınırla" anlatımını düzelt. `channel` yalnızca arama kaydına yazılan serbest etikettir (`search-events` çıktısında `channel` alanı); sonuçları sınırlamaz. `Q=` örneğinden `channel`'ı çıkar ya da kayıt etiketi olarak ayrıca göster. "Kısa liste" açıklaması: `efSearch=40` aday arasından `workspace_id = 42` filtresine uyanlar kalır (40 adaydan 4'ü); `Rows Removed by Filter` bu elenen adaylardır.
2. `04-deploy-report.md` smoke test satırındaki ve `ollama.md` içindeki aynı yanlış `channel` anlatımını düzelt; `## Düzeltme turu 1` bölümü ekle, son satırı yeniden yaz.
3. Düzeltince rehberdeki 5.4 komutlarını yeniden çalıştırıp çıktıları güncelle. Not: sistemde 302 şu an `3.4` (12 parça); temiz seed çıktısı için `down -v` ve `up --build -d` gerekir.

(Optional maddeler `NEEDS_FIX` sebebi değildir.)

Düzeltme sonrası qa yalnız yukarıdaki maddeleri yeniden doğrular, ardından adım 1-7'yi tekrar uygular.

## Düzeltme turu 1
| Madde | Kanıt (kendi çalıştırdığım) | Sonuç |
|---|---|---|
| 1. `ollama.md` 5.4 `channel` anlatımı | `grep -n channel ollama.md` → yalnız 2 satır: 281 (`channel` "isteğe bağlı etiket; sadece arama kaydına yazılır, sonuçları **sınırlamaz**") ve 383 (`search-events` örneği, `"channel":null`). "Kaynak adı filtresi" anlatımı ve `Q=` içindeki `channel` kalmadı. Satır 344 ve 377 açıklamaları `workspace_id = 42` filtresine göre yazılmış, planlı sorguya uyuyor | ✅ |
| 2. `04-deploy-report.md` | Smoke satırı (38) "`channel` kullanılmadı", "Düzeltme turu 1" bölümü var, `STATUS: DONE` | ✅ |
| 3. 5.4 komutları temiz seed'de | Temiz seed (302 `3.1`, 5 parça), rehber sırası: 302'ye v3.2 → 202, iş 1 `DONE 12/12`. Taşınma: KEYWORD dönen=0, VECTOR/HYBRID aynı üç sonuç (Yardım merkezi "Taşınırken internetini yanında götür", Dalga kılavuzu "s. 31 · Modemi yeni adreste kurma", "Talep #48120 · Taşındım…"). `nakil`: KEYWORD ve HYBRID rehberdeki sıra ve `matchedBy` ile aynı. Şifre VECTOR: aynı üç başlık. `POST_FILTER ef=40` dönen=4 `vectorShort=True`; `ITERATIVE_SCAN` 10; `POST_FILTER ef=200` 10. EXPLAIN 3 kez: `Index Scan using idx_chunks_embedding … rows=4.00`, `Filter: (workspace_id = '42'::bigint)`, `Rows Removed by Filter: 35` (rehberle birebir) | ✅ |

**35 mı 36 mı:** Temiz seed'de tekrarlanabilir olarak 4 (dönen) + 35 (elenen) = 39 aday. Benim önceki 36 (4+36 = 40) kirli veritabanındaydı (fazladan kaynaklar, farklı HNSW graf). HNSW yaklaşık bir taramadır: `ef_search=40` aday listesinin üst sınırıdır, index tam 40 satır döndürmek zorunda değildir; graf yapısına göre 39-40 gelebilir. Rehber bu yüzden "adayların çoğu" ve "sayılar her çalıştırmada biraz değişir" diyor, "40 aday − 4" diye kesin bir toplam iddia etmiyor; açıklama doğru. Kod/sözleşme sorunu yok.

### Adım 1-7 yeniden
Düzeltme yalnız dokümandı (`ollama.md`, `04-deploy-report.md`); `backend/` değişmedi, bu yüzden `verify` ve PIT tekrar koşulmadı (önceki sonuçlar geçerli: 345 test, PIT test gücü %90). Onay kartı maddeleri 1-12 turda önceki turdaki sonuçlarla aynı sistemde yeniden denendi: 1 (4 kaynak READY), 2 (keyword 0), 4 (4 → 10, EXPLAIN), 5 (v3.2 → 12 parça `DONE`), 11 (search-events) tekrarlandı ve tuttu; 3, 6-10 koda dokunulmadığı için önceki turun kanıtı geçerli. Madde 13 (`ollama.md`): 5.1-5.5 ve 6'nın çıktıları gerçekle uyuşuyor, `channel` bulgusu kapandı. Yeni Critical/Required bulgu yok.

### Sistemin son hali
Rehber akışını denemek için 302'ye v3.2 yükledim, sonra `down -v` + `up --build -d` ile **temiz seed'e döndürdüm**: postgres ve backend `Up (healthy)`, `Seed data completed loadedSources=112 skippedSources=0`, 1.410 parça, 302 `3.1` / 5 parça, `ingestion_jobs` 0, `search_events` 0. Test için bırakılan veri yok.

STATUS: DONE
