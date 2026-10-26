# Web Katmanı - Controller'lar ve REST API'ler

> İsimlendirme, path ve pagination kuralları `backend-conventions`'dadır. Hata response'ları `problem-details-rfc9457`, Swagger annotation'ları `spring-boot-openapi-documentation`.

## REST Controller Pattern'i

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

    @GetMapping
    public PageResponse<SourceResponse> listSources(@PathVariable long workspaceId,
            @RequestParam(name = "page", defaultValue = "0") @Min(value = 0, message = PageParameters.PAGE_MESSAGE) int page,
            @RequestParam(name = "size", defaultValue = "20") @Min(value = 1, message = PageParameters.SIZE_MESSAGE)
            @Max(value = PageParameters.MAX_SIZE, message = PageParameters.SIZE_MESSAGE) int size) {
        return PageResponse.from(sourceService.listSources(workspaceId, page, size));
    }

    @GetMapping("/{sourceId}")
    public SourceResponse getSource(@PathVariable long workspaceId, @PathVariable long sourceId) {
        return sourceService.getSource(workspaceId, sourceId);
    }

    @PostMapping(path = "/{sourceId}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<IngestionJobResponse> uploadSourceVersion(@PathVariable long workspaceId,
            @PathVariable long sourceId,
            @RequestPart(name = "file", required = false) @NotEmptyFile @Nullable MultipartFile file,
            @RequestPart(name = "version", required = false) @Pattern(regexp = VERSION_SIZE_PATTERN, message = "size must be at most 40")
            @Nullable String version) {
        IngestionJobResponse job = ingestionService.uploadSourceVersion(workspaceId, sourceId, version, Objects.requireNonNull(file));
        return ResponseEntity.accepted().location(URI.create(job.statusUrl())).body(job);
    }
}

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @PostMapping
    public SearchResultResponse searchChunks(@PathVariable long workspaceId,
            @Valid @RequestBody SearchChunksRequest request) {
        return searchService.searchChunks(workspaceId, request);
    }
}
```

- Controller metot adı, çağırdığı service metodu ile aynıdır.
- `Page<T>` veya Spring HATEOAS `PagedModel` döndürülmez; proje tipi `PageResponse<T>`.
- Arama `POST`'tur ama kaynak oluşturmaz: `200 OK`, `Location` yok.
- Dosya yükleme `202 Accepted` + `Location` (iş durumunun adresi, `statusUrl`) + `IngestionJobResponse` döner; iş arka planda sürer, istemci `GET .../ingestion-jobs/{jobId}` ile sorgular.
- `@RequestParam`/`@RequestPart`/`@PathVariable` üzerindeki constraint'ler (`@Min`, `@Pattern`, `@NotEmptyFile`) Spring Framework 7'nin yerleşik method validation'ı ile çalışır; ihlal `HandlerMethodValidationException` olarak `GlobalExceptionHandler`'a gelir ve `VALIDATION_FAILED` + `violations[]` döner.

## Validation İçeren Request DTO'ları

```java
public record SearchChunksRequest(
        @NotBlank(message = "must not be blank") @Size(max = 500, message = "size must be at most 500 characters") @Nullable String query,
        @Pattern(regexp = "(?i)keyword|vector|hybrid", message = "must be one of KEYWORD, VECTOR, HYBRID") @Nullable String mode,
        @Min(value = 10, message = "must be between 10 and 1000") @Max(value = 1000, message = "must be between 10 and 1000") @Nullable Integer efSearch,
        @Min(value = 1, message = "must be between 1 and 50") @Max(value = 50, message = "must be between 1 and 50") @Nullable Integer limit) {

    public SearchChunksRequest {
        query = query == null ? null : query.strip();
    }
}
```

- İsteğe bağlı alanlar `@Nullable` sarmalayıcı tiptir (`Integer`, `String`); varsayılanı service `SearchProperties`'ten uygular. Enum alanları `String` + `@Pattern` ile alınır: geçersiz değer Jackson hatası değil, alan adıyla `VALIDATION_FAILED` olur.
- Validation mesajları İngilizce ve sabittir (`message` her constraint'te açık yazılır; varsayılan mesaj locale'e göre değişebilir). Kullanıcıya gösterilecek Türkçe metni frontend üretir (bkz. `backend-conventions` bölüm 1).
- "Sadece boşluk olamaz" gibi kurallar `@NotBlank` ile karşılanır; büyük/küçük harf duyarsız kaynak adı tekilliği gibi iş kuralları validation'da değil domain'de/service'te kontrol edilir.
- Sorgu metni embed edilmeden önce service'te normalize edilir (baştaki/sondaki boşluk, çoklu boşluk); uzunluk sınırı embedding modelinin token sınırının altında kalacak şekilde seçilir.

## Response DTO'ları

```java
public record SourceResponse(long sourceId, long workspaceId, String name, SourceKind kind, @Nullable String version,
        int chunkCount, SourceStatus status, @Nullable Instant syncedAt, @Nullable String fileName, Instant createdAt,
        @Nullable IngestionJobResponse latestJob) {

    public static SourceResponse from(Source source, @Nullable IngestionJob latestJob) {
        return new SourceResponse(source.getId(), source.getWorkspaceId(), source.getName(), source.getKind(),
                source.getVersion(), source.getChunkCount(), source.getStatus(), source.getSyncedAt(),
                source.getFileName(), source.getCreatedAt(), latestJob == null ? null : IngestionJobResponse.from(latestJob));
    }
}

