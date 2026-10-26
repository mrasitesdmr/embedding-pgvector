# Tüm Test Türlerini Kesen (Cross-Cutting) Test Pattern'leri

Tüm Spring test türlerinde geçerli stratejik rehber: testing pyramid, context caching, implementation yerine davranışı test etme, Boot 4'e hazırlık ve evrensel anti-pattern'ler.

Kaynak: Andy Wilkinson, "Testing Spring Boot Applications" (SpringOne 2019) ve Spring Boot ekibinin önerileri.

---

## Testing Pyramid — Slice Seçimi

Her zaman test amacını karşılayan en dar slice'ı kullan. Sadece katmanlar arası davranışı (security + service + persistence birlikte) test ederken genişlet.

| Öncelik | Annotation | Kapsam | Tipik Açılış Süresi |
|---|---|---|---|
| 1 | Unit test (Spring yok) | Tek sınıf, context yok | < 100ms |
| 2 | `@WebMvcTest` | Sadece MVC controller'lar | 1–3s |
| 3 | `@DataJpaTest` | Sadece JPA entity'leri + repository'ler | 2–5s |
| 4 | `@WebFluxTest` | Sadece reactive controller'lar | 1–3s |
| 5 | `@SpringBootTest` | Tam context | 5–30s |
| 6 | `@SpringBootTest(RANDOM_PORT)` | Tam context + gerçek server | 10–30s+ |

**Karar kuralları**:
- `@WebMvcTest` → her REST controller testi (Spring Security classpath'te varsa filter chain de auto-configure edilir; bu projede MVP'de yok)
- `@DataJpaTest` → özel JPQL/SQL sorguları, projection'lar, auditing, constraint'ler
- `@WebFluxTest` → reactive controller testleri
- `@SpringBootTest` → katmanlar arası integration: service + security + persistence
- `@SpringBootTest(RANDOM_PORT)` → WebSocket (gerçek TCP gerekir) veya HTTP client integration testleri

---

## Context Caching — 1 Numaralı Performans Tuzağı

Spring Test, `ApplicationContext` instance'larını test sınıfları arasında cache'ler. Cache key, yapılandırmanın birebir kombinasyonudur. Herhangi bir fark yeni bir context oluşturur.

**Context cache'ini öldürenler**:

```java
// Her farklı @MockitoBean kombinasyonu yeni bir context cache girdisi oluşturur

// Context A (sınıf 1):
@WebMvcTest(SearchController.class)
class SearchControllerTest {
    @MockitoBean SearchService searchService;
}

// Context B (sınıf 2 — farklı mock seti):
@WebMvcTest(SourceController.class)
class SourceControllerTest {
    @MockitoBean SourceService sourceService;
    @MockitoBean IngestionService ingestionService;   // farklı kombinasyon → yeni context
}
```

