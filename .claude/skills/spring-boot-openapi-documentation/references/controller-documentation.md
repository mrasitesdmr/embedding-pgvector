# Controller Dokümantasyon Pattern'leri

Controller'lar ince kalır (service'i çağırır, DTO döner); annotation'lar sadece sözleşmeyi tarif eder. Path ve isimler `docs/api/openapi.yaml` ile birebir aynıdır.

## Temel Controller Dokümantasyonu

```java
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/sources")
@Tag(name = "Sources", description = "Knowledge sources of a workspace")
public class SourceController {

    private final SourceService sourceService;

    public SourceController(SourceService sourceService) {
        this.sourceService = sourceService;
    }

    @Operation(summary = "Get a source with its ingestion progress",
            description = "Returns the source name, kind, status and chunk counters.")
    @ApiResponse(responseCode = "200", description = "Source found")
    @ApiResponse(responseCode = "404", description = "Source not found (errorCode SOURCE_NOT_FOUND)",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/{sourceId}")
    public SourceResponse getSource(
            @Parameter(description = "Workspace identifier", example = "42") @PathVariable long workspaceId,
            @Parameter(description = "Source identifier", example = "301") @PathVariable long sourceId) {
        return sourceService.getSource(workspaceId, sourceId);
    }
}
```

- 200 response'unun şeması dönüş tipinden (`SourceResponse`) otomatik çıkarılır; `content` tekrar yazılmaz.
- Path variable'lar zaten zorunludur; `@Parameter(required = true)` yazmaya gerek yok.
- `@ApiResponse` tekrarlanabilir annotation'dır; `@ApiResponses({...})` sarmalayıcısı gerekmez.

## Request Body'leri Belgeleme

Request body şeması `@RequestBody` parametresinin tipinden (record) gelir; açıklama ve örnekler DTO üzerindeki `@Schema` ile verilir ([model-documentation.md](model-documentation.md)). Birden çok örnek gerekiyorsa:

```java
import io.swagger.v3.oas.annotations.media.ExampleObject;

@Operation(summary = "Search chunks of a workspace")
@ApiResponse(responseCode = "200", description = "Ranked hits")
@ApiResponse(responseCode = "400", description = "Invalid query, mode or limit (errorCode VALIDATION_FAILED)",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(responseCode = "503", description = "Embedding service unavailable (errorCode EMBEDDING_UNAVAILABLE)",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
@PostMapping("/api/workspaces/{workspaceId}/search")
public SearchResultResponse searchChunks(
        @PathVariable long workspaceId,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
                content = @Content(examples = {
                        @ExampleObject(name = "vector", value = """
                                {"query":"Şifremi unuttum, online işlemlere giremiyorum","mode":"VECTOR","limit":10}
                                """),
                        @ExampleObject(name = "hybrid", value = """
                                {"query":"fatura itiraz","mode":"HYBRID","limit":10}
                                """)}))
        @Valid @RequestBody SearchChunksRequest request) {
    return searchService.searchChunks(workspaceId, request);
}
```

Spring'in `@RequestBody`'si ile OpenAPI'nin `@RequestBody`'si aynı isimdedir; ikisi birlikte gerekiyorsa OpenAPI olanı tam nitelikli adla yazılır (yukarıdaki gibi).

## Liste ve Arama Endpoint'leri

```java
@Operation(summary = "List chunks of a source")
@GetMapping("/api/workspaces/{workspaceId}/sources/{sourceId}/chunks")
public PageResponse<ChunkResponse> listChunks(
        @PathVariable long workspaceId,
        @PathVariable long sourceId,
        @Parameter(description = "Zero-based page index")
        @RequestParam(name = "page", defaultValue = "0")
        @Min(value = 0, message = PageParameters.PAGE_MESSAGE) int page,
        @Parameter(description = "Page size")
        @RequestParam(name = "size", defaultValue = "20")
        @Min(value = 1, message = PageParameters.SIZE_MESSAGE)
        @Max(value = PageParameters.MAX_SIZE, message = PageParameters.SIZE_MESSAGE) int size) {
    return PageResponse.from(chunkService.listChunks(workspaceId, sourceId, page, size));
}
```

Sınırsız büyüyebilecek listeler `PageResponse` döner ([pagination-support.md](pagination-support.md)).

## Opsiyonel Query Parametreleri

İsteğe bağlı parametre `required = false` + `@Nullable` sarmalayıcı tiptir; enum değerleri `String` + `@Pattern` ile alınır ki geçersiz değer alan adıyla `VALIDATION_FAILED` dönsün (dönüşüm hatası değil). Projede bugün sadece `page`/`size` var; filtre eklemek sözleşme değişikliğidir.

## Header'ları Belgeleme

```java
@Operation(summary = "Start re-ingestion of a source")
@PostMapping("/api/workspaces/{workspaceId}/sources/{sourceId}/ingestion")
@ResponseStatus(HttpStatus.ACCEPTED)
public SourceResponse startIngestion(
        @PathVariable long workspaceId,
        @PathVariable long sourceId,
        @Parameter(description = "Client generated key; repeating the same key does not start a second ingestion", required = true)
        @RequestHeader("Idempotency-Key") String idempotencyKey) {
    return ingestionService.startIngestion(workspaceId, sourceId, idempotencyKey);
}
```

Header örneği (`example`) gerçek bir değer içermez; gizli bilgi (API anahtarı) header olarak istemciden alınmaz, backend'de ortam değişkenindedir.
