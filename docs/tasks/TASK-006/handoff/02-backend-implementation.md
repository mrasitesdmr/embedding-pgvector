TASK: TASK-006
# Backend uygulama raporu

## Kullanılan skill'ler
`backend-conventions`, `springboot-patterns`, `problem-details-rfc9457`, `flyway-migrations`, `postgres-patterns`, `jpa-patterns`, `spring-boot-openapi-documentation`. Yeni dependency yok.

## Yapılanlar
| Plan maddesi / Kural # | Dosya(lar) | Not |
|---|---|---|
| Silinecekler (K1, K2) | `seed/**` (paket tamamen), `config/SeedProperties.java`, `src/main/resources/seed/` (37 JSON), `application.properties` `seed.*`, `Source.seeded(...)` + `SeedSource` import'u, `backend/samples/` (3 dosya) | `Workspace.seeded` D2 gereği değişmedi |
| D1, D3 (K4, K5, G6) | `db/migration/V9__reset_data_and_insert_default_workspace.sql` | Plandaki iki ifade, bu sırayla: `TRUNCATE search_events, ingestion_jobs, chunks, sources, workspaces RESTART IDENTITY CASCADE;` ve `INSERT … (42, 'Belgelerim', now())` |
| D4, D5 (V10) | `db/migration/V10__add_popular_query_columns_to_search_events.sql` | `query_key text NOT NULL`, `keyword_count integer NULL`, `best_distance double precision NULL`, `ck_search_events_keyword_count`, `ck_search_events_best_distance`, `idx_search_events_workspace_id_query_key (workspace_id, query_key)`. Backfill yok (V9 tabloyu boşaltıyor) |
| D4 (K16) | `search/domain/entity/SearchEvent.java` | + `queryKey`, `keywordCount`, `bestDistance` alanları ve getter'ları. `record(...)` imzasının sonuna `@Nullable Integer keywordCount, @Nullable Double bestDistance` eklendi. `queryKey`'i kurucu `toQueryKey(query)` ile hesaplıyor. + `public static String toQueryKey(String query)` = `Source.toNameKey(query.strip().replaceAll("\\s+", " "))` |
| D5 (K17) | `search/service/SearchService.java` | `saveSearchEvent` artık `response.keywordCount()` ve `bestDistance(hits)` değerlerini geçiriyor: null olmayan `distance`'ların en küçüğü, hiç yoksa null. + `listPopularQueries(workspaceId)`: `ensureWorkspaceExists` → repository → DTO |
| K15-K17 | `search/repository/SearchEventRepository.java`, `search/repository/PopularQueryView.java` (yeni) | Native `findPopularQueries(workspaceId, maxDistance, limit)`. CTE `counted` D5 koşulunu uyguluyor, `grouped` `count(*)`, `max(created_at)` ve `max(id)` değerlerini alıyor, `latest` grubun en yeni sayılan olayının `query`'sini `DISTINCT ON … ORDER BY created_at DESC, id DESC` ile seçiyor. Sıra `count DESC, max(created_at) DESC, max(id) DESC`, sonra `LIMIT :limit`. Alias'lar `"query"`, `"searchCount"`, `"lastSearchedAt"`. `Instant` projection'ı canlıda doğru dönüyor |
| K15 (sözleşme) | `search/domain/dto/PopularQueryResponse.java`, `PopularQueriesResponse.java` (yeni) | Şemalar 0.5.0 ile aynı (zorunlu alanlar, `minimum 1`, `maxItems 5`). `List.copyOf` kullanılıyor |
| K15 (endpoint) | `search/controller/SearchEventController.java` | `GET /api/workspaces/{workspaceId}/search-events/popular-queries`, `operationId listPopularQueries`, `@ApiResponse` 200/404/500 |
| D6, D7 | `config/SearchProperties.java`, `application.properties` | + `@Min(1) @Max(20) int popularQueryLimit`, `@DecimalMin("0.0") @DecimalMax("2.0") double popularMaxDistance`. `search.popular-query-limit=5`, `search.popular-max-distance=0.49` (ölçüm aşağıda; lead kararı) |
| Açıklama ve örnekler (0.5.0) | `WorkspaceController` (`@Tag` metni), `WorkspaceResponse`, `SourceResponse` (`sourceId` 10001, ad, `kind`, `version` '2026.1', `chunkCount` 48, `fileName`, `latestJob`), `ChunkResponse` (`chunkNo`, `heading` "s. 6 · 5. Yıllık izin"), `SourceController` (`name` parametresi), `EmbedTextRequest`, `SearchChunksRequest` (sorgu "Yıllık izin kaç gün?", kanal "Temsilci paneli") | `/v3/api-docs` çıktısında Poyraz, Dalga, modem, Şifremi, seed, Fatura ve "yardım merkezi" geçmiyor (0 eşleşme) |
| `backend/CLAUDE.md` | `backend/CLAUDE.md` | Seed satırları kaldırıldı. Şema `V1`-`V10`, V9/V10 açıklaması ve `max(version::int)` notu eklendi. "Sık aranan" anahtarı, eşik ve sınır yazıldı. Örnek belge yolu `ornek-dokuman/`. Curl örnekleri örnek belgeyle güncellendi (yükleme, `sources/10001`, sürüm yükleme, arama, `popular-queries`, `chunks/1`). "Başlangıç verisi olmadan çalıştır" satırı kaldırıldı |
| Demo metni taraması | — | `grep -rniE '<plandaki desen>|<37 seed çalışma alanı adı>' backend/src/main backend/CLAUDE.md` → **0 satır**. Desene, silmeden önce JSON'lardan okuduğum 37 alan adı eklendi (ör. "atlas havayolları", "kervan lojistik", "pusula bank"). Desene ayrıca `seed` kelimesi eklenince tek eşleşme `Workspace.seeded` çıkıyor; D2 gereği bilerek kaldı |

