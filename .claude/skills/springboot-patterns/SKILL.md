---
name: springboot-patterns
description: Spring Boot mimari pattern'leri - katmanlı yapı (controller → service → repository), REST API tasarımı, DTO ve validation, pagination ve sıralama, caching, async işleme, filter'lar, dış çağrılarda retry ve production varsayılanları. Spring Boot backend'inde yeni bir feature/endpoint eklerken, katmanlar arası sorumluluk dağılımına karar verirken veya mevcut bir REST katmanını, service'i ya da async işi incelerken kullan.
license: MIT
metadata:
  origin: ECC
  source: https://github.com/affaan-m/ECC/tree/main/skills/springboot-patterns
---

# Spring Boot Geliştirme Pattern'leri

Ölçeklenebilir, production seviyesinde servisler için Spring Boot mimari ve API pattern'leri.

## Ne zaman devreye girer

- Spring MVC ile REST API yazarken
- Controller → service → repository katmanlarını yapılandırırken
- Spring Data JPA, caching veya async işleme yapılandırırken
- Validation, pagination veya event-driven pattern eklerken
- dev/test/prod profile'larını kurarken

İlgili skill'ler: hata response'ları `problem-details-rfc9457`, transaction'lar `spring-transactions`, loglama/tracing `observability-tracing`, WebSocket `spring-websocket`, isimlendirme ve pagination standardı `backend-conventions`.

## Paket yapısı (feature-first)

```
com.embedding.pgvector
├── source/           SourceController, SourceService, SourceRepository, domain/entity (Source, SourceKind, SourceStatus), domain/dto (SourceResponse)
├── ingestion/        IngestionService, IngestionJobRunner, IngestionRecovery, parsing/, chunking/, IngestionJob
├── search/           SearchController, SearchService, ChunkRetrievalService, ChunkSearchRepository (native keyword/vector/hybrid sorguları)
├── chunk/ workspace/ seed/
├── embedding/        client/ (EmbeddingClient, OllamaEmbeddingClient, EmbeddingProperties), controller/, service/
├── config/ exception/ pagination/
└── EmbeddingPgvectorApplication.java
```

Ayrıntılı ağaç ve kurallar: `backend-conventions` bölüm 3 "Paketler".

## REST API yapısı

```java
@RestController
@RequestMapping("/api/workspaces/{workspaceId}/sources")
public class SourceController {

    private final SourceService sourceService;
    private final IngestionService ingestionService;

    public SourceController(SourceService sourceService, IngestionService ingestionService) {
        this.sourceService = sourceService;
        this.ingestionService = ingestionService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<IngestionJobResponse> createSource(@PathVariable long workspaceId,
            @RequestPart(name = "name", required = false) @NotBlank @Pattern(regexp = NAME_SIZE_PATTERN) @Nullable String name,
            @RequestPart(name = "file", required = false) @NotEmptyFile @Nullable MultipartFile file) {
        IngestionJobResponse job = ingestionService.createSource(workspaceId, Objects.requireNonNull(name), Objects.requireNonNull(file));
        return ResponseEntity.accepted().location(URI.create(job.statusUrl())).body(job);
    }

    @GetMapping("/{sourceId}")
    public SourceResponse getSource(@PathVariable long workspaceId, @PathVariable long sourceId) {
        return sourceService.getSource(workspaceId, sourceId);
    }
}
```

- Controller ince: request'i al, validate et, service'i çağır, response dön. İş kuralı yok, `@Transactional` yok, repository erişimi yok.
- Controller entity döndürmez; her zaman response DTO.
- Uzun süren iş başlatan `POST` (dosya yükleme) `202 Accepted` + `Location` (iş durumunun adresi) + iş DTO'su döner; istemci işi `GET .../ingestion-jobs/{jobId}` ile sorgular. Senkron kaynak oluşturan bir `POST` olsaydı `201` + `Location` dönerdi. Arama (`POST /api/workspaces/{workspaceId}/search`) kaynak oluşturmadığı için `200` döner (`spring-boot-engineer/references/web.md`).

## Repository (Spring Data JPA)

