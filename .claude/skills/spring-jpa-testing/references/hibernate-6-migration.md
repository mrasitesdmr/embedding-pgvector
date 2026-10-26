# JPA Testing: Hibernate 6.x / 7.x Migration Tuzakları ve Spring Boot 4 Package Değişiklikleri

Özellikle test kodunu etkileyen Hibernate 6.x ve 7.x migration tuzaklarını ve Spring Boot 3.x → 4.x test autoconfigure package yeniden düzenlemesini kapsar. Buradaki tüm package isimleri Spring Boot 4 / Spring Framework 7'ye aittir.

---

## Spring Boot 3.x → 4.x: Test Package Yeniden Düzenlemesi

Spring Boot 4'te test autoconfigure sınıfları teknoloji bazlı modüllere ve yeni package'lara taşındı. Boot 4 projelerinde Boot 3 import path'lerini kullanmak derleme hatası verir (`package ... does not exist`).

| Annotation / Sınıf | Boot 3.x Package | Boot 4.x Package |
|---|---|---|
| `@DataJpaTest` | `org.springframework.boot.test.autoconfigure.orm.jpa` | `org.springframework.boot.data.jpa.test.autoconfigure` |
| `@AutoConfigureTestDatabase` | `org.springframework.boot.test.autoconfigure.jdbc` | `org.springframework.boot.jdbc.test.autoconfigure` |
| `TestEntityManager` | `org.springframework.boot.test.autoconfigure.orm.jpa` | `org.springframework.boot.jpa.test.autoconfigure` |
| `@WebMvcTest` | `org.springframework.boot.test.autoconfigure.web.servlet` | `org.springframework.boot.webmvc.test.autoconfigure` |
| `@WebFluxTest` | `org.springframework.boot.test.autoconfigure.web.reactive` | `org.springframework.boot.webflux.test.autoconfigure` |
| `@MockBean` | `org.springframework.boot.test.mock.mockito` | Yerini `@MockitoBean` aldı |
| `@MockitoBean` | *(Boot 3'te yoktu)* | `org.springframework.test.context.bean.override.mockito` |

**`@MockBean` → `@MockitoBean` notu**: Spring Framework 7, `org.springframework.test.context.bean.override.mockito.MockitoBean` (`@MockitoBean`)'i getirdi. `org.springframework.boot.test.mock.mockito` paketindeki Boot 3 `@MockBean`'i Boot 4'te kaldırıldı. Global find-and-replace yap: `import org.springframework.boot.test.mock.mockito.MockBean` → `import org.springframework.test.context.bean.override.mockito.MockitoBean` ve `@MockBean` → `@MockitoBean`.

**`@SpringBootTest` ve `@ServiceConnection` değişmedi**: Bunlar `org.springframework.boot.test.context.SpringBootTest` ve `org.springframework.boot.testcontainers.service.connection.ServiceConnection` altında kalmaya devam ediyor.

### Test Kodu için Hızlı Migration Checklist'i

```bash
# Güncellenmesi gereken tüm Boot 3 test autoconfigure import'larını bul
grep -r "org.springframework.boot.test.autoconfigure" src/test/
grep -r "org.springframework.boot.test.mock.mockito.MockBean" src/test/
```

---

## Hibernate 6.x Migration Tuzakları

### 5.1 Sequence Generator İsimlendirmesi (Hibernate 6.0)

Hibernate 6 varsayılan sequence isimlendirme stratejisini değiştirdi. Önceden (açık isim verilmeyen) tüm entity'ler `hibernate_sequence`'ı paylaşıyordu. Artık her entity varsayılan olarak `<entity/tablo adı>_SEQ` alır ve implicit sequence'lar JPA varsayılanı `allocationSize = 50` ile çalışır. (Bu proje `GenerationType.IDENTITY` kullandığı için doğrudan etkilenmez; toplu insert için sequence'a geçilirse geçerlidir.)

**Testlere etkisi**: `nextval('hibernate_sequence')` kullanan test verisi script'leri bozulur. Ya ID'leri sabit yaz ya da sequence referanslarını güncelle.

```sql
-- Önce (Hibernate 5.x):
INSERT INTO orders (id, ...) VALUES (nextval('hibernate_sequence'), ...);

-- Sonra (Hibernate 6.x):
INSERT INTO orders (id, ...) VALUES (nextval('orders_SEQ'), ...);
```

Eski davranışı geri getirmek için (sadece mevcut şemayla uyumluluk gerekiyorsa):
```properties
# Hibernate 5.3-5.6 isimlendirmesi
spring.jpa.properties.hibernate.id.db_structure_naming_strategy=legacy
# Tek ortak hibernate_sequence (5.3 öncesi)
spring.jpa.properties.hibernate.id.db_structure_naming_strategy=single
```
Hibernate 5'teki `hibernate.id.new_generator_mappings` property'si Hibernate 6'da kaldırılmıştır; etkisi yoktur.

