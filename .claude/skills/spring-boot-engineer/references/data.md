# Veri Erişimi - Spring Data JPA

> Bu dosya kısa bir giriş özetidir. Ayrıntılar ve tek doğru kaynaklar:
> entity/ilişki/N+1 → `jpa-patterns`, transaction ve eşzamanlı yazma → `spring-transactions`, schema, index ve pgvector → `postgres-patterns`, migration → `flyway-migrations`, repository testi → `spring-jpa-testing`.
>
> Not: Çalışma alanları, kaynaklar, parçalar (embedding'leriyle), içe alma işleri (`ingestion_jobs`, aşama ve ilerleme dahil) ve arama kayıtları PostgreSQL'de tutulur; bellekte iş durumu yoktur (`backend-conventions` bölüm 3 "Veritabanı").

## JPA Entity Pattern'i

```java
@Entity
@Table(name = "workspaces")
public class Workspace implements Persistable<Long> {

    public static final int MAX_SOURCES_PER_WORKSPACE = 10;

    @Id
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "workspace", fetch = FetchType.LAZY)
    private List<Source> sources = new ArrayList<>();

    @Transient
    private boolean newEntity = true;

    protected Workspace() {
    }

    public static Workspace seeded(long id, String name, Instant createdAt) {
        return new Workspace(id, name, createdAt);
    }

    public Source addSource(long sourceId, String sourceName, SourceKind kind, Instant createdAt) {
        if (sources.size() >= MAX_SOURCES_PER_WORKSPACE) {
            throw new ConflictException("SOURCE_LIMIT_REACHED", "Workspace source limit reached: " + id);
        }
        String normalizedName = sourceName.toLowerCase(Locale.ROOT);
        boolean nameTaken = sources.stream()
                .anyMatch(source -> source.getName().toLowerCase(Locale.ROOT).equals(normalizedName));
        if (nameTaken) {
            throw new ConflictException("SOURCE_NAME_TAKEN", "Source name is already used in workspace: " + id);
        }
        Source source = Source.ingesting(this, sourceId, sourceName, kind, createdAt);
        sources.add(source);
        return source;
    }

    // getId, getName, getCreatedAt, getSources, isNew / @PostLoad @PostPersist markNotNew
}
```

- Lombok yok: getter'lar elle, setter yerine domain metotları (`addSource`). Çalışma alanı kuralları (kaynak limiti, ad tekliği) entity'dedir; eşzamanlı eklemeler çalışma alanı satırındaki pessimistic lock ile sıraya girer, `(workspace_id, lower(name))` unique index'i son güvencedir (`spring-transactions`).
- Çalışma alanları seed verisiyle gelir (sabit id'ler, `Workspace.seeded`); oluşturma endpoint'i yoktur.
- `toLowerCase(Locale.ROOT)` ile DB'deki `lower(name)` Türkçe `İ` için farklı sonuç verebilir: "yardım MERKEZİ" entity kontrolünü geçip unique index'te yakalanır (servis `DataIntegrityViolationException`'ı `SOURCE_NAME_TAKEN`'a çevirir). İki karşılaştırmanın aynı sonucu vermesi gereken durumlar testle doğrulanır.
- `@Version` sadece çalışma alanının kendi kolonları değiştiğinde artar; `sources` koleksiyonuna ekleme versiyonu artırmaz.
- JPA için `protected` no-arg constructor; nesne statik factory (`create`) ile oluşturulur.
- Zaman `Instant` (DB'de `timestamptz`); `LocalDateTime` kullanılmaz.
- Index ve constraint'ler entity annotation'ında değil Flyway migration'ında tanımlanır; `spring.jpa.hibernate.ddl-auto=validate`.
- Bidirectional ilişkide iki tarafı da senkron tutan metot (`addSource`) entity'de olur.

## Embedding kolonu (pgvector)

Projede vektör kolonu Hibernate 7'nin vektör modülüyle (`org.hibernate.orm:hibernate-vector`) eşlenir (TASK-002):

```java
@Entity
@Table(name = "chunks")
public class Chunk {

    @Id
    @G```java
@Entity
@Table(name = "chunks")
public class Chunk {

    public static final int EMBEDDING_DIMENSIONS = 1024;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "chunk_id")
    @SequenceGenerator(name = "chunk_id", sequenceName = "chunks_id_seq", allocationSize = 50)
    private Long id;

    @Column(name = "workspace_id", nullable = false)
    private long workspaceId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_id", nullable = false)
    private Source source;

    @Column(name = "chunk_no", nullable = false)
    private int chunkNo;

    private @Nullable String heading;

    @Column(nullable = false)
    private String content;

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = EMBEDDING_DIMENSIONS)
    @Column(nullable = false)
    private float[] embedding;

    @Column(name = "embedding_model", nullable = false)
    private String embeddingModel;

    protected Chunk() {
    }

    public static Chunk create(Source source, ChunkDraft draft, float[] embedding, String embeddingModel, Instant createdAt) {
        // ...
    }
}
r; `ddl-auto=validate` uyuşmazlığı açılışta yakalar.
- `Chunk` → `Source` `@ManyToOne(LAZY)`; `Source` parçaları koleksiyon olarak tutmaz (binlerce satır × 1024 float).
- `SEQUENCE` + `allocationSize = 50` (DB'de `INCREMENT BY 50`) ve `hibernate.jdbc.batch_size=100`: bir sürümün yüzlerce parçası toplu insert edilir. `IDENTITY` id'de Hibernate batch insert yapmaz.
- `workspace_id` parçada da tutulur: filtreli vektör aramasında `sources` ile join gerekmez.
- Parça listelerinde embedding seçilmez: embedding'siz DTO projection kullanılır.

## Spring Data JPA Repository

```java
public interface WorkspaceRepository extends JpaRepository<Workspace, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Workspace w where w.id = :workspaceId")
    Optional<Workspace> findByIdForUpdate(@Param("workspaceId") long workspaceId);

}

public interface SourceRepository extends JpaRepository<Source, Long> {

    @Query("select s from Source s where s.workspace.id = :workspaceId and s.id = :id")
    Optional<Source> findByWorkspaceIdAndId(@Param("workspaceId") long workspaceId, @Param("id") long id);

    @Query(value = "SELECT nextval('sources_id_seq')", nativeQuery = true)
    long nextSourceId();
}

public interface ChunkRepository extends JpaRepository<Chunk, Long> {

    @Modifying(flushAutomatically = true)
    @Query("delete from Chunk c where c.source.id = :sourceId")
    int deleteBySourceId(@Param("sourceId") long sourceId);
}
```

- Spring Data interface'lerine `@Repository` eklenmez.
- Derived query isimleri entity alan adlarıyla; türetilmiş getter'ı olan alanlarda (`Source.getWorkspaceId()`) derived query yanlış özelliği bulur, açık `@Query` + JPQL attribute yolu (`s.workspace.id`) yazılır.
- Toplu silme JPQL `@Modifying` ile yapılır (parçalar belleğe yüklenmez); yeni sürüm yazılırken eski parçalar aynı transaction'da silinir.
- `@Modifying` sorguları service'teki `@Transactional` metottan çağrılır.

### Benzerlik sorgusu (native)

pgvector operatörleri JPQL'de yoktur; k-NN sorgusu native yazılır ve sonucu interface projection'a map'lenir:

```java
public interface ChunkHit {
    long getChunkId();
    long getSourceId();
    String getSourceName();
    String getContent();
    double getScore();
}

public interface ChunkRepository extends JpaRepository<Chunk, Long> {

    @Query(value = """
            SELECT c.id AS chunk_id, c.source_id, s.name AS source_name, c.content,
                   1 - (c.embedding <=> CAST(:queryEmbedding AS vector)) AS score
            FROM chunks c
            JOIN sources s ON s.id = c.source_id
            WHERE c.workspace_id = :workspaceId
              AND c.embedding_model = :embeddingModel
            ORDER BY c.embedding <=> CAST(:queryEmbedding AS vector)
            LIMIT :limit
            """, nativeQuery = true)
    List<ChunkHit> findNearest(@Param("workspaceId") long workspaceId,
            @Param("queryEmbedding") String queryEmbedding,
            @Param("embeddingModel") String embeddingModel,
            @Param("limit") int limit);
}
```

- `ORDER BY` ham mesafe (`<=>`) üzerindedir; `score` ile sıralamak HNSW index'ini devre dışı bırakır (`postgres-patterns` "pgvector").
- Sorgu vektörü pgvector metin biçiminde (`[0.1,0.2,...]`) geçirilir ve SQL'de `CAST(... AS vector)` edilir; vektör kütüphanesi seçildiyse onun parametre tipi kullanılır.
- Filtre seçiciyse (küçük çalışma alanı) iterative scan için `SET LOCAL hnsw.iterative_scan` aynı transaction'da çalıştırılır (`postgres-patterns`).
- Sorgu davranışı mock'la değil gerçek pgvector'e karşı `@DataJpaTest` ile test edilir (`spring-jpa-testing`).

## Projection

```java
public record SourceSummaryResponse(long sourceId, String name, long chunkCount) {
}

@Query("""
        select new com.embedding.pgvector.source.domain.dto.SourceSummaryResponse(s.id, s.name, count(c.id))
        from Source s left join Chunk c on c.source = s
        where s.workspace.id = :workspaceId
        group by s.id, s.name
        """)
List<SourceSummaryResponse> findSummaries(@Param("workspaceId") long workspaceId);
```

JPQL `count(...)` `Long` döndürür; constructor parametresini buna uygun `long` tut (`select new` constructor eşlemesinde tip uyuşmazlığı açılışta sorgu hatasına yol açabilir). Okuma ağırlıklı listelerde entity yerine DTO projection; ayrıntı ve N+1 çözümleri (`JOIN FETCH`, `@EntityGraph`, `@BatchSize`) `jpa-patterns`'te.

## Transaction Yönetimi

```java
@Service
public class IngestionService {

    // constructor injection (WorkspaceRepository, SourceRepository, IngestionJobRepository, IngestionJobRunner, Clock ...)

    @Transactional
    public IngestionJobResponse createSource(long workspaceId, String name, MultipartFile file) {
        Workspace workspace = workspaceRepository.findByIdForUpdate(workspaceId)
                .orElseThrow(() -> WorkspaceService.workspaceNotFound(workspaceId));
        byte[] content = readContent(file);
        SourceKind kind = fileTypeDetector.detect(fileName(file), content);
        Instant now = Instant.now(clock);
        Source source = workspace.addSource(sourceRepository.nextSourceId(), name.strip(), kind, now);
        sourceRepository.saveAndFlush(source);
        IngestionJob job = ingestionJobRepository.saveAndFlush(IngestionJob.queued(source, null, fileName(file), content.length, now));
        submitAfterCommit(job.getId(), content, kind);
        return IngestionJobResponse.from(job);
    }

    private void submitAfterCommit(long jobId, byte[] content, SourceKind kind) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                ingestionJobRunner.submit(jobId, content, kind);
            }
        });
    }
}
```

- Okuma service'lerinde (`SourceService`) sınıf ya da metot seviyesinde `@Transactional(readOnly = true)`, yazma metodunda `@Transactional`.
- İçe alma işini başlatmak gibi yan etkiler transaction içinde değil commit sonrasında yapılır (`TransactionSynchronization.afterCommit`, ya da event kullanılıyorsa `@TransactionalEventListener(phase = AFTER_COMMIT)`; bkz. `spring-transactions`). Aksi halde arka plan işi henüz commit edilmemiş kaynağı ve işi arar.
- `Source.getWorkspaceId()` gibi türetilmiş getter'lar yüzünden Spring Data `workspaceId`'yi özellik sanar; ilişki üzerinden sorgu açık JPQL ile yazılır (`s.workspace.id`), derived query ile değil (`backend/CLAUDE.md`).
- Embedding API çağrısı (ağ, saniyeler sürebilir) transaction içinde yapılmaz; bağlantıyı ve kilidi o süre boyunca tutar. Önce embedding'ler üretilir, sonra kısa bir transaction'da yazılır.
- Kaynak eklemede çalışma alanı `findByIdForUpdate` (pessimistic lock) ile okunur. `@Version` bu kuralı korumaz: `sources` `mappedBy` koleksiyonudur ve kaynak eklemek çalışma alanının versiyonunu artırmaz. Strateji ayrıntısı `spring-transactions`'ta.

## Zaman alanları

Projede JPA auditing (`@EnableJpaAuditing`, `@CreatedDate`) yoktur. Zaman service'te `Instant.now(clock)` ile okunur ve domain metoduna verilir (`workspace.addSource(..., now)`, `job.complete(now)`); testte `Clock.fixed(...)` yeterlidir (`backend-conventions` bölüm 4).

## Veritabanı Migration'ları (Flyway)

```sql
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE chunks (
    id              bigint       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_id       bigint       NOT NULL REFERENCES sources (id) ON DELETE CASCADE,
    workspace_id    bigint       NOT NULL,
    position        integer      NOT NULL,
    content         text         NOT NULL,
    embedding       vector(1024) NOT NULL,
    embedding_model text         NOT NULL,
    CONSTRAINT uk_chunks_source_id_position UNIQUE (source_id, position)
);

CREATE INDEX idx_chunks_embedding ON chunks USING hnsw (embedding vector_cosine_ops);
```

Dosya adı, güvenli migration kuralları ve tip seçimi: `flyway-migrations` (tam şablon `templates/V1__create_table_template.sql`), `postgres-patterns`.

## Hızlı Başvuru

| Anotasyon | Amaç |
|------------|---------|
| `@Entity` | Sınıfı JPA entity olarak işaretler |
| `@Table` | Tablo adını belirtir |
| `@Id` / `@GeneratedValue(IDENTITY)` | Primary key ve üretim stratejisi |
| `@Column` | Kolon mapping'i |
| `@OneToMany/@ManyToOne` | İlişkiler (varsayılan lazy tercih edilir) |
| `@JdbcTypeCode(SqlTypes.VECTOR)` + `@Array(length)` | pgvector kolonu (Hibernate vektör modülü seçildiyse) |
| `@Transactional` | Transaction sınırı (sadece service katmanında) |
| `@Query` | Özel JPQL / native sorgu (vektör benzerliği native) |
| `@Modifying` | Sorguyu UPDATE/DELETE olarak işaretler |
| `@EntityGraph` | İlişkiler için fetch grafiği |
| `@Version` | Optimistic locking sürüm alanı |
