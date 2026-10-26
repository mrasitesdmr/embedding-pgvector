# SpringDoc OpenAPI Annotation Referansı

> Bu dosya annotation söz dizimini ve attribute'ları gösterir; `Book` genel bir örnek response/request tipidir (projede entity değil DTO belgelenir). Proje kalıpları: [controller-documentation.md](controller-documentation.md), [model-documentation.md](model-documentation.md), [exception-handling.md](exception-handling.md). Hata response'ları her zaman `ProblemDetail`'dir.

## Temel Annotation'lar

### `@Tag`

Operation'ları mantıksal bir tag altında gruplar.

```java
// Controller seviyesi
@RestController
@Tag(name = "Book", description = "Book management APIs")
public class BookController { }

// Harici dokümanla
@Tag(
    name = "Public APIs",
    description = "Publicly accessible endpoints",
    externalDocs = @ExternalDocumentation(
        description = "Public API Guide",
        url = "https://docs.example.com/public"
    )
)
```

**Attribute'lar:**
- `name`: Tag tanımlayıcısı
- `description`: Tag açıklaması
- `externalDocs`: Harici dokümantasyon referansı

### `@Operation`

Tek bir API operation'ını tanımlar.

```java
@Operation(
    summary = "Get book by ID",
    description = "Retrieve detailed information about a specific book",
    operationId = "getBookById",
    deprecated = false,
    hidden = false,
    tags = {"Book"},
    externalDocs = @ExternalDocumentation(
        description = "Book API Guide",
        url = "https://docs.example.com/book"
    )
)
```

**Attribute'lar:**
- `summary`: Kısa özet (< 120 karakter)
- `description`: Detaylı açıklama
- `operationId`: Tekil operation ID
- `deprecated`: Deprecated olarak işaretler
- `hidden`: Dokümantasyondan gizler
- `tags`: Varsayılan tag'leri override eder
- `security`: Security gereksinimleri
- `responses`: Response tanımları
- `parameters`: Parametre tanımları

### `@ApiResponse` / `@ApiResponses`

HTTP response code'larını belgeler.

```java
@ApiResponses(value = {
    @ApiResponse(
        responseCode = "200",
        description = "Successfully retrieved",
        content = @Content(
            schema = @Schema(implementation = Book.class),
            mediaType = "application/json"
        )
    ),
    @ApiResponse(
        responseCode = "404",
        description = "Resource not found",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
    )
})
```

**Attribute'lar:**
- `responseCode`: HTTP status code
- `description`: Response açıklaması
- `content`: Response content şeması
- `headers`: Response header'ları
- `extensions`: Özel extension'lar

### `@Parameter`

Operation parametrelerini belgeler.

```java
public Book getBook(
    @Parameter(
        description = "Book ID",
        required = true,
        example = "1",
        deprecated = false,
        hidden = false,
        allowEmptyValue = false,
        allowReserved = true,
        schema = @Schema(type = "integer", format = "int64"),
        content = @Content(
            schema = @Schema(implementation = Book.class),
            examples = @ExampleObject(value = "1")
        )
    )
    @PathVariable Long id
) { }
```

