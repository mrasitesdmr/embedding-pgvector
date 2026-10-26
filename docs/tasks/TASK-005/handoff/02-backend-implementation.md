TASK: TASK-005
# Backend uygulama raporu

## Kullanılan skill'ler
`backend-conventions`, `springboot-patterns`, `problem-details-rfc9457`, `flyway-migrations`, `postgres-patterns`, `jpa-patterns`, `spring-transactions` (planın "Backend planı" listesi + rol tanımındaki zorunlular).

## Yapılanlar
| Plan maddesi / Kural # | Dosya(lar) | Not |
|---|---|---|
| D1, D12, G1 / K19 | `db/migration/V8__add_name_key_to_sources.sql` | Plandaki sırayla yazıldı: `name_key text NULL` eklenir → `UPDATE … SET name_key = lower(translate(normalize(name, NFC), 'İIı', 'iii'))` → `SET NOT NULL` → `DROP INDEX uk_sources_workspace_id_name` → `CREATE UNIQUE INDEX uk_sources_workspace_id_name_key ON sources (workspace_id, name_key)`. V1-V7 değişmedi. |
| D1, D2 / K16, K19 | `source/domain/entity/Source.java` | `@Column(name = "name_key", nullable = false) private String nameKey;` private constructor'da `this.nameKey = toNameKey(name)` (`ingesting` ve `seeded` ikisi de buradan geçiyor). `public static String toNameKey(String name)`: `Normalizer.normalize(name, NFC)` → `İ`, `I`, `ı` → `i` → `toLowerCase(Locale.ROOT)`. `public String getNameKey()`. |
| D1, D3 / K16, K17, K18 | `workspace/domain/entity/Workspace.java` | `addSource` artık `Source.toNameKey(sourceName)` ile mevcut kaynakların `getNameKey()`'ini karşılaştırıyor. İmza, `ConflictException("SOURCE_NAME_TAKEN", "Source name is already used in workspace: " + id)` aynı (mesaj adı içermiyor). Kullanılmayan `java.util.Locale` import'u kalktı. |
| D3 | `IngestionService`, `SeedDataLoader` | Plana göre değişmedi. |
| D8 / K6 | `src/main/resources/application.properties` | `spring.datasource.password=${POSTGRES_PASSWORD:embedding-local}`. URL ve kullanıcı varsayılanları aynı. |
| Backend planı `backend/CLAUDE.md` satırı | `backend/CLAUDE.md` | "Teknoloji/Veritabanı": migration aralığı `V1`-`V8`, `POSTGRES_PASSWORD` varsayılanı `embedding-local` (dev compose ile aynı, sadece local), `name_key` / `uk_sources_workspace_id_name_key` notu. "Komutlar": veritabanını başlat → `docker compose -f deployment/docker-compose.dev.yml up -d` (postgres + pgAdmin, `.env` yok; tam sistem açıksa önce onu durdur); çalıştır → `./mvnw spring-boot:run`; `SEED_ENABLED=false ./mvnw spring-boot:run`; "Başka veritabanı…" satırından `POSTGRES_PASSWORD=...` çıktı. |

Ek kontrol (kod değil): backfill SQL ifadesinin Java fonksiyonuyla aynı sonucu verdiğini, projede kullanılan imajda (`pgvector/pgvector:0.8.1-pg18`, DB collation `en_US.utf8`) geçici bir container'da denedim. Plan B1/B6'daki bütün örnekler iki tarafta da aynı çıktı: "Yardım merkezi" / "yardım MERKEZİ" / "YARDIM MERKEZI" / "Yardim Merkezi" → `yardim merkezi`; ayrık `I + U+0307` "İstanbul notları" → `istanbul notlari`; "ŞİFRE SSS" → `şifre sss`; "ĞÜÖÇ notları" → `ğüöç notlari`; "Dalga T5 modem kullanım kılavuzu" → `dalga t5 modem kullanim kilavuzu`; "Çözülmüş destek talepleri" → `çözülmüş destek talepleri`.

