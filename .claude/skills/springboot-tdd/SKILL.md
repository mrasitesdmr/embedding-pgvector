---
name: springboot-tdd
description: JUnit (Jupiter), Mockito, MockMvcTester, Testcontainers ve JaCoCo kullanarak Spring Boot için test-driven development (TDD). Özellik eklerken, bug düzeltirken, refactoring yaparken veya test yazarken kullan.
metadata:
  origin: ECC
  adapted-for: Spring Boot 4.1 / JUnit Jupiter 6 / Testcontainers 2
---

# Spring Boot TDD İş Akışı

> **Proje kuralı:** Test isimlendirmesi (`given<Precondition>_when<Action>_then<ExpectedResult>`), AAA yapısı, BDDMockito (`given/then`), `catchThrowable` ve `<Entity>Fixtures` için `backend-conventions` geçerlidir. Bu dosyadaki örneklerle çelişirse `backend-conventions` kazanır.
>
> **Ekip akışı:** Testleri `tester` agent'ı yazar ve çalıştırır; `backend-coder` test yazmaz (`CLAUDE.md` handoff protokolü). Bu skill'deki kırmızı → yeşil → refactor döngüsü, tek kişi çalışırken ya da `tester` bir bug için önce başarısız test yazıp coder'a geri gönderirken uygulanır.

Satır coverage'ı en az %80 olan (unit + integration) Spring Boot servisleri için TDD rehberi.

## Ne Zaman Kullanılır

- Yeni özellikler veya endpoint'ler
- Bug düzeltmeleri (önce bug'ı yakalayan başarısız test)
- Refactoring (önce mevcut davranışı sabitleyen testler)

## İş Akışı

1) Görev dosyasındaki her kural ve örnek senaryo için test yaz (başarısız olmalılar)
2) Testleri geçirecek en az kodu yaz
3) Testler yeşilken refactor et
4) Coverage'ı JaCoCo `check` ile zorunlu kıl

## Unit Test'ler (JUnit Jupiter + Mockito)

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
        ingestionService = new IngestionService(workspaceRepository, workspaceService, sourceRepository,
                ingestionJobRepository, fileTypeDetector, embeddingModelGuard, ingestionJobRunner, FIXED_CLOCK);
    }

    @Test
    void givenWorkspaceBelowSourceLimit_whenCreateSource_thenSavesIngestingSource() {
        // Arrange
        given(workspaceRepository.findByIdForUpdate(42L))
                .willReturn(Optional.of(WorkspaceFixtures.workspaceWithSources(42L, 3)));
        given(fileTypeDetector.detect(eq("fatura-sss.md"), any())).willReturn(SourceKind.MARKDOWN);
        given(sourceRepository.nextSourceId()).willReturn(10001L);
        given(ingestionJobRepository.saveAndFlush(any(IngestionJob.class))).willAnswer(invocation -> invocation.getArgument(0));

        // Act
        ingestionService.createSource(42L, "Fatura SSS", FILE);

        // Assert
        then(sourceRepository).should().saveAndFlush(sourceCaptor.capture());
        assertThat(sourceCaptor.getValue().getStatus()).isEqualTo(SourceStatus.INGESTING);
    }

    @Test
    void givenWorkspaceWithTenSources_whenCreateSource_thenThrowsSourceLimitReached() {
        // Arrange
        given(workspaceRepository.findByIdForUpdate(42L))
                .willReturn(Optional.of(WorkspaceFixtures.workspaceWithSources(42L, 10)));
        given(fileTypeDetector.detect(eq("fatura-sss.md"), any())).willReturn(SourceKind.MARKDOWN);

        // Act
        Throwable thrown = catchThrowable(() -> ingestionService.createSource(42L, "Fatura SSS", FILE));

        // Assert
        assertThat(thrown).isInstanceOf(ConflictException.class)
                .extracting("errorCode").isEqualTo("SOURCE_LIMIT_REACHED");
        then(sourceRepository).should(never()).saveAndFlush(any());
    }
}
```

Constructor'da `Clock` gibi mock'lanmayacak bir bağımlılık olduğu için service `@BeforeEach`'te elle kurulur (`@InjectMocks` karşılanamayan parametreye `null` geçer). Kaynak limiti kuralı ayrıca doğrudan `Workspace.addSource` üzerinde unit test edilir; iş aşamaları ve ilerleme hesabı `IngestionJob` entity'si üzerinde (`IngestionJobTest`).

Pattern'ler:
- Arrange-Act-Assert, `// Arrange` / `// Act` / `// Assert` yorumlarıyla
- Kısmi mock'lardan kaçın; açık stub'lamayı tercih et
- Sınır değerler için `@ParameterizedTest` (arama `limit` 0, 1, 50, 51; `efSearch` 9, 10, 1000, 1001). Multipart alanlarının (kaynak adı 1, 2, 80, 81 karakter) sınırları controller testinde denenir; DTO'ları yoktur