```java
public interface SourceRepository extends JpaRepository<Source, Long> {

    @Query("select s from Source s where s.workspace.id = :workspaceId and s.id = :id")
    Optional<Source> findByWorkspaceIdAndId(@Param("workspaceId") long workspaceId, @Param("id") long id);

    @Query(value = "select s from Source s where s.workspace.id = :workspaceId order by s.id",
            countQuery = "select count(s) from Source s where s.workspace.id = :workspaceId")
    Page<Source> findByWorkspaceIdOrderByIdAsc(@Param("workspaceId") long workspaceId, Pageable pageable);
}
```

- `Source.getWorkspaceId()` türetilmiş bir getter olduğu için Spring Data onu özellik sanar; ilişki üzerinden sorgular açık JPQL ile yazılır (`s.workspace.id`).
- Keyword (FTS), vektör (`<=>`) ve hybrid (RRF CTE'si) sorguları native'dir ve `ChunkSearchRepository`'de interface projection döner: `spring-boot-engineer/references/data.md` "Benzerlik sorgusu".

## Service katmanı

```java
@Service
@Transactional(readOnly = true)
public class SourceService {

    private final WorkspaceService workspaceService;
    private final SourceRepository sourceRepository;
    private final IngestionJobRepository ingestionJobRepository;

    // constructor injection

    public Page<SourceResponse> listSources(long workspaceId, int page, int size) {
        workspaceService.ensureWorkspaceExists(workspaceId);
        Page<Source> sources = sourceRepository.findByWorkspaceIdOrderByIdAsc(workspaceId, PageRequest.of(page, size));
        Map<Long, IngestionJob> latestJobs = findLatestJobs(sources.getContent());
        return sources.map(source -> SourceResponse.from(source, latestJobs.get(source.getId())));
    }

    public SourceResponse getSource(long workspaceId, long sourceId) {
        Source source = getExistingSource(workspaceId, sourceId);
        return SourceResponse.from(source, findLatestJobs(List.of(source)).get(source.getId()));
    }
}
```

- Sayfadaki kaynakların son işleri tek sorguyla alınır (`IngestionJobRepository.findLatestBySourceIds`); kaynak başına ayrı sorgu (N+1) yapılmaz.
- Dosya yükleme ve içe alma `IngestionService`'tedir (`spring-boot-engineer` "Service"). Eşzamanlı kaynak eklemenin ve aynı kaynağa iki işin kilitleme stratejisi `spring-transactions`'tadır.

Arama service'i orkestrasyon yapar: sorguyu `EmbeddingClient` ile vektöre çevirir (transaction dışında), moda göre keyword, vector ya da hybrid sorgusunu çalıştırır. Hybrid modda iki kolun sıraları RRF ile tek bir native SQL sorgusunda (CTE) birleştirilir (`1 / (rrfK + keywordRank) + 1 / (rrfK + vectorRank)`); RRF davranışı elle hesaplanmış sıralarla gerçek pgvector üzerinde test edilir.

## DTO ve validation

```java
public record SearchChunksRequest(
        @NotBlank(message = "must not be blank") @Size(max = 500, message = "size must be at most 500 characters") @Nullable String query,
        @Pattern(regexp = "(?i)keyword|vector|hybrid", message = "must be one of KEYWORD, VECTOR, HYBRID") @Nullable String mode,
        @Min(value = 1, message = "must be between 1 and 50") @Max(value = 50, message = "must be between 1 and 50") @Nullable Integer limit) {
}

public record SearchResultResponse(String query, SearchMode mode, /* ...ayarlar, sayılar, süreler... */ List<SearchHitResponse> hits) {

    public SearchResultResponse {
        hits = List.copyOf(hits);
    }
}
```

- DTO şekillerinin kesin kaynağı `docs/api/openapi.yaml`'dır; sabit kurallar `backend-conventions` bölüm 3'te.
- Request/response DTO'ları `record`. Entity → DTO dönüşümü DTO'da statik `from(...)`.
- Multipart endpoint'lerde request DTO'su yoktur; alanlar `@RequestPart` parametresidir.
- Validation hataları global handler'da `ProblemDetail`'e çevrilir (`problem-details-rfc9457`). Controller'da try/catch yok.

## Pagination ve sıralama

```java
@GetMapping
public PageResponse<SourceResponse> listSources(@PathVariable long workspaceId,
        @RequestParam(name = "page", defaultValue = "0") @Min(value = 0, message = PageParameters.PAGE_MESSAGE) int page,
        @RequestParam(name = "size", defaultValue = "20") @Min(value = 1, message = PageParameters.SIZE_MESSAGE)
        @Max(value = PageParameters.MAX_SIZE, message = PageParameters.SIZE_MESSAGE) int size) {
    return PageResponse.from(sourceService.listSources(workspaceId, page, size));
}
```

`Page<T>` doğrudan JSON'a serialize edilmez; service `Page<XResponse>` döner, controller `PageResponse.from(...)` ile sarar. `sort` parametresi yoktur, sıra repository sorgusunda sabittir; geçersiz `page`/`size` `VALIDATION_FAILED` döner (bkz. `backend-conventions` bölüm 6). Arama sonucu sayfalı değildir; `limit` ile sınırlıdır.

## Caching

`@EnableCaching` gerektirir.

```java
@Cacheable(value = "queryEmbeddings", key = "#model + ':' + #normalizedQuery")
public float[] embedQuery(String model, String normalizedQuery) {
    return embeddingClient.embed(model, normalizedQuery);
}
```

Embedding API'si ücretli ve yavaş olduğu için aynı sorgunun (normalize edilmiş metin + model) vektörü cache'lenebilir; cache key'inde model mutlaka yer alır (model değişince eski vektörler anlamsızdır). Cache sınırlı boyutlu ve süreli olur (Caffeine, plan kararı). Arama sonuçları cache'lenmez: kaynak eklendikçe değişir.

## Async işleme

`@EnableAsync` gerektirir. `spring.threads.virtual.enabled=true` ile `@Async` virtual thread kullanır.

```java
@Service
public class IngestionService {

    @Async
    public CompletableFuture<IngestionResult> ingest(long sourceId) {
        return CompletableFuture.completedFuture(chunkAndEmbed(sourceId));
    }
}
```

Aynı sınıf içinden `@Async` metot çağırma (proxy devreye girmez). İçe alma, kaynak oluşturma transaction'ı commit edildikten sonra (`@TransactionalEventListener(phase = AFTER_COMMIT)`) başlatılır. Embedding API'sine paralel istek sayısı sınırlanır (rate limit). Trace context propagation için `observability-tracing` bölüm 6.

## Filter'lar

```java
@Component
public class RequestTimingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestTimingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
            log.debug("Request completed method={} uri={} status={} durationMs={}",
                    request.getMethod(), request.getRequestURI(), response.getStatus(), durationMs);
        }
    }
}
```

HTTP metrikleri ve trace'ler zaten otomatik üretilir; bu filter'ı sadece ek bir ihtiyaç varsa yaz. Sıradan servlet filter'ı `@Component` olabilir; Spring Security filter chain'ine eklenecek filter ise `@Component` yapılmaz (iki kez çalışır, bkz. `springboot-security`).

## Dış çağrılar

- HTTP client olarak `RestClient` kullan; connect/read timeout'larını her zaman ayarla.
- Retry için Spring Framework 7'nin `@Retryable` + `@EnableResilientMethods`'u (bkz. `spring-transactions`); elle `while` + `Thread.sleep` retry döngüsü yazma.
- Dış çağrıyı transaction içinde yapma (embedding API'si dahil).
- Embedding istemcisi parçaları toplu (batch) gönderir; 429/5xx'te backoff ile retry, kalıcı hatada kaynak `FAILED` olur ve `EMBEDDING_UNAVAILABLE` döner.

## Production varsayılanları

- Constructor injection; field injection (`@Autowired` field) yok.
- `spring.mvc.problemdetails.enabled=true` (Boot 4.1'de varsayılanı `false`)
- `spring.jpa.open-in-view=false`
- HikariCP pool boyutu küçük (varsayılan 10) ve timeout'lar ayarlı (bkz. `postgres-patterns`)
- Okuma yollarında `@Transactional(readOnly = true)`
- Secret'lar environment variable'dan (`${DB_PASSWORD}`, `${EMBEDDING_API_KEY}` placeholder'ı); değerin kendisi `application.properties`'te yok
- Actuator'da sadece `health`, `info` (ve gerekiyorsa `prometheus`) expose edilir

**Unutma**: Controller'lar ince, service'ler odaklı, repository'ler basit, hatalar merkezi yönetilir.
