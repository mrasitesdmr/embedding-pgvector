---
name: jpa-patterns
description: Spring Boot'ta entity tasarımı, ilişkiler, query optimization, transaction'lar, auditing, indexing, pagination ve connection pooling için JPA/Hibernate pattern'leri. JPA entity'leri veya ilişkileri tasarlarken ya da bir Hibernate sorgusu, transaction veya N+1 problemi düzeltilmesi gerektiğinde kullan.
metadata:
  origin: ECC
  adapted-for: Spring Boot 4.1 / Hibernate 7
---

# JPA/Hibernate Pattern'leri

> **Proje kuralı:** İsimlendirme, yorum politikası, Lombok yasağı ve test yapısı için `backend-conventions` geçerlidir. Bu dosyadaki örneklerle çelişirse `backend-conventions` kazanır. Transaction ve eşzamanlı yazma kuralları `spring-transactions`'ta, schema/index kuralları `postgres-patterns`'te, migration kuralları `flyway-migrations`'tadır.

Spring Boot'ta veri modelleme, repository'ler ve performans ayarı için kullan.

## Ne Zaman Devreye Girer

- JPA entity'lerini ve table mapping'lerini tasarlarken
- İlişkileri tanımlarken (`@OneToMany`, `@ManyToOne`, `@ManyToMany`)
- Sorguları optimize ederken (N+1 önleme, fetch stratejileri, projection'lar)
- Auditing yapılandırırken
- Pagination, sıralama veya özel repository metotları kurarken
- Connection pool'u (HikariCP) ayarlarken

## Entity Tasarımı

```java
@Entity
@Table(name = "sources")
@EntityListeners(AuditingEntityListener.class)
public class Source implements Persistable<Long> {

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SourceKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SourceStatus status;

    @Column(name = "synced_at")
    private @Nullable Instant syncedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Transient
    private boolean newEntity = true;

    protected Source() {
    }

    public static Source ingesting(Workspace workspace, long id, String name, SourceKind kind, Instant createdAt) {
        return new Source(workspace, id, name, kind, SourceStatus.INGESTING, createdAt);
    }

    public void completeIngestion(@Nullable String newVersion, String newFileName, int newChunkCount, Instant newSyncedAt) {
        // version, fileName, chunkCount, syncedAt güncellenir
        status = SourceStatus.READY;
    }

    public void failIngestion() {
        status = syncedAt != null ? SourceStatus.READY : SourceStatus.FAILED;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        newEntity = false;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    // getter'lar, private constructor
}
```

- Entity adı tekil isim, sonek yok (`Source`, `SourceEntity` değil).
- Getter'lar elle; setter yerine domain fiili (`completeIngestion(...)`, `failIngestion()`). Kaynak `Workspace.addSource(...)` üzerinden oluşturulur (kural sahibi aggregate), o da `Source.ingesting(...)` factory'sini çağırır.
- Id sequence'ten elle atanır (`SourceRepository.nextSourceId()`; seed kaynakları sabit id taşır). Atanmış id'li entity `Persistable` ile "yeni mi" bilgisini kendisi verir; yoksa Spring Data `save` id'si dolu entity'yi `merge` eder ve gereksiz bir `SELECT` atar.
- `@Enumerated(EnumType.STRING)`; `ORDINAL` yok. DB tarafında `text` + `CHECK` (`postgres-patterns`).
- Zaman `Instant` ↔ `timestamptz`.
- Index, unique ve check constraint'ler Flyway migration'ında tanımlanır (`uk_`, `idx_` isimlendirmesi `postgres-patterns`'te). `spring.jpa.hibernate.ddl-auto=validate`.

Zaman alanları (`createdAt`, `syncedAt`, `startedAt`, `finishedAt`) JPA auditing (`@CreatedDate`) ile değil, service'in `Clock`'tan okuduğu değerle domain metoduna verilir (`Source.ingesting(..., Instant.now(clock))`, `job.complete(now)`). Böylece testte `Clock.fixed(...)` yeterlidir; projede `@EnableJpaAuditing` yoktur.

## İlişkiler ve N+1 Önleme

```java
@OneToMany(mappedBy = "workspace", cascade = CascadeType.ALL, orphanRemoval = true)
private List<Source> sources = new ArrayList<>();
```

- `@ManyToOne` varsayılanı `EAGER`'dır; her zaman `fetch = FetchType.LAZY` yaz.
- Koleksiyonlarda `EAGER` yok; gerektiğinde sorguda `JOIN FETCH` veya `@EntityGraph`.
- Okuma yolları için DTO projection.

```java
@Query("select w from Workspace w left join fetch w.sources where w.id = :workspaceId")
Optional<Workspace> findWithSourcesById(@Param("workspaceId") long workspaceId);
```

- `Chunk` → `Source` ilişkisi `@ManyToOne(fetch = LAZY)`'dir; bir kaynağın parçaları koleksiyon olarak `Source`'a map'lenmez (binlerce satır, embedding kolonuyla birlikte belleğe dolar). Parçalar sayfalı repository sorgusuyla okunur.
- Embedding kolonu listeleme sorgularında seçilmez; parça listesi için `embedding` içermeyen DTO projection kullan (1024 float × satır sayısı).

## Repository Pattern'leri

```java
public interface SourceRepository extends JpaRepository<Source, Long> {

    Optional<Source> findByWorkspaceIdAndId(long workspaceId, long id);

    Page<Source> findByWorkspaceIdAndStatus(long workspaceId, SourceStatus status, Pageable pageable);
}
```

Hafif sorgular için projection:

```java
public record ChunkSummaryResponse(Long id, int position, String content) {
}

Page<ChunkSummaryResponse> findSummariesBySourceId(long sourceId, Pageable pageable);
```

Projection embedding kolonunu içermez; Spring Data sadece record'daki alanları seçer. Vektör benzerliği sorgusu native'dir (`spring-boot-engineer/references/data.md`).

```java
```

## Transaction'lar

- `@Transactional` sadece service katmanında; okuma service'inde sınıf seviyesinde `readOnly = true`, yazma metodunda `@Transactional`.
- Arka plan içe alma işinin her aşaması (`startJob`, `moveToStage`, `recordEmbeddingProgress`, `completeJob`, `failJob`) kendi kısa transaction'ındadır (`IngestionJobLifecycleService`); embedding çağrısı transaction dışında yapılır. Böylece iş durumu her adımda commit edilir ve polling eden istemci ilerlemeyi görür.
- Managed entity'deki değişiklik commit'te otomatik flush edilir (dirty checking); ekstra `save()` gerekmez.

```java
@Transactional
public void failJob(long jobId, IngestionErrorCode errorCode) {
    IngestionJob job = getJob(jobId);
    job.fail(errorCode, Instant.now(clock));
    lockSource(job).failIngestion();
}
```

Propagation, locking ve commit sonrası yan etkiler: `spring-transactions`.

## Pagination

Service `Page<XResponse>` döndürür, controller `PageResponse.from(...)` ile sarar (`backend-conventions` bölüm 6).

```java
public Page<ChunkSummaryResponse> listChunks(long sourceId, Pageable pageable) {
    return chunkRepository.findSummariesBySourceId(sourceId, pageable);
}
```

Büyük tablolarda `OFFSET` yerine keyset pagination (`WHERE (created_at, id) < (:createdAt, :id)`), bkz. `postgres-patterns`.

## Indexing ve Performans

- Sık kullanılan filtreler ve her foreign key için index (migration'da)
- Sorgu pattern'ine uyan composite index (`status, created_at`)
- Sadece gereken kolonları çek (projection)
- Toplu yazmalarda (içe almada yüzlerce parça) `saveAll` + `spring.jpa.properties.hibernate.jdbc.batch_size`. `IDENTITY` ID'lerde Hibernate insert batching yapamaz; toplu insert gerekiyorsa sequence (`@SequenceGenerator`, `allocationSize`) kullan.

## Connection Pool (HikariCP)

```properties
spring.datasource.hikari.maximum-pool-size=10
spring.datasource.hikari.connection-timeout=30000
spring.datasource.hikari.validation-timeout=5000
```

Pool boyutu küçük tutulur; PostgreSQL `max_connections` ile uyumlu olmalı (`postgres-patterns`). Virtual thread'ler açıkken bile DB bağlantı sayısı pool ile sınırlıdır.

## Cache

- First-level cache `EntityManager` başınadır; entity'leri transaction'lar arasında tutma.
- Second-level cache'i sadece ölçülmüş ihtiyaçta ve eviction stratejisi doğrulanarak aç. Embedding'li parçalar ve arama sonuçları cache'lenmez.

## Migration'lar

- Flyway kullan; production'da Hibernate'in otomatik DDL'ine güvenme (`ddl-auto=validate`).
- Versioned migration'lar ileriye dönüktür: çalışmış dosya değiştirilmez, değişiklik yeni bir `V` dosyasıdır. Kolon silme/yeniden adlandırma çok adımlı yapılır (`flyway-migrations`).

## Veri Erişimini Test Etme

- `@DataJpaTest` + Testcontainers (`@ServiceConnection`, pgvector eklentili PostgreSQL imajı); dependency `spring-boot-starter-data-jpa-test`. H2 yok.
- Okuma assertion'larından önce `flush()` + `clear()` (`spring-jpa-testing`).
- SQL'i kontrol etmek için: `logging.level.org.hibernate.SQL=DEBUG`, parametre değerleri için `logging.level.org.hibernate.orm.jdbc.bind=TRACE`.

**Unutma**: Entity'leri sade, sorguları amaçlı, transaction'ları kısa tut. N+1'i fetch stratejileri ve projection'larla önle, okuma/yazma yollarına göre index ekle.
