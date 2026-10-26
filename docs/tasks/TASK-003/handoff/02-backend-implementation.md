TASK: TASK-003
# Backend uygulama raporu

## Kullanılan skill'ler
`backend-conventions`, `springboot-patterns`, `problem-details-rfc9457`, `flyway-migrations`, `spring-boot-openapi-documentation`, `jpa-patterns`, `postgres-patterns` (planın "Backend planı" listesi + rolün zorunlu üçlüsü).

## Yapılanlar
| Plan maddesi / Kural # | Dosya(lar) | Not |
|------------------------|------------|-----|
| Migration V7 (D3, K26, G4) | `db/migration/V7__add_result_limit_to_search_events.sql` | `result_limit integer NULL` + `ck_search_events_result_limit` (NULL ya da 1-50). Veri güncellemesi ve index yok |
| `application.properties` (D2) | `application.properties` | `search.stats-time-zone=Europe/Istanbul` |
| `SearchProperties` | `config/SearchProperties.java` | Sona `@NotNull ZoneId statsTimeZone` |
| `chunkNo` arama sonucunda (K18, B1) | `RetrievedHitView`, `ChunkSearchRepository`, `RetrievedHit`, `ChunkRetrievalService`, `SearchHitResponse`, `SearchService.toHits` | Üç native SELECT'e `c.chunk_no AS "chunkNo"` (`"chunkId"`'den sonra). `SearchHitResponse.chunkNo` `chunkId`'den sonra, required, `minimum 0` |
| `SearchEvent.resultLimit` / `SearchEventResponse.limit` (K26, B2, B14) | `SearchEvent`, `SearchEventResponse`, `SearchService.saveSearchEvent` | `SearchEvent.record(..., @Nullable Integer efSearch, int resultLimit, int resultCount, ...)`; `getResultLimit()` `@Nullable Integer` (eski satırlar null). Kayda `response.limit()` yazılır. `limit` `filterStrategy`'den sonra, `[integer, null]` |
| Göstergeler (K25, K39, D2, B3-B7) | `SearchEventStatsView` (yeni), `SearchEventRepository.countStats` (yeni), `SearchEventStatsResponse` (yeni), `SearchService.getSearchEventStats`, `SearchEventController.getSearchEventStats` | `GET /api/workspaces/{workspaceId}/search-events/stats`. Planda yazan sırayla: `ensureWorkspaceExists` → `LocalDate.now(clock.withZone(zone))` → `todayStart`, `last7DaysStart` (`STATS_WINDOW_DAYS = 7`, bugün + önceki 6 gün) → tek native `count(*) FILTER (...)` sorgusu → oranlar `last7DaysCount == 0 ? null : count / last7DaysCount` (yuvarlanmaz) |
| Alanın modelleri (K15, D5, B10) | `WorkspaceEmbeddingModelView` (yeni), `ChunkRepository.findEmbeddingModelsByWorkspaceIds`, `WorkspaceResponse`, `WorkspaceService` | `WorkspaceService`'e `ChunkRepository` enjekte edildi. `listWorkspaces` sayfadaki id'ler için tek sorgu; `getWorkspace` aynı metotla tek id. Alan başına artan sıralı, parça yoksa `[]`. `WorkspaceResponse.from(WorkspaceSummary, List<String>)`, compact constructor `List.copyOf` |
| Tek parça (K22, D4, G1, B11, B12) | `ChunkRepository.findViewBySourceIdAndId`, `ChunkService.getChunk`, `ChunkController.getChunk` | `GET /api/workspaces/{workspaceId}/sources/{sourceId}/chunks/{chunkId}`. Önce mevcut `getExistingSource` (`WORKSPACE_NOT_FOUND` / `SOURCE_NOT_FOUND`), sonra `source.id + id` ile JPQL projection; yoksa `NotFoundException("CHUNK_NOT_FOUND", "Chunk not found: <id>")` |
| `OpenApiConfig` (AC-5, B13) | `config/OpenApiConfig.java` | `API_VERSION = "0.3.0"`; `CHUNK_NOT_FOUND` `SOURCE_NOT_FOUND`'dan sonra, toplam 21 kod |
| `backend/CLAUDE.md` | `backend/CLAUDE.md` | Migration aralığı `V1`-`V7`; "Komutlar"a stats ve tek parça `curl` satırları |
| D1 (`latestJob` yok) | — | `SourceResponse`, `SourceService`, `IngestionJobRepository` değişmedi. Yeni log eklenmedi |

## Değişen dosyalar
Yeni (`backend/src/main/...`):
- `resources/db/migration/V7__add_result_limit_to_search_events.sql`
- `java/.../search/repository/SearchEventStatsView.java`
- `java/.../search/domain/dto/SearchEventStatsResponse.java`
- `java/.../chunk/repository/WorkspaceEmbeddingModelView.java`

