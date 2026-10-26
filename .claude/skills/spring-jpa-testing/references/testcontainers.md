# JPA Testing: Testcontainers ve @DataJpaTest vs @SpringBootTest

Testcontainers 2.x için `@ServiceConnection` pattern'ini, production'a yakın testlerde H2'nin neden yetersiz olduğunu ve `@DataJpaTest` (`org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest`) ile `@SpringBootTest` arasında ne zaman hangisinin seçileceğini kapsar.

Proje sürümleri (Boot 4.1.1 yönetir): Testcontainers 2.0.x, PostgreSQL image `postgres:18-alpine`.

---

## Neden H2 Değil

> "Kullanıcıların yaşayacağı davranışın aynısını doğrulamak için production'da çalıştırdığın database engine'ini kullanmalısın."
> — Vlad Mihalcea

Test veritabanı olarak H2'nin sorunları:

- PostgreSQL'e özel özellikleri taklit edemez: window function'lar, `jsonb`, array tipleri, ifade index'leri (`lower(name)`), `vector` tipi ve pgvector operatörleri (`<=>`), HNSW/IVFFlat index'leri, advisory lock'lar
- SQL syntax farkları — native query'lerle testler H2'de geçer, PostgreSQL'de fail olur
- Flyway migration'ları PostgreSQL'e özel DDL kullanır (`GENERATED ALWAYS AS IDENTITY`, `timestamptz`, regex `CHECK`) ve H2'de bozulur
- Sequence ve identity davranışı engine'ler arasında farklıdır

**Kural**: Production ile aynı database image'ı ile Testcontainers kullan. Projede H2 dependency'si yoktur.

**Kaynaklar**:
- [Vlad Mihalcea — Testcontainers Database Integration Testing](https://vladmihalcea.com/testcontainers-database-integration-testing/)
- [Testcontainers — Replace H2 with a Real Database](https://testcontainers.com/guides/replace-h2-with-real-database-for-testing/)

---

## Dependency'ler

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa-test</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-testcontainers</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers-postgresql</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers-junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
```

Testcontainers 2'de modül artifact'ları `testcontainers-` önekiyle yeniden adlandırıldı (1.x'teki `org.testcontainers:postgresql` değil) ve PostgreSQL container'ı `org.testcontainers.postgresql.PostgreSQLContainer` paketine taşındı; sınıf artık generic değildir (`PostgreSQLContainer<?>` yazılmaz). Eski `org.testcontainers.containers.PostgreSQLContainer` deprecated'dır.

---

## @DataJpaTest vs @SpringBootTest: Hangisi Ne Zaman

### @DataJpaTest Ne Yapar

- Repository'ler, `EntityManager`, `DataSource`, `JdbcTemplate`, Flyway migration'ları
- `TestEntityManager`'ı (`org.springframework.boot.jpa.test.autoconfigure.TestEntityManager`) auto-configure eder
- `@Service`, `@Controller` veya JPA dışı bean'leri YÜKLEMEZ
- Her test metodunu otomatik rollback'li `@Transactional` ile sarar
- Açılışta entity mapping'lerini (`ddl-auto=validate`) ve JPQL query syntax'ını doğrular

**Datasource değişimi**: `@AutoConfigureTestDatabase` (`org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase`) Boot 3.4'ten beri varsayılan olarak `Replace.NON_TEST` kullanır: sadece test için tanımlanmamış (ör. `application.properties`'teki) datasource'u embedded DB ile değiştirir; `@ServiceConnection` veya `@DynamicPropertySource` ile gelen Testcontainers datasource'una dokunmaz. Bu yüzden `@AutoConfigureTestDatabase(replace = NONE)` yazmak **gerekmez**. Eski (Boot 3.3 ve öncesi) örneklerde görürsen kaldırabilirsin.

### @SpringBootTest Ne Yapar

Tam application context'i yükler. Varsayılan olarak `@Transactional` EKLEMEZ — transaction'lar production'daki gibi service sınırlarında commit olur.

### Karar Tablosu

| Senaryo | Öneri |
|----------|---------------|
| Özel JPQL/native query'leri izole test etmek | Testcontainers ile `@DataJpaTest` |
| Flyway migration'ları + entity mapping'lerini doğrulamak | Testcontainers ile `@DataJpaTest` (açılış başarılıysa migration + mapping uyumlu) |
| Repository + service transaction sınırlarını test etmek | Testcontainers ile `@SpringBootTest` |
| Eşzamanlı kaynak ekleme (çalışma alanı başına 10 kaynak) gibi concurrency kuralları | `@SpringBootTest` (test seviyesinde transaction yok) |
| `AFTER_COMMIT` listener'ları (WebSocket yayını) | `@SpringBootTest` |
| Lazy loading'in transaction dışında fail olduğunu doğrulamak | `@SpringBootTest` |

### Vlad Mihalcea'nın @DataJpaTest'e Karşı Uyarısı

1. **Otomatik `@Transactional` gerçekçi olmayan koşullar yaratır**: Production'da transaction sınırlarını service katmanı yönetir; test transaction'ı flush zamanlamasını ve dirty-checking davranışını değiştirir.
2. **Flush davranışı uyuşmazlığı**: Rollback edilen bir test transaction'ında SQL statement'ları veritabanına hiç gönderilmeyebilir — constraint violation'lar fark edilmez (açık `flush()` ile çözülür).
3. **`LazyInitializationException`'ı maskeler**: Test transaction'ı `EntityManager`'ı test boyunca açık tutar.

`@DataJpaTest`'i tasarlandığı amaç için kullan: izole query doğruluğu. Production transaction davranışını doğrularken `@SpringBootTest` kullan.

**Kaynaklar**:
- [Vlad Mihalcea — The best way to clean up test data](https://vladmihalcea.com/clean-up-test-data-spring/)
- [Arho Huttunen — Testing the Persistence Layer with @DataJpaTest](https://www.arhohuttunen.com/spring-boot-datajpatest/)

---

## Proje Pattern'i: Paylaşılan @TestConfiguration + @ServiceConnection

```java
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer("postgres:18-alpine");
    }
}
```

`@ServiceConnection`, `spring.datasource.url/username/password` değerlerini çalışan container'dan auto-configure eder — elle property bağlamaya gerek yok. Sınıf `src/test/java/com/embedding/pgvector/TestcontainersConfiguration.java` konumundadır ve tüm veritabanı testleri onu import eder.

### @DataJpaTest ile Kullanım

```java
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
class SourceRepositoryTest {
}
```

### @SpringBootTest ile Kullanım

```java
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SourceServiceIntegrationTest {
}
```

### Container Yaşam Döngüsü

- Container bean olarak tanımlandığında yaşam döngüsünü Spring yönetir: context açılırken başlar, context cache'ten atılınca ya da JVM kapanırken durur. Aynı `@Import` + aynı test yapılandırması kullanan sınıflar context'i (ve container'ı) paylaşır.
- `@Testcontainers` + `@Container` annotation'larına bean pattern'inde gerek yoktur.
- `withReuse(true)` sadece lokal geliştirmede container'ı test koşuları arasında ayakta tutar (`~/.testcontainers.properties` içinde `testcontainers.reuse.enable=true` gerekir); CI'da etkisizdir. Paylaşılan config'e koyma; reuse edilen container'da önceki koşunun verisi kalır, bu yüzden temizlik her testten önce yapılır.
- Docker çalışmıyorsa testler açılışta fail olur; bu bir test hatası olarak raporlanır, test atlanmaz.

### Alternatif: @DynamicPropertySource (sadece service connection desteği olmayanlar için)

```java
@SpringBootTest
@Testcontainers
class ExternalServiceIntegrationTest {

