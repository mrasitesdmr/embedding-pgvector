TASK: TASK-004
# Backend uygulama raporu

## Kullanılan skill'ler
- `backend-conventions`
- `springboot-patterns`
- `problem-details-rfc9457`
- `jpa-patterns`
- `spring-boot-openapi-documentation`

Son ikisi planın "Backend planı" bölümünden. Yeni `errorCode` olmadığı için `problem-details-rfc9457` sadece kontrol amaçlı yüklendi.

## Yapılanlar
| Plan maddesi / Kural # | Dosya(lar) | Not |
|---|---|---|
| D1, K13: `findLatestBySourceIds` | `ingestion/repository/IngestionJobRepository.java` | Plandaki JPQL birebir kullanıldı: `j.source.id in :sourceIds and j.id = (select max(j2.id) ... where j2.source.id = j.source.id)`. Kaynak başına en büyük `id`'li işi döner; işi olmayan kaynak için kayıt dönmez. Migration yok; mevcut `idx_ingestion_jobs_source_id` index'i kullanılıyor. |
| D1, K18: `SourceResponse.latestJob` | `source/domain/dto/SourceResponse.java` | Record'a son bileşen olarak `@Nullable IngestionJobResponse latestJob` eklendi; `@Schema` açıklaması sözleşme açıklamasının İngilizce kısaltması. `from(Source)` kaldırıldı, yerine `from(Source, @Nullable IngestionJob)` geldi; iş varsa `IngestionJobResponse.from(job)` ile doldurulur. |
| D1: liste ve detay doldurma (N+1 yok) | `source/service/SourceService.java` | Constructor'a `IngestionJobRepository` eklendi. `listSources` sayfadaki kaynakların id'leriyle `findLatestBySourceIds`'i tek kez çağırır, sonucu `Map<Long, IngestionJob>`'a çevirir (`IngestionJob::getSourceId`) ve `SourceResponse.from(source, map.get(id))` ile eşler. Sayfa boşsa sorgu atılmaz. `getSource` aynı private metodu (`findLatestJobs`) tek kaynakla çağırır. `getExistingSource` değişmedi. |
| Sözleşme 0.4.0 | `config/OpenApiConfig.java` | `API_VERSION = "0.4.0"`. `ERROR_CODES` değişmedi (21). |
| AC-5: `latestJob` şeması | `config/OpenApiConfig.java` | Yeni `nullableLatestJobCustomizer` (`OpenApiCustomizer`) eklendi. `SourceResponse.properties.latestJob`'u `{description, anyOf: [{$ref: IngestionJobResponse}, {type: 'null'}]}` olarak yazar. Neden: aşağıda "Plandan sapmalar". |
| `backend/CLAUDE.md` komutu | `backend/CLAUDE.md` ("Komutlar") | Yeni satır: `curl -s localhost:8080/api/workspaces/42/sources/302` → `latestJob` alanı. |

Değişmeyenler: `IngestionService`, `IngestionJobRunner`, `Source`, controller'lar, endpoint path/method/status kodları ve `errorCode`'lar. Log eklenmedi.

## Değişen dosyalar
Değişen:
- `backend/src/main/java/com/embedding/pgvector/source/domain/dto/SourceResponse.java`
- `backend/src/main/java/com/embedding/pgvector/source/service/SourceService.java`
- `backend/src/main/java/com/embedding/pgvector/ingestion/repository/IngestionJobRepository.java`
- `backend/src/main/java/com/embedding/pgvector/config/OpenApiConfig.java`
- `backend/CLAUDE.md` (Komutlar: bir satır)

Yeni ya da silinen dosya yok. Dependency, plugin ve migration eklenmedi.

## Plandan sapmalar
1. **`latestJob` şeması customizer ile yazıldı.**
   - **Plan ne diyordu:** `@Schema(nullable = true)`.
   - **Sorun:** SpringDoc (OAS 3.1) `nullable = true` için `{"type":"null","$ref":"#/components/schemas/IngestionJobResponse"}` üretiyor. 3.1'de `$ref` ile kardeş `type` AND'lenir, bu yüzden bu şema sözleşmedeki `anyOf: [$ref, {type: 'null'}]` ile eşdeğer değil.
   - **Denenen diğer seçenekler:** Üçü de temiz `anyOf` vermedi.
     - `types={"object","null"}` + `implementation` → `{"type":["object","null"],"$ref":...}`
     - `anyOf={IngestionJobResponse.class}` + `types` → `type`, `$ref` ve `anyOf` birlikte
     - `oneOf={IngestionJobResponse.class, Void.class}` → sadece `$ref`, null yok
   - **Uygulanan çözüm:** Field'da sadece `@Schema(description=...)` bırakıldı. `OpenApiConfig`'teki yeni customizer, mevcut `ProblemDetail` customizer'ı gibi alanı sözleşmedeki biçimde yeniden yazıyor.
   - **Üretilen çıktı (doğrulandı):** `{"anyOf":[{"$ref":"#/components/schemas/IngestionJobResponse"},{"type":"null"}],"description":"..."}`. `SourceResponse.required` = `[chunkCount, createdAt, kind, name, sourceId, status, workspaceId]`, `latestJob` bu listede yok.
   - **Kimle konuşuldu:** Planner'a ve lead'e mesajla bildirildi. Planner itiraz etmedi; `01-plan.md`'yi güncelledi (backend tablosundaki `SourceResponse` satırı ve "Sözleşme değişiklikleri" altında "Uygulama notu 1"). Sözleşme değişmiyor.