## Değişen dosyalar
**Yeni:**
- `backend/src/main/resources/db/migration/V9__reset_data_and_insert_default_workspace.sql`
- `backend/src/main/resources/db/migration/V10__add_popular_query_columns_to_search_events.sql`
- `backend/src/main/java/com/embedding/pgvector/search/repository/PopularQueryView.java`
- `backend/src/main/java/com/embedding/pgvector/search/domain/dto/PopularQueryResponse.java`
- `backend/src/main/java/com/embedding/pgvector/search/domain/dto/PopularQueriesResponse.java`

**Değişen:**
- `search/domain/entity/SearchEvent.java`, `search/service/SearchService.java`, `search/repository/SearchEventRepository.java`, `search/controller/SearchEventController.java`
- `config/SearchProperties.java`, `src/main/resources/application.properties`
- `source/domain/entity/Source.java`, `source/domain/dto/SourceResponse.java`, `source/controller/SourceController.java`
- `workspace/controller/WorkspaceController.java`, `workspace/domain/dto/WorkspaceResponse.java`
- `chunk/domain/dto/ChunkResponse.java`, `embedding/domain/dto/EmbedTextRequest.java`, `search/domain/dto/SearchChunksRequest.java`
- `backend/CLAUDE.md`

**Silinen:**
- `backend/src/main/java/com/embedding/pgvector/seed/` (`service/SeedDataLoader`, `SeedDataReader`, `SeedResult`, `package-info`; `domain/dto/SeedChunk`, `SeedSource`, `SeedWorkspace`, `package-info`)
- `backend/src/main/java/com/embedding/pgvector/config/SeedProperties.java`
- `backend/src/main/resources/seed/` (37 JSON)
- `backend/samples/` (`Dalga-T5-Kullanim-Kilavuzu-v3.2.pdf`, `yardim-notlari.md`, `hata-kodlari.csv`)

Eklenen dependency: yok.

## Plandan sapmalar
- **Eşik 0.45 → 0.49 (D6):** 0.45 plan kuralını sağlıyordu: ilgili üç soru içeride, ilgisiz üç soru dışarıda kalıyordu. Ölçümde E4'teki büyük harfli varyantın payı dar çıktı (aşağıda). Bunu lead'e bildirdim, lead orta değeri (0.49) istedi. Değişen tek şey `application.properties`'teki bir satır; kod ve sözleşme aynı.
- **Yükseltme gözlemi sırası:** Dev volume (`embedding-pgvector-dev_pgdata`) hiç yoktu, yani veritabanı boştu. Gözlemin anlamlı olması için önce **eski (değiştirilmemiş) backend**'i bu boş dev veritabanında açtım: demo veriyi yükledi. Ardından kullanıcı verisi gibi alan 1'e bir dosya yükledim ve alan 42'de bir arama yaptım. Sonra kodu değiştirip yeni backend'i ilk kez açtım. `down -v` kullanmadım.
- **Plandaki sayım sorgusu:** `max(version)` metin karşılaştırması yaptığı için V10'dan sonra `9` döner ('9' > '10'). Doğrusu `max(version::int)`. Lead'e bildirdim, plana dokunulmadı (aşağıda).
- **Dev compose:** Plan bitişte `down -v` diyordu. Lead'in izniyle açık bıraktım (aşağıda).

## Doğrulama