Değişen:
- `resources/application.properties`
- `java/.../config/SearchProperties.java`, `java/.../config/OpenApiConfig.java`
- `java/.../search/repository/RetrievedHitView.java`, `ChunkSearchRepository.java`, `SearchEventRepository.java`
- `java/.../search/domain/entity/RetrievedHit.java`, `SearchEvent.java`
- `java/.../search/domain/dto/SearchHitResponse.java`, `SearchEventResponse.java`
- `java/.../search/service/ChunkRetrievalService.java`, `SearchService.java`
- `java/.../search/controller/SearchEventController.java`
- `java/.../chunk/repository/ChunkRepository.java`, `java/.../chunk/service/ChunkService.java`, `java/.../chunk/controller/ChunkController.java`
- `java/.../workspace/domain/dto/WorkspaceResponse.java`, `java/.../workspace/service/WorkspaceService.java`
- `backend/CLAUDE.md` (Teknoloji: migration aralığı; Komutlar: 2 satır)

Silinen: yok. Eklenen dependency/plugin: yok (`pom.xml` değişmedi).

## Plandan sapmalar
Yok. Biçimle ilgili tek not: plan `SearchEventController`'da `@GetMapping("/stats")` yazıyor; mevcut controller'larda sınıf düzeyinde `@RequestMapping` olmadığı ve her metot tam path taşıdığı için `@GetMapping("/api/workspaces/{workspaceId}/search-events/stats")` yazıldı. Ortaya çıkan path sözleşmeyle aynı. `ChunkController.getChunk` için de aynısı geçerli.

## Doğrulama
1. `cd backend && ./mvnw -q compile` → başarılı (ana kod derleniyor).
2. `cd backend && ./mvnw verify -Djacoco.haltOnFailure=false` → **BUILD FAILURE, test derlemesinde (testCompile)**. Bu sonuç bekleniyordu: planın "Değişen mevcut parçalar" bölümündeki imza değişiklikleri yüzünden 3 test dosyası eski constructor'ları kullanıyor. Planın 28. satırına göre bu testleri tester güncelleyecek. `src/test/**`'e dokunmadım. Hatalar:
   - `SearchControllerTest.java:179` `new SearchHitResponse(...)` (`chunkNo` eksik), `:236` `new SearchEventResponse(...)` (`limit` eksik)
   - `SearchServiceTest.java:52`, `:100` `new SearchProperties(...)` (`statsTimeZone` eksik); `:87`, `:381`, `:383` `new RetrievedHit(...)` (`chunkNo` eksik); `:430` `SearchEvent.record(...)` (`resultLimit` eksik)
   - `WorkspaceControllerTest.java:54` `new WorkspaceResponse(...)` (`embeddingModels` eksik)
3. Mevcut davranışın bozulmadığını görmek için backend'i scratchpad'e kopyaladım. Bu kopyada yalnız yukarıdaki çağrılara yeni argümanı ekledim (`chunkNo 0`, `limit 10`, `ZoneId Europe/Istanbul`, `List.of("bge-m3@1024")`) ve `./mvnw verify -Djacoco.haltOnFailure=false` çalıştırdım → **Tests run: 345, Failures: 2, Errors: 0**. Kalan 2 hata da planın beklenen sonucu, tester güncelleyecek:
   - `SchemaMigrationIntegrationTest.givenFlywaySchema_whenContextStarts_thenHibernateValidateSucceeds:85`: migration sayısı `6` bekleniyor, artık `7` (V7).
   - `OpenApiDocumentationIntegrationTest.givenRunningApplication_whenGetApiDocs_thenProblemDetailSchemaHasErrorCodeViolationsAndContractInfo:35`: `info.version` `0.2.0` bekleniyor, artık `0.3.0` (B13 bu testi zaten yeniden yazıyor).
   - Diğer 343 test geçti. Yani `ddl-auto=validate` V7 + `SearchEvent.resultLimit` ile açılıyor, arama/ingestion/kaynak davranışı değişmedi.
