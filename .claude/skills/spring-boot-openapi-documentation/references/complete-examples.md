# Uçtan Uca Örnek: Source ve Search API

Kaynak ekleme ve arama için controller, DTO, OpenAPI bean'i ve yapılandırmanın birlikte nasıl göründüğünü gösterir. Path ve alan adları `docs/api/openapi.yaml` ile birebir aynı tutulur.

## Controller ve DTO'lar

Projedeki gerçek kod en güncel ve doğrulanmış uçtan uca örnektir; yeni bir endpoint belgelerken önce bunlara bak:

| Ne | Dosya |
|----|-------|
| Multipart yükleme, `202` + `Location`, hata status'ları başına `@ApiResponse` | `backend/src/main/java/com/embedding/pgvector/source/controller/SourceController.java` |
| JSON gövdeli `POST` (`200`, kaynak oluşturmaz) | `.../search/controller/SearchController.java` + `.../search/domain/dto/SearchChunksRequest.java` |
| Nullable alanlar (`types = {"string", "null"}`), iç içe nullable DTO (`latestJob`) | `.../source/domain/dto/SourceResponse.java`, `.../ingestion/domain/dto/IngestionJobResponse.java` |
| Sayfalı liste (`page`/`size`, `sort` yok) | `SourceController#listSources`, `.../chunk/controller/ChunkController.java` |
| `ProblemDetail` şemasına `errorCode` enum'u ve `violations[]` ekleme | `.../config/OpenApiConfig.java` |

Kısa özet (`SourceController#createSource`):

```java
@Operation(operationId = "createSource",
        summary = "Create a source from an uploaded file and start its first ingestion job")
@ApiResponse(responseCode = "202", description = "Job accepted; it runs in the background",
        headers = @Header(name = "Location", description = "URL of the ingestion job (same as statusUrl)",
                schema = @Schema(type = "string")))
@ApiResponse(responseCode = "409", description = "SOURCE_NAME_TAKEN, SOURCE_LIMIT_REACHED, EMBEDDING_MODEL_MISMATCH",
        content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ResponseEntity<IngestionJobResponse> createSource(@PathVariable long workspaceId,
        @Parameter(description = "Source name. Stripped; 2-80 characters; unique in the workspace ignoring case.",
                required = true, schema = @Schema(type = "string", minLength = 2, maxLength = 80, example = "Fatura SSS"))
        @RequestPart(name = "name", required = false) @NotBlank @Pattern(regexp = NAME_SIZE_PATTERN) @Nullable String name,
        @Parameter(description = ".pdf, .txt, .md, .markdown or .csv, not empty, at most 20 MB", required = true)
        @RequestPart(name = "file", required = false) @NotEmptyFile @Nullable MultipartFile file) {
    IngestionJobResponse job = ingestionService.createSource(workspaceId, Objects.requireNonNull(name), Objects.requireNonNull(file));
    return ResponseEntity.accepted().location(URI.create(job.statusUrl())).body(job);
}
```

Arama sonucu her kolun değerini ayrı taşır (`keywordRank`, `keywordScore`, `vectorRank`, `distance`, `rrfScore`); tek bir "büyük olan daha iyi" `score` alanı yoktur. Embedding vektörü sadece `POST /api/embeddings` yanıtında yer alır (`backend-conventions` bölüm 3).

## OpenAPI Bean'i

```java
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    public OpenApiCustomizer problemDetailSchemaCustomizer() {
        return openApi -> {
            openApi.info(new Info().title(API_TITLE).version(API_VERSION));
            // ProblemDetail şemasına errorCode enum'u ve violations[] eklenir
        };
    }
}
```

`title` ve `version`, `docs/api/openapi.yaml`'daki `info` bloğuyla aynıdır (`Embedding ve pgvector API`, sözleşmeyle birlikte artan sürüm). Hata şeması customizer'ı aynı config sınıfına eklenir ([exception-handling.md](exception-handling.md)). Projede auth olmadığı için security scheme tanımlanmaz.

## application.properties

```properties
springdoc.api-docs.path=/v3/api-docs
springdoc.swagger-ui.path=/swagger-ui.html
springdoc.swagger-ui.operations-sorter=method
springdoc.swagger-ui.tags-sorter=alpha
springdoc.paths-to-match=/api/**
springdoc.default-produces-media-type=application/json
```

## pom.xml (ilgili kısım)

```xml
<properties>
    <java.version>26</java.version>
    <springdoc.version>3.1.1</springdoc.version>
</properties>

<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webmvc</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springdoc</groupId>
        <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
        <version>${springdoc.version}</version>
    </dependency>
</dependencies>
```

Parent `spring-boot-starter-parent` 4.1.1'dir (bkz. `backend/pom.xml`).

## Spring Security Eklenirse

Swagger yolları `permitAll` olmalıdır:

```java
.authorizeHttpRequests(auth -> auth
        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
        .anyRequest().authenticated())
```

Security scheme dokümantasyonu: [security-configuration.md](security-configuration.md).
