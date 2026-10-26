<!--
AMAÇ: Claude (ve ekip üyeleri) backend/ altında bir dosyayı okuyunca ya da düzenleyince yüklenir.
İçerik: harita, komutlar, dosya sahipliği, Boot 4 tuzakları, iş → skill yönlendirmesi.
Kuralların ayrıntısı backend-conventions skill'inde. Orada yazanı burada tekrar etme; iki kopya zamanla çelişir.
Durum: iskelet TASK-001'de kuruldu. Yeni dependency, plugin ya da komut eklenince "Teknoloji" ve "Komutlar" bölümlerini güncelle.
-->

# Backend (Spring Boot)

## Teknoloji
- Java 26, Spring Boot 4.1.1 (`spring-boot-starter-parent`), Maven 3.10.0 (wrapper `./mvnw` kullanılır, sistemdeki `mvn` değil)
- groupId `com.embedding`, artifactId `embedding-pgvector-backend`, kök paket `com.embedding.pgvector`, uygulama sınıfı `EmbeddingPgvectorApplication`
- Dependency'ler: `spring-boot-starter-webmvc`, `spring-boot-starter-restclient` (`RestClient` + `JdkClientHttpRequestFactory`), `spring-boot-starter-validation`, `spring-boot-starter-actuator`, `spring-boot-starter-data-jpa` (Hibernate 7.4.5.Final), `org.hibernate.orm:hibernate-vector` (7.4.5.Final, Boot BOM'undan, hibernate-core ile aynı sürüm), `spring-boot-starter-flyway` + `org.flywaydb:flyway-database-postgresql`, `org.postgresql:postgresql` (runtime), `org.apache.pdfbox:pdfbox:3.0.6` (`pdfbox.version`), `org.jspecify:jspecify:1.0.1`, `org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1`
- Test dependency'leri: `spring-boot-starter-test`, `spring-boot-starter-webmvc-test`, `spring-boot-starter-data-jpa-test` (`@DataJpaTest`), `spring-boot-testcontainers`, `org.testcontainers:testcontainers-junit-jupiter`, `org.testcontainers:testcontainers-postgresql` (Testcontainers 2.x, Boot BOM'undan; paket `org.testcontainers.postgresql.PostgreSQLContainer`), `org.awaitility:awaitility`
- Plugin'ler: `maven-dependency-plugin` (`properties` goal'ü Mockito agent yolunu verir), surefire (`@{argLine} -javaagent:<mockito-core>`), JaCoCo 0.8.15 (`check`: satır coverage en az %80, `verify` fazında), PIT `pitest-maven` 1.30.1 + `pitest-junit5-plugin` 1.2.3 (sadece komutla çalışır, build'e bağlı değil). JaCoCo ve PIT bu sürümlerle Java 26 class dosyalarını destekler.
- Veritabanı: PostgreSQL 18 + pgvector 0.8.1 (imaj `pgvector/pgvector:0.8.1-pg18`; testlerde de aynı imaj). Şema Flyway ile `src/main/resources/db/migration` (`V1`-`V10`); `spring.jpa.hibernate.ddl-auto=validate` (Hibernate şemayı değiştirmez, açılışta entity ↔ tablo uyumunu doğrular), `spring.jpa.open-in-view=false`. Bağlantı: `DATABASE_URL` (varsayılan `jdbc:postgresql://localhost:5432/embedding_pgvector`), `POSTGRES_USER` (varsayılan `embedding`), `POSTGRES_PASSWORD` (varsayılan `embedding-local`; local geliştirme compose'u `deployment/docker-compose.dev.yml` ile aynı şifre, sadece bu makinede kullanılır). Kaynak adı tekilliği `sources.name_key` kolonundadır: değeri Java'da `Source.toNameKey(name)` hesaplar, unique index `uk_sources_workspace_id_name_key (workspace_id, name_key)`; DB'de `lower()` ile karşılaştırma yapılmaz. `V9` her veritabanında bir kez çalışır: eski kurulumun bütün verisini siler (`TRUNCATE … RESTART IDENTITY CASCADE`) ve varsayılan çalışma alanını (42, "Belgelerim") ekler; temiz kurulumda sadece 42'yi ekler. Çalışma alanı oluşturan endpoint ya da açılış kodu yok. `V10` `search_events`'e `query_key`, `keyword_count`, `best_distance` ekler. `flyway_schema_history.version` metin kolonudur; son sürümü `max(version::int)` ile sorgula (`max(version)` '9' döner).
- Veri erişimi: Spring Data JPA + entity'ler (`Workspace`, `Source`, `Chunk`, `IngestionJob`, `SearchEvent`); `JdbcClient`/`JdbcTemplate` yok. Entity'ler setter'sız, domain fiilleriyle değişir; ilişkiler `LAZY`. PostgreSQL'e özgü sorgular (FTS, `<=>`, hybrid RRF CTE'si, `set_config`, `EXPLAIN`, kökler, `nextval`) repository'de `@Query(nativeQuery = true)` + interface projection; projection alias'ları çift tırnaklı camelCase (`AS "chunkId"`). Entity'de türetilmiş getter varsa (`Source.getWorkspaceId()`, `Chunk/IngestionJob.getSourceId()`) Spring Data bunu özellik sanar; bu alanlarla sorgu derived değil açık JPQL `@Query` ile yazılır.
- Id'ler: `Workspace`, `Source` atanmış id + `Persistable<Long>` (kullanıcı kaynakları `SourceRepository.nextSourceId()` → `sources_id_seq`, 10001'den); `Chunk` `SEQUENCE` `chunks_id_seq` (`allocationSize = 50`, DB `INCREMENT BY 50`) + `hibernate.jdbc.batch_size=100`, `order_inserts=true` (toplu insert); `IngestionJob`, `SearchEvent` `IDENTITY` (batch insert yok).
- Vektör: `Chunk.embedding` `@JdbcTypeCode(SqlTypes.VECTOR) @Array(length = 1024) float[]` (hibernate-vector) ile yazılır, okuma projection'larında seçilmez. Native arama sorgularında sorgu vektörü metin literal'i olarak bağlanır (`CAST(:queryEmbedding AS vector)`, `search/service/VectorLiteral.format`).
- Arka plan işleri: `IngestionExecutorConfig` → `ThreadPoolTaskExecutor ingestionExecutor` (`ingestion.worker-threads`, `ingestion.queue-capacity`); içe alma işleri bu havuzda çalışır. `@Async` yok.
- Başlangıç verisi yok: temiz kurulumda tek, boş çalışma alanı (42, "Belgelerim") vardır. Örnek belge: kök dizinde `ornek-dokuman/Atlaskap-Lojistik-Surec-El-Kitabi.pdf` (otomatik yüklenmez).
- "Sık aranan" (`GET …/search-events/popular-queries`): arama olayları `SearchEvent.toQueryKey(query)` anahtarıyla gruplanır (boşluklar teke iner, sonra `Source.toNameKey` kuralı). Sayılan olay: `result_count >= 1` ve (`keyword_count >= 1` ya da `best_distance <= search.popular-max-distance`). Eşik `search.popular-max-distance=0.49` (TASK-006'da örnek belgeyle ölçüldü), liste sınırı `search.popular-query-limit=5`; ikisi de `application.properties`'te, kod değişmeden ayarlanır.
- Dış servis: lokal Ollama (`embedding.base-url`, varsayılan `http://localhost:11434`; `embedding.model`, varsayılan `bge-m3`; `embedding.dimensions`, varsayılan `1024`). Ortam değişkenleri: `OLLAMA_BASE_URL`, `EMBEDDING_MODEL`, `EMBEDDING_DIMENSIONS`. Parçalara yazılan model etiketi `model@dimensions` (ör. `bge-m3@1024`). Boyut 1024 dışına çıkamaz: kolon `vector(1024)` (`Chunk.EMBEDDING_DIMENSIONS`). 1024 olmayan bir `EMBEDDING_DIMENSIONS` ya da başka boyutta vektör dönen bir model seçilirse açılışta uyarı logu yazılır ("Embedding dimensions differ from embedding column"). İçe alma işi `FAILED` + `EMBEDDING_MODEL_MISMATCH` ile biter, vektör araması 409 `EMBEDDING_MODEL_MISMATCH` döner. Başka boyutlu model için yeni migration (kolon boyutu) ve bütün parçaların yeniden embedding'i gerekir.
- Henüz olmayanlar: WebSocket/STOMP, Spring Security. Bunları varmış gibi kod yazma; eklenmeleri planda yazmalıdır. Skill'lerdeki örnekler kullansa bile önce `pom.xml`'e bak.

## Paket yapısı
Kök paket `com.embedding.pgvector`. Önce özellik, özelliğin içinde katman. Kökte katman paketi (`controllers`, `services`) ve `common` paketi açılmaz. Ayrıntı: `backend-conventions` bölüm 3 "Paketler".
```
<kök paket>
├── <feature>/           ör. document, search
│   ├── controller/      REST controller'lar
│   ├── service/         iş akışı
│   ├── repository/      Spring Data repository'leri
│   └── domain/
│       ├── entity/      entity'ler, enum'lar
│       └── dto/         Request ve Response record'ları
├── config/              uygulama yapılandırması
└── exception/           GlobalExceptionHandler, BaseException ve kategori exception'ları
```
- Katman sırası: controller → service → repository. Controller iş kuralı içermez; domain nesnesi controller'dan dışarı çıkmaz, DTO (`record`) döner.
- Testler aynı paket yapısıyla `src/test/java` altında.

## Komutlar
Hepsi `backend/` içinde çalıştırılır.
- Derle: `./mvnw -q compile`
- Derle + tüm testler + coverage eşiği: `./mvnw verify` (JaCoCo satır coverage %80'in altındaysa build kırılır). Veritabanı testleri Testcontainers ile çalışır; Docker açık olmalıdır.
- Coverage eşiğini kırmadan çalıştır (yeni kodun testleri henüz yokken, backend-coder için): `./mvnw verify -Djacoco.haltOnFailure=false`. Rapor yine üretilir, eşik sadece uyarı olarak yazılır.
- Coverage raporu: `target/site/jacoco/index.html` (`verify` sonrası; ham veri `target/jacoco.exec`)
- Mutation testi (PIT): `./mvnw test-compile org.pitest:pitest-maven:mutationCoverage` → rapor `target/pit-reports/index.html`
- Tek test sınıfı: `./mvnw test -Dtest=EmbeddingServiceTest`
- Tek test metodu: `./mvnw test -Dtest='EmbeddingServiceTest#givenClientReturnsEmbedding_whenEmbedText_thenReturnsModelDimensionsEmbeddingAndDuration'`
- Veritabanını başlat (geliştirme): kök dizinde `docker compose -f deployment/docker-compose.dev.yml up -d` (postgres + pgAdmin; ortam değişkeni ya da `.env` gerekmez). Tam sistem (`deployment/docker-compose.yml`) açıksa önce onu durdur: ikisi de 5432'yi kullanır.
- Çalıştır: `./mvnw spring-boot:run` (terminali bloklar; arka planda başlat, iş bitince durdur) → `curl -s localhost:8080/actuator/health` → `{"groups":["liveness","readiness"],"status":"UP"}`. Açılışta Flyway migration'ları uygulanır (eski kurulumda ilk açılışta `V9` eski veriyi siler, log: `Migrating schema "public" to version "9 - reset data and insert default workspace"`).
- Test kaynakları derlenmeden çalıştır (test derlemesi kırıkken): `./mvnw spring-boot:run -Dmaven.test.skip=true`
- Başka veritabanı, model, Ollama adresi ya da port ile çalıştır: `DATABASE_URL=jdbc:postgresql://localhost:55432/embedding_pgvector EMBEDDING_MODEL=bge-m3 OLLAMA_BASE_URL=http://localhost:11434 SERVER_PORT=8081 ./mvnw spring-boot:run`
- Embedding isteği: `curl -s localhost:8080/api/embeddings -H 'Content-Type: application/json' -d '{"text":"Yıllık izin kaç gün?"}'`
- Örnek belgeyi yükle (yeni kaynak): `curl -s -i -F name=Atlaskap-Lojistik-Surec-El-Kitabi -F file=@../ornek-dokuman/Atlaskap-Lojistik-Surec-El-Kitabi.pdf localhost:8080/api/workspaces/42/sources` → 202 + `Location` (temiz kurulumda kaynak 10001, iş 1)
- Dosya yükleme (yeni sürüm): `curl -s -i -F file=@../ornek-dokuman/Atlaskap-Lojistik-Surec-El-Kitabi.pdf -F version=2026.1 localhost:8080/api/workspaces/42/sources/10001/versions` → 202 + `Location` (iş durumu adresi)
- Arama: `curl -s localhost:8080/api/workspaces/42/search -H 'Content-Type: application/json' -d '{"query":"Yıllık izin kaç gün?","mode":"HYBRID"}'`
- Arama kayıtları göstergeleri (bugün, sonuçsuz ve az sonuç oranları; günler `search.stats-time-zone`, Europe/Istanbul): `curl -s localhost:8080/api/workspaces/42/search-events/stats`
- "Sık aranan" (en çok tekrarlanan, sonuç bulan en fazla 5 arama): `curl -s localhost:8080/api/workspaces/42/search-events/popular-queries` → `{"queries":[{"query":…,"searchCount":…,"lastSearchedAt":…}]}`
- Kaynak ve son içe alma işi (yenilemeden sonra süren işi bulmak için): `curl -s localhost:8080/api/workspaces/42/sources/10001` → `latestJob` alanında kaynağın en yeni işi (`stage`, `progress`, `errorCode`, `statusUrl`); hiç işi olmayan kaynakta `null`
- Tek parça (arama sonucundan açılan): `curl -s localhost:8080/api/workspaces/42/sources/10001/chunks/1` (yoksa 404 `CHUNK_NOT_FOUND`)
- Swagger UI: `http://localhost:8080/swagger-ui.html`. Üretilen sözleşme: `curl -s localhost:8080/v3/api-docs.yaml` (`docs/api/openapi.yaml` ile karşılaştırmak için)
- Testleri lokal Ollama kapalıymış gibi çalıştır: `OLLAMA_BASE_URL=http://127.0.0.1:9 ./mvnw verify`
- Geliştirirken tek sınıf ya da metot çalıştır. Handoff'a `STATUS: DONE` yazmadan önce `./mvnw verify` baştan sona geçmeli.

## Dosya sahipliği
| Yol | Sahibi |
|-----|--------|
| `src/main/**`, `pom.xml`, `.gitignore`, `mvnw*`, `.mvn/**` | backend-coder |
| `src/test/**` | tester |
| `Dockerfile`, `.dockerignore` | devops |

- `pom.xml`'i sadece backend-coder değiştirir. Tester'a test dependency'si gerekiyorsa backend-coder'a mesaj atar.
- Tester testi geçirmek için `src/main` altında değişiklik yapmaz; bulduğu hatayı raporlar.

## Kurallar
- Bağlayıcı kurallar `backend-conventions` skill'indedir: dil, yorum politikası (her üst seviye tipte Türkçe amaç Javadoc'u, metot yorumu yok), isimlendirme, test adı ve AAA yapısı, pagination. backend/ altında kod ya da test yazmadan önce bu skill'i yükle. Başka bir skill'in örneği bununla çelişirse `backend-conventions` geçerlidir.
- Endpoint path'i, HTTP method'u, DTO alanları ve status kodları `docs/api/openapi.yaml` ile birebir aynıdır. Sözleşmede olmayan endpoint ya da alan eklenmez.
- Her domain hatası kendi exception'ı ve sabit bir `errorCode` ile RFC 9457 response'una dönüşür (`problem-details-rfc9457`).
- Initializr'dan gelen dosyalar tab ile girintili olabilir. Dokunduğunda 4 boşluğa çevir.

## Spring Boot 4 tuzakları
Eğitim verisindeki eski API'ler burada derlenmez ya da yanlış çalışır.
- `@MockBean` yok → `@MockitoBean` (`org.springframework.test.context.bean.override.mockito`)
- Controller testi `MockMvcTester` ile yazılır; eski `mockMvc.perform(...).andExpect(...)` zinciri değil
- Test slice anotasyonları modüler `*-test` starter'larından gelir; paketleri değişti (`spring-testing-fundamentals`)
- Jackson 3: `tools.jackson.databind.json.JsonMapper`. `com.fasterxml.jackson.databind.ObjectMapper` yok
- ProblemDetail varsayılan olarak kapalıdır: `spring.mvc.problemdetails.enabled=true`
- Yapılandırma `application.properties` ile yapılır; YAML kullanılmaz
- `@DataJpaTest` testlerinde okuma assertion'ından önce `TestEntityManager.flush()` + `clear()`; yoksa test L1 cache'ten okur
- `IDENTITY` id'li entity'lerde Hibernate batch insert yapmaz (toplu yazılan `Chunk` bu yüzden `SEQUENCE`)
- Mockito, surefire'da `-javaagent` ile yüklenir. JaCoCo/PIT eklenirse agent'ları `@{argLine}` üzerinden birleşir; `argLine` ezilmez

## Hangi iş → hangi skill
| İş | Skill |
|----|-------|
| Her kod ve test | `backend-conventions` (zorunlu) |
| Endpoint, service, katmanlar | `springboot-patterns`, `spring-boot-engineer`, `java-coding-standards` |
| Hata response'u | `problem-details-rfc9457` |
| STOMP / WebSocket | `spring-websocket`; testi `spring-websocket-testing` |
| Paylaşılan durum, eşzamanlılık, async iş | `java-concurrency` |
| Girdi doğrulama, güvenlik | `springboot-security` |
| Unit test | `spring-testing-fundamentals`, `writing-good-tests`, `springboot-tdd` |
| Controller testi | `spring-mvc-testing` |
| Transaction, kilitleme | `spring-transactions` |
| JPA, PostgreSQL, pgvector, migration | `jpa-patterns`, `spring-jpa-testing`, `postgres-patterns`, `flyway-migrations`, `sql-optimization-patterns` |
| Swagger / OpenAPI | `spring-boot-openapi-documentation` |
| Loglama, tracing | `observability-tracing` |
| Review ve doğrulama | `java-code-review`, `code-review-and-quality`, `springboot-verification` |
| Hata ayıklama | `systematic-debugging` |
