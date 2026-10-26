# Test - Spring Boot Test

> Test kuralları `backend-conventions` bölüm 5'tedir (given_when_then, AAA, BDDMockito, `catchThrowable`, `<Entity>Fixtures`, `@DisplayName` yok). Bu dosya Boot 4 test altyapısının özetidir; ayrıntılı pattern'ler:
> unit / AssertJ / Mockito → `spring-testing-fundamentals`, controller → `spring-mvc-testing`, repository ve Testcontainers → `spring-jpa-testing`, STOMP → `spring-websocket-testing`.
>
> Ekip akışında testleri `tester` agent'ı yazar; `backend-coder` test yazmaz.

## Spring Boot 4.1 Notları

Spring Boot 4.1: JUnit Jupiter 6, Mockito 5, AssertJ 3.27, Jackson 3, Testcontainers 2. Test anotasyonları teknoloji bazlı modüllerdedir; her teknoloji için ilgili `*-test` starter'ını ekle:

| Anotasyon / sınıf | Paket | Gereken test dependency'si |
|---|---|---|
| `@WebMvcTest`, `@AutoConfigureMockMvc` | `org.springframework.boot.webmvc.test.autoconfigure` | `spring-boot-starter-webmvc-test` |
| `MockMvcTester` | `org.springframework.test.web.servlet.assertj` | (`spring-test` ile gelir) |
| `@DataJpaTest` | `org.springframework.boot.data.jpa.test.autoconfigure` | `spring-boot-starter-data-jpa-test` |
| `@AutoConfigureTestDatabase` | `org.springframework.boot.jdbc.test.autoconfigure` | (`data-jpa-test` ile gelir) |
| `TestEntityManager` | `org.springframework.boot.jpa.test.autoconfigure` | (`data-jpa-test` ile gelir) |
| `TestRestTemplate`, `@AutoConfigureTestRestTemplate`, `@AutoConfigureRestTestClient` | `org.springframework.boot.resttestclient` (+ `.autoconfigure`) | `spring-boot-resttestclient` |
| `RestTestClient` | `org.springframework.test.web.servlet.client` | (`spring-test` ile gelir) |
| `@WithMockUser` | `org.springframework.security.test.context.support` | `spring-boot-starter-security-test` (Security eklenirse) |
| `@ServiceConnection` | `org.springframework.boot.testcontainers.service.connection` | `spring-boot-testcontainers` |
| `PostgreSQLContainer` | `org.testcontainers.postgresql` | `org.testcontainers:testcontainers-postgresql` |
| `@MockitoBean`, `@MockitoSpyBean` | `org.springframework.test.context.bean.override.mockito` | (`spring-test` ile gelir) |

- `@SpringBootTest` tek başına `MockMvc`, `TestRestTemplate` veya `RestTestClient` bean'i sağlamaz: `@AutoConfigureMockMvc`, `@AutoConfigureTestRestTemplate` ya da `@AutoConfigureRestTestClient` ekle.
- Jackson 3 paketleri `tools.jackson.*` (anotasyonlar `com.fasterxml.jackson.annotation`). JSON için `tools.jackson.databind.json.JsonMapper` bean'ini inject et.
- Mockito, `pom.xml`'de surefire `argLine` ile Java agent olarak yüklenir (JDK 21+ dinamik agent yüklemeyi kısıtlar).

## Unit Test

