---
name: flyway-migrations
description: >
  Database migration'ları, schema değişiklikleri, seed data veya database yapısını değiştiren
  herhangi bir SQL oluştururken kullan. Flyway naming convention'larını, versioning'i ve güvenli
  migration pattern'lerini kapsar.
---

# Flyway Migrations

> **Proje kuralı:** İsimlendirme, yorum politikası, Lombok yasağı ve test yapısı için `backend-conventions` geçerlidir. Tip seçimi, constraint/index isimlendirmesi için `postgres-patterns` geçerlidir. Bu dosyadaki örneklerle çelişirse bu iki skill kazanır.

## Dependency'ler

Boot 4'ün modüler starter'ları Flyway'i transitive olarak GETİRMEZ — tek başına `spring-boot-starter-data-jpa`
olursa migration'lar sessizce hiç çalışmaz. Flyway starter'ını ve database'ine özel modülü ekle (sürümleri Boot yönetir; Boot 4.1.1 → Flyway 12.x):

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-flyway</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
</dependency>
```

`flyway-database-postgresql` zorunludur; eksikse açılış "Unsupported Database" hatasıyla fail olur.

## Dosya Naming Convention'ı

```
src/main/resources/db/migration/

V{version}__{description}.sql       ← versioned (bir kez çalışır)
R__{description}.sql                ← repeatable (checksum değişince yeniden çalışır)
U{version}__{description}.sql       ← undo (ücretli Flyway Teams/Enterprise gerektirir; bu projede kullanılmaz)

Örnekler:
V1__create_rooms_table.sql
V2__create_players_table.sql
V3__add_closed_at_to_rooms.sql
V4__seed_initial_terms.sql
R__create_game_report_views.sql
```

Kurallar:
- Version ile description arasında çift alt çizgi `__`
- Description snake_case, İngilizce, fiil ile başlar (`create_`, `add_`, `drop_`, `seed_`)
- Version'lar tam sayı ve sıralı (`V1`, `V2`, ...); asla geri dönüp boşluk doldurma
- Herhangi bir ortamda çalışmış bir migration'ı asla değiştirme; düzeltme yeni bir `V` dosyasıdır
- Geri alma (undo) yerine ileriye dönük düzeltme (roll-forward) migration'ı yazılır

## Örnek Migration'lar

```sql
-- V1__enable_vector_extension.sql
CREATE EXTENSION IF NOT EXISTS vector;

-- V3__create_sources_table.sql
CREATE SEQUENCE sources_id_seq START WITH 10001 INCREMENT BY 1;

CREATE TABLE sources (
    id           bigint      PRIMARY KEY,
    workspace_id bigint      NOT NULL,
    name         text        NOT NULL,
    kind         text        NOT NULL,
    version      text        NULL,
    file_name    text        NULL,
    status       text        NOT NULL,
    chunk_count  integer     NOT NULL DEFAULT 0,
    synced_at    timestamptz NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_sources_workspaces FOREIGN KEY (workspace_id) REFERENCES workspaces (id),
    CONSTRAINT ck_sources_name CHECK (char_length(name) BETWEEN 2 AND 80),
    CONSTRAINT ck_sources_kind CHECK (kind IN ('WEB_CRAWL', 'PDF', 'MARKDOWN', 'CSV', 'TICKET_EXPORT')),
    CONSTRAINT ck_sources_status CHECK (status IN ('INGESTING', 'READY', 'FAILED'))
);

CREATE INDEX idx_sources_workspace_id ON sources (workspace_id);
CREATE UNIQUE INDEX uk_sources_workspace_id_name ON sources (workspace_id, lower(name));
ALTER SEQUENCE sources_id_seq OWNED BY sources.id;

-- V4__create_chunks_table.sql (kısaltılmış; tamamı templates/V1__create_table_template.sql)
CREATE TABLE chunks (
    id              bigint       PRIMARY KEY,
    workspace_id    bigint       NOT NULL,
    source_id       bigint       NOT NULL,
    chunk_no        integer      NOT NULL,
    heading         text         NULL,
    content         text         NOT NULL,
    tsv             tsvector     GENERATED ALWAYS AS (
                        to_tsvector('turkish'::regconfig, coalesce(heading, '') || ' ' || content)) STORED,
    embedding       vector(1024) NOT NULL,
    embedding_model text         NOT NULL,
    CONSTRAINT fk_chunks_sources FOREIGN KEY (source_id) REFERENCES sources (id) ON DELETE CASCADE
);

CREATE INDEX idx_chunks_embedding ON chunks USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64);
CREATE INDEX idx_chunks_tsv ON chunks USING gin (tsv);
CREATE INDEX idx_chunks_workspace_id ON chunks (workspace_id);
CREATE INDEX idx_chunks_source_id_chunk_no ON chunks (source_id, chunk_no);

