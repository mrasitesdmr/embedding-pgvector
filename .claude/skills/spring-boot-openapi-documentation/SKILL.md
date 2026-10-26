---
name: spring-boot-openapi-documentation
description: Spring Boot 4 uygulamasında SpringDoc OpenAPI 3 ve Swagger UI ile REST API dokümantasyonu üretme pattern'leri. Swagger/OpenAPI kurulurken, Swagger UI yapılandırılırken, controller'a @Operation/@ApiResponse/@Tag eklenirken, DTO'lar @Schema ile belgelenirken, pageable endpoint'ler veya hata response'ları dokümante edilirken ya da kod ile docs/api/openapi.yaml sözleşmesinin uyumu kontrol edilirken kullan.
license: MIT
metadata:
  source: https://github.com/giuseppe-trisciuoglio/developer-kit/tree/main/plugins/developer-kit-java/skills/spring-boot-openapi-documentation
  adapted-for: Spring Boot 4.1 / SpringDoc 3.1
---

# SpringDoc ile Spring Boot OpenAPI Dokümantasyonu

## Proje kuralı (öncelikli)

- **Tek doğru kaynak `docs/api/openapi.yaml`'dır.** Planner günceller; backend-coder endpoint'leri buna birebir uyarak yazar. Annotation'lar sözleşmeyi *tarif eder*, sözleşmeyi değiştirmez.
- Kod ile sözleşme ayrışırsa sözleşmeyi değil kodu düzelt. Sözleşmede eksik ya da hata varsa kendin değiştirme: planner'a ve lead'e mesaj at, raporunda `NEEDS_FIX` ile belirt (kök `CLAUDE.md`).
- Uyum kontrolü: uygulamayı çalıştır, `/v3/api-docs.yaml` çıktısını `docs/api/openapi.yaml` ile path, method, status code, `errorCode` ve schema alanları bazında karşılaştır.
- Hata response'ları `ProblemDetail` (RFC 9457) şemasıyla ve `errorCode` extension'ıyla belgelenir — bkz. `problem-details-rfc9457`. Projeye özel `ErrorResponse` sınıfı açılmaz.
- Annotation metinleri (`summary`, `description`, `example`) İngilizce yazılır (üretilen API dokümanının parçasıdır). Skill'lerdeki açıklama metinleri Türkçedir.
- Swagger annotation'ları controller ve DTO'lardadır; entity'ler API'ye çıkmadığı için `@Schema` ile belgelenmez.
- İsimlendirme, path (`/api/<çoğul>`, `{workspaceId}`, `{sourceId}`), pagination (`PageResponse`) kuralları `backend-conventions`'tadır.

## Hızlı referans

