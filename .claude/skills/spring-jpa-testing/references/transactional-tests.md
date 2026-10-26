# JPA Testing: @DataJpaTest Pattern'leri, @Transactional Tuzakları ve Test Verisi

`org.springframework.boot.jpa.test.autoconfigure.TestEntityManager` (`TestEntityManager`) kullanarak `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest` (`@DataJpaTest`) testleri yazmak için temel pattern'ler. Flush/clear döngülerini, `@Transactional` tuzağını, test verisi kurulumunu, `@Modifying` bulk update'leri ve neyin test edilmeyeceğini kapsar.


> Örneklerdeki `Order`/`Customer`/`Product`/`User` genel illüstrasyondur. Kısa parçalarda `// Arrange` / `// Act` / `// Assert` yorumları yer kazanmak için atlanmış olabilir; projede yazılan her testte zorunludur, exception beklentisi `catchThrowable` ile yazılır (`backend-conventions`).

> **Projedeki karşılıklar:** Gerçek repository testleri `SourceRepositoryTest` (çalışma alanı sınırı, `uk_sources_workspace_id_name` ihlali), `ChunkRepositoryTest` (vektör ve FTS sorguları, toplu silme) ve `IngestionJobRepositoryTest` (son iş sorgusu, aktif iş kısmi unique index'i) dosyalarındadır (`backend/src/test/java/.../repository/`). Projede cascade, `orphanRemoval`, `@Version` ve inheritance yoktur; ilişkiler `LAZY`, `open-in-view=false`. Fixture'lar `support/` paketindedir (`WorkspaceFixtures`, `SourceFixtures`, `ChunkFixtures`, `IngestionJobFixtures`, `VectorFixtures`).

---

## Flush+Clear Pattern'i

`@DataJpaTest` testlerindeki en yaygın hata: veriyi kaydedip flush ve clear yapmadan geri okumak.

```java
import jakarta.persistence.EntityManager;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.beans.factory.annotation.Autowired;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
class OrderRepositoryTest {

    @Autowired TestEntityManager entityManager;
    @Autowired OrderRepository orderRepository;

    @Test
    void givenCustomerWithTwoOrders_whenFindByCustomerId_thenReturnsBothOrders() {
        Customer customer = entityManager.persist(new Customer("alice@example.com"));
        entityManager.persist(new Order(customer, OrderStatus.PENDING));
        entityManager.persist(new Order(customer, OrderStatus.SHIPPED));
        entityManager.flush();   // SQL'i veritabanına gönder
        entityManager.clear();   // L1 cache'i boşalt — repository okuması DB'ye gitmek zorunda kalır

        List<Order> orders = orderRepository.findByCustomerId(customer.getId());

        assertThat(orders).hasSize(2);
    }
}
```

**Neden önemli**: `flush()` olmadan SQL veritabanına hiç ulaşmayabilir. `clear()` olmadan `findByCustomerId` entity'yi first-level cache'ten döner ve veritabanına hiç gitmez — sorgun hiç çalıştırılmamış olur.

Sadece flush gerekiyorsa kısayol olarak `persistAndFlush()` kullan:

```java
User saved = entityManager.persistAndFlush(new User("alice@example.com"));
entityManager.clear(); // L1 cache'ten atmak için yine clear() gerekir
```

---

## Testlerde @Transactional Tuzağı

Birçok güvenilir kaynak, integration testlerini `@Transactional` ile sarmaya karşı uyarır:

### Neden Sorun Yaratır

1. **Lazy loading testte çalışır, production'da fail olur**: Test transaction'ı `EntityManager`'ı açık tutar, lazy collection'lar yüklenir — production kodunun karşılaşacağı `LazyInitializationException` maskelenir.

2. **Hibernate flush yapmayabilir**: Transaction rollback edildiği için `FlushModeType.AUTO` hiç tetiklenmeyebilir. Constraint violation'lar ve unique index ihlalleri fark edilmez.

3. **Otomatik dirty-checking yanlış pozitifleri**: `@Transactional` bir testte değiştirilen entity'ler, `save()` çağrılmadan flush anında otomatik persist edilir. Service katmanı açıkça save etmiyorsa bu production'da olmaz.

4. **Farklı transaction sınırları**: Production, service metot sınırlarında commit eder. Test seviyesindeki `@Transactional` her şeyi tek transaction'da birleştirir ve çok transaction'lı bug'ları gizler.

### Testlerde @Transactional NE ZAMAN Uygundur

- İzole query testi için `@DataJpaTest` (rollback kullanışlı, transaction'lar sınırlı)
- Açık `flush()` çağrısı içeren testler — SQL çalışmasını zorlar ve constraint violation'ları yakalarsın
- Production transaction davranışının önemli olmadığı derived query metotlarının hızlı doğrulaması

### Testlerde @Transactional'dan NE ZAMAN KAÇINMALI

- Service katmanı transaction sınırlarını doğrulayan tam integration testleri
- `@Modifying` bulk update sorgularının testleri
- Lazy loading davranışını doğrulayan testler (bkz. `references/lazy-loading-tests.md`)
- Optimistic locking (`@Version`) testleri
- `@TransactionalEventListener(phase = AFTER_COMMIT)` ile tetiklenen yan etkilerin testleri (bkz. `spring-transactions`)

**Kaynaklar**:
- [Nurkiewicz — Spring Pitfalls: Transactional Tests Considered Harmful](https://nurkiewicz.com/2011/11/spring-pitfalls-transactional-tests.html)
- [rieckpil — Spring Boot Testing Pitfall: Transaction Rollback](https://rieckpil.de/spring-boot-testing-pitfall-transaction-rollback-in-tests/)
- [Marco Behler — Should My Tests Be @Transactional?](https://www.marcobehler.com/2014/06/25/should-my-tests-be-transactional)

---

## Temel Test Pattern'leri

### Derived Query Metotlarını Test Etme

```java
@Test
void givenActiveAndInactiveUser_whenFindByEmailAndActiveTrue_thenReturnsOnlyActiveUser() {
    entityManager.persist(new User("bob@example.com", true));
    entityManager.persist(new User("bob@example.com", false));
    entityManager.flush();
    entityManager.clear();

    List<User> results = userRepository.findByEmailAndActiveTrue("bob@example.com");

    assertThat(results).hasSize(1);
    assertThat(results.get(0).isActive()).isTrue();
}

@Test
void givenFiveProducts_whenFindTop3ByOrderByCreatedAtDesc_thenReturnsThree() {
    for (int i = 0; i < 5; i++) {
        entityManager.persist(new Product("P" + i, BigDecimal.TEN));
    }
    entityManager.flush();
    entityManager.clear();

    List<Product> top3 = productRepository.findTop3ByOrderByCreatedAtDesc();
    assertThat(top3).hasSize(3);
}
```

### Özel @Query Metotlarını Test Etme

```java
// Repository metodu:
// @Query("SELECT o FROM Order o WHERE o.status = :status AND o.total >= :minTotal")
// List<Order> findByStatusAndMinTotal(@Param("status") OrderStatus status,
//                                     @Param("minTotal") BigDecimal minTotal);

@Test
void givenOrdersWithDifferentTotals_whenFindByStatusAndMinTotal_thenReturnsOnlyMatchingOrder() {
    Customer c = entityManager.persist(new Customer("alice@example.com"));
    entityManager.persist(orderWith(c, OrderStatus.PENDING, new BigDecimal("50.00")));
    entityManager.persist(orderWith(c, OrderStatus.PENDING, new BigDecimal("150.00")));
    entityManager.persist(orderWith(c, OrderStatus.SHIPPED, new BigDecimal("200.00")));
    entityManager.flush();
    entityManager.clear();

    List<Order> results = orderRepository
        .findByStatusAndMinTotal(OrderStatus.PENDING, new BigDecimal("100.00"));

    assertThat(results).hasSize(1);
    assertThat(results.get(0).getTotal()).isEqualByComparingTo("150.00");
}
```

### Pagination Testi

```java
@Test
void givenTenProducts_whenFindAllWithPageSizeThree_thenReturnsFirstPageOfFour() {
    for (int i = 0; i < 10; i++) {
        entityManager.persist(new Product("P" + i, BigDecimal.TEN));
    }
    entityManager.flush();
    entityManager.clear();

    Pageable pageable = PageRequest.of(0, 3, Sort.by("name").ascending());
    Page<Product> page = productRepository.findAll(pageable);

    assertThat(page.getContent()).hasSize(3);
    assertThat(page.getTotalElements()).isEqualTo(10);
    assertThat(page.getTotalPages()).isEqualTo(4);
    assertThat(page.getContent().get(0).getName()).isEqualTo("P0");
}
```

### Specification Testi

```java
@Test
void givenProductsInTwoCategories_whenFindAllByCategorySpecification_thenReturnsOnlyThatCategory() {
    entityManager.persist(new Product("Widget", "ELECTRONICS", BigDecimal.TEN));
    entityManager.persist(new Product("Screw", "HARDWARE", BigDecimal.ONE));
    entityManager.flush();
    entityManager.clear();

    List<Product> results = productRepository
        .findAll(ProductSpecs.hasCategory("ELECTRONICS"));

    assertThat(results).hasSize(1);
    assertThat(results.get(0).getName()).isEqualTo("Widget");
}
```

### Interface Projection Testi

```java
// interface UserSummary { String getEmail(); String getDisplayName(); }
// List<UserSummary> findByActiveTrue();

@Test
void givenActiveAndInactiveUser_whenFindByActiveTrue_thenReturnsProjectionOfActiveUser() {
    entityManager.persist(new User("alice@example.com", "Alice Smith", true));
    entityManager.persist(new User("bob@example.com", "Bob Jones", false));
    entityManager.flush();
    entityManager.clear();

    List<UserSummary> summaries = userRepository.findByActiveTrue();

    assertThat(summaries).hasSize(1);
    assertThat(summaries.get(0).getEmail()).isEqualTo("alice@example.com");
    assertThat(summaries.get(0).getDisplayName()).isEqualTo("Alice Smith");
}
```

### Entity Auditing Testi

```java
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class, FixedClockTestConfiguration.class})
class SourceAuditingTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired TestEntityManager entityManager;
    @Autowired SourceRepository sourceRepository;

    @Test
    void givenNewTerm_whenSave_thenAuditDatesComeFromClock() {
        // Act
        Source saved = sourceRepository.save(new Source(42L, "Yardım merkezi", SourceKind.WEB_CRAWL));
        entityManager.flush();
        entityManager.clear();

        // Assert
        Source loaded = entityManager.find(Source.class, saved.getId());
        assertThat(loaded.getCreatedAt()).isEqualTo(FIXED_NOW);
        assertThat(loaded.getUpdatedAt()).isEqualTo(FIXED_NOW);
    }
}
```

**Not**: `@EnableJpaAuditing`, `@DataJpaTest` tarafından varsayılan olarak yüklenmez. Projenin `JpaAuditingConfig`'i (auditing'i `Clock` bean'ine bağlayan `DateTimeProvider` ile, bkz. `jpa-patterns`) ve `FixedClockTestConfiguration` (`spring-boot-engineer/references/testing.md`) birlikte import edilir; böylece test, zamanın sistem saatinden değil `Clock`'tan geldiğini de doğrular. Çıplak `@EnableJpaAuditing` içeren ayrı bir test config'i açma: `DateTimeProvider` olmadan auditing sistem saatini kullanır ve production davranışından sapar. Bu test, testin konusu `save`'in kendisi olduğu için kurulumu repository ile yapar; okuma `TestEntityManager` ile yapılır.

### Constraint Violation Testi

```java
@Test
void givenExistingEmail_whenPersistDuplicateAndFlush_thenThrowsConstraintViolation() {
    // Arrange
    entityManager.persistAndFlush(new User("alice@example.com"));

    // Act
    Throwable thrown = catchThrowable(() -> {
        entityManager.persist(new User("alice@example.com"));
        entityManager.flush(); // constraint kontrolünü flush() tetikler
    });

    // Assert
    assertThat(thrown).isInstanceOf(jakarta.persistence.PersistenceException.class);
}
```

**Önemli**: İkinci `entityManager.flush()` zorunludur — olmadan insert veritabanına gitmeyebilir ve ihlali göremezsin. `TestEntityManager` Spring exception çevirisinden geçmediği için ham `PersistenceException` fırlar; aynı ihlali `repository.saveAndFlush(...)` ile tetiklersen `DataIntegrityViolationException` alırsın. Servisin ihlali `ConflictException("SOURCE_NAME_TAKEN", ...)`'a çevirdiğini doğrulayan test service seviyesinde (`@SpringBootTest`) yazılır.

### Transaction Rollback Kontrolü

```java
// @DataJpaTest her testi @Transactional ile sarar → her testten sonra rollback.
// Gerçek commit gerekiyorsa:

@Test
@Commit   // Transaction'ı commit eder — az kullan, veriyi kendin temizle
void givenNewOrder_whenSave_thenTransactionIsCommitted() { /* ... */ }

// Ya da saran transaction'ı tamamen kapat:
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OrderRepositoryCommitTest {
    // Test verisi @BeforeEach'te elle temizlenmeli
    @BeforeEach
    void cleanUp() {
        orderRepository.deleteAll();
    }
}
```

---

## Test Verisi Kurulumu ve Temizliği

### Vlad Mihalcea'nın Yaklaşımı: Her Testten Önce Truncate

> "Temizliği her testten **önce** yap, sonra değil. `@AfterEach` içindeki temizlik test hatalarında veya debug oturumlarında çalışmayabilir."

Hibernate 6.2'den beri `SchemaManager` API'sini kullan:

```java
import org.hibernate.engine.spi.SessionFactoryImplementor;

@Autowired EntityManagerFactory entityManagerFactory;

@BeforeEach
void cleanUp() {
    entityManagerFactory
        .unwrap(SessionFactoryImplementor.class)
        .getSchemaManager()
        .truncateMappedObjects();
}
```

Faydaları: JPA entity'lerine map'lenen tüm tabloları truncate eder, foreign key sırasına otomatik uyar, her veritabanıyla çalışır. Boot 4'teki Hibernate 7 / Jakarta Persistence 3.2 ile `unwrap`'sız standart karşılığı da kullanılabilir: `entityManagerFactory.getSchemaManager().truncate()` (`truncateMappedObjects()` bunun eş anlamlısıdır).

**Dikkat**: Flyway migration'ıyla yüklenen referans veri (ör. lab demosunun örnek çalışma alanı ve kaynakları) de entity'ye map'li bir tablodaysa silinir. Böyle tabloları truncate etmek yerine sadece testin yazdığı tabloları temizle (`JdbcClient` ile `TRUNCATE chunks, sources RESTART IDENTITY CASCADE`) ya da referans veriyi test başında yeniden yükle.

**Kaynak**: [Vlad Mihalcea — The best way to clean up test data](https://vladmihalcea.com/clean-up-test-data-spring/)

### Karmaşık Veri Kurulumu için @Sql

Karmaşık test verisi için SQL script'lerini yüklemek üzere `@Sql` kullan:

```java
import org.springframework.test.context.jdbc.Sql;

@Test
@Sql("/test-data/users.sql")
void givenSqlFixture_whenComplexQuery_thenReturnsExpectedRows() { ... }
```

Dikkat: Schema değiştiğinde SQL script'lerinin bakımı gerekir. Dayanıklılık için programatik kurulumu tercih et.

### Test Edilen Repository'yi Kurulum için KULLANMA

```java
// YANLIŞ: döngüsel mantık — repository bug'ı hem kurulumu hem assertion'ı maskeler
userRepository.save(new User("alice@example.com"));
List<User> users = userRepository.findByActiveTrue();

// DOĞRU: kurulum için TestEntityManager
entityManager.persistAndFlush(new User("alice@example.com", true));
entityManager.clear();
List<User> users = userRepository.findByActiveTrue();
```

Aynı repository'yi hem kurulum hem assertion için kullanmak, test edilen metottaki bug'ları maskeleyen döngüsel bir mantık yaratır.

**Kaynak**: [Arho Huttunen — Testing the Persistence Layer](https://www.arhohuttunen.com/spring-boot-datajpatest/)

---

## @Modifying Bulk Update Testleri

### Pattern

```java
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

@Modifying
@Transactional  // JpaRepository değil Repository extend ediliyorsa ZORUNLU
@Query("UPDATE User u SET u.status = :status WHERE u.id = :id")
int updateStatus(@Param("id") Long id, @Param("status") String status);
```

`JpaRepository` yerine `org.springframework.data.repository.Repository` extend edildiğinde `@Modifying` sorgular `@Transactional`'ı miras ALMAZ. Olmadan şunu alırsın:

```
InvalidDataAccessApiUsageException: Executing an update/delete query
```

### Bulk Update'leri Test Etme

```java
@Test
void givenActiveUser_whenUpdateStatus_thenStatusIsChangedInDatabase() {
    User user = entityManager.persistAndFlush(new User("Alice", "ACTIVE"));
    entityManager.clear();

    int updated = userRepository.updateStatus(user.getId(), "INACTIVE");

    assertThat(updated).isEqualTo(1);
    entityManager.clear(); // Bulk update sonrası bayat cache state'ini temizle
    User reloaded = entityManager.find(User.class, user.getId());
    assertThat(reloaded.getStatus()).isEqualTo("INACTIVE");
}
```

Adımlar:
1. Test verisini `TestEntityManager` ile ekle
2. Bulk update'i çalıştır
3. `entityManager.clear()` — bulk update'ler Hibernate dirty-checking'i atlar; L1 cache eski state'i tutar
4. Yeniden oku ve assert et

### Güvenlik için clearAutomatically

`@Modifying(clearAutomatically = true)`, bulk update'ten sonra persistence context'i otomatik temizler:

```java
@Modifying(clearAutomatically = true)
@Transactional
@Query("UPDATE StepExecution s SET s.itemCount = :count, s.version = s.version + 1 WHERE s.id = :id")
int updateItemCount(@Param("id") UUID id, @Param("count") long count);
```

### Bulk Update'lerde Version Artışını Test Etme

JPQL bulk update'ler Hibernate'in dirty-checking'ini ve optimistic lock version artışını atlar. Version field'ını sorguda elle yönetiyorsan bunu doğrula:

```java
@Test
void givenStepExecution_whenBulkUpdateItemCount_thenVersionIsIncremented() {
    StepExecution step = createAndSaveStep();
    int initialVersion = step.getVersion();

    repository.updateItemCount(step.getId(), 42);
    entityManager.clear();

    StepExecution reloaded = entityManager.find(StepExecution.class, step.getId());
    assertThat(reloaded.getVersion()).isEqualTo(initialVersion + 1);
    assertThat(reloaded.getItemCount()).isEqualTo(42);
}
```

**Kaynaklar**:
- [Thorben Janssen — Implementing Bulk Updates with Spring Data JPA](https://thorben-janssen.com/implementing-bulk-updates-with-spring-data-jpa/)
- [Baeldung — Spring Data JPA @Modifying Annotation](https://www.baeldung.com/spring-data-jpa-modifying-annotation)

---

## Neler Test EDİLMEZ

### Bunları Atla (Framework Doğrular)

| Ne | Neden |
|------|-----|
| Miras alınan CRUD: `save()`, `findById()`, `delete()` | Spring Data JPA ekibi tarafından test edilmiş |
| Derived query derlemesi: `findByName()`, `findByStatusAndType()` | Spring Data uygulama açılışında doğrular (ama iş kuralı taşıyan derived query — ör. `SourceRepository.existsByWorkspaceIdAndNameIgnoreCase` — davranışı test edilir) |
| Schema üretimi | Bunun yerine Flyway migration'ları kullan; migration çalışmasını açılış doğrulaması olarak test et |

### Bunları MUTLAKA Test Et

- **Özel `@Query` metotları** — hem JPQL hem native SQL; native query'ler özellikle kırılgandır
- **`@Modifying` sorgular** — dirty-checking'i atlar, doğrulanmalı
- **Projection interface'leri/DTO'lar** — field mapping hataları yaygındır
- **Veritabanı constraint'leri** — unique constraint'ler, foreign key'ler, check constraint'ler
- **Aggregate persistence gidiş-dönüşü** — save + clear + yeniden yükle + tüm field'ları doğrula
- **Optimistic locking** — `@Version` ile eşzamanlı değişiklik tespiti
- **Inheritance discriminator değerleri** — doğru alt tipin saklanması ve okunması

**Kaynaklar**:
- [Arho Huttunen — Testing the Persistence Layer](https://www.arhohuttunen.com/spring-boot-datajpatest/)
- [Reflectoring.io — Testing JPA Queries with @DataJpaTest](https://reflectoring.io/spring-boot-data-jpa-test/)

---

## Anti-Pattern Özeti

| Anti-Pattern | Çözüm |
|---|---|
| `flush()+clear()` olmadan `save()` sonra `findById()` | Okuma assertion'larından önce her zaman flush+clear |
| Sadece `save()` ve `findById()`'yi test etmek | Spring Data altyapısını değil, KENDİ query metotlarını test et |
| İzole repository testleri için `@SpringBootTest` yüklemek | `@DataJpaTest` kullan — %90 daha az altyapı |
| PostgreSQL syntax'lı native query'ler için H2 | Production DB image'ı ile Testcontainers kullan |
| Test verisini test edilen repository ile kurmak | Kurulum için `TestEntityManager` kullan |
| `@Transactional` test davranışının production ile aynı olduğunu varsaymak | Aynı değil — lazy loading, flush zamanlaması ve transaction sınırları farklı |