## Doğrulama
1. **`./mvnw -q compile`** → geçti.
2. **Gerçek ağaçta `./mvnw clean verify -Djacoco.haltOnFailure=false`** → **BUILD FAILURE (test derlemesi).**
   - Hata: `SourceControllerTest.java:[265,33] constructor SourceResponse ... cannot be applied to given types`.
   - Plan bu kırılmayı öngörüyor ("Değişen mevcut parçalar": `SourceResponse` 11 bileşenli oldu, fixture'lara `latestJob` eklenecek). Testleri güncellemek tester'ın işi; `src/test/**`'e dokunulmadı.
   - Clean olmadan çalışan önceki (incremental) koşuda iki test kırıldı, kalan 364 test geçti:
     - `SourceControllerTest#givenSources_whenListSources_thenReturnsPageResponseWithDefaultPaging`: `NoSuchMethodError`, aynı constructor sorunu.
     - `OpenApiDocumentationIntegrationTest#givenApplication_whenGetApiDocs_thenContainsNewPathsAndChunkNotFound`: `expected "0.3.0" but was "0.4.0"`.
3. **Ek doğrulama, `src/test`'e yazmadan:** Backend scratchpad'e kopyalandı (`.../scratchpad/backend-copy`). Kopyada iki test şöyle düzeltildi:
   - `SourceControllerTest:265` constructor'ına 11. argüman olarak `null` eklendi.
   - `OpenApiDocumentationIntegrationTest:35` `0.4.0` yapıldı; `:57`'deki "latestJob yok" kontrolü yerine şemayı yazdıran bir satır kondu.

   Sonra kopyada `./mvnw clean verify -Djacoco.haltOnFailure=false` çalıştırıldı → **BUILD SUCCESS, Tests run: 366, Failures: 0, Errors: 0, Skipped: 0, "All coverage checks have been met."**

   Bu koşuda Testcontainers + pgvector ile çalışan entegrasyon testleri `listSources`/`getSource` üzerinden yeni JPQL'i gerçek PostgreSQL'de çalıştırdı. Üretilen `latestJob` şeması "Plandan sapmalar"daki çıktıdır.
4. **Sonuç:** Tester aşağıdaki iki testi güncelleyince tam `./mvnw verify`'ın geçmesi bekleniyor. Coverage eşiği açık olsa da bu kopyada tuttu.

## Tester için notlar
- **Güncellenmesi gereken mevcut testler (plan B13 + fixture):**
  - `SourceControllerTest:265`: `new SourceResponse(...)` artık 11 argüman alıyor; sonuncusu `latestJob` (`@Nullable IngestionJobResponse`).
  - `OpenApiDocumentationIntegrationTest:35`: sürüm `0.4.0`.
  - `OpenApiDocumentationIntegrationTest:57`: `latestJob` artık var. B6 için beklenen şekil: `anyOf[0].$ref = "#/components/schemas/IngestionJobResponse"`, `anyOf[1].type = "null"`, `latestJob` `required`'da yok.
- **Repository (B1):**
  - `findLatestBySourceIds(Collection<Long>)` korelasyonlu alt sorguyla kaynak başına `max(id)`'yi döner.
  - Boş koleksiyonla çağrılmaz, guard service'te (`findLatestJobs`). Repository testinde boş liste denemeyin; `in ()` Hibernate'te sorun çıkarabilir.
  - B1 fixture'ı kurarken DB kısıtına dikkat: `uk_ingestion_jobs_source_id_active` bir kaynakta aynı anda tek aktif (DONE/FAILED olmayan) iş olmasına izin verir. A: 1 FAILED + 3 DONE, B: 2 EMBEDDING uygun.
- **Service (B5):**
  - `SourceService` constructor sırası: `(WorkspaceService, SourceRepository, IngestionJobRepository)`.
  - `listSources`, `findLatestBySourceIds`'i sayfa içeriğinin sırasıyla bir `List<Long>` ile tek kez çağırır (`[301, 302, 303]`).
  - Boş sayfada çağrı yoktur (`then(ingestionJobRepository).shouldHaveNoInteractions()`).
  - `getSource`, `List.of(sourceId)` ile çağırır.
  - Eşleme `IngestionJob.getSourceId()` ile yapılır. Mock'ta dönen `IngestionJob`'un `source`'u gerçek bir `Source` olmalı, çünkü `getSourceId()` `source.getId()` okur.
- **JSON (B6):** Projede `NON_NULL` inclusion yok; işi olmayan kaynakta JSON'da `"latestJob": null` anahtarı yazılır.
- **İç içe alanlar:** `latestJob` `IngestionJobResponse.from(job)` ile üretilir. Bütün alanlar ve `statusUrl` (`/api/workspaces/{workspaceId}/ingestion-jobs/{jobId}`) `GET .../ingestion-jobs/{jobId}` ile aynıdır.
- **B3 değeri:** `progress` = 0.1 + 0.8 × 96/214 ≈ 0.458879.
- **Zaman ve eşzamanlılık:** Yeni zaman ya da eşzamanlılık noktası yok. Okuma yolu `@Transactional(readOnly = true)` içinde ve `IngestionJob.source` LAZY olsa da `getSourceId()` proxy id'sini okur, ek sorgu atmaz.

## Düzeltme

STATUS: DONE