**Attribute'lar:**
- `description`: Parametre açıklaması
- `required`: Zorunlu mu (varsayılan: çıkarımla belirlenir)
- `example`: Örnek değer
- `deprecated`: Deprecated olarak işaretler
- `hidden`: Dokümantasyondan gizler
- `allowEmptyValue`: Boş string'e izin verir
- `allowReserved`: Rezerve karakterlere izin verir (:/?#[]@!$&'()*+,;=)
- `schema`: Parametre şeması
- `content`: Parametre content'i
- `explode`: Array/object parametrelerini açar (explode)
- `style`: Parametre stili (matrix, label, form, simple, spaceDelimited, pipeDelimited, deepObject)

### `@RequestBody` (OpenAPI)

Request body'yi belgeler (Spring'in `@RequestBody`'si ile karıştırılmamalı).

```java
@PostMapping
public Book create(
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
        description = "Book to create",
        required = true,
        content = @Content(
            schema = @Schema(implementation = Book.class),
            examples = @ExampleObject(
                name = "Example Book",
                value = "{\"title\": \"Clean Code\", \"author\": \"Robert C. Martin\"}"
            )
        )
    )
    @Valid @RequestBody Book book
) { }
```

### `@Schema`

Model şemalarını belgeler.

```java
@Schema(
    description = "Book response",
    name = "Book",
    type = "object",
    requiredMode = Schema.RequiredMode.REQUIRED,
    nullable = false,
    readOnly = false,
    writeOnly = false,
    example = "{\"id\": 1, \"title\": \"Clean Code\"}",
    externalDocs = @ExternalDocumentation(
        description = "Book Model Docs",
        url = "https://docs.example.com/book-model"
    ),
    implementation = Book.class,
    not = Book.class,
    oneOf = {Book.class, Magazine.class},
    anyOf = {Book.class, Magazine.class},
    allOf = {BaseEntity.class}
)
public class Book { }

// Field seviyesi
@Schema(
    description = "Book title",
    example = "Clean Code",
    requiredMode = Schema.RequiredMode.REQUIRED,
    minLength = 1,
    maxLength = 200,
    pattern = "^[a-zA-Z0-9 ]*$",
    type = "string",
    format = "string",
    allowableValues = {"Fiction", "Non-Fiction", "Technical"},
    defaultValue = "Untitled",
    accessMode = Schema.AccessMode.READ_ONLY,
    hidden = false
)
private String title;
```

**Attribute'lar:**
- `description`: Field açıklaması
- `example`: Örnek değer
- `required`: Zorunlu mu (deprecated; `requiredMode` kullan)
- `type`: Veri tipi (string, number, integer, boolean, array, object)
- `format`: Veri formatı (ör. email, date, date-time, uuid)
- `minimum`/`maximum`: Sayısal kısıtlar
- `minLength`/`maxLength`: String uzunluk kısıtları
- `pattern`: Regex pattern
- `allowableValues`: İzin verilen değerler listesi
- `defaultValue`: Varsayılan değer
- `readOnly`: Read-only property
- `writeOnly`: Write-only property
- `accessMode`: READ_ONLY, WRITE_ONLY, READ_WRITE
- `hidden`: Dokümantasyondan gizler
- `implementation`: Generic'ler için implementation sınıfı
- `nullable`: Null olabilir mi
- `deprecated`: Deprecated olarak işaretler

### `@SecurityRequirement`

Security gereksinimlerini uygular.

```java
// Controller seviyesi
@SecurityRequirement(name = "bearer-jwt")
@RestController
public class BookController { }

// Operation seviyesi
@Operation(
    summary = "Secure endpoint",
    security = @SecurityRequirement(name = "bearer-jwt")
)

// Birden fazla security scheme (VEYA mantığı)
@Operation(
    security = {
        @SecurityRequirement(name = "bearer-jwt"),
        @SecurityRequirement(name = "api-key")
    }
)

// Security yok: sınıf seviyesindeki gereksinimi ezmek için boş @SecurityRequirements
// (@Operation(security = {}) varsayılanla aynıdır, ezmez)
@SecurityRequirements
@GetMapping("/public")
public PageResponse<BookResponse> listPublicBooks(@ParameterObject Pageable pageable) { }
```

### `@Hidden`

Dokümantasyondan gizler.

```java
// Endpoint'i gizle
@Operation(hidden = true)
@GetMapping("/internal")
public String internal() { }

// Tüm controller'ı gizle
@Hidden
@RestController
public class InternalController { }
```

### `@ParameterObject`

Karmaşık object'leri parametre olarak belgeler.

```java
@GetMapping
public PageResponse<BookResponse> listBooks(
    @ParameterObject @PageableDefault(size = 20) Pageable pageable
) { }

// Spring Data Pageable ve özel filtre record'larıyla çalışır
```

## Validation Annotation'ları (Otomatik Belgelenir)

### Standart Bean Validation

```java
@NotNull           // zorunlu alan
@NotBlank          // zorunlu, boş olmayan string
@NotEmpty          // zorunlu, boş olmayan koleksiyon
@Size(min = 1, max = 200)  // string/koleksiyon uzunluğu
@Min(0)            // sayısal alt sınır
@Max(1000)         // sayısal üst sınır
@DecimalMin("0.0") // ondalık alt sınır
@DecimalMax("999.99") // ondalık üst sınır
@Pattern(regexp = "^[A-Z].*") // regex pattern
@Email             // e-posta formatı
@Past              // geçmiş tarih
@PastOrPresent     // bugün veya geçmiş
@Future            // gelecek tarih
@FutureOrPresent   // bugün veya gelecek
@Positive          // pozitif sayı
@PositiveOrZero    // pozitif veya sıfır
@Negative          // negatif sayı
@NegativeOrZero    // negatif veya sıfır
@AssertTrue        // true olmalı
@AssertFalse       // false olmalı
```

## İleri Annotation'lar

### `@ArraySchema`

Array şemalarını belgeler.

```java
@Schema(
    description = "List of books",
    implementation = Book[].class
)
List<Book> books;

// ArraySchema ile
@ArraySchema(
    schema = @Schema(implementation = Book.class),
    arraySchema = @Schema(
        description = "Array of books",
        minItems = 0,
        maxItems = 100,
        uniqueItems = false
    )
)
List<Book> books;
```

### `@Content`

Detaylı content dokümantasyonu.

```java
@Content(
    mediaType = "application/json",
    schema = @Schema(implementation = Book.class),
    examples = {
        @ExampleObject(
            name = "Example 1",
            value = "{\"title\": \"Clean Code\"}",
            summary = "Simple example"
        ),
        @ExampleObject(
            name = "Example 2",
            value = "{\"title\": \"Effective Java\"}",
            summary = "Another example",
            externalValue = "https://example.com/book-example.json"
        )
    }
)
```

### `@ExampleObject`

Örnek değerler.

```java
@ExampleObject(
    name = "Book Example",
    value = "{\"id\": 1, \"title\": \"Clean Code\"}",
    summary = "A simple book example",
    externalValue = "https://example.com/examples/book.json"
)
```

### `@ExternalDocumentation`

Harici dokümantasyon referansları.

```java
@ExternalDocumentation(
    description = "Detailed API documentation",
    url = "https://docs.example.com/api"
)
```

## Composition Annotation'ları

### `@DiscriminatorObject`

Polymorphic tipler için.

```java
@Schema(
    discriminatorProperty = "type",
    discriminatorMapping = {
        @DiscriminatorMapping(value = "book", schema = Book.class),
        @DiscriminatorMapping(value = "magazine", schema = Magazine.class)
    }
)
public abstract class Publication { }
```

## Annotation Best Practice'leri

1. **Açıklayıcı summary'ler yaz**: 120 karakterin altında tut
2. **Detaylı description ver**: Davranışı ve kullanım senaryolarını açıkla
3. **Tüm response code'larını belgele**: 2xx, 4xx, 5xx dahil
4. **Örnek ekle**: Gerçekçi request/response örnekleri ver
5. **Validation'dan yararlan**: Bean Validation annotation'larının kısıtları otomatik belgelemesine izin ver
6. **Mantıksal grupla**: İlgili endpoint'leri `@Tag` ile organize et
7. **Tutarlı ol**: Controller'lar arasında benzer annotation pattern'leri kullan
8. **İç endpoint'leri gizle**: `@Hidden` veya ayrı API grupları kullan
9. **Security'yi belgele**: `@SecurityRequirement`'ı uygun yerlerde uygula
10. **Karmaşık tipleri belgele**: İç içe object'ler ve generic'ler için `@Schema` kullan
