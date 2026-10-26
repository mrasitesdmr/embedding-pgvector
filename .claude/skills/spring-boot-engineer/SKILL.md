---
name: spring-boot-engineer
description: Spring Boot configuration'ları üretir, REST controller'lar oluşturur, Spring Security authentication akışlarını uygular ve Spring Data JPA repository'lerini kurar. Spring Boot (Spring MVC) uygulaması geliştirirken kullan; Spring Data JPA, Spring Security, Java REST API tasarımı veya test için çağır.
license: MIT
metadata:
  author: https://github.com/Jeffallan
  company: https://synergetic.solutions
  version: "1.1.0"
  domain: backend
  triggers: Spring Boot, Spring Framework, Spring MVC, Spring Security, Spring Data JPA, Java REST API
  role: specialist
  scope: implementation
  output-format: code
---

# Spring Boot Mühendisi

> **Proje kuralı:** İsimlendirme, yorum politikası, Lombok yasağı ve test yapısı (given_when_then + AAA, BDDMockito `given/then`) için `backend-conventions` skill'i geçerlidir. Bu dosyadaki örneklerle çelişirse `backend-conventions` kazanır.

## Temel İş Akışı

1. **Gereksinimleri analiz et** — Servis sınırlarını, API'leri, veri modellerini ve güvenlik ihtiyaçlarını belirle
2. **Mimariyi tasarla** — Katmanları, veri erişimini ve güvenliği planla; koda başlamadan önce tasarımı onaylat
3. **Uygula** — Servisleri constructor injection ve katmanlı mimari ile oluştur (aşağıdaki Hızlı Başlangıç'a bak)
4. **Güvenliği sağla** — Projede hesap ve Spring Security yok; tek gizli bilgi embedding API anahtarıdır (ortam değişkeni). CORS ve input validation yine zorunlu. Spring Security eklenirse kurallar `springboot-security` ve `references/security.md`'de. Derleme ya da testler başarısız olursa: hata çıktısını incele, hatalı kuralı veya configuration'ı düzelt ve devam etmeden önce tekrar çalıştır
5. **Test et** — unit, integration ve slice test'leri yaz (ekip akışında testleri `tester` agent'ı yazar); `./mvnw test` çalıştır ve devam etmeden önce hepsinin geçtiğini doğrula. Testler başarısız olursa: stack trace'i incele, hatalı assertion'ı veya bileşeni izole et, düzelt ve tüm test setini tekrar çalıştır
6. **Çalıştır ve doğrula** — Actuator ile health check'leri yapılandır; `/actuator/health`'in `UP` döndüğünü doğrula. Health durumu `DOWN` ise: yanıttaki `components` detayına bak, hatalı bileşeni (ör. datasource) düzelt ve tekrar doğrula

## Referans Rehberi

Bağlama göre detaylı rehberi yükle:

| Konu | Referans | Ne Zaman Yüklenir |
|------|----------|-------------------|
| Web Katmanı | `references/web.md` | Controller'lar, REST API'ler, validation, exception handling |
| Veri Erişimi | `references/data.md` | Spring Data JPA, repository'ler, transaction'lar, projection'lar |
| Güvenlik | `references/security.md` | Spring Security, OAuth2, JWT, method security |
| Test | `references/testing.md` | @SpringBootTest, MockMvc, Testcontainers, test slice'ları |

## Hızlı Başlangıç — Çalışan Minimal Yapı

Standart bir Spring Boot özelliği şu katmanlardan oluşur. Örnekler proje kurallarına (`backend-conventions`) uygundur; kopyala-yapıştır başlangıç noktası olarak kullan. Paket: `com.embedding.pgvector.source`.

### Entity

```java
@Entity
@Table(name = "sources")
public class Source implements Persistable<Long> {

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SourceKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SourceStatus status;

    @Column(name = "chunk_count", nullable = false)
    private int chunkCount;

    @Column(name = "synced_at")
    private @Nullable Instant syncedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Transient
    private boolean newEntity = true;

    protected Source() {
    }

    public static Source ingesting(Workspace workspace, long id, String name, SourceKind kind, Instant createdAt) {
        return new Source(workspace, id, name, kind, SourceStatus.INGESTING, createdAt);
    }

    public void completeIngestion(@Nullable String newVersion, String newFileName, int newChunkCount, Instant newSyncedAt) {
        // ...alanları güncelle
        status = SourceStatus.READY;
    }

    public void failIngestion() {
        status = hasSuccessfulVersion() ? SourceStatus.READY : SourceStatus.FAILED;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        newEntity = false;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    // getter'lar ve private constructor
}
```