**Cache'i geçersiz kılan diğer şeyler**:
- `@DirtiesContext` — test sınıfından sonra context'i cache'ten atar (sadece test gerçekten state'i bozuyorsa kullan)
- `@ActiveProfiles` — farklı profile'lar = farklı context'ler
- Farklı `@TestConfiguration` sınıflarıyla `@Import` = farklı context'ler
- Farklı property'lerle `@TestPropertySource` = farklı context'ler

**Best practice**: Mock setlerini standartlaştır. Projede `@SpringBootTest` integration testleri ortak taban sınıftan (`FakeEmbeddingIntegrationTest`: aynı Testcontainers PostgreSQL + aynı sahte `EmbeddingClient`) türediği için tek context'i paylaşır; yeni integration testi bu tabanı kullanmalı, kendi `@MockitoBean`/`@TestPropertySource` kombinasyonunu açmamalıdır.

Varsayılan cache limiti: 32 context. Bunun üstünde LRU eviction + yeniden oluşturma büyük test suite'lerinde ciddi yavaşlamaya yol açar.

---

## @DirtiesContext — Az Kullan

`org.springframework.test.annotation.DirtiesContext` (`@DirtiesContext`), işaretlenen test sınıfı veya metodundan sonra application context'i cache'ten atar. Sonraki testler için context'in tamamen yeniden kurulmasını zorlar.

Ne zaman kullanılır:
- Test, `@Configuration` bean'lerini veya paylaşılan static state'i değiştiriyorsa
- Test, context'i kirleten background thread'ler başlatıyorsa

Ne zaman KULLANILMAZ:
- Test fail oluyor ve temiz context istiyorsun — bunun yerine testi düzelt
- Rutin temizlik — her testten **önce** `@BeforeEach` ile temizle; `@SpringBootTest` testine `@Transactional` eklenmez (istisna: `@DataJpaTest`'in kendi rollback'i, bkz. `backend-conventions` bölüm 5)

---

## Davranış > Implementation

Her alanda **gözlemlenebilir sonuçları** assert et, implementation detaylarını değil.

| Alan | Bunu Assert Et (Davranış) | Bunu Değil (Implementation) |
|---|---|---|
| MVC | HTTP status code + response body field'ları | `verify(service).findById(1L)` |
| JPA | flush+clear sonrası yeniden okunan entity state'i | flush öncesi bellekteki entity state'i |
| Security | Yetkisiz isteklerde 401/403 status code'ları | İç filter chain çağrıları |
| WebFlux | StepVerifier sinyalleri + yayınlanan değerler | `.block()` sonucu |
| WebSocket | `BlockingQueue`'ya düşen mesaj | İç broker routing'i |
| Service | Dönüş değeri, exception tipi, gözlemlenebilir yan etki (kayıt yapıldı/yapılmadı, event yayınlandı) | Sorgu metotlarının kaç kez çağrıldığı, mapper gibi iç yardımcıların çağrılması |

---

## Evrensel Anti-Pattern'ler

| Anti-Pattern | Neden Kötü | Çözüm |
|---|---|---|
| Her test için `@SpringBootTest` | Yavaş açılış, neyi test ettiğini gizler | En dar slice'ı kullan |
| Davranış yerine mock çağrı sayısını doğrulamak | Kırılgan — implementation'a bağlanır, refactor'da kırılır | Gözlemlenebilir sonuçları assert et |
| JSON response'u string olarak karşılaştırmak (`content().string(...)`) | Field sırası ve boşluklarda kırılır | `MockMvcTester` ile `bodyJson().extractingPath(...)` veya `isLenientlyEqualTo(...)` kullan |
| Testlerde security'yi kapatmak (`addFilters = false`) | Sahte güven — production'da çalışanı test etmiyorsun | Security'yi açıkça test et |
| Dialect'e duyarlı sorgular için H2 kullanmak | H2 ≠ PostgreSQL — lokalde geçer, production'da patlar | Production DB ile Testcontainers kullan |
| Reactive testlerde `.block()` | Reactive hataları gizler, scheduler varsayımlarını bozar | `StepVerifier` veya `WebTestClient` kullan |
| Async testlerde `Thread.sleep()` | Flaky, deterministik değil, yavaş | `Awaitility`, `BlockingQueue.poll(timeout)` veya `CountDownLatch` kullan |
| Value object ve DTO'ları gereksiz mock'lamak | Bakım yükü, değer katmaz | Gerçek instance kullan |
| Test hatalarını örtmek için `@DirtiesContext` | Asıl sorunu gizler | Test izolasyon sorununu düzelt |
| `@BeforeEach` içinde kullanılmayan stub'lar | `UnnecessaryStubbingException`, sinyal/gürültü oranı | Stub'ları onları kullanan testlerin içine taşı |

---

## Async Assertion'lar için Awaitility

`BlockingQueue` veya `StepVerifier`'a uymayan asenkron işlemler için:

```java
import org.awaitility.Awaitility;
import static org.awaitility.Awaitility.*;

// Koşul sağlanana kadar timeout ile yokla
await()
    .atMost(Duration.ofSeconds(10))
    .pollInterval(Duration.ofMillis(100))
    .untilAsserted(() -> {
        assertThat(emailSentCount.get()).isEqualTo(1);
    });

// Basit predicate ile yoklama
await()
    .atMost(Duration.ofSeconds(5))
    .until(() -> auditLog.contains("ORDER_CREATED"));
```

Awaitility, `Thread.sleep()`'ten çok daha güvenilirdir ve koşul sağlanmadığında net timeout mesajları verir.

---

## Boot 4 Hazırlık Checklist'i

### Stack Gereksinimleri
- Java 17+ (Boot 4 minimumu; bu proje Java 26)
- Sadece Jakarta namespace'leri (`javax.*` yok)
- Deprecated Boot 2/3 API'si yok

### Annotation Migration'ı

| Boot 3.x | Boot 4.x | Yeni Paket |
|---|---|---|
| `@MockBean` | `@MockitoBean` | `org.springframework.test.context.bean.override.mockito` |
| `@SpyBean` | `@MockitoSpyBean` | `org.springframework.test.context.bean.override.mockito` |
| `@WebMvcTest` import'u | Aynı isim, yeni paket | `org.springframework.boot.webmvc.test.autoconfigure` |
| `@DataJpaTest` import'u | Aynı isim, yeni paket | `org.springframework.boot.data.jpa.test.autoconfigure` |
| `TestEntityManager` import'u | Aynı isim, yeni paket | `org.springframework.boot.jpa.test.autoconfigure` |
| `@WebFluxTest` import'u | Aynı isim, yeni paket | `org.springframework.boot.webflux.test.autoconfigure` |

### Hızlı Migration Script'i

```bash
# Güncellenmesi gereken tüm import'ları bul
grep -r "org.springframework.boot.test.autoconfigure.orm.jpa" src/test/
grep -r "org.springframework.boot.test.autoconfigure.web.servlet" src/test/
grep -r "org.springframework.boot.test.autoconfigure.web.reactive" src/test/
grep -r "org.springframework.boot.test.mock.mockito.MockBean" src/test/
grep -r "org.springframework.boot.test.mock.mockito.SpyBean" src/test/
```

### AOT Güvenlik Kuralları

Spring Boot 4, AOT derlemeyi (GraalVM native image) destekler. Reflection hack'leri veya classloader manipülasyonu kullanan test kodu AOT ile çalışmayabilir.

- Production kodunda constructor injection (test sınıflarında `@Autowired` field kabul edilir; bkz. `backend-conventions` bölüm 4)
- `@TestConfiguration` sınıflarında bağımlılıkları `@Bean` metot parametresi olarak al
- Özel classloader manipülasyonu yerine framework'ün sağladığı test annotation'larını tercih et
- Production kodundaki field'lar için `ReflectionTestUtils` kullanma — bunun yerine görünürlüğü düzelt

---

## Context Caching Optimizasyon Pattern'leri

### Pattern 1: Paylaşılan Base Test Sınıfı

```java
// Tüm controller'ları yükleyen ortak slice: aynı annotation + aynı mock seti = tek context
@WebMvcTest
abstract class AbstractWebMvcTest {

    @Autowired
    protected MockMvcTester mockMvcTester;

    @MockitoBean
    protected SourceService sourceService;

    @MockitoBean
    protected SearchService searchService;
}

// Alt sınıflar @WebMvcTest'i YENİDEN tanımlamaz ve yeni mock eklemez → context paylaşılır
class SourceControllerTest extends AbstractWebMvcTest { ... }

class SearchControllerTest extends AbstractWebMvcTest { ... }
```

Dikkat: alt sınıf `@WebMvcTest(SourceController.class)` yazarsa `controllers` attribute'u farklılaştığı için cache key de farklılaşır ve context paylaşılmaz. Trade-off: base sınıf tüm controller'ları ve bazı testlerin ihtiyaç duymadığı mock'ları yükler. Bu projede varsayılan `@WebMvcTest(XController.class)` ile tek controller'dır (`spring-mvc-testing`); controller testi sayısı arttığında ve context açılışı belirgin şekilde yavaşladığında bu pattern'e geçilir.

### Pattern 2: Paylaşılan @TestConfiguration

```java
// Ortak test altyapısı tek yerde
@TestConfiguration(proxyBeanMethods = false)
public class FixedClockTestConfiguration {

    @Bean
    @Primary
    Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    }
}

// Her yerde import et — aynı config sınıfı = aynı cache key katkısı
@WebMvcTest(UserController.class)
@Import(FixedClockTestConfiguration.class)
class UserControllerTest { ... }
```

---

## Test İsimlendirme Kuralları

Tutarlı isimlendirme, hataları okunur kılar. **Bu projede geçerli format `backend-conventions`'daki `given<Precondition>_when<Action>_then<ExpectedResult>` formatıdır**; aşağıdaki örnek kaynak repodaki genel yaklaşımı gösterir ve bu projede kullanılmaz:

```
methodUnderTest_scenarioDescription_expectedBehavior

findById_withValidId_returnsUser()
findById_withUnknownId_throwsNotFoundException()
createOrder_whenStockInsufficient_throwsOutOfStockException()
getOrder_withoutAuth_returns401()
getOrder_asUser_withDifferentOwner_returns403()
```

Kaynaktaki pattern: test edilen şey (`findById`), koşul (`withValidId`), beklenti (`returnsUser`). Proje karşılığı: `givenExistingUser_whenFindById_thenReturnsUser`.
