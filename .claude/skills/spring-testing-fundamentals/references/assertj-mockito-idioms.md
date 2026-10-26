# AssertJ + Mockito / BDDMockito Idiom'ları

Tüm Spring Boot test türlerinde kullanılan assertion ve mocking pattern'leri için hızlı referans. Klasik Mockito (`when/thenReturn`, `verify`) yerine `org.mockito.BDDMockito` (`given/willReturn`, `then(...).should()`) kullanılır.

> Proje kuralı: exception testlerinde Act ve Assert bölümlerini ayırmak için `catchThrowable` + `assertThat(thrown)` kullanılır; `assertThatThrownBy` / `assertThatExceptionOfType` Act ile Assert'i birleştirdiği için kullanılmaz (bkz. `backend-conventions`).

---

## Static Import Kuralları

```java
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
```

Wildcard (`Assertions.*`, `BDDMockito.*`) import da kabul edilir; `org.junit.jupiter.api.Assertions` ve Hamcrest import edilmez.

---

## AssertJ — Scalar Assertion'lar

```java
assertThat(source.getName()).isEqualTo("Fatura SSS");
assertThat(source.getStatus()).isEqualTo(SourceStatus.INGESTING);
assertThat(workspace.getSources()).hasSize(10);
assertThat(source.hasSuccessfulVersion()).isFalse();
assertThat(source.getSyncedAt()).isNull();
assertThat(optionalSource).isPresent().get().extracting(Source::getName).isEqualTo("Fatura SSS");
assertThat(optionalSource).isEmpty();

// double — skor ve mesafe gibi kayan noktalı değerler isEqualTo ile değil tolerans ile
assertThat(hit.distance()).isCloseTo(0.1269, within(1e-4));
assertThat(hit.rrfScore()).isCloseTo(1.0 / (60 + 1) + 1.0 / (60 + 3), within(1e-9));

// BigDecimal — scale'den bağımsız karşılaştırma için isEqualByComparingTo (isEqualTo 1.0 ile 1.00'ı farklı sayar)
assertThat(embeddingCost).isEqualByComparingTo("0.02");
```

---

## AssertJ — Collection Assertion'ları

```java
// Sıra önemli (arama sonucu sıralamadır; sıra davranışın kendisi)
assertThat(chunkIds).containsExactly(12L, 7L, 31L);

// Sıra önemsiz
assertThat(sourceNames).containsExactlyInAnyOrder("Fatura SSS", "Yardım merkezi");

// Alt küme
assertThat(chunkIds).contains(12L, 31L);
assertThat(chunkIds).hasSize(3).doesNotHaveDuplicates();

// Filtreleme
assertThat(hits)
        .filteredOn(hit -> hit.sourceId() == 301L)
        .hasSize(2);

// Tek alan çıkarma
assertThat(hits)
        .extracting(SearchHitResponse::chunkId)
        .containsExactly(12L, 7L);

// Birden çok alan (tuple)
assertThat(hits)
        .extracting(SearchHitResponse::chunkId, SearchHitResponse::rank)
        .containsExactly(
                tuple(12L, 1),
                tuple(7L, 2));

// VECTOR modda mesafelerin artan sırada olması (küçük mesafe = daha yakın)
assertThat(hits).extracting(SearchHitResponse::distance).isSorted();

// allSatisfy / anySatisfy
assertThat(hits).allSatisfy(hit ->
        assertThat(hit.distance()).isBetween(0.0, 2.0));   // cosine mesafe 0-2 aralığında
```

`tuple` için: `import static org.assertj.core.groups.Tuple.tuple;`

---

## AssertJ — Exception Assertion'ları