### Yükseltme gözlemi (D3, G6), dev veritabanı `embedding-pgvector-dev`
Sorgu (plandaki sorgu): `select (select count(*) from workspaces) w, (select count(*) from sources) s, (select count(*) from chunks) c, (select count(*) from ingestion_jobs) j, (select count(*) from search_events) e, (select max(version) from flyway_schema_history) v`

**Önce** (2026-10-25T19:13:27Z; eski backend demo veriyi yükledikten, alan 1'e `Kullanici-notlari` yüklendikten ve alan 42'de bir arama yapıldıktan sonra; yeni backend henüz açılmamış):
```
 w  |  s  |  c   | j | e | v
----+-----+------+---+---+---
 37 | 113 | 1413 | 1 | 1 | 8

 id |      name
----+----------------
  1 | Poyraz Telekom
 42 | Dalga Fiber

  id   | workspace_id |       name        |   kind
-------+--------------+-------------------+----------
 10001 |            1 | Kullanici-notlari | MARKDOWN
```
**Sonra** (2026-10-25T19:17:11Z; `./mvnw spring-boot:run` ile yeni backend açıldı, log: `Current version of schema "public": 8` → `Migrating … "9 - reset data and insert default workspace"` → `"10 - add popular query columns to search events"` → `Successfully applied 2 migrations … now at version v10`; seed logu yok):
```
 w | s | c | j | e | v
---+---+---+---+---+---
 1 | 0 | 0 | 0 | 0 | 9      ← v: metin karşılaştırması, bkz. not

 id |    name
----+------------
 42 | Belgelerim

 version | success
---------+---------
 9       | t
 10      | t

 v_numeric (max(version::int)) = 10, version kolonunun tipi = text
 nextval('sources_id_seq') = 10001   (sonra setval(…, 10001, false) ile geri alındı)
```
`GET /api/workspaces` → `{"content":[{"workspaceId":42,"name":"Belgelerim","sourceCount":0,"chunkCount":0,"embeddingModels":[]}],…,"totalElements":1}`.

**Not, tester ve qa için:** Plandaki sorguda `v` sütunu V10'dan sonra `9` gösterir. Son sürümü `(select max(version::int) from flyway_schema_history where version is not null) v` ile al; doğru değer **10**.

**Tek seferlik olma:** Örnek PDF yüklenip 17 arama yapıldıktan sonra backend yeniden başlatıldı. Log: `Current version of schema "public": 10` → `Schema "public" is up to date. No migration necessary.` Sayılar: `w=1, s=1, c=24, j=1, e=17`, `flyway_schema_history`'de `version='9'` olan 1 satır var. Veri yerinde kaldı.

### Eşik ölçümü (D6), gerçek Ollama (`bge-m3`), HYBRID varsayılanları
Örnek PDF: `curl -F name=Atlaskap-Lojistik-Surec-El-Kitabi -F file=@../ornek-dokuman/Atlaskap-Lojistik-Surec-El-Kitabi.pdf localhost:8080/api/workspaces/42/sources` → 202, kaynak 10001, iş 1 `DONE`, **24 parça** (`errorCode null`, yaklaşık 1,5 sn).

| Sorgu | Grup | `keywordCount` | En küçük `distance` | 1. sonuç |
|---|---|---|---|---|
| Yıllık izin kaç gün? | ilgili | 0 | **0.2880** | "5. Yıllık izin ve diğer izinler" (s. 7), SEMANTIC |
| Masraf beyanı kaç gün içinde yapılır? | ilgili | 0 | **0.2599** | "6. Masraf ve harcırah" (s. 8), SEMANTIC |
| parolamı unuttum hesabım kilitlendi | ilgili | **1** | **0.3951** | "14. Bilgi teknolojileri destek" (s. 16), KEYWORD+SEMANTIC |
| kuantum fiziği | ilgisiz | 0 | 0.6846 | s. 4, SEMANTIC |
| futbol maçı kaç kaç bitti | ilgisiz | 0 | 0.6487 | s. 16, SEMANTIC |
| kek tarifi | ilgisiz | 0 | **0.5569** | s. 14, SEMANTIC |
| *Ek:* "␣␣PAROLAMI unuttum hesabım kilitlendi␣" (E4) | ilgili | **0** | **0.4256** | s. 16, SEMANTIC |
| *Ek:* "Parolamı unuttum, hesabım kilitlendi" (README) | ilgili | 1 | 0.3876 | s. 16, KEYWORD+SEMANTIC |

Bütün sorgular `returnedCount 10` döndü, çünkü vektör kolu her zaman `limit` kadar sonuç verir (G1). İlk 3 sonuç kontrolü (AC-3 için ön bilgi): izin sorusunda 1. ve 2. sonuçta "Yıllık izin" geçiyor; masraf ve parola sorularında 1. sonuç ilgili bölüm ("Masraf", "Parola").

