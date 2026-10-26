# Hata Response'larının Dokümantasyonu

Projenin hata formatı RFC 9457 `ProblemDetail`'dir (`org.springframework.http.ProblemDetail`) ve her hata `errorCode` extension'ı taşır. Exception handler'ın kendisi ve `errorCode` tablosu `problem-details-rfc9457` skill'indedir; bu dosya sadece bunun OpenAPI'de nasıl belgeleneceğini anlatır. Projeye özel `ErrorResponse`, `ValidationErrorResponse` gibi ayrı hata sınıfları **açılmaz**; Spring'in `ProblemDetail` sınıfı yeniden tanımlanmaz.

## Handler Dokümana Girmez

`@RestControllerAdvice` metotları endpoint değildir; SpringDoc onları operation olarak listelemez. `@Operation(hidden = true)` eklemeye gerek yoktur. SpringDoc, advice'taki `@ExceptionHandler` + `@ResponseStatus` kombinasyonlarından tüm operation'lara genel response'lar türetebilir; proje hata response'larını aşağıdaki gibi açıkça belgeler.

## Endpoint'e Özel Hataları Belgeleme

```java
@Operation(summary = "Add a knowledge source to a workspace")
@ApiResponse(responseCode = "201", description = "Source created")
@ApiResponse(responseCode = "400", description = "Invalid name or kind (errorCode VALIDATION_FAILED)",
        content = @Content(mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class),
                examples = @ExampleObject(value = """
                        {
                          "type": "https://embedding-pgvector.example/errors/validation-failed",
                          "title": "Validation Failed",
                          "status": 400,
                          "detail": "Request validation failed",
                          "instance": "/api/workspaces/42/sources",
                          "errorCode": "VALIDATION_FAILED",
                          "violations": [{"field": "name", "message": "size must be between 2 and 80"}]
                        }
                        """)))
@ApiResponse(responseCode = "404", description = "Workspace not found (errorCode WORKSPACE_NOT_FOUND)",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(responseCode = "409", description = "Source limit reached (SOURCE_LIMIT_REACHED) or name is taken (SOURCE_NAME_TAKEN)",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ResponseEntity<IngestionJobResponse> createSource(@PathVariable long workspaceId,
        @RequestPart(name = "name", required = false) @NotBlank @Pattern(regexp = NAME_SIZE_PATTERN) @Nullable String name,
        @RequestPart(name = "file", required = false) @NotEmptyFile @Nullable MultipartFile file) {
    IngestionJobResponse job = ingestionService.createSource(workspaceId, Objects.requireNonNull(name), Objects.requireNonNull(file));
    return ResponseEntity.accepted().location(URI.create(job.statusUrl())).body(job);
}
```

- `description` içinde ilgili `errorCode` değerleri yazılır; istemci bu kodlara göre davranır (`backend-conventions` bölüm 1).
- Media type `application/problem+json`'dur.
- `ProblemDetail` şeması `type`, `title`, `status`, `detail`, `instance` alanlarını üretir; `errorCode` ve `violations` extension'ları aşağıdaki customizer ile şemaya eklenir.

## Ortak Hataları Global Eklemek (OpenApiCustomizer)

Her endpoint'te tekrarlanan 500 cevabını ve `ProblemDetail` extension alanlarını tek yerde tanımla:

```java
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    OpenApiCustomizer problemDetailCustomizer() {
        return openApi -> {
            Schema<?> problemDetail = openApi.getComponents().getSchemas().get("ProblemDetail");
            if (problemDetail != null) {
                problemDetail.addProperty("errorCode", new StringSchema().example("SOURCE_NOT_FOUND"));
                problemDetail.addProperty("violations", new ArraySchema().items(new ObjectSchema()
                        .addProperty("field", new StringSchema().example("query"))
                        .addProperty("message", new StringSchema().example("size must be between 2 and 500"))));
            }
            openApi.getPaths().values().forEach(pathItem -> pathItem.readOperations().forEach(operation ->
                    operation.getResponses().addApiResponse("500", new ApiResponse()
                            .description("Unexpected error (errorCode INTERNAL_ERROR)")
                            .content(new Content().addMediaType("application/problem+json",
                                    new MediaType().schema(new Schema<>().$ref("#/components/schemas/ProblemDetail")))))));
        };
    }
}
```

`Schema`, `StringSchema`, `ArraySchema`, `ObjectSchema`, `ApiResponse`, `Content`, `MediaType` burada `io.swagger.v3.oas.models.*` model sınıflarıdır (annotation değil).

## Validation Hatalarını Belgeleme

Validation hatası ayrı bir şema değildir: `ProblemDetail` + `errorCode = VALIDATION_FAILED` + `violations` dizisi. Alan kuralları (2-20 karakter vb.) zaten request DTO şemasında `minLength`/`maxLength` olarak görünür ([model-documentation.md](model-documentation.md)).

## Best Practice'ler

1. Görev dosyasındaki her hata senaryosunun status'u ve `errorCode`'u endpoint'te belgelenir
2. Tek hata formatı: `ProblemDetail` (`application/problem+json`)
3. `detail` İngilizce ve insan içindir; istemci mantığı `errorCode`'a dayanır
4. Hata örneklerinde stack trace, SQL veya iç sınıf adı olmaz
5. HTTP status ile body'deki `status` aynıdır; durum çakışması 409, iş kuralı ihlali 422, validation 400
6. Hata response'unda izleme için ayrı `requestId` alanı yok; log ve trace eşlemesi `traceId` ile yapılır (`observability-tracing`)