```java
@ExtendWith(MockitoExtension.class)
class IngestionServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private static final MockMultipartFile FILE =
            new MockMultipartFile("file", "fatura-sss.md", "text/markdown", "# Fatura SSS".getBytes(StandardCharsets.UTF_8));

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private WorkspaceService workspaceService;

    @Mock
    private SourceRepository sourceRepository;

    @Mock
    private IngestionJobRepository ingestionJobRepository;

    @Mock
    private FileTypeDetector fileTypeDetector;

    @Mock
    private EmbeddingModelGuard embeddingModelGuard;

    @Mock
    private IngestionJobRunner ingestionJobRunner;

    @Captor
    private ArgumentCaptor<Source> sourceCaptor;

    private IngestionService ingestionService;

    @BeforeEach
    void setUp() {
        ingestionService = new IngestionService(workspaceRepository, workspaceService, sourceRepository, ingestionJobRepository,
                fileTypeDetector, embeddingModelGuard, ingestionJobRunner, FIXED_CLOCK);
    }

    @Test
    void givenWorkspaceWithFreeSlot_whenCreateSource_thenSavesIngestingSourceWithRequestedName() {
        // Arrange
        given(workspaceRepository.findByIdForUpdate(42L))
                .willReturn(Optional.of(WorkspaceFixtures.workspaceWithSources(42L, 0)));
        given(fileTypeDetector.detect(eq("fatura-sss.md"), any())).willReturn(SourceKind.MARKDOWN);
        given(sourceRepository.nextSourceId()).willReturn(10001L);
        given(ingestionJobRepository.saveAndFlush(any(IngestionJob.class))).willAnswer(invocation -> invocation.getArgument(0));

        // Act
        ingestionService.createSource(42L, "  Fatura SSS ", FILE);

        // Assert
        then(sourceRepository).should().saveAndFlush(sourceCaptor.capture());
        assertThat(sourceCaptor.getValue().getName()).isEqualTo("Fatura SSS");
        assertThat(sourceCaptor.getValue().getStatus()).isEqualTo(SourceStatus.INGESTING);
    }

    @Test
    void givenUnknownWorkspaceId_whenCreateSource_thenThrowsWorkspaceNotFound() {
        // Arrange
        given(workspaceRepository.findByIdForUpdate(999L)).willReturn(Optional.empty());

        // Act
        Throwable thrown = catchThrowable(() -> ingestionService.createSource(999L, "Fatura SSS", FILE));

        // Assert
        assertThat(thrown).isInstanceOf(NotFoundException.class)
                .extracting("errorCode").isEqualTo("WORKSPACE_NOT_FOUND");
    }
}
```

- Service'in constructor'ında `Clock` gibi mock'lanmaması gereken bir değer varsa `@InjectMocks` yerine service `@BeforeEach`'te elle kurulur; `@InjectMocks` karşılanamayan parametreye sessizce `null` geçer ve test NPE ile düşer.
- Assertion mock'un döndürdüğü değere değil, gözlemlenebilir yan etkiye (kaydedilen `Source`) yapılır.
- Transaction senkronizasyonuna dayanan kod (`submitAfterCommit`) unit testte aktif transaction olmadığı için çalışmaz; arka plan işinin başlaması integration testinde doğrulanır.
- `SearchService` testlerinde `EmbeddingClient` deterministik sahte implementasyonla verilir (aynı metin → aynı vektör); RRF birleştirmesi elle hesaplanmış sıralarla doğrulanır.

## Controller Slice Testi

```java
@WebMvcTest(SourceController.class)
class SourceControllerTest {

    @Autowired
    private MockMvcTester mockMvcTester;

    @MockitoBean
    private SourceService sourceService;

    @Test
    void givenUnknownSourceId_whenGetSource_thenReturns404ProblemDetail() {
        // Arrange
        given(sourceService.getSource(42L, 999L)).willThrow(new NotFoundException("SOURCE_NOT_FOUND", "Source not found: 999"));

        // Act
        MvcTestResult result = mockMvcTester.get().uri("/api/workspaces/{workspaceId}/sources/{sourceId}", 42, 999).exchange();

        // Assert
        assertThat(result).hasStatus(HttpStatus.NOT_FOUND)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().extractingPath("$.errorCode").isEqualTo("SOURCE_NOT_FOUND");
    }
}
```

`@WebMvcTest`, `@RestControllerAdvice` bean'lerini de yükler; böylece `GlobalExceptionHandler` controller testinde gerçek haliyle çalışır.

## Tam Integration Testi

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
class SourceIntegrationTest {

    @Autowired
    private RestTestClient restTestClient;