**Eşik kararı: 0.49.**
- 0.45 de kurala uyuyordu: ilgili en büyük 0.3951, ilgisiz en küçük 0.5569.
- E4'teki büyük harfli varyantın kelime kolu **0**. PostgreSQL FTS `PAROLAMI`'yı küçük harfe çevirirken `I`'yı `ı` değil `i` yapıyor (`parolami`), Türkçe kök belgedeki `parolamı` ile tutmuyor. Bu yüzden varyant sadece mesafeyle sayılıyor: 0.4256. Bu değer 0.45'e sadece 0.024 uzak.
- Orta değer (0.4256 + 0.5569) / 2 = 0.49125 → **0.49**. İki tarafta da yaklaşık 0.065 pay kalıyor: 0.49 − 0.4256 = 0.064, 0.5569 − 0.49 = 0.067.
- Lead bu değeri onayladı.
- 0.49 ile canlı sonuç, dev DB'deki 17 olay üzerinde: `4 "parolamı unuttum hesabım kilitlendi"`, `3 "Masraf beyanı kaç gün içinde yapılır?"`, `3 "Yıllık izin kaç gün?"`, `1 "Parolamı unuttum, hesabım kilitlendi"`. Kuantum, futbol ve kek listede yok. PAROLAMI varyantı küçük harfli grupla birleşti; gösterilen metin grubun en yeni olayının metni. Eşit sayılı masraf ve izin gruplarında daha yeni olan (masraf) önde.
- `GET /api/workspaces/999/search-events/popular-queries` → 404 `application/problem+json`, `errorCode WORKSPACE_NOT_FOUND`.
- `search_events` satırlarında `query_key` beklenen değerleri taşıyor: "Yıllık izin kaç gün?" → `yillik izin kaç gün?`; "PAROLAMI unuttum…" → `parolami unuttum hesabim kilitlendi`.

### Sözleşme uyumu
`curl localhost:8080/v3/api-docs`: `listPopularQueries` tag Search, yanıtlar 200/404/500. `PopularQueriesResponse` (`required [queries]`, `maxItems 5`) ve `PopularQueryResponse` (`required [query, searchCount, lastSearchedAt]`, `searchCount int64 minimum 1`) `openapi.yaml` 0.5.0 ile aynı. Workspaces tag metni ve `WorkspaceResponse.name` örneği "Belgelerim" de sözleşmeyle aynı.

### Build
- `./mvnw -q clean compile` → **başarılı** (çıktı yok, exit 0). `clean` gerekiyordu: `target/classes`'ta kalan eski `SeedDataLoader.class` ve seed JSON'ları çalışma anında yeniden yüklenebilirdi.
- `cd backend && ./mvnw verify -Djacoco.haltOnFailure=false` → **BUILD FAILURE, `testCompile` aşamasında** (82 derleme hatası). Hepsi planın kaldırdığı ya da değiştirdiği API'lerden geliyor ve sadece `src/test/**` altında (tester'ın dosyaları):
  - `seed/service/SeedDataReaderTest.java`, `seed/service/SeedDataLoaderIntegrationTest.java`: seed sınıfları yok. Plan bu testleri siliyor.
  - `support/SourceFixtures.java`: `SeedSource` ve `Source.seeded` yok. Plan `Source.ingesting` + `completeIngestion` kullanmayı söylüyor.
  - `search/service/SearchServiceTest.java`: `SearchProperties` kurucusuna 2 yeni alan, `SearchEvent.record`'a 2 yeni parametre eklendi.
- **Ek kontrol, proje dışında:** Backend'i scratchpad'e kopyaladım ve sadece kopyada şunları yaptım: iki seed testini sildim, `SourceFixtures.readySource`'u `Source.ingesting(…)` + `completeIngestion(version, "file-<id>.bin", chunkCount, SYNCED_AT)` ile kurdum, `SearchServiceTest`'teki 2 kurucuya `, 5, 0.49` ve 1 `record` çağrısına `, null, null` ekledim. Kopyada `./mvnw verify -Djacoco.haltOnFailure=false` → `Tests run: 389, Failures: 3, Errors: 2, Skipped: 0`, yani **384 test geçti**. Kalan 5'in hepsi test beklentisi, main kodda hata yok:
  1. `SchemaMigrationIntegrationTest.givenFlywaySchema_whenContextStarts_thenHibernateValidateSucceeds`: 8 migration bekliyor, 10 var.
  2. `NameKeyMigrationIntegrationTest`: V7'ye kadar kuruyor, sonra `target` vermeden migrate ediyor. V9 veriyi sildiği için `[]` görüyor. `target("8")` gerekiyor.
  3. `OpenApiDocumentationIntegrationTest`: path listesinde yeni `/api/workspaces/{workspaceId}/search-events/popular-queries` beklenmiyor.
  4. `SearchIntegrationTest.givenEventStoredWithoutLimit_whenListSearchEvents_thenLimitIsNull` ve 5. `SearchEventStatsIntegrationTest.givenEventsAroundIstanbulDayBoundaries_…`: ham SQL ile `search_events` satırı ekliyorlar, `query_key` vermiyorlar (`NOT NULL` ihlali).

  Kopya silindi; proje içindeki test dosyalarına dokunmadım.