| Konu | Değer |
|------|-------|
| Dependency (WebMVC) | `org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1` (Boot 4.x → SpringDoc 3.x; 3.1.x Boot 4.1'e göre build edilmiştir) |
| Yapılandırma | `application.properties` içinde `springdoc.api-docs.*`, `springdoc.swagger-ui.*` |
| Erişim | JSON `/v3/api-docs`, YAML `/v3/api-docs.yaml`, UI `/swagger-ui/index.html` (`/swagger-ui.html` oraya yönlendirir) |
| Temel annotation'lar | `@Tag`, `@Operation`, `@ApiResponse`, `@Parameter`, `@Schema`, `@ParameterObject` |
| Pagination | `@RequestParam page/size` + `@Min`/`@Max` (`Pageable` yok, `sort` yok), dönüş `PageResponse<T>` |

## Adımlar

### 1. Dependency ekle

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>3.1.1</version>
</dependency>
```

SpringDoc sürümünü Spring Boot yönetmez; sürüm `pom.xml`'de açıkça yazılır. Ayrıntı: [dependency-setup.md](references/dependency-setup.md).

### 2. Yapılandır

```properties
springdoc.api-docs.path=/v3/api-docs
springdoc.swagger-ui.path=/swagger-ui.html
springdoc.swagger-ui.operations-sorter=method
springdoc.swagger-ui.tags-sorter=alpha
springdoc.paths-to-match=/api/**
springdoc.default-produces-media-type=application/json
```

`api-docs.path` varsayılan değerinde (`/v3/api-docs`) bırakılır; uyum kontrolü ve frontend araçları bu yolu kullanır. Production profile'ında `springdoc.api-docs.enabled=false` ve `springdoc.swagger-ui.enabled=false`. Detay: [configuration.md](references/configuration.md).

### 3. Controller'ları belgele

Projedeki gerçek örnek (`SourceController#createSource`, kısaltılmış):

```java
@RestController
@RequestMapping("/api/workspaces/{workspaceId}/sources")
@Tag(name = "Sources", description = "Knowledge sources of a workspace")
public class SourceController {

    @Operation(operationId = "createSource",
            summary = "Create a source from an uploaded file and start its first ingestion job")
    @ApiResponse(responseCode = "202", description = "Job accepted; it runs in the background",
            headers = @Header(name = "Location", description = "URL of the ingestion job (same as statusUrl)",
                    schema = @Schema(type = "string")))
    @ApiResponse(responseCode = "400",
            description = "VALIDATION_FAILED: name missing / blank / not 2-80 characters, file missing or empty",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "SOURCE_NAME_TAKEN, SOURCE_LIMIT_REACHED, EMBEDDING_MODEL_MISMATCH",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "413", description = "FILE_TOO_LARGE: the file is larger than 20 MB",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<IngestionJobResponse> createSource(
            @PathVariable long workspaceId,
            @Parameter(description = "Source name. Stripped; 2-80 characters; unique in the workspace ignoring case.",
                    required = true, schema = @Schema(type = "string", minLength = 2, maxLength = 80, example = "Fatura SSS"))
            @RequestPart(name = "name", required = false) @NotBlank(message = "must not be blank")
            @Pattern(regexp = NAME_SIZE_PATTERN, message = "size must be between 2 and 80") @Nullable String name,
            @Parameter(description = ".pdf, .txt, .md, .markdown or .csv, not empty, at most 20 MB", required = true)
            @RequestPart(name = "file", required = false) @NotEmptyFile @Nullable MultipartFile file) {
        IngestionJobResponse job = ingestionService.createSource(workspaceId, Objects.requireNonNull(name), Objects.requireNonNull(file));
        return ResponseEntity.accepted().location(URI.create(job.statusUrl())).body(job);
    }
}
```

- Her hata status'u için ayrı `@ApiResponse`; `description` o status'ta dönebilecek `errorCode`'ları sayar. Sözleşmedeki `errorCode` enum'u `OpenApiConfig`'te tek listede tutulur.
- `required = false` + validation anotasyonu: eksik alan `VALIDATION_FAILED` döner; ama şemada alanın zorunlu görünmesi için `@Parameter(required = true)` ayrıca yazılır.

Pattern'ler: [controller-documentation.md](references/controller-documentation.md).

### 4. DTO'ları belgele

```java
public record SearchChunksRequest(
        @Schema(description = "Leading and trailing whitespace is stripped; then 1-500 characters (UTF-16 code units).",
                maxLength = 500, example = "Şifremi unuttum, online işlemlere giremiyorum",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "must not be blank")
        @Size(max = 500, message = "size must be at most 500 characters")
        @Nullable String query,

        @Schema(types = {"string", "null"}, pattern = "(?i)keyword|vector|hybrid", example = "HYBRID",
                description = "KEYWORD, VECTOR or HYBRID, case-insensitive. Default: search.default-mode (HYBRID).")
        @Pattern(regexp = "(?i)keyword|vector|hybrid", message = "must be one of KEYWORD, VECTOR, HYBRID")
        @Nullable String mode) {
}
```

`required = true` deprecated'dır; `requiredMode` kullan. Bean Validation annotation'ları şemaya otomatik yansır (`minLength`, `maxLength`, `required`). Nullable alanlar OpenAPI 3.1 biçiminde `types = {"string", "null"}` ile yazılır. Detay: [model-documentation.md](references/model-documentation.md).

### 5. Pagination'ı belgele

```java
@GetMapping
public PageResponse<SourceResponse> listSources(@PathVariable long workspaceId,
        @Parameter(description = "Zero-based page index")
        @RequestParam(name = "page", defaultValue = "0") @Min(value = 0, message = PageParameters.PAGE_MESSAGE) int page,
        @Parameter(description = "Page size")
        @RequestParam(name = "size", defaultValue = "20") @Min(value = 1, message = PageParameters.SIZE_MESSAGE)
        @Max(value = PageParameters.MAX_SIZE, message = PageParameters.SIZE_MESSAGE) int size) {
    return PageResponse.from(sourceService.listSources(workspaceId, page, size));
}
```

`PageResponse` proje standardıdır; `sort` parametresi yoktur (bkz. `backend-conventions` bölüm 6). Detay: [pagination-support.md](references/pagination-support.md).

### 6. Doğrula

Uygulamayı çalıştır (`./mvnw spring-boot:run`), `/swagger-ui/index.html`'i aç ve `curl localhost:8080/v3/api-docs.yaml` çıktısını sözleşmeyle karşılaştır.

## Best practice'ler

- `summary` kısa (< 120 karakter) ve net olsun
- Her endpoint için başarı (2xx) ve görev dosyasındaki her hata (4xx) kodunu, `errorCode`'uyla belgele
- Gerçekçi `example` değerleri ver (`42`, `Fatura SSS`, `Şifremi unuttum`); hassas veri ve gerçek müşteri içeriği koyma
- Endpoint'leri domain'e göre `@Tag` ile grupla
- İç endpoint'leri `@Hidden` ile gizle
- Ortak hata response'larını her metoda tekrar yazmak yerine `OpenApiCustomizer` ile global ekle ([exception-handling.md](references/exception-handling.md))
- Generic tiplerde şema üretimi bozulabilir; gerekirse açık `@Schema(implementation = ...)` ver
- DTO'larda döngüsel referans bırakma (sonsuz şema recursion'ı)

## Referanslar

- [dependency-setup.md](references/dependency-setup.md) — Maven dependency ve sürüm seçimi
- [configuration.md](references/configuration.md) — temel ve ileri yapılandırma
- [controller-documentation.md](references/controller-documentation.md) — controller ve endpoint dokümantasyonu
- [model-documentation.md](references/model-documentation.md) — DTO ve validation dokümantasyonu
- [pagination-support.md](references/pagination-support.md) — Pageable, `PageResponse`, Slice
- [exception-handling.md](references/exception-handling.md) — `ProblemDetail` hata şemaları
- [advanced-configuration.md](references/advanced-configuration.md) — API grupları, customizer'lar, `OpenAPI` bean'i
- [security-configuration.md](references/security-configuration.md) — JWT, OAuth2, API key şemaları (MVP'de auth yok)
- [build-integration.md](references/build-integration.md) — Maven plugin ve CI entegrasyonu
- [complete-examples.md](references/complete-examples.md) — uçtan uca örnek
- [annotations-reference.md](references/annotations-reference.md) — tüm annotation'lar ve attribute'ları
- [troubleshooting.md](references/troubleshooting.md) — sık sorunlar ve çözümleri

Not: Referanslardaki kod örnekleri proje kurallarına uyar; annotation metinleri İngilizcedir. Örneklerdeki Türkçe yorumlar sadece açıklama içindir; projeye yazılan kodda yorum İngilizcedir (tek istisna sınıf üstündeki Türkçe amaç Javadoc'u) ve `backend-conventions` bölüm 2'ye tabidir.
