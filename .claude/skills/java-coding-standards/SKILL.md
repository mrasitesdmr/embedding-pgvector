---
name: java-coding-standards
description: "Spring Boot servisleri için Java coding standard'ları: naming, immutability, Optional kullanımı, stream'ler, exception'lar, generic'ler, dependency injection, logging, configuration ve proje yapısı. Spring Boot servisinde Java kodu yazarken ya da review ederken kullan."
metadata:
  origin: ECC
  adapted-for: Embedding ve pgvector (Java 26, Spring Boot 4.1)
---

# Java Coding Standard'ları

> **Proje kuralı:** İsimlendirme, yorum politikası, Lombok yasağı, paket yapısı ve test yapısı (given_when_then + AAA, BDDMockito `given/then`) için `backend-conventions` skill'i geçerlidir. Bu dosyadaki örneklerle çelişirse `backend-conventions` kazanır.

Spring Boot servislerinde okunabilir, bakımı kolay Java kodu için standartlar.

## Ne Zaman Kullanılır

- Spring Boot projesinde Java kodu yazarken ya da review ederken
- Naming, immutability veya exception handling convention'larını uygularken
- Record, sealed class veya pattern matching ile çalışırken
- Optional, stream veya generic kullanımını review ederken

## Temel İlkeler

- Zekice (clever) kod yerine anlaşılır kodu tercih et
- Varsayılan olarak immutable; shared mutable state'i en aza indir
- Anlamlı exception'larla fail fast
- Tutarlı naming ve feature-first package yapısı

## Naming

Kalıp tabloları `backend-conventions` bölüm 3'tedir. Özet:

```java
public class SourceService {}
public record SearchChunksRequest(String query, @Nullable String mode, @Nullable Integer limit) {}

private final SourceRepository sourceRepository;
public Source getSource(long sourceId) {}
public Optional<Source> findSource(long sourceId) {}

private static final int MAX_SOURCES_PER_WORKSPACE = 10;
```

- `get<Noun>` bulamazsa exception fırlatır, `find<Noun>` `Optional` döner.
- DTO'lar `<Verb><Noun>Request` / `<Noun>Response`; `Dto` soneki kullanılmaz.

## Immutability

```java
public record SearchResultResponse(String query, SearchMode mode, List<SearchHitResponse> hits) {

    public SearchResultResponse {
        hits = List.copyOf(hits);
    }
}

public final class QueryEmbedding {

    private final float[] values;
    private final String model;

    public QueryEmbedding(float[] values, String model) {
        this.values = values.clone();
        this.model = model;
    }

    public float[] values() {
        return values.clone();
    }
}
```

- Record'ları ve `final` field'ları tercih et; koleksiyonları dışarı verirken `List.copyOf` ile kopyala. Dizi (`float[]` embedding) değiştirilebilir olduğu için içeri alırken ve dışarı verirken `clone()` edilir; record bileşeni olarak dizi tutulmaz (`equals`/`hashCode` diziyi referansla karşılaştırır).
- JPA entity'leri bu kuralın istisnasıdır (bkz. `jpa-patterns`); entity'ler controller'dan dışarı çıkmaz.

## Optional Kullanımı

```java
return sourceRepository.findByWorkspaceIdAndId(workspaceId, sourceId)
        .map(SourceResponse::from)
        .orElseThrow(() -> new NotFoundException("SOURCE_NOT_FOUND", "Source not found: " + sourceId));
```

- `get()` yerine `map` / `orElseThrow`.
- `Optional` sadece dönüş tipi; parametre ve field olarak kullanılmaz.

## Stream Best Practice'leri

```java
List<Long> chunkIds = hits.stream()
        .map(SearchHitResponse::chunkId)
        .toList();
```

- Dönüşümler için stream, yan etkiler için döngü. Karmaşık iç içe stream yerine döngü.
- `Stream.toList()` değiştirilemez liste döner; değiştirilecekse `Collectors.toCollection(ArrayList::new)`.

## Dependency Injection

```java
@Service
public class SourceService {

    private final SourceRepository sourceRepository;

    public SourceService(SourceRepository sourceRepository) {
        this.sourceRepository = sourceRepository;
    }
}
```

Field injection (`@Autowired` field) yok; Lombok `@RequiredArgsConstructor` yok.

## Exception'lar

- Domain error'ları için unchecked exception: kökteki `exception` paketinin kategori sınıfları (`NotFoundException`, `ConflictException`, `UnauthorizedException`, `ForbiddenException`; hepsi `BaseException`'dan türer, bkz. `problem-details-rfc9457`). Teknik exception'ları context ekleyip `cause` ile wrap et.
- Hata başına exception sınıfı açılmaz (hata adını taşıyan `<Hata>Exception` sınıfı yok). Hatayı fırlatılan yerde verilen string `errorCode` ayırt eder: `new NotFoundException("SOURCE_NOT_FOUND", ...)`.
- Merkezi olarak rethrow/log etmiyorsan geniş `catch (Exception exception)`'dan kaçın.
- Exception'ları HTTP'ye çevirme işi tek bir `@RestControllerAdvice`'tadır (`GlobalExceptionHandler`); ayrı ayrı handler sınıfı açma.

