# JPA Testing: Lazy Loading, Entity Yaşam Döngüsü, Inheritance ve Read-Only Repository'ler

`org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest` (`@DataJpaTest`) testlerinde production'a göre lazy loading davranışını, N+1 tespitini, `SINGLE_TABLE` inheritance testini ve read-only repository'lerde `@QueryHint(HINT_READ_ONLY)` ClassCastException tuzağını kapsar.


> Örneklerdeki `Order`/`Customer`/`Product`/`User` genel illüstrasyondur. Kısa parçalarda `// Arrange` / `// Act` / `// Assert` yorumları yer kazanmak için atlanmış olabilir; projede yazılan her testte zorunludur, exception beklentisi `catchThrowable` ile yazılır (`backend-conventions`).

> **Projedeki karşılıklar:** Gerçek repository testleri `SourceRepositoryTest` (çalışma alanı sınırı, `uk_sources_workspace_id_name` ihlali), `ChunkRepositoryTest` (vektör ve FTS sorguları, toplu silme) ve `IngestionJobRepositoryTest` (son iş sorgusu, aktif iş kısmi unique index'i) dosyalarındadır (`backend/src/test/java/.../repository/`). Projede cascade, `orphanRemoval`, `@Version` ve inheritance yoktur; ilişkiler `LAZY`, `open-in-view=false`. Fixture'lar `support/` paketindedir (`WorkspaceFixtures`, `SourceFixtures`, `ChunkFixtures`, `IngestionJobFixtures`, `VectorFixtures`).

---

## @DataJpaTest'te Lazy Loading: Gizli Tuzak

```java
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
class OrderRepositoryTest {

    @Autowired TestEntityManager entityManager;
    @Autowired OrderRepository orderRepository;

    @Test
    void givenOrderWithItems_whenFindByIdInsideTestTransaction_thenLazyItemsLoad() {
        Customer customer = entityManager.persist(new Customer("alice@example.com"));
        Order order = entityManager.persist(new Order(customer));
        entityManager.persist(new OrderItem(order, "PROD-1", 2));
        entityManager.persist(new OrderItem(order, "PROD-2", 1));
        entityManager.flush();
        entityManager.clear();

        Order loaded = orderRepository.findById(order.getId()).orElseThrow();

        // Bu ÇALIŞIR, çünkü @DataJpaTest testi @Transactional ile sarar
        // ve EntityManager test boyunca açık kalır.
        // Production'da transaction dışında bu satır LazyInitializationException fırlatırdı.
        assertThat(loaded.getItems()).hasSize(2);
    }
}
```

**Sorun**: `@OneToMany(fetch = LAZY)` collection'ları `@DataJpaTest`'te sorunsuz yüklenir, çünkü test seviyesindeki `@Transactional`, `EntityManager`'ı açık tutar. Bu, production kodunun transaction dışında erişimde karşılaşacağı `LazyInitializationException`'ı maskeler.

### Production'da LazyInitializationException Fırlatıldığını Test Etme

Production'a benzer davranışı doğrulamak için `@Transactional` olmadan `@SpringBootTest` kullan:

```java
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class OrderLazyLoadingTest {

    @Autowired OrderRepository orderRepository;

    @Test
    void givenOrderWithItems_whenItemsAccessedOutsideTransaction_thenThrowsLazyInitializationException() {
        // Arrange: kalemli siparişi kaydet (service üzerinden veya TransactionTemplate ile)
        UUID savedId = createOrderWithItems();

        // @SpringBootTest testi @Transactional ile SARMAZ
        Order order = orderRepository.findById(savedId).orElseThrow();

        // Act
        Throwable thrown = catchThrowable(() -> order.getItems().size());

        // Assert
        assertThat(thrown).isInstanceOf(org.hibernate.LazyInitializationException.class);
    }
}
```

---

## Testlerde N+1 Tespiti

`@DataJpaTest`'te N+1 problemlerini görmek zordur, çünkü testte tek entity vardır. SQL sayısını görünür kılacak kadar veriyle ve bir query sayacıyla test yaz.

### DataSource-Proxy ile N+1 Tespiti

DataSource-Proxy kullanan bir SQL sayacı, sorgu sayısını assert edebilir:

```java
import net.ttddyy.dsproxy.asserts.ProxyTestDataSource;

// @BeforeEach'te DataSource'u DataSource-Proxy ile sar
// Sonra testte:
@Test
void givenTenOrders_whenFindAllWithCustomer_thenExecutesSingleQuery() {
    // Arrange: müşterili 10 sipariş
    for (int i = 0; i < 10; i++) {
        Customer c = entityManager.persist(new Customer("user" + i + "@example.com"));
        entityManager.persist(new Order(c, OrderStatus.PENDING));
    }
    entityManager.flush();
    entityManager.clear();

    proxyDataSource.reset();
    List<Order> orders = orderRepository.findAllWithCustomer();

    // 11 (1 + N) değil, 1 sorgu (join fetch) olmalı
    assertThat(proxyDataSource.getQueryExecutions()).hasSize(1);
}
```

### N+1'i JOIN FETCH ile Çözme

```java
// N+1'i önlemek için JOIN FETCH kullanan repository metodu:
@Query("SELECT o FROM Order o JOIN FETCH o.customer WHERE o.status = :status")
List<Order> findAllWithCustomer(@Param("status") OrderStatus status);
```

**Dikkat**: Birden fazla dal içeren `SINGLE_TABLE` inheritance ile `JOIN FETCH` kullanmaktan kaçın — `references/hibernate-6-migration.md` içindeki Hibernate 6.x `ClassCastException` bölümüne bak.

---

## Entity İlişkilerini Test Etme

### @OneToMany: Cascade ve Orphan Removal

```java
@Test
void givenOrderWithTwoItems_whenSave_thenItemsArePersistedByCascade() {
    Customer customer = entityManager.persistAndFlush(new Customer("alice@example.com"));
    Order order = new Order(customer);
    order.addItem(new OrderItem("PROD-1", 2));
    order.addItem(new OrderItem("PROD-2", 1));

    orderRepository.save(order);
    entityManager.flush();
    entityManager.clear();

    Order loaded = orderRepository.findById(order.getId()).orElseThrow();
    // Transaction içinde erişim — @DataJpaTest'te güvenli
    assertThat(loaded.getItems()).hasSize(2);
}

@Test
void givenOrderWithItems_whenDeleteById_thenItemsAreRemoved() {
    // Arrange: kalemli sipariş
    UUID orderId = createOrderWithItems(2);
    entityManager.flush();
    entityManager.clear();

    orderRepository.deleteById(orderId);
    entityManager.flush();
    entityManager.clear();

    // Kalemlerin de silindiğini doğrula (CascadeType.REMOVE / orphanRemoval=true)
    assertThat(orderItemRepository.findByOrderId(orderId)).isEmpty();
}
```

### @ManyToOne: Foreign Key Bütünlüğü

`TestEntityManager` Spring'in exception çevirisinden (`PersistenceExceptionTranslationPostProcessor`) geçmez; ham JPA/Hibernate exception'ı (`PersistenceException`, altında Hibernate `ConstraintViolationException`) fırlar. Aynı ihlal bir Spring Data repository metodu (`saveAndFlush`) üzerinden tetiklenirse `DataIntegrityViolationException` fırlar.

```java
@Test
void givenOrderWithoutCustomer_whenFlush_thenThrowsConstraintViolation() {
    // Arrange
    Order order = new Order(null, OrderStatus.PENDING); // customer_id NOT NULL constraint'ine takılır

    // Act
    Throwable thrown = catchThrowable(() -> {
        entityManager.persist(order);
        entityManager.flush();
    });

    // Assert
    assertThat(thrown)
            .isInstanceOf(jakarta.persistence.PersistenceException.class)
            .hasRootCauseInstanceOf(java.sql.SQLException.class);
}
```

---

## SINGLE_TABLE Inheritance Testi

### SINGLE_TABLE Nasıl Çalışır

```java
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;

@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "type")
public abstract class JobExecution { ... }

@Entity
@DiscriminatorValue("COLLECTION")
public class CollectionExecution extends JobExecution { ... }
```

Hiyerarşideki tüm entity'ler tek bir tabloyu paylaşır. `type` kolonu satırları ayırt eder.

### SINGLE_TABLE Test Stratejileri

1. **Polymorphic query'leri test et**: Base repository'deki `findAll()` hem base hem subclass instance'larını döner.
2. **Discriminator değerlerini test et**: Bir subclass entity'si ekle, ham tabloyu sorgula, discriminator kolonunu doğrula.
3. **Subclass'a özel field'ları test et**: Subclass'a özel kolonlar base class instance'larında null'dır.
4. **Subclass repository filtrelemesini test et**: Bir subclass'a tiplenmiş repository sadece eşleşen discriminator satırlarını döner.
5. **Inheritance dalları arasında join fetch'ten kaçın**: Hibernate 6 `ClassCastException` fırlatabilir — bkz. `references/hibernate-6-migration.md`.

### Örnek Test Pattern'i

```java
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
class JobExecutionRepositoryTest {

    @Autowired TestEntityManager entityManager;
    @Autowired JobExecutionRepository jobExecutionRepository;

    @Test
    void givenMixedSubtypes_whenFindAll_thenReturnsSubtypeInstances() {
        CollectionExecution exec = new CollectionExecution();
        exec.setStatus(BatchStatus.STARTING);
        entityManager.persistAndFlush(exec);
        entityManager.clear();

        List<JobExecution> all = jobExecutionRepository.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0)).isInstanceOf(CollectionExecution.class);
    }

    @Test
    void givenMixedSubtypes_whenFindAllOnSubtypeRepository_thenReturnsOnlyThatSubtype() {
        CollectionExecution collExec = new CollectionExecution();
        ReportExecution reportExec = new ReportExecution();
        entityManager.persist(collExec);
        entityManager.persist(reportExec);
        entityManager.flush();
        entityManager.clear();

        List<CollectionExecution> results = collectionExecutionRepository.findAll();
        assertThat(results).hasSize(1);
        assertThat(results.get(0)).isInstanceOf(CollectionExecution.class);
    }
}
```

**Kaynaklar**:
- [Baeldung — Query JPA Repository with Single Table Inheritance](https://www.baeldung.com/jpa-inheritance-single-table)
- [Vlad Mihalcea — The best way to map @DiscriminatorColumn](https://vladmihalcea.com/the-best-way-to-map-the-discriminatorcolumn-with-jpa-and-hibernate/)
- [Hibernate Discourse — ClassCastException with join fetch and inheritance](https://discourse.hibernate.org/t/classcastexception-in-hibernate-6-when-join-fetch-is-used-in-a-query-with-entity-inheritance/7815)

---

## Read-Only Repository'ler

### Read-Only Repository Pattern'i

DDD read model'leri için `JpaRepository` yerine sadece query metotları içeren `org.springframework.data.repository.Repository`'yi extend et. Bu, aggregate sınırını compile time'da zorlar — çağıranlar yanlışlıkla `save()` veya `delete()` çağıramaz.

```java
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

@NoRepositoryBean
public interface ReadOnlyRepository<T, ID> extends Repository<T, ID> {
    Optional<T> findById(ID id);
    List<T> findAll();
    long count();
}
```

### @QueryHint(HINT_READ_ONLY) ClassCastException — Hibernate 6.6.x

**Belirti**: `findById` üzerindeki `@QueryHint(name = HINT_READ_ONLY, value = "true")`, Hibernate 6.6.x'te `ClassCastException` fırlatır.

**Kök neden**: Spring Data JPA, `findById`'yi `EntityManager.find()` ile uygular ve hint'leri `Map<String, Object>` olarak geçirir. `@QueryHints` annotation'ı değerleri her zaman `String` olarak geçirir, ancak Hibernate 6.6 `entityManager.find()` ile `org.hibernate.readOnly` hint'i için `Boolean` bekler.

```java
// Hibernate 6.6.x'te BOZUK:
@QueryHints(@QueryHint(name = org.hibernate.jpa.QueryHints.HINT_READ_ONLY, value = "true"))
Optional<T> findById(ID id); // ClassCastException: String, Boolean'a cast edilemez
```

### Tercih Edilen Çözüm: @Transactional(readOnly = true)

Spring Framework 5.1'den beri `@Transactional(readOnly = true)`, read-only bayrağını `Session.setDefaultReadOnly(true)` ile Hibernate `Session`'ına iletir. Bu:

- Yüklenen tüm entity'ler için dirty-checking'i kapatır
- Hydrated state snapshot'larını atlar (bellek tasarrufu)
- `entityManager.find()` dahil tüm query türleriyle tutarlı çalışır
- String/Boolean tip uyuşmazlığından tamamen kaçınır

```java
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserReadService {

    private final UserRepository userRepository;

    public UserReadService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<UserResponse> listUsers() {
        return userRepository.findAll().stream().map(UserResponse::from).toList();
    }
}
```

### Read-Only Davranışını Test Etme

Read-only bayrağı sadece transaction'ı **başlatan** sınırda uygulanır; dıştaki bir test transaction'ına katılan (`REQUIRED`) metotta etkisizdir. Bu yüzden test `@DataJpaTest`'in otomatik transaction'ı içinde değil, kendi read-only transaction'ını açan `@SpringBootTest`'te yazılır:

```java
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ReadOnlyTransactionTest {

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private UserRepository userRepository;

    @Test
    void givenReadOnlyTransaction_whenEntityModified_thenChangeIsNotFlushed() {
        // Arrange
        User saved = userRepository.save(new User("alice@example.com"));
        TransactionTemplate readOnlyTransaction = new TransactionTemplate(transactionManager);
        readOnlyTransaction.setReadOnly(true);

        // Act
        readOnlyTransaction.executeWithoutResult(status ->
                userRepository.findById(saved.getId()).orElseThrow().changeEmail("modified@example.com"));

        // Assert
        assertThat(userRepository.findById(saved.getId()).orElseThrow().getEmail()).isEqualTo("alice@example.com");
    }
}
```

**Kaynaklar**:
- [Vlad Mihalcea — Spring read-only transaction Hibernate optimization](https://vladmihalcea.com/spring-read-only-transaction-hibernate-optimization/)
- [Thorben Janssen — Hibernate's Read-Only Query Hint](https://thorben-janssen.com/read-only-query-hint/)
- [Spring Data JPA #1503 — HINT_READONLY not applied to findOne()](https://github.com/spring-projects/spring-data-jpa/issues/1503)

---

## Optimistic Locking Testleri

`@Version` field'ları kaybolan güncellemelere (lost update) karşı korur. Eşzamanlı değişikliğin tespit edildiğini test et:

```java
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@Test
void givenStaleEntityVersion_whenSaveAndFlush_thenThrowsOptimisticLockingFailure() {
    // Arrange
    Product saved = productRepository.saveAndFlush(new Product("Widget", BigDecimal.TEN));
    Product firstCopy = productRepository.findById(saved.getId()).orElseThrow();
    Product secondCopy = productRepository.findById(saved.getId()).orElseThrow();
    firstCopy.changePrice(new BigDecimal("15.00"));
    productRepository.saveAndFlush(firstCopy);
    secondCopy.changePrice(new BigDecimal("20.00"));

    // Act
    Throwable thrown = catchThrowable(() -> productRepository.saveAndFlush(secondCopy));

    // Assert
    assertThat(thrown).isInstanceOf(ObjectOptimisticLockingFailureException.class);
}
```

**Not**: Bu test `@SpringBootTest` (veya `@DataJpaTest` + sınıf seviyesinde `@Transactional(propagation = Propagation.NOT_SUPPORTED)`) gerektirir: her repository çağrısı kendi transaction'ında çalışmalı ki iki kopya ayrı nesneler olsun. Tek test transaction'ı içinde iki `findById` aynı L1 cache instance'ını döner ve çakışma oluşmaz. Repository üzerinden fırlayan exception Spring'in çevirdiği `ObjectOptimisticLockingFailureException`'dır (ham JPA `OptimisticLockException` değil); servis bu durumda retry eder veya 409 döner (`spring-transactions`).