## Değişen dosyalar
- Yeni: `backend/src/main/resources/db/migration/V8__add_name_key_to_sources.sql`
- Değişen: `backend/src/main/java/com/embedding/pgvector/source/domain/entity/Source.java`
- Değişen: `backend/src/main/java/com/embedding/pgvector/workspace/domain/entity/Workspace.java`
- Değişen: `backend/src/main/resources/application.properties`
- Değişen: `backend/CLAUDE.md`
- Silinen: yok. Yeni dependency yok (`java.text.Normalizer` JDK'da). `pom.xml` değişmedi.

## Plandan sapmalar
Yok.

## Doğrulama
1. **Repodaki hali:** `cd backend && ./mvnw verify -Djacoco.haltOnFailure=false` → **BUILD FAILURE, Tests run: 377, Failures: 3, Errors: 54.** Bütün kırılmalar planın "Test senaryoları → Mevcut testlerde güncellenecekler" listesinde tester'a verilen üç noktadan geliyor:
   - 54 error: `support/FakeEmbeddingIntegrationTest.insertSource` (satır 97) ve `SchemaMigrationIntegrationTest` (satır 64) ham `INSERT INTO sources` yapıyor, `name_key` vermiyor → `NOT NULL` ihlali (`DataIntegrityViolationException`). `insertSource`'u kullanan Search/Source/Workspace/Chunk/Ingestion/Seed/EmbeddingModelMismatch integration testlerinin hepsi bu yüzden kuruluşta düşüyor.
   - `SchemaMigrationIntegrationTest` satır 56: `uk_sources_workspace_id_name` + `lower(` beklentisi (index artık `uk_sources_workspace_id_name_key`) → plan B5.
   - `SchemaMigrationIntegrationTest` satır 85: başarılı migration sayısı `7` bekleniyor, artık `8` (planın listesinde yok; tester için not, aşağıda).
   - `IngestionIntegrationTest.givenLargeFileWithManyChunks…` (satır 494): tam koşuda `FAILED`, sebebi `Chunk` id çakışması (`NonUniqueObjectException … Chunk with id '4'`). Önceki testler kuruluşta erken düştüğü için `chunks_id_seq` / Hibernate pooled optimizer sırası değişiyor. Tek başına koşunca geçiyor (`./mvnw -q test -Dtest='IngestionIntegrationTest#givenLargeFileWithManyChunks_whenCreateSource_thenEmbeddedInBatchesOf96AndAllWritten'` → exit 0). Üretim koduyla ilgisi yok.
   - `SourceRepositoryTest` satır 68 (`contains("uk_sources_workspace_id_name")`) bugün de geçiyor, çünkü yeni ad eski adı önek olarak içeriyor. Plana göre yine de yeni adla güncellenmeli.
2. **Kanıt koşusu (repoya yazılmadı):** `backend/`'i scratchpad'e kopyaladım (`target` hariç). **Sadece kopyada** yukarıdaki üç test noktasını planın tarif ettiği gibi düzelttim: `insertSource`'a `name_key` = `Source.toNameKey(name)`, SchemaMigration insert'ine `name_key`, index beklentisi `uk_sources_workspace_id_name_key` + `(workspace_id, name_key)`, migration sayısı 8, `SourceRepositoryTest` index adı. Kopyada `./mvnw verify -Djacoco.haltOnFailure=false` → **BUILD SUCCESS, Tests run: 377, Failures: 0, Errors: 0, Skipped: 0**, JaCoCo "All coverage checks have been met" (satır coverage %96,53). Kopya silindi. Repodaki `src/test/**` dosyalarına dokunulmadı.
3. Coverage eşiği repodaki koşuda kapalıydı (`-Djacoco.haltOnFailure=false`). Eşik ve tam `./mvnw verify` tester ve qa'da doğrulanacak.

## Tester için notlar
- **Önce kırık fixture'lar** (yukarıdaki liste): `FakeEmbeddingIntegrationTest.insertSource` ve `SchemaMigrationIntegrationTest` ham insert'lerine `name_key`, B5 index beklentisi, **`SchemaMigrationIntegrationTest` satır 85'teki `isEqualTo(7)` → `8`** (planın listesinde bu satır yok), `SourceRepositoryTest` satır 68 index adı. Bunlar düzelince takım geçiyor (kanıt koşusu).
- **`Source.toNameKey` statik ve saf:** B1 unit testi doğrudan çağırabilir. Ayrık İ için `"İstanbul notları"` literal'i kullanılmalı; editör bunu birleşik `İ`'ye çevirebilir, `̇` kaçışı güvenli.
- **B2:** `Workspace.seeded(...)` + `addSource(...)` ile Spring'siz kurulabilir. Reddedilen eklemede `sources` listesine bir şey eklenmez (kontrol, `Source.ingesting`'den önce).
- **B3 (`@DataJpaTest`):** `Source.ingesting(workspace, id, "YARDIM MERKEZI", kind, createdAt)` ile aynı alana ikinci kaynak → `saveAndFlush` unique index'e takılır. Burada entity kontrolünü bilerek atlamak için `Workspace.addSource` değil `Source.ingesting` kullanılmalı.
- **B4 (log):** Türkçe ad artık `Workspace.addSource`'ta reddediliyor, DB'ye `INSERT` gitmiyor. `IngestionService.createSource`'taki `DataIntegrityViolationException` yakalaması değişmedi (D3). Kaynak eklemede çalışma alanı `findByIdForUpdate` ile kilitli olduğu için normal akışta oraya ulaşılmaz.
- **B6:** V8 backfill ifadesi `lower(translate(normalize(name, NFC), 'İIı', 'iii'))`. `lower()` DB collation'ına bağlı. Testcontainers imajı aynı (`en_US.utf8`) olduğu için Java ile eşleşiyor (yukarıdaki elle karşılaştırma). Flyway `target("7")` → satır ekle → `migrate()` akışında V7'deki satırlarda `name_key` kolonu henüz yok, insert'te verilmemeli.
- Zaman ve eşzamanlılık: bu task'ta yeni `Clock` ya da eşzamanlılık noktası yok.

## Düzeltme

STATUS: DONE