4. Aynı scratch kopyada geçici bir Testcontainers testiyle yeni endpoint'leri gerçek PostgreSQL + pgvector üzerinde çağırdım. Bu test teslim edilmedi, `src/test`'e girmedi. Sonuçlar:
   - `GET /api/workspaces/42` → `"embeddingModels":["aaa@8","bge-m3@1024"]`; parçasız alan 19 → `[]` (liste ve tekil uç).
   - `GET .../sources/301/chunks/{id}` → 200, 6 alan; olmayan id → 404 `CHUNK_NOT_FOUND`, `type .../errors/chunk-not-found`; başka alanın kaynağı → 404 `SOURCE_NOT_FOUND`.
   - KEYWORD arama → hit `"chunkNo":7`; arama kaydı `"limit":5`; native insert ile eklenen eski kayıt `"limit":null`.
   - `GET .../search-events/stats` (2 kayıt: bugün 1/5 sonuç, dün 4 sonuç + `result_limit NULL`) → `totalCount 2, todayCount 1, last7DaysCount 2, zeroResultCount 0, lowResultCount 1, zeroResultRate 0.0, lowResultRate 0.5, timeZone "Europe/Istanbul", todayStart 2026-10-24T21:00:00Z, last7DaysStart 2026-10-18T21:00:00Z`. Kayıtsız alan → sayılar 0, oranlar `null` (JSON'da var). Olmayan alan → 404 `WORKSPACE_NOT_FOUND`.
   - Üretilen `/v3/api-docs.yaml` sözleşmeyle karşılaştırıldı: `info.version 0.3.0`; iki yeni path, `operationId` (`getSearchEventStats`, `getChunk`) ve status'lar (200/404/500) aynı; `SearchEventStatsResponse`, `WorkspaceResponse`, `SearchHitResponse`, `SearchEventResponse` alan sırası ve `required` listeleri sözleşmeyle aynı; `errorCode` enum'u 21 değer, `CHUNK_NOT_FOUND` `SOURCE_NOT_FOUND`'dan sonra.

## Tester için notlar
- **Önce test derlemesini düzelt:** yukarıdaki "Doğrulama 2"deki 10 çağrı ve "Doğrulama 3"teki 2 assertion (`flyway_schema_history` sayısı 7, `info.version` 0.3.0). Bunlar düzelmeden `./mvnw verify` çalışmaz.
- **Clock / gün sınırı (B3, B4, B7):** `SearchService.getSearchEventStats` saati `Clock`'tan (`LocalDate.now(clock.withZone(zone))`), zone'u `SearchProperties.statsTimeZone()`'dan okur. Unit testte `SearchProperties` constructor'ının son argümanı `ZoneId.of("Europe/Istanbul")`. `searchEventRepository.countStats(workspaceId, todayStart, last7DaysStart)` `ArgumentCaptor` ile yakalanabilir. Integration testte `FixedClockTestConfiguration` (`2026-10-25T10:00:00Z`) kullanılabilir. `countStats` sorgusunun kendisi saate bakmaz (`now()` yok); sınırlar parametre olarak gelir, bu yüzden B3'teki eski tarihli satırlar native insert ile güvenle yazılabilir.
- **`result_limit` NULL satırı (B3, B14, G4):** JPA ile yazılamaz (`SearchEvent.record` `int resultLimit` alır). Native `INSERT INTO search_events (workspace_id, created_at, query, mode, result_count, total_ms) VALUES (...)` ile yazılır, `result_limit` verilmezse NULL olur. CHECK constraint: `result_limit` NULL ya da 1-50.
- **`SearchEventStatsResponse.from`** saf statik metot, `SearchEventStatsView` stub'ıyla unit test edilebilir. Oran tipi `Double`: `last7DaysCount == 0` ise `null`, değilse yuvarlanmamış `count / last7DaysCount` (assertion'da `isCloseTo(..., within(1e-6))`).
- **`WorkspaceService`** artık iki repository alıyor: `WorkspaceService(WorkspaceRepository, ChunkRepository)`. Unit test varsa `ChunkRepository` mock'u eklenmeli. Boş sayfada `findEmbeddingModelsByWorkspaceIds` çağrılmaz (`Map.of()`), boş `IN ()` sorgusu atılmaz. Sıralama Java'da (`sorted()`), JPQL'de `order by` yok.
- **`ChunkService.getChunk`** sırası: `sourceService.getExistingSource(workspaceId, sourceId)` (alan + kaynak kontrolü, mevcut metot) → `chunkRepository.findViewBySourceIdAndId(sourceId, chunkId)`. "Başka kaynağın parçası" ve "yeni sürümle silinmiş parça" aynı yoldan `CHUNK_NOT_FOUND` döner (B11'in 2. ve 4. testi). Yeni sürümle silinmeyi `uploadVersion` + `awaitFinishedJob` yardımcılarıyla ya da doğrudan `DELETE FROM chunks` ile kurabilirsin.
- **`chunkNo` (B1):** `FakeEmbeddingIntegrationTest.insertChunk(workspaceId, sourceId, chunkNo, ...)` ile `chunkNo=7` verilebilir. Üç modda da `"chunkNo"` native SELECT'ten geliyor.
- **`embeddingModels` (B10):** `insertChunk(..., modelTag)` overload'u farklı etiket yazar (ör. `"aaa@8"`). Etiket farklı olunca HYBRID/VECTOR aramada `EMBEDDING_MODEL_MISMATCH` olur, ama `/api/workspaces` uçları etkilenmez.
- `SearchHitResponse` argüman sırası: `rank, chunkId, chunkNo, sourceId, ...`. `SearchEventResponse`: `..., filterStrategy, limit, resultCount, ...`. `WorkspaceResponse`: `..., chunkCount, embeddingModels`.

## Düzeltme
—

STATUS: DONE
