---
name: spring-testing-fundamentals
description: "Spring Boot 4'te @MockBean yerine @MockitoBean geldi, test slice annotation'ları yeni paketlere taşındı ve context caching davranışı değişti. Doğru AssertJ/BDDMockito idiom'ları ve Boot 4 migration'ı için oku. Belirli bir slice annotation'ı yoksa varsayılan test skill'idir. Tetikleyiciler: unit test, assertThat, BDDMockito, given/willReturn, ArgumentCaptor, @MockitoBean, @MockitoSpyBean, @DirtiesContext, UnnecessaryStubbingException, testing pyramid, context cache."
license: Apache-2.0
metadata:
  source: https://github.com/spring-ai-community/spring-testing-skills/tree/main/skills/spring-testing-fundamentals
  version: 0.1.0
---

# Spring Testing Fundamentals

**Sinyaller**: `assertThat`, `assertThatThrownBy` (projede yerine `catchThrowable`), `given/willReturn`, `then(mock).should`, `ArgumentCaptor`, `@MockitoBean`, `@MockitoSpyBean`, `@DirtiesContext`, context caching, testing pyramid

## Proje kuralı (öncelikli)

Test metot isimlendirmesi (`given_when_then`), gövde yapısı (Arrange / Act / Assert), BDDMockito ve `catchThrowable` için `backend-conventions` skill'i geçerlidir.

## Test edildiği sürümler

- Proje: Spring Boot 4.1.1, Spring Framework 7.0, JUnit Jupiter 6, Mockito 5.23, AssertJ 3.27 (sürümleri Boot yönetir)

## Bu skill'i KULLANMA

Alana özel pattern gerekiyorsa:
- JPA repository, Testcontainers, `@DataJpaTest` → `spring-jpa-testing`
- REST controller, MockMvc, `@WebMvcTest` → `spring-mvc-testing`
- WebSocket/STOMP → `spring-websocket-testing`
- Security, `@WithMockUser`, JWT → `springboot-security` (projede Spring Security yok)

## Unit test iskeleti (Spring context yok)

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

    private IngestionService ingestionService;

    @BeforeEach
    void setUp() {
        ingestionService = new IngestionService(workspaceRepository, workspaceService, sourceRepository,
                ingestionJobRepository, fileTypeDetector, embeddingModelGuard, ingestionJobRunner, FIXED_CLOCK);
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
        then(sourceRepository).should(never()).saveAndFlush(any());
    }
}
```

Constructor'da `Clock` gibi mock'lanmaması gereken bir değer olduğu için service `@BeforeEach`'te elle kurulur; `@InjectMocks` karşılanamayan parametreye sessizce `null` geçer. `@InjectMocks` sadece tüm constructor parametreleri `@Mock` olan sınıflarda kullanılır. Transaction senkronizasyonuna (`afterCommit`) dayanan kısım unit testte çalışmaz; arka plan işinin başladığı integration testinde doğrulanır.

## Referansları ne zaman oku

| Durum | Oku |
|-------|-----|
| AssertJ scalar, Optional, BigDecimal, collection, exception assertion'ları | `references/assertj-mockito-idioms.md` |
| BDDMockito `given/willReturn`, `then(mock).should`, argument matcher'lar | `references/assertj-mockito-idioms.md` |
| `ArgumentCaptor` kullanımı ve verification pattern'leri | `references/assertj-mockito-idioms.md` |
| `UnnecessaryStubbingException` ve strict stubbing düzeltmeleri | `references/assertj-mockito-idioms.md` |
| Boot 4'te `@MockitoBean` vs `@MockitoSpyBean` | `references/assertj-mockito-idioms.md` |
| Testing pyramid: hangi slice annotation ne zaman | `references/cross-cutting-testing-patterns.md` |
| Context caching: testler neden yavaş, cache key, `@DirtiesContext` etkisi | `references/cross-cutting-testing-patterns.md` |
| Tüm Spring test türlerinde geçerli anti-pattern'ler | `references/cross-cutting-testing-patterns.md` |
| Boot 4 annotation migration checklist'i | `references/cross-cutting-testing-patterns.md` |