### Ortam durumu
- Dev compose (`embedding-pgvector-dev`: postgres + pgAdmin) **açık**, lead izin verdi. İçinde 42 "Belgelerim", kaynak 10001 (örnek PDF, 24 parça), 1 iş ve 17 arama olayı var.
- 8080'de backend çalışmıyor.
- Tam sistemin volume'una (`embedding-pgvector_pgdata`) dokunmadım; tester'ın yükseltme gözlemi için duruyor.
- Dev veriyi sıfırlamak için: `docker compose -f deployment/docker-compose.dev.yml down -v`.

## Tester için notlar
- **Derleme için önce yapılacaklar** (yukarıdaki listeyle aynı): seed testlerini sil, `SourceFixtures`'u yeniden kur, `SearchProperties` kurucularına `popularQueryLimit, popularMaxDistance` ekle (sıra: `statsTimeZone`'dan sonra), `SearchEvent.record(...)` çağrılarının sonuna `keywordCount, bestDistance` ekle. Sonra kalan 5 beklentiyi güncelle. Ham SQL insert'lerinde `query_key` ver ya da `SearchEvent.record` + repository kullan.
- **Eşik:** `search.popular-max-distance=0.49`. B6'daki sınır testleri plana göre `0.45` ile yazılacaksa testte `@TestPropertySource(properties = "search.popular-max-distance=0.45")` ile sabitle, yoksa 0.46'lık olay sayılır.
- **E4:** "␣␣PAROLAMI unuttum hesabım kilitlendi␣" kelime kolunda 0 sonuç veriyor, sadece mesafeyle (0.4256 ≤ 0.49) sayılıyor. Küçük harfli "parolamı…" kelime koluyla da sayılıyor (kw 1). E4'ün beklediği liste ve sıra bu değerlerle tutuyor.
- **B2/B9 anahtar örnekleri** (canlıda doğrulandı): "Yıllık izin kaç gün?" → `yillik izin kaç gün?`; "Masraf beyanı kaç gün içinde yapılır?" → `masraf beyani kaç gün içinde yapilir?`. `\s` Java'da ASCII boşluklarını eşler (tab dahil). `strip()` baş/sondaki Unicode boşluklarını da kırpar.
- **B3:** Örnek PDF 24 parçaya bölünüyor (plan `>= 22` diyor). Bölüm metinleri "5. Yıllık izin ve diğer izinler", "6. Masraf ve harcırah", "14. Bilgi teknolojileri destek" (parola bu bölümde). Parça başlıkları sayfa başlığı biçiminde ("s. 7 · Atlaskap Lojistik A.Ş. · Kurumsal…").
- **B1 / altyapı:** `PostgresIntegrationTest.truncateTables()` her testten önce `workspaces` dahil bütün tabloları boşaltıyor. Bu yüzden V9'un eklediği (42, Belgelerim) satırı bu testlerde görünmez, testlerin kendi alanlarını eklemesi çakışmaz (kopyada geçti). B1'in kendi container'ını kullanması ve truncate yapmaması bu yüzden gerekli. Temiz kurulumda `chunks` id'leri 1'den başlıyor (canlıda 1-24).
- **Yükseltme gözlemi sorgusu:** `max(version)` yerine `max(version::int)` kullan (bkz. Doğrulama).
- **B13 / sıralama seam'i:** Sıra `count DESC, max(created_at) DESC, max(id) DESC`. Aynı `created_at` değerinde eşitliği id bozuyor (B7 "C/D"). `created_at` `SearchEvent.record`'a verilen `Instant`'tan geliyor, `Clock` gerekmiyor.
- Zaman ve eşzamanlılık açısından yeni bir nokta yok. `listPopularQueries` salt okuma yapıyor, transaction açmıyor (diğer `SearchService` okumaları gibi).

## Düzeltme

STATUS: DONE