Schema Flyway migration'ı ile oluşturulur (`flyway-migrations`); entity sadece mapping'dir. Getter'lar elle yazılır (Lombok yok); setter yoktur, durum domain fiiliyle değişir (`completeIngestion`, `failIngestion`). Id sequence'ten elle verildiği için (`sourceRepository.nextSourceId()`) entity `Persistable` ile kendi "yeni mi" bilgisini taşır; aksi halde Spring Data `save` id'si dolu entity'yi `merge` eder. Yeni kaynak her zaman `INGESTING` doğar; ilk iş başarısız olursa `FAILED`, önceki sürümü olan kaynakta başarısız yeni sürüm `READY` bırakır. Embedding kolonlu `Chunk` entity'si ve benzerlik sorgusu için `references/data.md`.

### Repository

```java
public interface SourceRepository extends JpaRepository<Source, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Source s where s.workspace.id = :workspaceId and s.id = :sourceId")
    Optional<Source> findByWorkspaceIdAndIdForUpdate(@Param("workspaceId") long workspaceId, @Param("sourceId") long sourceId);

    @Query(value = "select s from Source s where s.workspace.id = :workspaceId order by s.id",
            countQuery = "select count(s) from Source s where s.workspace.id = :workspaceId")
    Page<Source> findByWorkspaceIdOrderByIdAsc(@Param("workspaceId") long workspaceId, Pageable pageable);

    @Query(value = "SELECT nextval('sources_id_seq')", nativeQuery = true)
    long nextSourceId();
}
```

### Service (constructor injection)

Okuma `SourceService`'te, dosya yükleme ve içe alma `IngestionService`'tedir:

```java
@Service
public class IngestionService {

    // constructor injection: WorkspaceRepository, WorkspaceService, SourceRepository, IngestionJobRepository, FileTypeDetector,
    // EmbeddingModelGuard, IngestionJobRunner, Clock

    @Transactional
    public IngestionJobResponse createSource(long workspaceId, String name, MultipartFile file) {
        Workspace workspace = workspaceRepository.findByIdForUpdate(workspaceId)
                .orElseThrow(() -> WorkspaceService.workspaceNotFound(workspaceId));
        byte[] content = readContent(file);
        String fileName = fileName(file);
        SourceKind kind = fileTypeDetector.detect(fileName, content);
        embeddingModelGuard.ensureWorkspaceCompatible(workspaceId);
        Instant now = Instant.now(clock);
        Source source = workspace.addSource(sourceRepository.nextSourceId(), name.strip(), kind, now);
        try {
            sourceRepository.saveAndFlush(source);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("SOURCE_NAME_TAKEN", "Source name is already used in workspace: " + workspaceId);
        }
        IngestionJob job = ingestionJobRepository.saveAndFlush(IngestionJob.queued(source, null, fileName, content.length, now));
        submitAfterCommit(job.getId(), content, kind);
        return IngestionJobResponse.from(job);
    }
}
```

Kaynak limiti ve tekil ad kuralı `Workspace.addSource` içindedir; çalışma alanı satırı kilitlenerek okunur (`backend-conventions` bölüm 3, `spring-transactions`). İş, transaction commit edildikten sonra arka plan executor'ına verilir (`submitAfterCommit`).

### REST Controller

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
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(sourceService.listSources(workspaceId, page, size));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<IngestionJobResponse> createSource(@PathVariable long workspaceId,
            @RequestPart(name = "name", required = false) @NotBlank @Pattern(regexp = NAME_SIZE_PATTERN) @Nullable String name,
            @RequestPart(name = "file", required = false) @NotEmptyFile @Nullable MultipartFile file) {
        IngestionJobResponse job = ingestionService.createSource(workspaceId, Objects.requireNonNull(name),
                Objects.requireNonNull(file));
        return ResponseEntity.accepted().location(URI.create(job.statusUrl())).body(job);
    }
}
```

- Kaynak listesi zamanla büyüyebildiği için sayfalıdır: service `Page<XResponse>` döner, controller `PageResponse.from(...)` ile sarar (`backend-conventions` bölüm 6).
- Dosya yükleme multipart'tır, request DTO'su yoktur; alanlar `@RequestPart` parametresidir ve validation anotasyonları parametrenin üstündedir (controller'da method validation açık olmalı). `required = false` + `@NotBlank`/`@NotEmptyFile`: eksik alan da `VALIDATION_FAILED` + `violations[]` olarak döner, Spring'in `MissingServletRequestPartException`'ına düşmez.
- İş arka planda sürdüğü için yanıt `202 Accepted` + `Location` (iş durumunun adresi) + `IngestionJobResponse`'tur; kaynak değil iş döner.

### DTO (record)

JSON gövdeli istek örneği aramadır:

```java
public record SearchChunksRequest(
        @NotBlank(message = "must not be blank") @Size(max = 500, message = "size must be at most 500 characters") @Nullable String query,
        @Pattern(regexp = "(?i)keyword|vector|hybrid", message = "must be one of KEYWORD, VECTOR, HYBRID") @Nullable String mode,
        @Min(value = 1, message = "must be between 1 and 50") @Max(value = 50, message = "must be between 1 and 50") @Nullable Integer limit) {

    public SearchChunksRequest {
        query = query == null ? null : query.strip();
    }
}