```java
// Proje kalıbı: Act'te yakala, Assert'te doğrula
// Act
Throwable thrown = catchThrowable(() -> sourceService.getSource(42L, 999L));

// Assert
assertThat(thrown)
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("999");

// Exception'ın alanlarını doğrulamak
assertThat(thrown)
        .asInstanceOf(InstanceOfAssertFactories.type(NotFoundException.class))
        .extracting(NotFoundException::getErrorCode)
        .isEqualTo("SOURCE_NOT_FOUND");

// Exception fırlatılmadığını doğrulamak
// Act
Throwable thrown = catchThrowable(() -> sourceService.getSource(42L, 301L));

// Assert
assertThat(thrown).isNull();
```

---

## BDDMockito — Stubbing

```java
// given / willReturn
given(workspaceRepository.findByIdForUpdate(42L))
        .willReturn(Optional.of(WorkspaceFixtures.workspaceWithSources(42L, 2)));

// willThrow
given(embeddingClient.embed(anyList()))
        .willThrow(new ServiceUnavailableException("EMBEDDING_UNAVAILABLE", "Embedding service returned 503"));

// willAnswer — argümana göre dinamik dönüş
given(sourceRepository.save(any(Source.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

// void metotlar
willDoNothing().given(ingestionProgressBroadcaster).publish(any());
willThrow(new IllegalStateException("Broker unavailable")).given(ingestionProgressBroadcaster).publish(any());
```

---

## Argument Matcher'lar

```java
// Matcher gerekmiyorsa düz değer
given(sourceRepository.findByWorkspaceIdAndId(42L, 301L)).willReturn(Optional.of(source));

// any(Class) — null olmayan ve tipi uyan her değer
given(sourceRepository.save(any(Source.class))).willReturn(source);

// KURAL: Bir argümanda matcher varsa TÜM argümanlar matcher olmalı
// YANLIŞ (matcher ile literal karışık):
given(fileTypeDetector.detect(eq("fatura-sss.md"), any())).willReturn(SourceKind.MARKDOWN);   // DOĞRU
given(fileTypeDetector.detect("fatura-sss.md", any())).willReturn(SourceKind.MARKDOWN);       // YANLIŞ: literal + matcher

// argThat — satır içi koşul
given(ingestionJobRepository.saveAndFlush(argThat(job -> job.getStage() == IngestionStage.QUEUED)))
        .willAnswer(invocation -> invocation.getArgument(0));
```

Sık kullanılan matcher'lar:

