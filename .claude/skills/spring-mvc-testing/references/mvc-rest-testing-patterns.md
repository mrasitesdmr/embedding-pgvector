# MVC / REST Controller Test Pattern'leri

`@WebMvcTest` (`org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`) ve `MockMvcTester` (`org.springframework.test.web.servlet.assertj.MockMvcTester`) ile Spring MVC controller'larını test etmek için hızlı referans. Test isimleri ve AAA yapısı `backend-conventions`'a uyar.

Projede controller testleri **`MockMvcTester`** ile yazılır: AssertJ tabanlıdır, checked exception fırlatmaz, Hamcrest gerektirmez. Klasik `MockMvc` + `andExpect(...)` API'si geçerlidir ama yeni testlerde kullanılmaz (sonda karşılaştırma tablosu var).

---

## @WebMvcTest Temelleri

`@WebMvcTest` bir Spring MVC test slice'ı oluşturur:

- **Yükler**: `@Controller` / `@RestController`, `@ControllerAdvice` / `@RestControllerAdvice`, `@JacksonComponent`, `Converter`, `Filter`, `WebMvcConfigurer`, `HandlerMethodArgumentResolver`; Spring Security classpath'teyse filter chain
- **YÜKLEMEZ**: `@Service`, `@Repository`, sıradan `@Component` — controller'ın bağımlılıklarını `@MockitoBean` ile mock'la
- `MockMvcTester` bean'i AssertJ classpath'teyken otomatik gelir; ek annotation gerekmez

```java
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@WebMvcTest(SearchController.class)
class SearchControllerTest {

    @Autowired
    private MockMvcTester mockMvcTester;

    @MockitoBean
    private SearchService searchService;

    @Test
    void givenTwoHits_whenSearchChunks_thenReturnsHitsInRankOrder() {
        // Arrange
        given(searchService.searchChunks(eq(42L), any(SearchChunksRequest.class)))
                .willReturn(new SearchResultResponse("Şifremi unuttum", SearchMode.VECTOR, List.of(
                        new SearchHitResponse(12L, 301L, "Yardım merkezi", "Şifre sıfırlama adımları", 0.8731, 1),
                        new SearchHitResponse(7L, 302L, "Fatura SSS", "Online işlemlere giriş", 0.8120, 2))));

        // Act
        MvcTestResult result = mockMvcTester.post().uri("/api/workspaces/{workspaceId}/search", 42)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"query":"Şifremi unuttum","mode":"VECTOR","limit":10}
                        """)
                .exchange();

        // Assert
        assertThat(result).hasStatusOk()
                .bodyJson().extractingPath("$.hits[*].chunkId").asArray().containsExactly(12, 7);
    }
}
```

**`@WebMvcTest`'i test edilen controller sınıfıyla sınırla.** Sınırlamazsan tüm controller'lar yüklenir ve hepsinin bağımlılıklarını mock'laman gerekir.

---

## GET / POST / PUT / DELETE

```java
// GET: path variable ve query param
MvcTestResult result = mockMvcTester.get().uri("/api/workspaces/{workspaceId}/sources", 42)
        .param("status", "READY")
        .param("page", "0")
        .exchange();

// POST: JSON body (text block — DTO'yu serileştirmekten daha okunur ve sözleşmeyi birebir gösterir)
MvcTestResult result = mockMvcTester.post().uri("/api/workspaces/{workspaceId}/sources", 42)
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
                {"name":"Fatura SSS","kind":"MARKDOWN"}
                """)
        .exchange();

// PUT
MvcTestResult result = mockMvcTester.put().uri("/api/workspaces/{workspaceId}/sources/{sourceId}", 42, 301)
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
                {"name":"Fatura ve ödeme SSS"}
                """)
        .exchange();

// DELETE
MvcTestResult result = mockMvcTester.delete().uri("/api/workspaces/{workspaceId}/sources/{sourceId}", 42, 301).exchange();
assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
```

Body'yi bir nesneden üretmek gerekiyorsa Jackson 3 `JsonMapper` inject edilir (`@Autowired private JsonMapper jsonMapper;` → `jsonMapper.writeValueAsString(request)`); Jackson 2 `ObjectMapper` kullanılmaz.

---

## JSON Assertion'ları