```java
throw new NotFoundException("SOURCE_NOT_FOUND", "Source not found: " + sourceId);
```

## Generic'ler ve Type Safety

- Raw type'lardan kaçın; generic parametreleri tanımla.
- Yeniden kullanılabilir utility'ler için bounded generic'leri tercih et.

```java
public <T extends Identifiable> Map<Long, T> indexById(Collection<T> items) { ... }
```

## Proje Yapısı (feature-first)

```
src/main/java/com/embedding/pgvector/
  source/               (workspace, chunk, search, ingestion aynı iç yapıyla)
    controller/
    service/
    repository/
    domain/
      entity/
      dto/
      event/
  config/
  exception/            GlobalExceptionHandler, BaseException, kategori exception'ları
  security/
  websocket/
src/main/resources/
  application.properties
  db/migration/         Flyway (bkz. flyway-migrations)
src/test/java/...       (main'i yansıtır)
```

Kökte katman-first paketler (`controllers/`, `services/`, `util/`) açılmaz; katman paketleri özellik paketinin içindedir (`backend-conventions` bölüm 3 "Paketler").

## Formatting ve Style

- 4 boşluk girinti
- Dosya başına bir public top-level type
- Metotları kısa ve odaklı tut; private metotlara ayır
- Member sırası: constant'lar, field'lar, constructor'lar, public metotlar, protected, private

## Kaçınılacak Code Smell'ler

- Uzun parametre listeleri → parametre record'u (Lombok builder yok)
- Derin nesting → early return
- Magic number'lar → isimlendirilmiş constant'lar
- Static mutable state → dependency injection
- Sessiz catch blokları → logla ve aksiyon al ya da rethrow et

## Logging

```java
private static final Logger log = LoggerFactory.getLogger(SourceService.class);

log.info("Source created workspaceId={} sourceId={} kind={}", workspaceId, source.getId(), source.getKind());
log.error("Embedding request failed sourceId={} batchSize={}", sourceId, batchSize, exception);
```

Format ve seviye kuralları: `observability-tracing` bölüm 5.

## Null Handling

- Package'ları `@NullMarked` ile işaretle; `@Nullable`'ı sadece kaçınılmazsa kullan.
- Null anotasyonları JSpecify'dan gelir: `org.jspecify.annotations.Nullable`, `org.jspecify.annotations.NullMarked` (`org.springframework.lang.*` kullanma).
- Input'larda Bean Validation (`@NotNull`, `@NotBlank`).

## Configuration

```java
@ConfigurationProperties(prefix = "app.search")
public record SearchProperties(int defaultLimit, int efSearch, int rrfK, int maxScanTuples) {}
```

`@Value` ile tek tek alan enjekte etmek yerine `@ConfigurationProperties` record'u. Değerler `application.properties`'te. Görev dosyasındaki iş kuralı sınırları (çalışma alanında en fazla 10 kaynak, kaynak adı 2-80 karakter) ortama göre değişmez; config'e değil domain'deki sabite (`MAX_SOURCES_PER_WORKSPACE`) aittir. Ortama göre ayarlanan arama parametreleri (`ef_search`, RRF `k`) ve embedding modeli config'tedir.

## Test Beklentileri

Test yazım kuralları `backend-conventions` bölüm 5'tedir; slice seçimi için `spring-testing-fundamentals`.

```java
@WebMvcTest(SourceController.class)
class SourceControllerTest {

    @Autowired
    private MockMvcTester mockMvcTester;

    @MockitoBean
    private SourceService sourceService;
}

@ExtendWith(MockitoExtension.class)
class IngestionServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private SourceRepository sourceRepository;

    // ...diğer bağımlılıklar @Mock

    private IngestionService ingestionService;

    @BeforeEach
    void setUp() {
        ingestionService = new IngestionService(workspaceRepository, workspaceService, sourceRepository,
                ingestionJobRepository, fileTypeDetector, embeddingModelGuard, ingestionJobRunner, FIXED_CLOCK);
    }
}
```

`IngestionService` constructor'ı `Clock` aldığı için `@InjectMocks` kullanılmaz (karşılanamayan parametreye `null` geçer); service elle kurulur (`spring-boot-engineer/references/testing.md`).

**Unutma**: Kodu amaçlı, typed ve observable tut. Gerekliliği kanıtlanmadıkça micro-optimization yerine maintainability'yi optimize et.