    @Container
    static GenericContainer<?> mockServer = new GenericContainer<>("mockserver/mockserver:5.15.0")
            .withExposedPorts(1080);

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("app.dictionary.base-url",
                () -> "http://" + mockServer.getHost() + ":" + mockServer.getMappedPort(1080));
    }
}
```

PostgreSQL için `@ServiceConnection` kullanılır; `@DynamicPropertySource` sadece Boot'un service connection desteği olmayan servisler içindir.

---

## Slice ve Tam Testler için Ayrı @TestConfiguration

Projeye ileride Redis gibi ek altyapı eklenirse, `@DataJpaTest`'in gereksiz container başlatmaması için yapılandırmayı ayır:

- `TestcontainersConfiguration` — sadece PostgreSQL (slice ve tam testler)
- `FullInfrastructureTestConfiguration` — PostgreSQL'i `@Import` eder + diğer container'lar (sadece `@SpringBootTest`)

**Kaynak**: [Wim Deblauwe — How I Test Production-Ready Spring Boot Applications](https://www.wimdeblauwe.com/blog/2025/07/30/how-i-test-production-ready-spring-boot-applications/)

---

## Testcontainers ile Context Caching

Spring, application context'leri testler arasında cache'ler. Aynı `@TestConfiguration`'ı import eden testler aynı context'i ve container'ı yeniden kullanır. `@ActiveProfiles`, farklı `@MockitoBean` seti veya farklı `@Import` yeni bir context (ve yeni bir container) demektir.

Kesinlikle gerekmedikçe `@DirtiesContext` kullanma — context'i atar ve container'ı yeniden başlatmaya zorlar.

**Kaynaklar**:
- [JetBrains Blog — Testing Spring Boot Applications Using Testcontainers](https://blog.jetbrains.com/idea/2024/12/testing-spring-boot-applications-using-testcontainers/)
- [Testcontainers for Java — 2.0 migration](https://java.testcontainers.org/)