```java
assertThat(result).bodyJson().extractingPath("$.mode").isEqualTo("VECTOR");
assertThat(result).bodyJson().extractingPath("$.hits").asArray().hasSize(2);
assertThat(result).bodyJson().extractingPath("$.hits[0].sourceName").isEqualTo("Yardım merkezi");
assertThat(result).bodyJson().doesNotHavePath("$.hits[0].embedding");

// Tüm body'yi tipli nesneye çevir
assertThat(result).bodyJson().convertTo(SearchResultResponse.class)
        .satisfies(response -> assertThat(response.hits()).extracting(SearchHitResponse::rank).containsExactly(1, 2));

// Sözleşme: JSON karşılaştırma (alan sırasından bağımsız, LENIENT)
assertThat(result).bodyJson().isLenientlyEqualTo("""
        {"query":"Şifremi unuttum","mode":"VECTOR","hits":[{"chunkId":12,"rank":1},{"chunkId":7,"rank":2}]}
        """);
```

Tam string karşılaştırması (`hasBodyTextEqualTo`) JSON için kullanılmaz — alan sırası ve boşluklarda kırılır. Skor gibi kayan noktalı alanlar JSON karşılaştırmasına konmaz; `convertTo(...)` ile alınıp `isCloseTo` ile doğrulanır.

---

## Request Validation (400 Bad Request)

```java
@Test
void givenOneCharacterQuery_whenSearchChunks_thenReturns400WithViolation() {
    // Act
    MvcTestResult result = mockMvcTester.post().uri("/api/workspaces/{workspaceId}/search", 42)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                    {"query":"a","mode":"VECTOR","limit":10}
                    """)
            .exchange();

    // Assert
    assertThat(result).hasStatus(HttpStatus.BAD_REQUEST)
            .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
    assertThat(result).bodyJson().extractingPath("$.errorCode").isEqualTo("VALIDATION_FAILED");
    assertThat(result).bodyJson().extractingPath("$.violations[*].field").asArray().containsExactly("query");
}
```

Validation hata formatı (`errorCode`, `violations`) `problem-details-rfc9457`'de tanımlıdır.

---

## Domain Hatalarını Test Etme (ProblemDetail)

`@WebMvcTest`, `@RestControllerAdvice` bean'lerini de yükler; `GlobalExceptionHandler` testte gerçek haliyle çalışır. Service mock'u domain exception'ı fırlatır, test HTTP sözleşmesini doğrular:

```java
@Test
void givenWorkspaceAtSourceLimit_whenCreateSource_thenReturns409SourceLimitReached() {
    // Arrange
    given(ingestionService.createSource(eq(42L), eq("Fatura SSS"), any(MultipartFile.class)))
            .willThrow(new ConflictException("SOURCE_LIMIT_REACHED", "Workspace source limit reached: 42"));

    // Act
    MvcTestResult result = mockMvcTester.post().uri("/api/workspaces/{workspaceId}/sources", 42).multipart()
            .part(new MockPart("name", "Fatura SSS".getBytes(StandardCharsets.UTF_8)))
            .file(new MockMultipartFile("file", "fatura-sss.md", "text/markdown", "# Fatura".getBytes(StandardCharsets.UTF_8)))
            .exchange();

    // Assert
    assertThat(result).hasStatus(HttpStatus.CONFLICT)
            .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
    assertThat(result).bodyJson().extractingPath("$.errorCode").isEqualTo("SOURCE_LIMIT_REACHED");
    assertThat(result).bodyJson().doesNotHavePath("$.trace");
}
```

`spring.mvc.problemdetails.enabled` Boot 4.1'de varsayılan olarak **false**'tur; projede `true` yapılır ve kendi `GlobalExceptionHandler`'ımız tüm eşlemeyi üstlenir. Testler `application/problem+json` ve sabit `errorCode` bekler.

---

## Response Header'ları

```java
assertThat(result).hasStatus(HttpStatus.CREATED)
        .headers().hasHeaderSatisfying(HttpHeaders.LOCATION,
                values -> assertThat(values).singleElement().asString().matches(".*/api/workspaces/42/sources/\\d+"));
```

---

## Tam Context Integration Testi (@SpringBootTest + @AutoConfigureMockMvc)

Tam application context gerektiğinde (gerçek service + veritabanı):

```java
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SourceFlowIntegrationTest {

    @Autowired
    private MockMvcTester mockMvcTester;

    @Test
    void givenCreatedSource_whenSameNameWithDifferentCaseCreated_thenReturns409SourceNameTaken() {
        ...
    }
}
```