**Kaynak**: [Thorben Janssen — 8 Things to Know When Migrating to Hibernate 6.x](https://thorben-janssen.com/things-to-know-when-migrating-to-hibernate-6-x/)

---

### 5.2 Jakarta Persistence Package Migration'ı (Hibernate 6.0)

Tüm `javax.persistence.*` import'ları `jakarta.persistence.*` oldu. Bu global bir find-and-replace'tir ama tüm test kodunu, `TestEntityManager` kullanımını ve JPA'ya map'lenen tiplere referans veren SQL script'lerini etkiler.

```java
// Önce (javax):
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

// Sonra (jakarta):
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
```

`@Entity`, `@Id`, `@Column`, `@OneToMany` vb. de etkilenir; JTA'nın `javax.transaction.Transactional`'ı da `jakarta.transaction.Transactional` oldu.

**Not**: `org.springframework.transaction.annotation.Transactional` (Spring'in `@Transactional`'ı) DEĞİŞMEDİ; projede her zaman Spring'in olanı kullanılır.

---

### 5.3 HQL'de Kolon İsimleri Yasak (Hibernate 6.0)

Hibernate 6, HQL/JPQL'de sadece JPA attribute isimlerini kabul eder — veritabanı kolon isimleri reddedilir. Field isimleri yerine kolon isimleri kullanan özel JPQL'li testler fail olur.

```java
// Hibernate 6'da BOZUK (alan adı "embeddingModel" yerine kolon adı "embedding_model" kullanılmış):
@Query("select c from Chunk c where c.embedding_model = :model")

// DOĞRU (JPA attribute adı):
@Query("select c from Chunk c where c.embeddingModel = :model")
```

Belirti: uygulama açılışında `SemanticException` / `UnknownPathException` (`embedding_model` attribute olarak çözülemez).

---

### 5.4 Instant ve Duration Mapping Değişiklikleri (Hibernate 6.0+)

`Instant` ve `Duration` tip mapping'leri Hibernate 5 ile 6 arasında değişti. Schema eski bir Hibernate sürümüyle üretilmişse bu tipleri kullanan entity'ler farklı davranabilir.

Yaygın belirti: timezone'suz `TIMESTAMP`'ten `TIMESTAMP WITH TIME ZONE`'a geçerken `Instant` field'larında timezone işleme farkları. (Bu projede PostgreSQL'de `timestamptz` kullanılır; bkz. `postgres-patterns`.)

---

### 5.5 JOIN FETCH ve Inheritance ile ClassCastException (Hibernate 6.0+)

Bilinen bir Hibernate 6 sorunu: entity inheritance hiyerarşileriyle `JOIN FETCH` kullanan sorgular, tek sorguda birden fazla inheritance dalı fetch edildiğinde `ClassCastException` fırlatabilir.

**Belirti**:
```
java.lang.ClassCastException: class CollectionExecution cannot be cast to class ReportExecution
```

**Geçici çözüm**: Birden fazla inheritance dalı arasında `JOIN FETCH`'ten kaçın. Bunun yerine subclass'a özel repository'ler kullan veya inheritance hiyerarşisini ayrı sorgularla yükle.

`SINGLE_TABLE` inheritance testlerinde her seferinde tek bir discriminator tipini fetch et:

```java
// SORUNLU: karışık inheritance dallarıyla JOIN FETCH
@Query("SELECT j FROM JobExecution j JOIN FETCH j.steps")
List<JobExecution> findAllWithSteps();

// DAHA GÜVENLİ: somut alt tipe göre fetch
@Query("SELECT c FROM CollectionExecution c JOIN FETCH c.steps")
List<CollectionExecution> findCollectionsWithSteps();
```

**Kaynak**: [Hibernate Discourse — ClassCastException in Hibernate 6 when join fetch is used with entity inheritance](https://discourse.hibernate.org/t/classcastexception-in-hibernate-6-when-join-fetch-is-used-in-a-query-with-entity-inheritance/7815)

---

### 5.6 @CreationTimestamp + @UuidGenerator Tuzağı (Hibernate 6.x)

`org.hibernate.annotations.UuidGenerator`, entity ID'sini `persist()` anında set eder. Zaten persist edilmiş bir entity üzerindeki sonraki `save()` çağrıları `persist()` değil `merge()` tetikler; `@CreationTimestamp` kullanılıyorsa bu, `createTime` değeri `null` olan sahte bir `UPDATE` üretebilir.

**Kök neden**: `merge()`, `@CreationTimestamp` initialization'ını tetiklemez. Merge anında `createTime` null ise Hibernate veritabanına null yazar.

**Çözüm**: Timestamp field'larını field tanımında varsayılan değerle initialize et:

```java
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Entity
public class AuditedEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @CreationTimestamp
    private Instant createTime = Instant.now(); // merge() güvenliği için ZORUNLU başlangıç değeri

    @UpdateTimestamp
    private Instant updateTime = Instant.now(); // aynı düzeltme
}
```

**Bu bug'ı ortaya çıkaran test pattern'i**:

```java
@Test
void givenSavedEntity_whenSavedAgainAfterReload_thenCreationTimestampIsPreserved() {
    // Arrange
    AuditedEntity first = repository.saveAndFlush(new AuditedEntity());
    entityManager.clear();
    AuditedEntity reloaded = repository.findById(first.getId()).orElseThrow();
    reloaded.rename("Updated");

    // Act
    repository.saveAndFlush(reloaded);
    entityManager.clear();

    // Assert
    AuditedEntity persisted = repository.findById(first.getId()).orElseThrow();
    assertThat(persisted.getCreateTime()).isNotNull().isEqualTo(first.getCreateTime());
}
```

---

### 5.7 @QueryHint HINT_READ_ONLY String/Boolean Sorunu (Hibernate 6.6.x)

(Çözüm için `references/lazy-loading-tests.md`'ye de bak.)

`findById` üzerindeki `@QueryHint(name = HINT_READ_ONLY, value = "true")`, Hibernate 6.6.x'te `ClassCastException`'a yol açar.

**Kök neden**: Spring Data JPA'nın `findById`'si `EntityManager.find()` kullanır ve hint'leri `Map<String, Object>` olarak geçirir. `@QueryHints` annotation'ı değerleri her zaman `String` olarak geçirir, ancak Hibernate 6.6 `em.find()` ile kullanılan `org.hibernate.readOnly` için `Boolean` ister.

```java
// Hibernate 6.6.x'te BOZUK:
@QueryHints(@QueryHint(name = HibernateHints.HINT_READ_ONLY, value = "true"))  // org.hibernate.jpa.HibernateHints
Optional<T> findById(ID id);
// ClassCastException: java.lang.String cannot be cast to java.lang.Boolean
```

**Çözüm**: Bunun yerine service katmanında `@Transactional(readOnly = true)` kullan:

```java
@Service
@Transactional(readOnly = true)
public class ChunkService {

    public ChunkResponse getChunk(Long chunkId) {
        return chunkRepository.findById(chunkId)
                .map(ChunkResponse::from)
                .orElseThrow(() -> new NotFoundException("CHUNK_NOT_FOUND", "Chunk not found: " + chunkId));
    }
}
```

Spring Framework 5.1'den beri `@Transactional(readOnly = true)`, `Session.setDefaultReadOnly(true)` ile Hibernate `Session`'ına iletilir ve tip uyuşmazlığı olmadan aynı optimizasyonu sağlar.

**İlgili issue'lar**:
- [Spring Data JPA #1503 — HINT_READONLY not applied to findOne()](https://github.com/spring-projects/spring-data-jpa/issues/1503)
- [Spring Framework #21494 — Propagate read-only to Hibernate Session](https://github.com/spring-projects/spring-framework/issues/21494)

---

## Hibernate 7.x Notları (Spring Boot 4)

Spring Boot 4, Hibernate 7.x ile gelir. Hibernate 7 büyük ölçüde Hibernate 6.x'in iyileştirilmiş devamıdır:

- Jakarta persistence zorunluluğu devam ediyor (`javax.persistence` desteği yok)
- 6.0'daki sequence isimlendirme stratejisi devam ediyor (`<table_name>_SEQ`)
- 6.0'daki HQL kolon ismi kısıtı devam ediyor
- `@CreationTimestamp` + `@UuidGenerator` initialization çözümü (5.6) aynen geçerli

7.0'a özel yeni tuzaklar için [Hibernate 7.0 Migration Guide](https://docs.hibernate.org/orm/7.0/migration-guide/)'a bak.

---

## Testler için Migration Checklist'i

Boot 3 + Hibernate 5/6'dan Boot 4 + Hibernate 7'ye geçerken:

- [ ] Tüm `org.springframework.boot.test.autoconfigure.orm.jpa.*` import'larını Boot 4 package'larına güncelle
- [ ] `@MockBean`'i `org.springframework.test.context.bean.override.mockito` paketindeki `@MockitoBean` ile değiştir
- [ ] `javax.persistence` için grep yap ve `jakarta.persistence` ile değiştir
- [ ] Test SQL script'lerinde `nextval('hibernate_sequence')` için grep yap
- [ ] Kolon isimleri kullanan HQL/JPQL için grep yap (ör. `camelCase` field'lara karşı sorgularda `snake_case`)
- [ ] `@UuidGenerator` + `@CreationTimestamp` içeren entity'leri kontrol et — `= Instant.now()` varsayılanları ekle
- [ ] `findById` üzerindeki `@QueryHint(HINT_READ_ONLY)`'yi kaldır — yerine `@Transactional(readOnly = true)` kullan
- [ ] `@AutoConfigureTestDatabase(replace = NONE)` artık gereksiz (Boot 3.4+ varsayılanı `NON_TEST`)
- [ ] `SINGLE_TABLE` inheritance sorgularını test et — birden fazla dal arasında `JOIN FETCH`'ten kaçın

**Kaynaklar**:
- [Thorben Janssen — 8 Things to Know When Migrating to Hibernate 6.x](https://thorben-janssen.com/things-to-know-when-migrating-to-hibernate-6-x/)
- [Hibernate 6.0 Migration Guide](https://docs.hibernate.org/orm/6.0/migration-guide/)
- [Hibernate 6.6 Migration Guide](https://docs.jboss.org/hibernate/orm/6.6/migration-guide/migration-guide.html)