public record SourceResponse(long sourceId, long workspaceId, String name, SourceKind kind, @Nullable String version,
        int chunkCount, SourceStatus status, @Nullable Instant syncedAt, @Nullable String fileName, Instant createdAt,
        @Nullable IngestionJobResponse latestJob) {

    public static SourceResponse from(Source source, @Nullable IngestionJob latestJob) {
        return new SourceResponse(source.getId(), source.getWorkspaceId(), source.getName(), source.getKind(),
                source.getVersion(), source.getChunkCount(), source.getStatus(), source.getSyncedAt(),
                source.getFileName(), source.getCreatedAt(), latestJob == null ? null : IngestionJobResponse.from(latestJob));
    }
}
```

Alan listesinin tek doğru kaynağı `docs/api/openapi.yaml`'dır (`SearchChunksRequest`'in tamamında `filterStrategy`, `efSearch`, `channel`, `explain` da var).

### Global Exception Handler

Projede tek bir `GlobalExceptionHandler` vardır; kalıbı ve şablonu `problem-details-rfc9457` skill'indedir. Yeni bir domain hatası için uygun kategori sınıfını (`NotFoundException`, `ConflictException` vb.) yeni bir `errorCode` ile fırlat, yeni exception sınıfı açma; handler'a yeni metot ekleme.

### Test Slice

```java
@WebMvcTest(SourceController.class)
class SourceControllerTest {

    @Autowired
    private MockMvcTester mockMvcTester;

    @MockitoBean
    private SourceService sourceService;

    @MockitoBean
    private IngestionService ingestionService;

    @Test
    void givenAcceptedUpload_whenCreateSource_thenReturns202WithLocationAndJob() {
        // Arrange
        given(ingestionService.createSource(eq(42L), eq("Fatura SSS"), any(MultipartFile.class))).willReturn(
                new IngestionJobResponse(7L, 42L, 10001L, null, "yardim.md", IngestionStage.QUEUED, 0.0, 0, null, null,
                        Instant.parse("2026-10-25T10:00:00Z"), null, null, "/api/workspaces/42/ingestion-jobs/7"));

        // Act
        MvcTestResult result = mockMvcTester.post().uri("/api/workspaces/42/sources").multipart()
                .part(new MockPart("name", "Fatura SSS".getBytes(StandardCharsets.UTF_8)))
                .file(new MockMultipartFile("file", "yardim.md", "text/markdown", "# a".getBytes(StandardCharsets.UTF_8)))
                .exchange();

        // Assert
        assertThat(result).hasStatus(HttpStatus.ACCEPTED);
        assertThat(result).headers().hasValue("Location", "/api/workspaces/42/ingestion-jobs/7");
        assertThat(result).bodyJson().extractingPath("$.stage").isEqualTo("QUEUED");
    }
}
```

Ayrıntı: `spring-mvc-testing`.

## Constraints

### MUTLAKA YAP

| Kural | Doğru Pattern |
|-------|-------------|
| Constructor injection | `public MyService(Dep dep) { this.dep = dep; }` |
| API girdisini doğrula | Veri değiştiren her endpoint'te `@Valid @RequestBody MyRequest req` |
| Type-safe configuration | Record'a bağlı `@ConfigurationProperties(prefix = "app")` |
| Uygun stereotype | İş mantığı için `@Service`, veri için `@Repository`, HTTP için `@RestController` |
| Transaction scope | Çok adımlı yazmalarda `@Transactional`; okumalarda `@Transactional(readOnly = true)` |
| İç detayları gizle | Domain exception'larını tek `@RestControllerAdvice` içinde yakala; stack trace değil problem details dön |
| Secret'ları dışarıda tut | Environment variable kullan — asla `application.properties` içine yazma |

### ASLA YAPMA
- Field injection kullanma (alanlarda `@Autowired`)
- API endpoint'lerinde girdi doğrulamasını atlama
- `@Service`/`@Repository`/`@Controller` uygunken `@Component` kullanma
- Secret ya da kimlik bilgilerini `application.properties`/`application.yml` içinde saklama
- URL, kimlik bilgisi veya ortama özel değerleri koda gömme (hardcode)
- Eski Spring Boot 2.x/3.x pattern'lerini kullanma (ör. `WebSecurityConfigurerAdapter`, `@MockBean`, `com.fasterxml.jackson.databind` / Jackson 2 — Jackson 3 paketi `tools.jackson.*`)
- Lombok, `/api/v1` gibi versiyon önekli path ya da YAML config kullanma (proje kuralı)
- Controller'dan entity dönme; her zaman response DTO'su dön
