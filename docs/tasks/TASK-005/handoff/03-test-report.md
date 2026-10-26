TASK: TASK-005
# Test raporu

## Kullanılan skill'ler
`writing-good-tests`, `backend-conventions`, `spring-testing-fundamentals`, `frontend-conventions`, `e2e-testing`.

## Senaryo → test eşlemesi
| # | Senaryo / Kural | Test (dosya#metot) | Sonuç |
|---|-----------------|--------------------|-------|
| B1 (K16, G2) | Aynı anahtar (8 literal satır) | `SourceTest#givenNamesDifferingOnlyInCaseOrDottedI_whenToNameKey_thenReturnsSameKey` | geçti |
| B1 (G2) | Ayrık İ (`I`+U+0307) | `SourceTest#givenDecomposedDottedCapitalI_whenToNameKey_thenEqualsComposedForm` | geçti |
| B1 | Farklı anahtar (Şifre/Sifre, Göç/Goc, Ücret/Ucret) | `SourceTest#givenNamesDifferingInTurkishLetters_whenToNameKey_thenReturnsDifferentKeys` | geçti |
| B2 (K16, K17) | 4 Türkçe varyant → `SOURCE_NAME_TAKEN`, 1 kaynak | `WorkspaceTest#givenYardimMerkezi_whenAddSourceWithTurkishCaseVariant_thenThrowsSourceNameTaken` | geçti |
| B2 | "Şifre SSS" ≠ "Sifre SSS", `nameKey = sifre sss` | `WorkspaceTest#givenSifreSss_whenAddSourceSifreSssWithoutCedilla_thenAddsSource` | geçti |
| B3 (K19) | Aynı name_key → unique index reddeder | `SourceRepositoryTest#givenSourceInWorkspace_whenSaveAnotherWithSameNameKey_thenUniqueIndexRejects` | geçti |
| B3 | Başka alanda aynı anahtar kaydedilir | `SourceRepositoryTest#givenSourceInWorkspace_whenSaveSameNameKeyInOtherWorkspace_thenSaved` | geçti |
| B4 (K17, K18, AC-7) | 409 + `application/problem+json` + `SOURCE_NAME_TAKEN`, 1 kaynak/1 iş, eylem sırasında log'da ` ERROR ` ve `MERKEZ/merkez` yok (3 ad) | `SourceIntegrationTest#givenYardimMerkeziCreatedViaApi_whenCreateSourceWithTurkishUppercaseName_thenReturns409WithoutErrorLog` | geçti |
| B5 (K19) | `name_key text NOT NULL`, unique index `(workspace_id, name_key)`, eski index yok | `SchemaMigrationIntegrationTest#givenMigratedSchema_whenInspectSources_thenNameKeyNotNullAndUniqueIndexOnWorkspaceAndNameKey` | geçti |
| B6 (G1, D12) | V7 → satır ekle → V8 backfill, Java ile aynı | `NameKeyMigrationIntegrationTest#givenSourcesCreatedBeforeV8_whenMigrateToV8_thenNameKeyBackfilledLikeJava` | geçti |
| E1 (K20, K22, D10, D11) | Yenileme iş süresinden bağımsız (tarayıcı tarafı yanıt tutma) | `frontend/e2e/upload.spec.ts#givenModemGuide_whenUploadVersionAndReload_thenProgressContinuesAndBecomesReady` | 20/20 geçti |
| E-10 (K21) | Takım 10 kez | `npm run test:e2e` × 10 | 10/10 |
| S-6 | Takım yük altında 10 kez | aynı, Ollama'ya paralel embed + 4 CPU yükü | 10/10 |

Mevcut testlerde güncellenenler: `FakeEmbeddingIntegrationTest.insertSource` (`name_key` = `Source.toNameKey(name)`), `SchemaMigrationIntegrationTest` ham insert + migration sayısı `7`→`8` (planda olmayan satır) + eski `lower(` index beklentisi kaldırıldı, `SourceRepositoryTest` index adı. `ApiTestClient.Response`'a `contentType` alanı eklendi (B4 için).
Sapma: B3'te beklenen istisna `PersistenceException` değil `DataIntegrityViolationException` (repository `saveAndFlush` çağrısı Spring çevirisinden geçiyor); kök mesajda `uk_sources_workspace_id_name_key` doğrulanıyor.
TASK.md'de planda testsiz kalan kural yok. K22 (`console.log` yok): `grep -rn 'console\.' frontend/e2e` → 0.

E1 tasarımı: `page.route` ile kaynak listesi/detayı ve `ingestion-jobs/{id}` istekleri gerçek backend'e gider; iş gerçekte `DONE/FAILED` olduysa yanıt yükleme öncesi satır + `INGESTING` + 202'deki aktif iş ile değiştirilir. Yenileme sonrası `ingestion-jobs/{jobId}` isteği görülünce tutma kapanır, sonra gerçek bitiş (toast, "Hazır", "v3.2") beklenir. Süre `test.info().annotations` ('ingestion-ms') ile yazılır (list reporter göstermiyor).

## Mutation
PIT (`Source`, `Workspace` + `SourceTest`, `WorkspaceTest`): 34 mutant üretildi, 29 öldü (%85), test gücü %97; hayatta kalan yok, 4 "no coverage" (bu iki sınıfın seçili unit testleri dışında kalan, entity'nin integration testleriyle kapsanan satırlar; `toNameKey`/`addSource` kuralı değil). Stryker kurulu değil (E2E için de uygulanmaz).

## Komutlar ve sonuçları
- `cd backend && ./mvnw verify` → BUILD SUCCESS, 399 test, 0 başarısız, 0 hata; JaCoCo "All coverage checks have been met".
- `cd frontend && npm run build` → geçti; `npm run lint` → uyarı/hata yok; `npm test` → 361 test geçti.
- E2E sistemi: `docker compose -f deployment/docker-compose.yml down -v`, sonra `POSTGRES_PASSWORD=embedding-local ... up --build -d` → 3 servis healthy, seed sonrası `chunks` = 1410.
- `E2E_BASE_URL=http://localhost:3000 npm run test:e2e` 10 kez (normal): her koşu 10 passed, 0 failed, 6,7-7,7 sn, `retries: 0`.
- Aynı komut 10 kez yük altında (4 paralel `/api/embed` döngüsü + 4 `yes` CPU yükü): her koşu 10 passed, 7,4-8,1 sn.
- Not: E2E verisi büyüdü (20 koşu × 1 yeni kaynak + 302'ye sürüm); sistem temiz seed'de değil. qa Q2/Q3/Q6'dan önce `down -v` ile yeniden kurmalı.

## Çalıştırılamayanlar
Yok. Sistem tester ortamında eski proje adıyla (`deployment`) açık bırakıldı; devops `docker compose -p deployment down -v` ile kapatmalı.

## Düzeltme
Yok.

STATUS: DONE