`@SpringBootTest + @AutoConfigureMockMvc`'yi az kullan — tam context açar, `@WebMvcTest`'ten belirgin şekilde yavaştır.

---

## Debug

```java
MvcTestResult result = mockMvcTester.get().uri("/api/workspaces/{workspaceId}/sources/{sourceId}", 42, 301).exchange();
assertThat(result).debug().hasStatusOk();   // request/response'u stdout'a yazar — commit'ten önce kaldır
```

---

## Alternatif: RestTestClient (Spring Framework 7)

`RestTestClient` (`org.springframework.test.web.servlet.client.RestTestClient`), `WebTestClient` tarzı fluent bir API'dir; hem `MockMvc` üzerinde hem de gerçek bir server'a (RANDOM_PORT) karşı çalışabilir. Projede gerçek HTTP gerektiren `RANDOM_PORT` integration testlerinde kullanılabilir:

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class SearchHttpIntegrationTest {

    @Autowired
    private RestTestClient restTestClient;
}
```

`@AutoConfigureRestTestClient` paketi `org.springframework.boot.resttestclient.autoconfigure`'dır (`spring-boot-resttestclient`). Slice controller testleri için standart `MockMvcTester`'dır.

---

## MockMvc → MockMvcTester Karşılıkları

| Klasik `MockMvc` | `MockMvcTester` |
|---|---|
| `mockMvc.perform(get("/x"))` | `mockMvcTester.get().uri("/x").exchange()` |
| `.andExpect(status().isOk())` | `assertThat(result).hasStatusOk()` |
| `.andExpect(status().isCreated())` | `assertThat(result).hasStatus(HttpStatus.CREATED)` |
| `.andExpect(jsonPath("$.name").value("x"))` | `assertThat(result).bodyJson().extractingPath("$.name").isEqualTo("x")` |
| `.andExpect(jsonPath("$.items", hasSize(3)))` | `...extractingPath("$.items").asArray().hasSize(3)` |
| `.andExpect(header().exists("Location"))` | `assertThat(result).headers().containsHeader("Location")` |
| `.andDo(print())` | `assertThat(result).debug()` |
| `throws Exception` | gerekmez |

---

## Boot 3.x → 4.x Migration Tablosu

| Alan | Boot 3.x | Boot 4.x |
|---|---|---|
| `@WebMvcTest` / `@AutoConfigureMockMvc` import'u | `org.springframework.boot.test.autoconfigure.web.servlet` | `org.springframework.boot.webmvc.test.autoconfigure` |
| Test dependency'si | `spring-boot-starter-test` | `spring-boot-starter-webmvc-test` |
| `@MockBean` | `org.springframework.boot.test.mock.mockito.MockBean` | `org.springframework.test.context.bean.override.mockito.MockitoBean` |
| Test client | `MockMvc` (3.4+ `MockMvcTester`) | `MockMvcTester` (projede tercih), `MockMvc`, `RestTestClient` |
| `ProblemDetail` | Opt-in (`spring.mvc.problemdetails.enabled=true`) | Hâlâ opt-in (varsayılan `false`) |
| JSON | Jackson 2 `ObjectMapper` | Jackson 3 `JsonMapper` (`tools.jackson.databind.json`) |

---

## Anti-Pattern'ler

| Anti-Pattern | Çözüm |
|---|---|
| Her controller testi için `@SpringBootTest` | `@WebMvcTest(XController.class)` kullan |
| `@WebMvcTest`'i controller ile sınırlamamak | Controller sınıfını her zaman belirt |
| Tam JSON'u string olarak assert etmek | `extractingPath` / `isLenientlyEqualTo` kullan |
| POST/PUT'ta `contentType(APPLICATION_JSON)`'u unutmak | Olmadan 415 Unsupported Media Type döner |
| Body'yi kontrol etmeden sadece status beklemek | Hem status'u hem önemli field'ları (hatalarda `errorCode`) kontrol et |
| HTTP davranışı yerine mock etkileşimlerini doğrulamak | Controller testi service iç yapısını değil, HTTP sözleşmesini doğrular |
| Path/response şeklini `docs/api/openapi.yaml`'dan farklı yazmak | Test sözleşmedeki path ve alan adlarını kullanır |
| Boot 4 kodunda `@MockBean`, Jackson 2 `ObjectMapper` | `@MockitoBean`, `JsonMapper` |