public record SearchResultResponse(String query, SearchMode mode, @Nullable FilterStrategy filterStrategy,
        @Nullable Integer efSearch, int limit, int returnedCount, /* ...sayılar ve süreler... */ List<SearchHitResponse> hits,
        @Nullable String vectorPlan) {

    public SearchResultResponse {
        hits = List.copyOf(hits);
    }
}
```

- Tarih/saat `Instant` (ISO-8601). `LocalDateTime` API'de kullanılmaz.
- JSON'da kimlikler `<entity>Id` (`sourceId`, `chunkId`). Arama sonucunda her kolun değeri ayrı döner (`keywordRank`/`keywordScore`, `vectorRank`/`distance`, `rrfScore`); embedding vektörü response'a konmaz (`backend-conventions` bölüm 3). Alanların tam listesi `docs/api/openapi.yaml`'dadır.

## Global Exception Handling

Tek bir `GlobalExceptionHandler` (`ResponseEntityExceptionHandler`'ı extend eder) projenin tüm HTTP hata eşlemesini yapar. Şablon ve kurallar: `problem-details-rfc9457/templates/GlobalExceptionHandler.java`. Validation hataları `violations` extension'ıyla döner.

## Özel Validation

```java
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = NotOnlyPunctuationValidator.class)
public @interface NotOnlyPunctuation {

    String message() default "Query must contain at least one letter or digit";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

public class NotOnlyPunctuationValidator implements ConstraintValidator<NotOnlyPunctuation, String> {

    private static final Pattern LETTER_OR_DIGIT = Pattern.compile("[\\p{L}\\p{N}]");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || LETTER_OR_DIGIT.matcher(value).find();
    }
}
```

Veritabanına giden kontroller (ör. "bu kaynak adı çalışma alanında alınmış mı") validator'da değil service'te yapılır; validator'dan repository çağırmak eşzamanlı isteklerde yarış koşulu yaratır (bkz. `spring-transactions`).

## Dış API'ler İçin RestClient (embedding servisi)

Dependency: `spring-boot-starter-restclient`. Timeout'lar `spring.http.clients.connect-timeout` / `spring.http.clients.read-timeout` ile ayarlanır.

```java
@Configuration(proxyBeanMethods = false)
public class EmbeddingClientConfig {

    @Bean
    RestClient embeddingRestClient(RestClient.Builder builder, EmbeddingProperties embeddingProperties) {
        return builder
                .baseUrl(embeddingProperties.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + embeddingProperties.apiKey())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}

@Service
public class HttpEmbeddingClient implements EmbeddingClient {

    private final RestClient embeddingRestClient;
    private final EmbeddingProperties embeddingProperties;

    public HttpEmbeddingClient(RestClient embeddingRestClient, EmbeddingProperties embeddingProperties) {
        this.embeddingRestClient = embeddingRestClient;
        this.embeddingProperties = embeddingProperties;
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        EmbeddingApiResponse response = embeddingRestClient.post()
                .uri("/embeddings")
                .body(new EmbeddingApiRequest(embeddingProperties.model(), texts, embeddingProperties.dimensions()))
                .retrieve()
                .onStatus(HttpStatusCode::is5xxServerError, (request, httpResponse) -> {
                    throw new ServiceUnavailableException("EMBEDDING_UNAVAILABLE", "Embedding service returned " + httpResponse.getStatusCode());
                })
                .body(EmbeddingApiResponse.class);
        return response.vectors();
    }
}
```

- Base URL, model adı, boyut ve API anahtarı hardcode edilmez; `EmbeddingProperties` (`@ConfigurationProperties`) record'undan gelir, anahtar ortam değişkeninden.
- Dönen her vektörün boyutu `embeddingProperties.dimensions()` ile karşılaştırılır; uyuşmazsa yazılmaz (kolon `vector(1024)` zaten reddeder, hata erken ve açık verilir).
- Request/response loglaması için interceptor yazma; `RestClient` çağrıları zaten otomatik trace edilir (`observability-tracing`). Gövde (metin, vektör) loglanmaz.
- Dış çağrı transaction içinde yapılmaz; retry gerekiyorsa `@Retryable` (`spring-transactions`).
- Testlerde gerçek API'ye gidilmez: `EmbeddingClient`'ın deterministik sahte implementasyonu kullanılır (`backend-conventions` bölüm 4).

## CORS Configuration

Spring Security yokken CORS `WebMvcConfigurer` ile, origin listesi properties'ten:

```java
@Configuration(proxyBeanMethods = false)
public class WebConfig implements WebMvcConfigurer {

    private final CorsProperties corsProperties;

    public WebConfig(CorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(corsProperties.allowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                .maxAge(3600);
    }
}
```

- Production'da `*` yok. WebSocket endpoint'inin origin ayarı ayrıdır (`spring-websocket`).
- Spring Security eklenirse CORS security filter seviyesine taşınır (`springboot-security`).

## Hızlı Başvuru

| Anotasyon | Amaç |
|------------|---------|
| `@RestController` | Sınıfı REST controller olarak işaretler (`@Controller` + `@ResponseBody`) |
| `@RequestMapping` | HTTP isteklerini handler metotlarına map'ler |
| `@GetMapping/@PostMapping` | HTTP metoduna özel mapping'ler |
| `@PathVariable` | URI yolundan değer alır |
| `@RequestParam` | Query parametrelerini alır |
| `@RequestBody` | İstek gövdesini metot parametresine bağlar |
| `@Valid` | İstek gövdesinde validation'ı tetikler |
| `@RestControllerAdvice` | REST controller'lar için global exception handling |
| `@ResponseStatus` | Metodun HTTP durum kodunu belirler |