-- V7__add_result_limit_to_search_events.sql (dolu tabloya nullable kolon)
ALTER TABLE search_events ADD COLUMN result_limit integer NULL;
```

- `CREATE EXTENSION vector` ilk migration'dır; pgvector eklentili PostgreSQL imajı gerekir (düz `postgres` imajında `could not open extension control file` hatası alınır). Migration kullanıcısının eklenti oluşturma yetkisi olmalıdır.
- Yeni ve boş tablo oluşturan migration'da index'i `CONCURRENTLY` olmadan aynı dosyada oluşturmak doğrudur (kilitlenecek veri yok). HNSW boş tabloda kurulabilir ve veri geldikçe güncellenir; IVFFlat ise kümeleri veriden öğrendiği için veri yüklendikten **sonra**, ayrı bir migration'da kurulur (`postgres-patterns` "pgvector").
- Her foreign key kolonuna index (PostgreSQL otomatik eklemez).
- PK `bigint` (identity ya da sequence; seed verisi sabit id taşıyan tablolarda sequence, sabit id'lerin üstünden başlar), metin `text` + `CHECK`, zaman `timestamptz`, embedding `vector(<boyut>)` (`postgres-patterns`).
- Embedding boyutu (`1024`) modelin boyutuyla aynıdır. Model değişince boyut da değişebilir: yeni boyutlu kolon + yeniden embedding + yeni index expand/contract adımlarıyla yapılır (aşağıda), mevcut kolonun tipi yerinde değiştirilmez.

## Güvenli Migration Pattern'leri (dolu tablolar)

```sql
-- Güvenli: nullable kolon ekle
ALTER TABLE ingestion_jobs ADD COLUMN cancelled_at timestamptz;

-- Güvenli: sabit default'lu NOT NULL kolon ekle (PostgreSQL 11+ tabloyu yeniden yazmaz)
ALTER TABLE chunks ADD COLUMN char_count integer NOT NULL DEFAULT 0;

-- Dolu tabloda index: CONCURRENTLY (yazmaları kilitlemez); HNSW kurulumu büyük tabloda dakikalar sürer
CREATE INDEX CONCURRENTLY idx_chunks_embedding ON chunks USING hnsw (embedding vector_cosine_ops);
```

`CREATE INDEX CONCURRENTLY` bir transaction içinde çalışamaz; Flyway varsayılan olarak her migration'ı transaction'a sarar. Bu statement'ı **kendi dosyasında tek başına** tut ve yanına aynı adla bir sidecar config dosyası koy:

```
V8__add_chunks_embedding_hnsw_index.sql
V8__add_chunks_embedding_hnsw_index.sql.conf   →  içerik: executeInTransaction=false
```

Büyük vektör index'i kurulurken oturumun `maintenance_work_mem` değeri index'in sığacağı kadar yüksek olmalıdır; migration dosyasının başında `SET maintenance_work_mem = '2GB';` gibi oturum ayarı yapılabilir (sunucu belleği ölçülerek seçilir).

Kolon yeniden adlandırma çok adımlıdır (expand/contract):

```sql
-- V9: yeni kolonu ekle ve doldur (synced_at → last_synced_at)
ALTER TABLE sources ADD COLUMN last_synced_at timestamptz;
UPDATE sources SET last_synced_at = synced_at WHERE last_synced_at IS NULL AND synced_at IS NOT NULL;

-- V10 (kod iki kolona da yazıp yeni kolonu okuyacak şekilde deploy edildikten sonra): kalan farkları tekrar doldur
UPDATE sources SET last_synced_at = synced_at WHERE last_synced_at IS NULL AND synced_at IS NOT NULL;

-- V11 (eski kolonu okuyan/yazan kod kalmadığında): eski kolonu sil
ALTER TABLE sources DROP COLUMN synced_at;
```

Embedding modelini değiştirmek de aynı kalıptır: `embedding_v2 vector(<yeni boyut>)` eklenir, worker parçaları yeni modelle yeniden embed eder, yeni kolona index kurulur, kod yeni kolonu okumaya geçer, eski kolon ve index silinir. Geçiş boyunca arama tek bir modelin satırlarında yapılır (`embedding_model` filtresi).

```sql
-- Tehlikeli: kolonu doğrudan rename etmek (çalışan eski sürümü bozar)
ALTER TABLE sources RENAME COLUMN synced_at TO last_synced_at;

-- Tehlikeli: dolu tabloya default'suz NOT NULL kolon (mevcut satırlar yüzünden fail olur)
ALTER TABLE chunks ADD COLUMN char_count integer NOT NULL;