```java
private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

@ParameterizedTest
@ValueSource(ints = {0, 51})
void givenLimitOutsideOneToFifty_whenValidate_thenHasLimitViolation(int limit) {
    // Act
    Set<ConstraintViolation<SearchChunksRequest>> violations =
            validator.validate(new SearchChunksRequest("Şifremi unuttum", null, null, null, limit, null, null));

    // Assert
    assertThat(violations).extracting(violation -> violation.getPropertyPath().toString()).containsExactly("limit");
}
```

## Web Katmanı Testleri (MockMvcTester)

```java
@WebMvcTest(SourceController.class)
class SourceControllerTest {

    @Autowired
    private MockMvcTester mockMvcTester;

    @MockitoBean
    private SourceService sourceService;

    @Test
    void givenWorkspaceWithoutSources_whenListSources_thenReturnsEmptyPage() {
        // Arrange
        given(sourceService.listSources(eq(42L), any(Pageable.class))).willReturn(Page.empty());

        // Act
        MvcTestResult result = mockMvcTester.get().uri("/api/workspaces/42/sources").exchange();

        // Assert
        assertThat(result).hasStatusOk()
                .bodyJson().extractingPath("$.content").asArray().isEmpty();
    }
}
```

- Mock bean: `@MockitoBean` (`org.springframework.test.context.bean.override.mockito`)
- `@WebMvcTest`, `@AutoConfigureMockMvc`: `org.springframework.boot.webmvc.test.autoconfigure` (`spring-boot-starter-webmvc-test`)
- `@SpringBootTest` tek başına `MockMvcTester` sağlamaz; `@AutoConfigureMockMvc` ekle
- Ayrıntı: `spring-mvc-testing`

## Integration Test'leri (SpringBootTest)

```java
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SourceIntegrationTest {

    @Autowired
    private MockMvcTester mockMvcTester;

    @Test
    void givenValidSourceRequest_whenCreateSource_thenReturns201() {
        // Act
        MvcTestResult result = mockMvcTester.post().uri("/api/workspaces/42/sources")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Fatura SSS","kind":"MARKDOWN"}
                        """)
                .exchange();

        // Assert
        assertThat(result).hasStatus(HttpStatus.CREATED);
    }
}
```

## Persistence Test'leri (DataJpaTest)

- `@DataJpaTest`: `org.springframework.boot.data.jpa.test.autoconfigure`; `TestEntityManager`: `org.springframework.boot.jpa.test.autoconfigure` (`spring-boot-starter-data-jpa-test`)
- Boot 3.4+'te `@AutoConfigureTestDatabase`'in varsayılanı `Replace.NON_TEST`'tir; `@ServiceConnection` ile gelen Testcontainers datasource'u değiştirilmez, ek annotation gerekmez.