| Matcher | Eşleşir |
|---------|---------|
| `any()` | null dahil her değer |
| `any(Class)` | null **olmayan** ve tipi uyan değer |
| `anyString()`, `anyInt()`, `anyLong()` | null **olmayan** String / int / long (Mockito 2+'dan beri null eşleşmez) |
| `eq(value)` | `equals` ile eşit değer |
| `isNull()` / `notNull()` | null / null olmayan |
| `argThat(predicate)` | özel koşul |

---

## BDDMockito — Verification

```java
then(sourceRepository).should().save(any(Source.class));        // bir kez çağrıldı
then(embeddingClient).should(times(2)).embed(anyList());        // N kez (ör. 2 batch)
then(sourceRepository).should(never()).save(any());             // hiç çağrılmadı
then(ingestionProgressBroadcaster).shouldHaveNoMoreInteractions();  // az kullan — kırılgandır
```

Doğrulama, gözlemlenebilir yan etkiler içindir (kayıt yapıldı/yapılmadı, event yayınlandı). Dönüş değeri zaten assert edilen bir sorgu çağrısını ayrıca `should()` ile doğrulama.

---

## ArgumentCaptor

Mock metoda geçirilen argümanı yakala ve üzerinde assertion yap:

```java
@Captor
private ArgumentCaptor<IngestionJob> jobCaptor;

@Test
void givenWorkspaceBelowSourceLimit_whenCreateSource_thenSavesQueuedJobWithFileName() {
    // Arrange
    given(workspaceRepository.findByIdForUpdate(42L))
            .willReturn(Optional.of(WorkspaceFixtures.workspaceWithSources(42L, 1)));
    given(fileTypeDetector.detect(eq("fatura-sss.md"), any())).willReturn(SourceKind.MARKDOWN);
    given(sourceRepository.nextSourceId()).willReturn(10001L);
    given(ingestionJobRepository.saveAndFlush(any(IngestionJob.class))).willAnswer(invocation -> invocation.getArgument(0));

    // Act
    ingestionService.createSource(42L, "Fatura SSS", FILE);

    // Assert
    then(ingestionJobRepository).should().saveAndFlush(jobCaptor.capture());
    assertThat(jobCaptor.getValue().getStage()).isEqualTo(IngestionStage.QUEUED);
    assertThat(jobCaptor.getValue().getFileName()).isEqualTo("fatura-sss.md");
}
```

Birden çok çağrı için `jobCaptor.getAllValues()`. `@Captor`'ın inject edilmesi için `@ExtendWith(MockitoExtension.class)` gerekir; alternatif: `ArgumentCaptor.forClass(IngestionJob.class)`.

---

## @MockitoBean vs @MockitoSpyBean (Boot 4)

```java
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

// @MockitoBean — tam mock; stub'lanmayan metotlar varsayılan değer döner
// Boot 4'te kaldırılan org.springframework.boot.test.mock.mockito.MockBean'in yerine
@MockitoBean
private SourceService sourceService;

// @MockitoSpyBean — GERÇEK bean'i sarar; stub'lanmayan metotlar gerçek kodu çalıştırır
// Boot 4'te kaldırılan @SpyBean'in yerine
@MockitoSpyBean
private IngestionProgressBroadcaster ingestionProgressBroadcaster;

// Spy'da sadece gerekeni değiştir:
willDoNothing().given(ingestionProgressBroadcaster).onIngestionProgressed(any());
```

**`@MockitoSpyBean` uyarısı**: Gerçek bean'in test slice context'inde yüklenebilir olması gerekir. `@WebMvcTest`'te sadece web katmanı bean'leri yüklenir — bir service'in dependency'leri karşılanmamışsa o service üzerindeki `@MockitoSpyBean` fail olur.

---

## Strict Stubbing ve UnnecessaryStubbingException

`MockitoExtension` varsayılan olarak strict stubbing kullanır: testte hiç çağrılmayan bir stub tanımlarsan `UnnecessaryStubbingException` fırlar.

```java
// SEBEP: stub @BeforeEach'te, ama sadece bir test kullanıyor
@BeforeEach
void setUp() {
    given(sourceRepository.findByWorkspaceIdAndId(42L, 301L)).willReturn(Optional.of(source));
}
```

**Düzeltmeler** (tercih sırasıyla):

```java
// A (tercih edilen): stub'ı ona ihtiyaç duyan testin Arrange bölümüne taşı
@Test
void givenExistingSource_whenGetSource_thenReturnsSource() {
    // Arrange
    given(sourceRepository.findByWorkspaceIdAndId(42L, 301L)).willReturn(Optional.of(source));
    ...
}

// B: sadece bu mock için lenient strictness
@Mock(strictness = Mock.Strictness.LENIENT)
private SourceRepository sourceRepository;

// C (kaçın): sınıf seviyesinde gevşetme
@MockitoSettings(strictness = Strictness.LENIENT)
class SourceServiceTest { }
```

`lenient().when(...)` klasik Mockito API'sidir; BDD stiliyle uyumlu olduğu için B seçeneği tercih edilir.

---

## Web Katmanında Assertion (MockMvcTester)

Projede controller testleri `MockMvcTester` (AssertJ tabanlı) ile yazılır; Hamcrest `jsonPath` matcher'ları kullanılmaz.

```java
MvcTestResult result = mockMvcTester.post().uri("/api/workspaces/{workspaceId}/search", 42)
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
                {"query":"Şifremi unuttum","mode":"VECTOR","limit":2}
                """)
        .exchange();

assertThat(result).hasStatusOk()
        .bodyJson().extractingPath("$.hits[*].chunkId").asArray().containsExactly(12, 7);

// Body'yi tipli nesneye çevirip AssertJ ile
assertThat(result).bodyJson().convertTo(SearchResultResponse.class)
        .satisfies(response -> assertThat(response.hits()).hasSize(2));
```

Ayrıntı: `spring-mvc-testing`. JSON serileştirme gerekiyorsa Jackson 3 `JsonMapper` (`tools.jackson.databind.json.JsonMapper`) inject edilir; Jackson 2 `ObjectMapper` kullanılmaz.

---

## Sık Yapılan Hatalar

```java
// HATA 1: Act'ten sonra stub'lamak — stub her zaman Arrange'da, çağrıdan ÖNCE
ingestionService.createSource(42L, "Fatura SSS", FILE);
given(workspaceRepository.findByIdForUpdate(42L)).willReturn(...);   // YANLIŞ — çok geç

// HATA 2: Matcher ile literal karıştırmak
given(fileTypeDetector.detect("fatura-sss.md", any())).willReturn(SourceKind.MARKDOWN);       // HATA
given(fileTypeDetector.detect(eq("fatura-sss.md"), any())).willReturn(SourceKind.MARKDOWN);   // DOĞRU

// HATA 3: Implementation detayını doğrulamak
then(sourceMapper).should().toResponse(any());   // gürültü — davranışı değil implementation'ı test eder
// DOĞRU: gözlemlenebilir yan etkiyi doğrula
then(ingestionJobRepository).should().saveAndFlush(any(IngestionJob.class));

// HATA 4a: Skoru isEqualTo ile doğrulamak — kayan nokta; isCloseTo(..., within(...)) kullan

// HATA 4: DTO veya value object mock'lamak — gerçek instance kullan (record'lar zaten basit)

// HATA 5: @BeforeEach'te tüm testlerin kullanmadığı stub'lar — stub'ları ilgili testlere taşı
```

---

## Boot 3.x → 4.x Mockito/AssertJ Değişiklikleri

| Alan | Boot 3.x | Boot 4.x |
|---|---|---|
| `@MockBean` | `org.springframework.boot.test.mock.mockito.MockBean` | Kaldırıldı — `org.springframework.test.context.bean.override.mockito.MockitoBean` |
| `@SpyBean` | `org.springframework.boot.test.mock.mockito.SpyBean` | Kaldırıldı — `@MockitoSpyBean` |
| MVC test client | `MockMvc` (Boot 3.4+ `MockMvcTester` de var) | `MockMvcTester` (projede tercih edilen), `MockMvc`, `RestTestClient` |
| JUnit | Jupiter 5 | Jupiter 6 |
| Jackson | 2 (`com.fasterxml.jackson.databind`) | 3 (`tools.jackson.databind`) |
| Strict stubbing | `MockitoExtension` ile varsayılan | Aynı |

---

## Hızlı Referans

| İhtiyaç | Kullan |
|---|---|
| Dönüş değeri stub'lama | `given(mock.method(args)).willReturn(value)` |
| Void metodu hiçbir şey yapmayacak şekilde stub'lama | `willDoNothing().given(mock).method(args)` |
| Exception fırlatacak şekilde stub'lama | `given(mock.method(args)).willThrow(exception)` |
| Çağrının yapıldığını doğrulama | `then(mock).should().method(args)` |
| Hiç çağrılmadığını doğrulama | `then(mock).should(never()).method(any())` |
| Argümanı yakalama | `then(mock).should().method(captor.capture()); captor.getValue()` |
| Exception assertion'ı | `catchThrowable(() -> ...)` + `assertThat(thrown).isInstanceOf(X.class)` |
| Collection field assertion'ı | `assertThat(list).extracting(X::field).contains(...)` |
| BigDecimal assertion'ı | `assertThat(value).isEqualByComparingTo("9.50")` |
| Skor / mesafe (double) | `assertThat(score).isCloseTo(0.8731, within(1e-4))` |
| JSON response | `assertThat(result).bodyJson().extractingPath("$.field").isEqualTo(value)` |