-- Tehlikeli: vektör boyutunu yerinde değiştirmek (mevcut satırlar yeni boyuta uymaz, index yeniden kurulur)
ALTER TABLE chunks ALTER COLUMN embedding TYPE vector(3072);
```

Büyük tablolarda `UPDATE` backfill'ini parçalara böl (ör. `WHERE id BETWEEN ...`); tek dev `UPDATE` uzun kilit ve WAL üretir.

## Yapılandırma (`application.properties`)

```properties
spring.flyway.locations=classpath:db/migration
spring.flyway.validate-on-migrate=true
spring.flyway.out-of-order=false
spring.jpa.hibernate.ddl-auto=validate
```

- `spring.flyway.enabled` varsayılan `true`; tekrar yazmaya gerek yok.
- `baseline-on-migrate` varsayılan `false` kalır. Sadece daha önce Flyway'siz oluşturulmuş mevcut bir veritabanına Flyway eklenirken, o ortama özel profile'da açılır; yeni projede açmak boş olmayan yanlış bir şemaya sessizce baseline atar.

## Veri: Referans Data vs. Dev Seed

| Veri türü | Örnek | Nereye |
|-----------|-------|--------|
| Uygulamanın her ortamda çalışması için gereken referans veri | Lab demosunun sabit çalışma alanı, kaynak türü kodları | Versioned migration (`V6__seed_demo_workspace.sql`); değişiklik yeni `V` dosyası |
| Embedding'i olan demo içeriği | Lab'daki bilgi bankası parçaları | Migration değil: içe alma akışıyla (embedding API'si) ya da önceden üretilmiş vektörleri yükleyen ayrı bir import komutuyla; vektörler SQL dosyasına gömülmez (dosya şişer, model değişince geçersizleşir) |
| Sadece geliştirmeyi kolaylaştıran sahte veri | Örnek kaynaklar | `@Profile("dev")` seeder (aşağıda) |
| Test verisi | Senaryoya özel kayıtlar, elle hesaplanmış küçük vektörler | Testin kendisi (`TestEntityManager`, `<Entity>Fixtures`) |

```java
@Component
@Profile("dev")
public class DevDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private final WorkspaceRepository workspaceRepository;
    private final Clock clock;

    public DevDataSeeder(WorkspaceRepository workspaceRepository, Clock clock) {
        this.workspaceRepository = workspaceRepository;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (workspaceRepository.count() > 0) {
            log.info("Dev data already present, skipping seed");
            return;
        }
        Workspace workspace = Workspace.create("Dalga Fiber", Instant.now(clock));
        workspace.addSource("Fatura SSS", SourceKind.MARKDOWN);
        workspaceRepository.save(workspace);
        log.info("Dev data seeded workspaces={} sources={}", 1, 1);
    }
}
```

Şablon: [templates/DevDataSeeder.java](templates/DevDataSeeder.java). `Clock` bean'i `backend-conventions` bölüm 4'teki gibi `config` paketinde tanımlı olmalıdır; yoksa seeder açılışta "No qualifying bean of type Clock" hatasıyla fail olur.

## Takım Workflow'u: Eşzamanlı Migration'lar

- Aynı anda migration oluşturan birden çok geliştirici version çakışmasına yol açar; yeni version'ı almadan önce ana branch'i çek.
- İki migration aynı version'ı hedeflerse, sonra merge edilen kendi version'ını artırır.
- `validate-on-migrate=true` olduğundan çakışma uygulama açılışında ve Testcontainers testlerinde yakalanır; CI'da ayrıca `./mvnw verify` çalışır.
- `out-of-order=true` hiçbir ortamda açılmaz — migration sıralama bug'larını gizler.

## Gotcha'lar

- Agent dosyaları `V1_create_rooms.sql` diye adlandırıyor (tek alt çizgi) — çift `__` olmalı
- Agent mevcut migration dosyalarını değiştiriyor — çalışmış bir migration'ı asla düzenleme
- Agent dolu tabloya default'suz `NOT NULL` kolon ekliyor — nullable kullan ya da default ver
- Agent kolonları doğrudan rename ediyor — deploy'lar boyunca çok adımlı add/backfill/drop kullan
- Agent dev'e özel sahte veriyi migration'a koyuyor — `@Profile("dev")` seeder kullan; ama her ortamda gereken referans veri migration'dadır
- Agent dolu tabloda normal bir migration'da `CREATE INDEX CONCURRENTLY` kullanıyor — transaction içinde fail olur; kendi dosyası + `.sql.conf` sidecar'ında `executeInTransaction=false` gerekir
- Agent index'leri atlıyor — foreign key'leri ve WHERE/ORDER BY'da kullanılan kolonları her zaman indexle
- Agent ilk adım olarak `DROP TABLE` veya `DROP COLUMN` içeren migration oluşturuyor — önce yeni yapıyı ekle, kodu deploy et, eskisini sonraki bir migration'da drop et
- Agent sadece `flyway-core` ekliyor — auto-configuration `spring-boot-starter-flyway` ile gelir; database desteği `flyway-database-postgresql` modülündedir
- Agent UUID PK, `varchar(255)` veya `timestamp` kullanıyor — `bigint` identity, `text` + `CHECK`, `timestamptz` kullan (`postgres-patterns`)

Örnek dosyalar: [examples/good-migration.sql](examples/good-migration.sql), [examples/bad-migration.sql](examples/bad-migration.sql), [templates/V1__create_table_template.sql](templates/V1__create_table_template.sql).