    @Test
    void givenSeededWorkspace_whenCreateSource_thenReturns202WithJobLocation() {
        // Arrange
        MultipartBodyBuilder parts = new MultipartBodyBuilder();
        parts.part("name", "Fatura SSS");
        parts.part("file", new ByteArrayResource("# Fatura SSS".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "fatura-sss.md";
            }
        });

        // Act
        RestTestClient.ResponseSpec response = restTestClient.post().uri("/api/workspaces/{workspaceId}/sources", 42)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(parts.build())
                .exchange();

        // Assert
        response.expectStatus().isAccepted()
                .expectHeader().exists(HttpHeaders.LOCATION)
                .expectBody().jsonPath("$.stage").isEqualTo("QUEUED");
    }
}
```

- `RANDOM_PORT` testlerinde test ve server ayrı thread'lerdedir; test seviyesindeki `@Transactional` rollback yapmaz. Veriyi her testten **önce** temizle (`spring-jpa-testing/references/transactional-tests.md`).
- `@TestMethodOrder` ile testleri birbirine bağımlı yapma; her test kendi verisini kurar.
- `TestcontainersConfiguration` tanımı (pgvector eklentili imaj): `spring-jpa-testing/references/testcontainers.md`.
- Projedeki integration testleri ortak bir taban sınıfından türer (`FakeEmbeddingIntegrationTest`: Testcontainers PostgreSQL + deterministik sahte `EmbeddingClient` + `api()` yardımcıları); yeni testte önce onu kullan. Arka plan işinin bitmesini beklerken `Thread.sleep` değil Awaitility ya da iş durumunu sorgulayan bir döngü kullanılır.
- Integration testlerinde `EmbeddingClient` bean'i deterministik sahte implementasyonla değiştirilir (`@TestConfiguration` + `@Primary`); testler ağa çıkmaz.

## Test Configuration

`src/test/resources/application-test.properties` sadece testlere özel değerleri içerir; veritabanı bağlantısı `@ServiceConnection`'dan gelir (H2 yok):

```properties
management.tracing.export.enabled=false
logging.level.org.hibernate.SQL=DEBUG
```

Zamana bağlı iş kuralları için `Clock` bean'i enjekte edilir; testte sabit saat:

```java
@TestConfiguration(proxyBeanMethods = false)
public class FixedClockTestConfiguration {

    @Bean
    @Primary
    Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    }
}
```

## Test Verisi

```java
public final class WorkspaceFixtures {

    private WorkspaceFixtures() {
    }

    public static Workspace workspaceWithSources(long workspaceId, int sourceCount) {
        Workspace workspace = Workspace.create("Dalga Fiber", Instant.parse("2026-01-01T00:00:00Z"));
        ReflectionTestUtils.setField(workspace, "id", workspaceId);
        for (int index = 1; index <= sourceCount; index++) {
            workspace.addSource("Kaynak " + index, SourceKind.MARKDOWN);
        }
        return workspace;
    }
}
```

## Hızlı Başvuru

| Anotasyon | Amaç |
|------------|---------|
| `@SpringBootTest` | Tam uygulama context'i ile integration test |
| `@WebMvcTest` | MVC controller'larını mock'lanmış service'lerle test eder |
| `@DataJpaTest` | JPA repository'lerini test eder (Testcontainers ile) |
| `@MockitoBean` | Spring context'ine mock bean ekler |
| `@ServiceConnection` | Container bağlantı bilgilerini Spring Boot'a otomatik aktarır |
| `@ActiveProfiles("test")` | Test profilini etkinleştirir (farklı profil = farklı context cache key) |

## Test İçin Kurallar

- AAA yapısı ve `given_when_then` isimleri (`backend-conventions`)
- En dar slice'ı seç (`spring-testing-fundamentals`)
- Dış bağımlılıkları mock'la; veritabanı için Testcontainers
- Satır coverage en az %80 (`springboot-verification`)
- Happy path ve görev dosyasındaki her hata senaryosu test edilir
- Birden çok girdi varyasyonu için `@ParameterizedTest`
