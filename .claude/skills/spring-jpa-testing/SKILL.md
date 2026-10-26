---
name: spring-jpa-testing
description: "@DataJpaTest, assertion'lardan önce TestEntityManager + flush()/clear() gerektirir; aksi halde testler Hibernate L1 cache'ten okur ve yanlışlıkla geçer. Herhangi bir JPA/repository testi yazmadan önce oku. Tetikleyiciler: repository testi, integration test, @DataJpaTest, JpaRepository, TestEntityManager, @ServiceConnection, Testcontainers, @Modifying, Hibernate 6/7, @AutoConfigureTestDatabase, testlerde @Transactional."
license: Apache-2.0
metadata:
  source: https://github.com/spring-ai-community/spring-testing-skills/tree/main/skills/spring-jpa-testing
  version: 0.1.0
---

# Spring JPA Testing

KRİTİK: `@DataJpaTest`, test verisi kurulumu için `TestEntityManager` ve okuma assertion'larından önce `flush()` + `clear()` gerektirir. Bunu atlayan testler Hibernate L1 cache'ten okur ve sahte şekilde geçer; sorgu hiç veritabanına gitmez.

**Sinyaller**: `@DataJpaTest`, `JpaRepository`, `EntityManager`, `TestEntityManager`, `@ServiceConnection`, `PostgreSQLContainer`, `@AutoConfigureTestDatabase`, `@Modifying`, `clearAutomatically`, Testcontainers, Hibernate 6/7

## Proje kuralı (öncelikli)

Test isimlendirmesi ve AAA yapısı: `backend-conventions`. Integration testlerde gerçek veritabanı için Testcontainers 2.x + `@ServiceConnection` (pgvector eklentili PostgreSQL imajı, ör. `pgvector/pgvector:<sürüm>-pg18`) kullan; H2 kullanılmaz. Test verisi `<Entity>Fixtures` ile üretilir, temizlik her testten **önce** yapılır.

> Not: Kaynaklar ve parçalar PostgreSQL'de (pgvector) tutulur; içe alma işinin anlık ilerlemesi bellekte tutulabilir (`backend-conventions` bölüm 3). Bu skill JPA/PostgreSQL kullanan her task'ta devreye girer. Vektör benzerliği sorguları (`<=>`, HNSW index) sadece gerçek pgvector üzerinde test edilir.

## Test edildiği sürümler

- Proje: Spring Boot 4.1.1, Hibernate 7.4, JUnit Jupiter 6, Testcontainers 2.0 (sürümleri Boot yönetir)

## Kritik kurallar — @DataJpaTest'te her zaman uygula

**1. Test verisini TestEntityManager ile kur — asla Mockito mock'u kullanma.**
`@DataJpaTest` gerçek bir veritabanı sağlar; repository'yi mock'lamak testin amacını bozar. Test verisini `entityManager.persist()` / `entityManager.persistAndFlush()` ile ekle. Test edilen repository'yi kurulum için kullanma; repository'deki bir bug hem kurulumu hem assertion'ı maskeler.

**2. Okuma assertion'larından önce her zaman flush() ve clear() çağır.**
Flush olmadan SQL veritabanına hiç ulaşmayabilir. Clear olmadan repository entity'leri L1 cache'ten döner; sorgu gerçekten çalışmaz.

```java
@DataJpaTest
@Import(TestcontainersConfiguration.class)
class SourceRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private SourceRepository sourceRepository;

    @Test
    void givenSourceOfOtherWorkspace_whenFindByWorkspaceIdAndId_thenEmpty() {
        // Arrange
        Workspace dalga = WorkspaceFixtures.workspace(42L, "Dalga Fiber");
        Workspace kiyi = WorkspaceFixtures.workspace(19L, "Kıyı Net");
        entityManager.persist(dalga);
        entityManager.persist(kiyi);
        entityManager.persist(SourceFixtures.readySource(dalga, 301L, SourceKind.WEB_CRAWL, null, 0));
        entityManager.flush();
        entityManager.clear();

        // Act
        Optional<Source> foreign = sourceRepository.findByWorkspaceIdAndId(19L, 301L);
        Optional<Source> own = sourceRepository.findByWorkspaceIdAndId(42L, 301L);

        // Assert
        assertThat(foreign).isEmpty();
        assertThat(own).isPresent();
    }
}
```

Boot 3.4+'te `@AutoConfigureTestDatabase` varsayılanı `Replace.NON_TEST`'tir: `@ServiceConnection` ile gelen Testcontainers datasource'u değiştirilmez, `replace = NONE` yazmak gerekmez. `@Modifying` bulk update'ler, constraint violation testi ve `@Transactional` tuzağı için `references/transactional-tests.md` dosyasına bak.

## Bu skill'i KULLANMA

- REST controller veya HTTP endpoint testi → `spring-mvc-testing`
- Spring context'siz saf unit test, AssertJ/Mockito idiom'ları → `spring-testing-fundamentals`

## Referansları ne zaman oku

| Durum | Oku |
|-------|-----|
| `@DataJpaTest`'i Testcontainers ve `@ServiceConnection` ile kurmak | `references/testcontainers.md` |
| `@DataJpaTest` ile `@SpringBootTest` arasında seçim | `references/testcontainers.md` |
| `@Transactional` rollback sorunları, flush/clear problemleri, test izolasyonu | `references/transactional-tests.md` |
| Derived query, `@Query`, pagination, specification, projection, auditing test pattern'leri | `references/transactional-tests.md` |
| Lazy loading testte çalışıp production'da patlıyor, N+1 sorunları | `references/lazy-loading-tests.md` |
| `SINGLE_TABLE` inheritance, read-only repository, `@QueryHint` tuzakları | `references/lazy-loading-tests.md` |
| Hibernate 6.x/7.x migration hataları, Jakarta paketleri, sequence naming | `references/hibernate-6-migration.md` |
| Boot 3.x → 4.x test paket import değişiklikleri | `references/hibernate-6-migration.md` |
