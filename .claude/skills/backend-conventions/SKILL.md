---
name: backend-conventions
description: Embedding ve pgvector projesinin backend'i için bağlayıcı proje kuralları - kod dili (İngilizce), yorum politikası (her sınıfın üstünde kısa Türkçe amaç yorumu zorunlu, metot üstüne yorum yok), sınıf/metot/değişken/paket/REST isimlendirmesi, domain sözlüğü (workspace, source, chunk, embedding, search), vektör kolonu ve benzerlik sorgusu kuralları, test isimlendirmesi (given_when_then) ve AAA test yapısı, pagination standardı (PageResponse). backend/ altında herhangi bir Java kodu veya test yazarken, isimlendirmeye karar verirken, pagination'lı endpoint eklerken ya da kod review yaparken kullan. Diğer skill'lerdeki örneklerle çelişirse bu skill geçerlidir.
---

# Backend Proje Kuralları

Bu kurallar diğer tüm skill'lerin örneklerinden önce gelir.

## 1. Dil

- Tüm kod İngilizce: sınıf, metot, değişken, paket, enum, sabit, log mesajı, exception mesajı, test adı, commit edilen config anahtarları ve kod içi yorumlar (bölüm 2'nin izin verdiği tek satırlık "neden" yorumu dahil). Tek istisna bölüm 2'deki sınıf yorumudur; o Türkçe yazılır. Skill'lerdeki örneklerde görülen Türkçe yorumlar sadece açıklama içindir; projeye yazılmaz.
- Türkçe karakter veya Türkçe kelime identifier'larda yok (`kaynak`, `parca`, `aramaSorgusu` yanlış → `source`, `chunk`, `query`).
- Domain sözlüğü: çalışma alanı (kiracı) → `workspace`, bilgi kaynağı → `source`, kaynak türü → `sourceKind` (`WEB_CRAWL`, `PDF`, `MARKDOWN`, `CSV`, `TICKET_EXPORT`), kaynak durumu → `sourceStatus` (`INGESTING`, `READY`, `FAILED`), parça → `chunk`, parçalama → `chunking`, embedding (vektör) → `embedding`, embedding modeli → `embeddingModel`, vektör boyutu → `dimensions`, içe alma işi → `ingestionJob`, içe alma aşaması → `ingestionStage` (`QUEUED`, `PARSING`, `CHUNKING`, `EMBEDDING`, `WRITING`, `DONE`, `FAILED`), arama sorgusu → `query`, arama modu → `searchMode` (`KEYWORD`, `VECTOR`, `HYBRID`), arama sonucu → `searchHit`, benzerlik skoru → `score`, mesafe → `distance`, sıralama birleştirme → `rankFusion` (RRF: `reciprocalRankFusion`), isabet oranı → `recall`.
- Kullanıcıya gösterilen metinler bu kuralın dışındadır; dili kök `CLAUDE.md`'deki genel kurallar belirler. Orada aksi yazmadıkça: backend hata response'unda `detail` İngilizce kalır, istemci kararını sabit `errorCode` üzerinden verir ve kullanıcıya gösterilecek Türkçe metni ("Kaynak bulunamadı" gibi) frontend `errorCode`'a göre üretir (bkz. `problem-details-rfc9457`).

## 2. Yorum politikası

- Her üst seviye tipin (class, interface, record, enum, annotation; main ve test) üstünde **Türkçe**, 1-2 satırlık bir Javadoc (`/** ... */`) bulunur. Sınıfın ne işe yaradığını ve ne için kullanıldığını söyler; alanları, metotları veya uygulama adımlarını saymaz. `@author`, `@since` gibi tag yok.
  ```java
  /**
   * Çalışma alanındaki kaynakları sayfa sayfa listeler ve tek bir kaynağı
   * en son içe alma işiyle birlikte getirir.
   */
  @Service
  public class SourceService {
  ```
- Metot üstüne yorum **yazılmaz**: Javadoc (`/** ... */`) yok, `//` veya `/* */` ile metot açıklaması yok. İç içe (nested) tiplere yorum yazılmaz.
- Kod kendini isimlerle anlatır. Açıklama gerekiyorsa önce metodu/değişkeni yeniden adlandır veya küçük bir metoda ayır.
- Metot gövdesinde yorum sadece koddan çıkarılamayan bir **neden** için ve tek satır (ör. bir workaround'un sebebi). Ne yaptığını anlatan yorum yok.
- Testlerdeki `// Arrange`, `// Act`, `// Assert` işaretleri bu kuralın istisnasıdır (bölüm 5).
- Yorum satırına alınmış (commented-out) kod bırakılmaz.

## 3. İsimlendirme

### Sınıflar (PascalCase, isim)

| Rol | Kalıp | Örnek |
|-----|-------|-------|
| Entity / domain | Tekil isim, ek yok | `Workspace`, `Source`, `Chunk` (kaynağın parçası; `SourceChunk` değil) |
| REST controller | `<Feature>Controller` | `SourceController`, `SearchController` |
| WebSocket controller | `<Feature>MessageController` | `IngestionMessageController` |
| Service | `<Feature>Service` | `SourceService`, `SearchService` |
| Repository | `<Entity>Repository` | `SourceRepository`, `ChunkRepository` |
| Request DTO | `<Verb><Noun>Request` (JSON gövde) | `SearchChunksRequest`. Multipart endpoint'lerde (dosya yükleme) DTO yok; alanlar `@RequestPart` parametresidir |
| Response DTO | `<Noun>Response` (fiil yok: `CreateSourceResponse` değil) | `SourceResponse`, `SearchHitResponse`, `SearchResultResponse` |
| WebSocket payload | `<Noun>Message` | `IngestionProgressMessage` |
| Domain event | `<Noun><PastVerb>Event` (projede henüz yok; commit sonrası iş `TransactionSynchronization` ile başlatılıyor) | `IngestionCompletedEvent` |
| Exception | Sadece HTTP kategorisi: `<Category>Exception`. Hata başına sınıf açılmaz; hatayı `errorCode` ayırt eder | `NotFoundException`, `ConflictException` |
| Config / properties | `<Topic>Config`, `<Topic>Properties` | `WebSocketConfig`, `EmbeddingProperties`, `SearchProperties` |
| Dış servis istemcisi | `<Topic>Client` | `EmbeddingClient` |
| Mapper / listener / interceptor | `<Noun>Mapper`, `<Noun>Listener`, `<Noun>Interceptor` | `SourceMapper` |

- `*Impl` sadece aynı interface'in birden fazla implementasyonu varsa (ör. `EmbeddingClient` için gerçek model istemcisi ve testte deterministik sahte istemci). Tek implementasyon için interface açma.
- `Manager`, `Helper`, `Util`, `Processor`, `Data`, `Info` gibi belirsiz isimler yok.

### Metotlar (camelCase, fiil ile başlar)

| Anlam | Kalıp | Örnek |
|-------|-------|-------|
| Oluştur | `create<Noun>` | `createSource` |
| Tekil getir, yoksa exception | `get<Noun>` | `getSource(sourceId)` |
| Tekil getir, yoksa boş | `find<Noun>` → `Optional` | `findSource(sourceId)` |
| Liste / sayfa | `list<Nouns>` | `listSources(workspaceId, pageable)` |
| Güncelle | `update<Noun>` veya domain fiili | `renameSource`, `startIngestion` |
| Sil | `delete<Noun>` | `deleteSource` |
| Arama | `search<Nouns>` | `searchChunks(workspaceId, request)` |
| Boolean | `is`, `has`, `can`, `exists` | `isUploadable()`, `hasSuccessfulVersion()`, `existsBySourceIdAndStageNotIn(...)` |
| Dönüşüm | `to<Type>`, statik `from(...)`, `of(...)` | `SourceResponse.from(source)` |

- Controller metot adı, çağırdığı service metodu ile aynı.
- Spring Data repository metotları derived query isimlendirmesine uyar (`IngestionJobRepository.findByWorkspaceIdAndId`, `existsBySourceIdAndStageNotIn`); property path entity alanlarıyla birebir eşleşmelidir. Türetilmiş getter'ı olan alanlarda (`Source.getWorkspaceId()` aslında `workspace.id`) Spring Data yanlış özelliği bulur; bu sorgular açık JPQL `@Query` ile yazılır (`select s from Source s where s.workspace.id = :workspaceId and s.id = :id`). Vektör benzerliği gibi derived query ile yazılamayan sorgular `@Query(nativeQuery = true)` ile yazılır (bölüm 3 "Veritabanı").
- Kısaltma yok: `source` (`src` değil), `request` (`req` değil), `embedding` (`emb`/`vec` değil), `exception` (`e`/`ex` sadece tek satırlık lambda'da kabul).
- Koleksiyonlar çoğul (`chunks`, `hits`), boolean'lar `is/has/can` ile (`isReady`).
- Sabitler `UPPER_SNAKE_CASE` (`MAX_SOURCES_PER_WORKSPACE`, `EMBEDDING_DIMENSIONS`). Magic number yok; `k`, `ef_search`, RRF `k=60` gibi arama parametreleri `SearchProperties`'ten gelir.

### Paketler

Kök `com.embedding.pgvector`. Paket adları küçük harf ve tekil. Önce özellik, özelliğin içinde katman:

```
com.embedding.pgvector
├── source/                  (workspace, chunk, search, ingestion aynı iç yapıyla)
│   ├── controller/          SourceController
│   ├── service/             SourceService
│   ├── repository/          SourceRepository
│   └── domain/
│       ├── entity/          Source, SourceKind, SourceStatus
│       ├── dto/             Request, Response (ve WebSocket gelince STOMP Message) record'ları
│       └── event/           (ihtiyaç olunca) IngestionCompletedEvent
├── ingestion/               Ek olarak parsing/ (dosyadan metin) ve chunking/ (parçalama)
├── embedding/
│   ├── client/              EmbeddingClient, OllamaEmbeddingClient, EmbeddingProperties (model adı, boyut)
│   ├── controller/, service/, domain/dto/   POST /api/embeddings
├── seed/                    Başlangıç verisini yükleyen servis
├── pagination/              PageResponse
├── config/                  ClockConfig, OpenApiConfig (WebSocket gelince WebSocketConfig)
├── exception/               GlobalExceptionHandler, BaseException, NotFoundException,
│                            ConflictException, UnauthorizedException, ForbiddenException
└── websocket/               (ilk WebSocket task'ında açılır) IngestionProgressBroadcaster, StompErrorHandler
```

- Özellik paketinde exception paketi yoktur; hatalar kökteki `exception` paketindeki kategori sınıflarıyla fırlatılır (yukarıdaki isimlendirme tablosu, `problem-details-rfc9457`).
- Kökte katman-first paket (`controllers`, `services`) ve `common` paketi açılmaz. Birden fazla özelliğin kullandığı DTO (ör. `PageResponse`) ilk ihtiyaç duyan task'ta kökte kendi paketinde açılır (`pagination/`).
- İhtiyacı olmayan alt paket boş olarak açılmaz (ör. event'i olmayan özellikte `domain/event` yok).
- Her paketin `package-info.java` dosyası `@NullMarked` taşır.
- Testler aynı paket yapısıyla `src/test/java` altındadır; test, test ettiği sınıfla aynı pakettedir.

### REST ve JSON

- Path: `/api/<çoğul-kebab-case>` (`/api/workspaces/{workspaceId}/sources`, `/api/workspaces/{workspaceId}/search`). Versiyon öneki (`/api/v1`) yok. Fiil path'te değil HTTP method'unda; arama, sorgu gövdesi uzun ve filtreli olabildiği için `POST .../search` ile yapılır (kaynak oluşturmaz, `200` döner).
- Path variable ve query param isimleri camelCase (`{workspaceId}`, `{sourceId}`, `?kind=`). Kimlik her yerde (REST path, STOMP destination, `@PathVariable`/`@DestinationVariable` parametresi, DTO alanı) `<entity>Id` adını taşır (`sourceId`); entity alanı `Source.id` olarak kalır. Pagination için sadece standart `page`, `size`, `sort` kullanılır (bölüm 6).
- JSON alanları camelCase. Tarih/saat ISO-8601 (`Instant`).
- Response şekillerinin tek doğru kaynağı `docs/api/openapi.yaml`'dır; DTO record'ları ona birebir uyar ve aynı kavram her endpoint'te aynı DTO ile döner. Bu skill şekilleri tekrar etmez, sadece sabit kuralları yazar:
  - Arama: `SearchResultResponse` içinde `List<SearchHitResponse> hits`. Lab eğitim amaçlı olduğu için her sonuç kollarını ayrı gösterir: `keywordRank`/`keywordScore` (`ts_rank_cd`), `vectorRank`/`distance` (cosine mesafe `<=>`, küçük olan daha yakın), `rrfScore` (sadece `HYBRID`). Bir kolun bulmadığı sonuçta o kolun alanları `null`'dır; `matchedBy` hangi kolların bulduğunu söyler.
  - Kaynak: `SourceResponse` (`version`, `chunkCount`, `status`, `syncedAt`, `fileName`, `latestJob` …). İçe alma ilerlemesi kaynakta değil, işte durur: `IngestionJobResponse` (`stage`, `progress`, `embeddedChunks`, `totalChunks`, `errorCode`), `GET /api/workspaces/{workspaceId}/ingestion-jobs/{jobId}` ile sorgulanır. `SourceResponse.latestJob` aynı şekildedir; sayfa yenilenince süren işi ve son başarısızlığın nedenini frontend buradan bulur. WebSocket eklenirse STOMP payload'ının alanları `IngestionJobResponse`'un aynı adlı alanlarıyla aynı tipte olur (`frontend-conventions` bölüm 8).
- Vektörün kendisi (1024 sayı) response'a konmaz; gerekiyorsa sadece boyutu ve model etiketi döner (`embeddingModel`, `dimensions`). Tek istisna `POST /api/embeddings`'tir: işi bir metnin vektörünü göstermektir (TASK-001).
- Enum değerleri `UPPER_SNAKE_CASE`.

### Veritabanı

snake_case, çoğul tablo adları (`workspaces`, `sources`, `chunks`). PK `bigint` identity, zaman `timestamptz` (Java'da `Instant`), metin `text` + `CHECK`. Ayrıntı: `postgres-patterns`; migration: `flyway-migrations`.

- pgvector eklentisi ilk migration'da açılır: `CREATE EXTENSION IF NOT EXISTS vector;`.
- Embedding kolonu boyutuyla yazılır: `chunks.embedding vector(1024) NOT NULL` (model `bge-m3`; boyut `embedding.dimensions` / `EmbeddingProperties.dimensions` ile aynı, model değişirse yeni boyutlu kolon ve yeniden embedding gerekir). Embedding'i üreten model `chunks.embedding_model text NOT NULL` kolonunda saklanır; sorgu ile doküman aynı modelle çevrilmemişse sonuçlar anlamsızdır, bu yüzden arama sadece aktif modelin satırlarında yapılır.
- Benzerlik araması: cosine mesafe operatörü `<=>`, `ORDER BY embedding <=> :queryEmbedding LIMIT :k`. Index HNSW + `vector_cosine_ops`; index ve sorgu operatörü aynı mesafe ailesinden olmazsa index kullanılmaz. Çok kiracılı filtre (`workspace_id = :workspaceId`) ile birlikte iterative index scan ayarı ve filtre seçiciliği için `postgres-patterns`'e bak.
- Keyword kolu `tsvector` + GIN index ile yazılır; hybrid arama iki kolun sıralarını RRF ile birleştirir (puanları değil sıraları).

Kaynak kuralları (bir çalışma alanında en fazla 10 kaynak, çalışma alanı içinde büyük/küçük harf duyarsız tekil kaynak adı) `Workspace`'in kendisindedir; service sadece orkestrasyon yapar. Kalıcı veri PostgreSQL'dedir. İçe alma işi de (`IngestionJob`, aşaması ve işlenen parça sayısı dahil) `ingestion_jobs` tablosunda JPA entity olarak tutulur; böylece sayfa yenilense ya da backend yeniden başlasa da iş bilgisi kaybolmaz (yarıda kalan iş açılışta `INGESTION_INTERRUPTED` ile kapatılır).

- Entity'ler mutable'dır ve `protected` no-arg constructor taşır. Değişiklik domain metoduyla yapılır: `workspace.addSource(name, kind)` → eklenen `Source`.
- "Kontrol et + değiştir" atomikliği: transaction içinde `WorkspaceRepository.findByIdForUpdate` (`PESSIMISTIC_WRITE`) ile çalışma alanı satırı kilitlenir. `(workspace_id, lower(name))` unique index son güvencedir (`spring-transactions`).
- Düz `findById` + `save` yarış koşuludur: `sources` koleksiyonu `mappedBy` (inverse) olduğundan kaynak eklemek `Workspace`'in `@Version`'ını artırmaz, optimistic lock iki eşzamanlı eklemeyi yakalamaz. Kaynak ekleme her zaman `findByIdForUpdate` ile yapılır.
- Ad karşılaştırması Java'da ve veritabanında aynı sonucu vermelidir. `toLowerCase(Locale.ROOT)` ile PostgreSQL `lower()` Türkçe `İ`/`I` için farklı davranabilir; uyuşmazlıkta kontrol entity'yi geçer ve unique index'te patlar.

## 4. Kod stili

- Constructor injection; `@Autowired` field injection yok (istisna: test sınıflarında `@Autowired` / `@MockitoBean` field'ları kullanılır). Lombok kullanılmaz.
- DTO'lar `record`. Entity'ler controller'dan dışarı çıkmaz.
- `Optional` sadece dönüş tipi olarak; parametre ve field olarak yok.
- Logger: `private static final Logger log = LoggerFactory.getLogger(X.class);`. Mesaj formatı: İngilizce cümle + `key=value` alanları (`log.info("Chunks embedded sourceId={} chunkCount={} model={}", sourceId, count, model)`); sorgu metni ve parça içeriği loglanmaz (kullanıcı verisidir), sadece uzunluğu ve kimlikler loglanır. Ayrıntı `observability-tracing`.
- Yapılandırma `application.properties` (+ profile'a özel `application-<profile>.properties`) ile yapılır; YAML kullanılmaz. Skill'lerdeki YAML örnekleri aynı anahtarların properties karşılığı olarak okunur. Embedding API anahtarı properties'e yazılmaz, ortam değişkeninden gelir.
- Girinti 4 boşluk. Null anotasyonları JSpecify (`@NullMarked`, `@Nullable`).
- Zaman `Clock` bean'inden okunur: `config` paketindeki `ClockConfig`'te `@Bean Clock clock() { return Clock.systemUTC(); }` tanımlıdır, kod `Instant.now(clock)` kullanır (`Instant.now()` değil). İstisna: Hibernate'in istediği entity alan başlangıç değerleri (`spring-jpa-testing/references/hibernate-6-migration.md` 5.6) gibi framework workaround'ları; iş kuralına giren zaman her zaman `Clock`'tan okunur. Testte `FixedClockTestConfiguration` ile sabitlenir (`spring-boot-engineer/references/testing.md`).
- Embedding modeli dış bir servistir: kod `EmbeddingClient` interface'ine bağlıdır, testte aynı metne her zaman aynı vektörü dönen deterministik sahte istemci verilir. Unit ve integration testleri gerçek model API'sine gitmez.
- `@Component` yerine uygun stereotype: iş mantığı `@Service`, veri `@Repository` (Spring Data interface'lerinde gerekmez), HTTP `@RestController`, STOMP `@Controller`. Listener, interceptor, mapper gibi altyapı sınıfları `@Component` olabilir.
- JSON için Jackson 3 (`tools.jackson.databind.json.JsonMapper`); `com.fasterxml.jackson.databind.ObjectMapper` kullanılmaz (anotasyonlar `com.fasterxml.jackson.annotation` paketinde kalır).
- Hata response'ları: `problem-details-rfc9457` (`spring.mvc.problemdetails.enabled=true`; varsayılanı `false`).

## 5. Testler

### İsimlendirme

Format: `given<Precondition>_when<Action>_then<ExpectedResult>`

```
givenWorkspaceWithTenSources_whenCreateSource_thenThrowsSourceLimitReached
givenSourceNameUsedWithDifferentCase_whenCreateSource_thenThrowsSourceNameTaken
givenVectorMode_whenSearchChunks_thenReturnsHitsOrderedByScoreDescending
givenUnknownSourceId_whenGetSource_thenReturns404ProblemDetail
```

- Her parça PascalCase kelimelerle, parçalar arasında tek `_`.
- `when` kısmı test edilen metodu/aksiyonu, `then` kısmı gözlemlenen sonucu söyler.
- `@DisplayName` kullanılmaz; metot adı yeterli.
- Test sınıfları: unit → `<ClassUnderTest>Test`, integration → `<ClassUnderTest>IntegrationTest` (ikisi de surefire tarafından çalıştırılır).

### Gövde yapısı: AAA

```java
@Test
void givenWorkspaceWithTenSources_whenCreateSource_thenThrowsSourceLimitReached() {
    // Arrange
    Workspace workspace = WorkspaceFixtures.workspaceWithSources(42L, 10);
    given(workspaceRepository.findByIdForUpdate(42L)).willReturn(Optional.of(workspace));
    given(fileTypeDetector.detect(eq("fatura-sss.md"), any())).willReturn(SourceKind.MARKDOWN);
    MockMultipartFile file = new MockMultipartFile("file", "fatura-sss.md", "text/markdown", "# Fatura SSS".getBytes(UTF_8));

    // Act
    Throwable thrown = catchThrowable(() -> ingestionService.createSource(42L, "Fatura SSS", file));

    // Assert
    assertThat(thrown).isInstanceOf(ConflictException.class)
            .extracting("errorCode").isEqualTo("SOURCE_LIMIT_REACHED");
    then(sourceRepository).should(never()).saveAndFlush(any());
    then(ingestionJobRepository).should(never()).saveAndFlush(any());
}
```

- Örnek kuralın iş akışını gösterir (bölüm 3 "Veritabanı"). Aynı kural doğrudan `Workspace.addSource(...)` üzerinde de unit test edilir.
- Üç bölüm her zaman `// Arrange`, `// Act`, `// Assert` yorumlarıyla ayrılır; Arrange gerekmiyorsa atlanabilir.
- Hata beklenen testte hem kategori sınıfı hem `errorCode` doğrulanır; sadece sınıfa bakmak `SOURCE_LIMIT_REACHED` ile `SOURCE_NAME_TAKEN`'ı ayırt etmez.
- Act bölümü tek bir aksiyon içerir. Exception beklenen durumda `catchThrowable` ile yakala, assert'i Assert bölümünde yap (`assertThatThrownBy` Act ile Assert'i birleştirdiği için kullanılmaz).
- Bir test = bir davranış. Birden fazla assertion aynı davranışı doğruluyorsa serbest.
- Mockito BDD stili: `given(...).willReturn(...)`, `then(mock).should(...)`. `when(...)`/`verify(...)` yok.
- Assertion'lar AssertJ (`assertThat`). JUnit `assertEquals` yok. Skor karşılaştırmalarında `isCloseTo(expected, within(1e-6))`; `double` eşitliği `isEqualTo` ile doğrulanmaz.
- Test verisi için `<Entity>Fixtures` sınıflarında statik factory metotları (`WorkspaceFixtures.workspaceWithSources(...)`, `ChunkFixtures.chunkWithEmbedding(...)`). Builder sınıfı veya `TestDataFactory` açılmaz. Vektör fixture'ları elle hesaplanabilecek küçük, normalize vektörlerdir; beklenen sıralama testte o vektörlerden türetilir.
- `Thread.sleep()` yok (bkz. `java-concurrency`).

### Kapsam

- Unit test: `@ExtendWith(MockitoExtension.class)`, Spring context yok; her iş kuralı için en az bir test (görev dosyasındaki her kural ve örnek senaryo).
- `@SpringBootTest` integration testlerine `@Transactional` eklenmez; veri her testten **önce** temizlenir (`@BeforeEach`). İstisna: `@DataJpaTest` kendi test-başına rollback'ini getirir ve izole sorgu testi için olduğu gibi kullanılır (flush + clear zorunlu); ayrıntı `spring-jpa-testing`.
- Coverage hedefi: satır bazında en az %80 (JaCoCo `check` ile zorunlu, bkz. `springboot-verification`).
- Controller testi: `@WebMvcTest` + `MockMvcTester` (`spring-mvc-testing`).
- Repository / integration: Testcontainers 2.x + `@ServiceConnection`, image pgvector eklentili PostgreSQL (`pgvector/pgvector:<sürüm>-pg18`; düz `postgres` imajında `vector` eklentisi yoktur); H2 kullanılmaz, benzerlik sorguları gerçek pgvector üzerinde test edilir (`spring-jpa-testing`).
- WebSocket: `spring-websocket-testing`.
- Spring Boot 4: `@MockitoBean` (`@MockBean` yok).

## 6. Pagination standardı

- Query param'lar: `page` (0'dan başlar, en az 0) ve `size` (1-100; varsayılan 20, çalışma alanı listesinde 50). `sort` alınmaz: her listenin sırası sabittir ve repository sorgusunda yazılıdır (kaynaklar `id`, parçalar `chunk_no, id`, arama kayıtları `created_at DESC, id DESC`) (TASK-002 D17).
- Geçersiz `page`/`size` sessizce düzeltilmez, `VALIDATION_FAILED` + `violations[].field = "page"`/`"size"` döner. Bu yüzden Spring'in `Pageable` çözümleyicisi (`@PageableDefault`, `max-page-size`; aşan değeri sessizce kırpar) kullanılmaz; parametreler `@RequestParam` + Bean Validation ile alınır, mesaj ve sınırlar `pagination/PageParameters`'tadır.
- Katman sorumluluğu: service `Page<XResponse>` döndürür (`PageRequest.of(page, size)` + repository'nin sabit sıralı sorgusu; entity → DTO dönüşümü service'te), controller `PageResponse.from(...)` ile sarar:

```java
@GetMapping
public PageResponse<SourceResponse> listSources(
        @PathVariable long workspaceId,
        @RequestParam(name = "page", defaultValue = "0")
        @Min(value = 0, message = PageParameters.PAGE_MESSAGE) int page,
        @RequestParam(name = "size", defaultValue = "20")
        @Min(value = 1, message = PageParameters.SIZE_MESSAGE)
        @Max(value = PageParameters.MAX_SIZE, message = PageParameters.SIZE_MESSAGE) int size) {
    return PageResponse.from(sourceService.listSources(workspaceId, page, size));
}
```

- Zamanla sınırsız büyüyebilen koleksiyonları dönen liste endpoint'leri (kaynaklar, bir kaynağın parçaları, arama kayıtları) her zaman sayfalıdır. Arama sonucu sayfalı değildir: `limit` (k, en fazla 50) ile sınırlı düz `List` döner; benzerlik aramasında `OFFSET` ile ikinci sayfa istemek ANN index'iyle tutarsız sonuç verir.
- `Page<T>` doğrudan döndürülmez; proje response tipi:

```java
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
```

- Toplam sayı gerekmiyorsa (sonsuz kaydırma) repository `Slice<T>` döner (count sorgusu yok), controller `SliceResponse<T>(content, page, size, hasNext)` ile sarar (`spring-boot-openapi-documentation/references/pagination-support.md`).
- Çok büyük tablolarda (`chunks`) `OFFSET` yerine cursor/keyset pagination (`postgres-patterns`).
- İleride bir liste sıralanabilir olursa alanları whitelist'le; istemcinin rastgele kolonla sıralamasına izin verme. Bu bir sözleşme değişikliğidir (planner).

## Kontrol listesi

```
- [ ] Tüm identifier ve log mesajları İngilizce; log'da sorgu metni ve parça içeriği yok
- [ ] Her üst seviye tipin üstünde 1-2 satırlık Türkçe amaç Javadoc'u var; metot üstünde yorum veya Javadoc yok, commented-out kod yok
- [ ] Sınıf ve metot isimleri bölüm 3'teki kalıplara ve domain sözlüğüne uyuyor
- [ ] Sınıf doğru pakette (özellik → controller/service/repository/domain; ortak kod kökte config/exception/embedding/websocket); hata başına exception sınıfı yok
- [ ] Vektör kolonu boyutlu, index ve sorgu operatörü aynı mesafe ailesinden, arama sadece aktif embedding modelinin satırlarında
- [ ] Testler given_when_then isimli ve AAA yapısında; gerçek embedding API'sine giden test yok
- [ ] Sınırsız büyüyebilen liste endpoint'leri PageResponse döndürüyor (bölüm 6)
- [ ] Lombok, `@Autowired` field injection, `@MockBean`, `@DisplayName`, Jackson 2 `ObjectMapper` yok
```
