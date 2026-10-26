# Model Dokümantasyon Pattern'leri

API'de sadece request/response DTO'ları (`record`) görünür; JPA entity'leri controller'dan dışarı çıkmadığı için `@Schema` ile belgelenmez (`backend-conventions`).

## Validation İçeren Request DTO'su

```java
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SearchChunksRequest(
        @Schema(description = "Leading and trailing whitespace is stripped; then 1-500 characters (UTF-16 code units).",
                maxLength = 500, example = "Şifremi unuttum, online işlemlere giremiyorum",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "must not be blank")
        @Size(max = 500, message = "size must be at most 500 characters")
        @Nullable String query,

        @Schema(types = {"integer", "null"}, format = "int32", minimum = "1", maximum = "50", example = "10",
                description = "Number of results, also the LIMIT of each branch. Default search.default-limit (10).")
        @Min(value = 1, message = "must be between 1 and 50")
        @Max(value = 50, message = "must be between 1 and 50")
        @Nullable Integer limit) {
}

@Schema(description = "Request to re-embed a workspace with another model")
public record ReembedWorkspaceRequest(
        @Schema(description = "Embedding model tag", example = "bge-m3@1024",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @Pattern(regexp = "^[a-z0-9.-]+@[0-9]+$")
        String modelTag,

        @Schema(description = "Chunks sent to the embedding service per request", example = "64")
        @Min(1) @Max(256)
        int batchSize) {
}
```

- Bean Validation annotation'ları şemaya yansır: `@NotBlank`/`@NotNull` → `required`, `@Size` → `minLength`/`maxLength`, `@Pattern` → `pattern`, `@Min`/`@Max` → `minimum`/`maximum`. Alan `@Nullable` sarmalayıcı tip olduğunda ya da `types` ile 3.1 nullable biçimi yazıldığında springdoc bazı sınırları düşürebilir; projede sınırlar `@Schema`'da da açıkça yazılır ve `/v3/api-docs` çıktısı `docs/api/openapi.yaml` ile karşılaştırılır.
- Nullable alanlar OpenAPI 3.1 biçiminde: `@Schema(types = {"string", "null"})`.
- `required = true` deprecated'dır; `requiredMode = Schema.RequiredMode.REQUIRED`.
- `example` değerleri gerçekçi ve sözleşmeyle aynıdır.

## Response DTO'su

```java
public record SearchHitResponse(
        @Schema(description = "1-based position in this response", requiredMode = Schema.RequiredMode.REQUIRED)
        int rank,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        long chunkId,

        @Schema(types = {"integer", "null"}, format = "int32", description = "1-based position in the vector branch")
        @Nullable Integer vectorRank,

        @Schema(types = {"number", "null"}, format = "double",
                description = "Cosine distance (<=>) to the query vector; smaller is closer")
        @Nullable Double distance,

        @Schema(types = {"number", "null"}, format = "double",
                description = "Only in HYBRID mode: 1 / (rrfK + keywordRank) + 1 / (rrfK + vectorRank), a missing rank adds 0")
        @Nullable Double rrfScore) {
}

public record SourceResponse(
        @Schema(example = "302", requiredMode = Schema.RequiredMode.REQUIRED)
        long sourceId,

        @Schema(description = "INGESTING while a job is running (old chunks stay searchable), READY when the last "
                + "successful version is in place, FAILED when the source never had a successful version.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        SourceStatus status,

        @Schema(description = "Number of chunks of the current (last successful) version", example = "9",
                requiredMode = Schema.RequiredMode.REQUIRED)
        int chunkCount,

        @Schema(description = "The newest ingestion job of the source (highest jobId), in any stage. Null when the "
                + "source never had a job (seed sources).")
        @Nullable IngestionJobResponse latestJob) {
}
```

- Zaman alanları `Instant` (ISO-8601); `LocalDateTime` kullanılmaz.
- Response'ta olmaması gereken alanlar (embedding vektörü, API anahtarı) DTO'ya hiç konmaz; ham `distance` ise lab eğitim amaçlı olduğu için bilerek döner (`backend-conventions` bölüm 3). `@Schema(hidden = true)` / `@JsonIgnore` ile gizlemek yerine DTO'yu doğru tasarla.

## İç İçe (Nested) Record'lar

```java
@Schema(description = "Recall measurement of an index against exact search")
public record RecallReportResponse(
        @Schema(description = "Index method", example = "HNSW")
        String indexMethod,

        @Schema(description = "Per-query recall results")
        List<QueryRecallResponse> queries) {
}

@Schema(description = "Recall of one query")
public record QueryRecallResponse(
        @Schema(example = "Şifremi unuttum")
        String query,

        @Schema(description = "Shared hits with exact search divided by k", example = "0.9")
        double recallAtK) {
}
```

## Enum Dokümantasyonu

Enum değerlerinin anlamı enum'un üstünde değil, kullanıldığı alanın `@Schema(description = ...)`'ında yazılır (ör. `SourceResponse.status`: "INGESTING while a job is running (old chunks stay searchable) ..."); aynı enum farklı alanlarda farklı bağlamla açıklanabilir.

```java
public enum SourceStatus {
    INGESTING,
    READY,
    FAILED
}

public enum IngestionStage {
    QUEUED,
    PARSING,
    CHUNKING,
    EMBEDDING,
    WRITING,
    DONE,
    FAILED
}
```

Enum değerleri `UPPER_SNAKE_CASE` olarak serileşir ve şemada `enum` listesi otomatik üretilir. Değerlerin anlamı gerekiyorsa enum'un `@Schema(description = ...)` metninde açıklanır.

## Read-Only ve Write-Only Alanlar

Ayrı request/response record'ları kullanıldığı için `accessMode` nadiren gerekir. Aynı record hem istek hem yanıtta kullanılıyorsa:

```java
@Schema(description = "Server generated identifier", accessMode = Schema.AccessMode.READ_ONLY, example = "42")
Long id
```

## Collection Alanları

```java
@Schema(description = "Headings the chunk belongs to", example = "[\"Hesap\", \"Şifre sıfırlama\"]")
List<String> tags
```

Koleksiyonlar null yerine boş liste olarak döner; şemada `nullable` işaretlenmez.

## Polymorphic Tipler

Farklı mesaj tipleri tek alanda taşınacaksa sealed interface + Jackson type bilgisi (projede henüz yok; örnek WebSocket gelirse kullanılabilecek bir içe alma olayı tasarımıdır):

```java
@Schema(description = "Ingestion event", oneOf = {IngestionProgressMessage.class, IngestionFailedMessage.class})
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = IngestionProgressMessage.class, name = "PROGRESS"),
        @JsonSubTypes.Type(value = IngestionFailedMessage.class, name = "FAILED")
})
public sealed interface IngestionEventMessage permits IngestionProgressMessage, IngestionFailedMessage {
}
```

İsimlendirme `backend-conventions` bölüm 3'e uyar: istemciye giden payload'lar `<Noun>Message` / `<Noun>Response`'tur; `<Noun><PastVerb>Event` soneki sadece uygulama içi domain event'leri içindir (`SourceCreatedEvent`) ve API'ye çıkmaz.

`@JsonTypeInfo` / `@JsonSubTypes` Jackson 3'te de `com.fasterxml.jackson.annotation` paketindedir.