```java
@DataJpaTest
@Import(TestcontainersConfiguration.class)
class ChunkRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ChunkRepository chunkRepository;

    @Test
    void givenThreeChunks_whenFindNearest_thenReturnsChunksOrderedByCosineDistance() {
        // Arrange
        Source source = entityManager.persist(SourceFixtures.source(42L, "Yardım merkezi"));
        entityManager.persist(ChunkFixtures.chunkWithEmbedding(source, "aynı yön", 1f, 0f, 0f));
        entityManager.persist(ChunkFixtures.chunkWithEmbedding(source, "dik", 0f, 1f, 0f));
        entityManager.persist(ChunkFixtures.chunkWithEmbedding(source, "yakın", 0.8f, 0.6f, 0f));
        entityManager.flush();
        entityManager.clear();

        // Act
        List<ChunkHit> result = chunkRepository.findNearest(42L, ChunkFixtures.vectorLiteral(1f, 0f, 0f), ChunkFixtures.MODEL, 2);

        // Assert
        assertThat(result).extracting(ChunkHit::getContent).containsExactly("aynı yön", "yakın");
    }
}
```

Ayrıntı: `spring-jpa-testing`.

## Testcontainers

- Production ile aynı image: `postgres:18-alpine`; container `@TestConfiguration` içinde `@Bean @ServiceConnection` olarak tanımlanır (`spring-jpa-testing/references/testcontainers.md`)
- `@DynamicPropertySource` sadece service connection desteği olmayan durumlar için
- Dependency'ler (Testcontainers 2.x, sürümü Boot yönetir): `org.springframework.boot:spring-boot-testcontainers`, `org.testcontainers:testcontainers-postgresql`, `org.testcontainers:testcontainers-junit-jupiter`; sınıf `org.testcontainers.postgresql.PostgreSQLContainer`

## Coverage (JaCoCo)

`pom.xml`'de surefire `argLine`'ı `@{argLine} ...` ile başlar; JaCoCo `prepare-agent` bu property'yi doldurur ve Mockito agent'ı ile birleşir. Java 26'yı resmi destekleyen ilk sürüm 0.8.15'tir.

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.15</version>
    <executions>
        <execution>
            <id>prepare-agent</id>
            <goals>
                <goal>prepare-agent</goal>
            </goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>verify</phase>
            <goals>
                <goal>report</goal>
            </goals>
        </execution>
        <execution>
            <id>check</id>
            <phase>verify</phase>
            <goals>
                <goal>check</goal>
            </goals>
            <configuration>
                <rules>
                    <rule>
                        <element>BUNDLE</element>
                        <limits>
                            <limit>
                                <counter>LINE</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.80</minimum>
                            </limit>
                        </limits>
                    </rule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

## Assertion'lar

- AssertJ (`assertThat`); JUnit `assertEquals` yok
- JSON yanıtları için `MockMvcTester` + `bodyJson().extractingPath(...)`
- Exception'lar için: `catchThrowable(...)` + Assert bölümünde `assertThat(thrown)` (`assertThatThrownBy` Act ile Assert'i birleştirdiği için kullanılmaz)

## Test Verisi

`<Entity>Fixtures` sınıflarında statik factory metotları (builder sınıfı açılmaz):

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

Vektör fixture'ları elle sonucu bilinen küçük vektörlerdir; testteki 3 boyutlu vektörler gerçek kolonun boyutuyla (1024) uyuşmaz. Bu yüzden `ChunkFixtures.chunkWithEmbedding` vektörü sıfırlarla `EMBEDDING_DIMENSIONS` boyutuna tamamlar; cosine mesafe sıfır bileşenlerden etkilenmez, beklenen sıralama elle hesaplanabilir kalır:

```java
public static float[] padded(float... leading) {
    return Arrays.copyOf(leading, EMBEDDING_DIMENSIONS);
}
```

## Komutlar

- Tüm testler + coverage kontrolü: `./mvnw verify`
- Sadece testler: `./mvnw test`
- Tek sınıf: `./mvnw test -Dtest=SourceServiceTest`

**Unutma**: Testleri hızlı, izole ve deterministik tut. Uygulama detaylarını değil davranışı test et.
