TASK: TASK-007
# Plan

## Özet
**Mevcut durum:** `backend/src/main` altında 133 `.java` dosyası var: 98'i bir üst seviye tip taşır, 35'i `package-info.java`'dır (tip yok, sadece `@NullMarked` + `package`). `backend/src/test` altında 59 dosya var, hepsi bir üst seviye tip taşır; testte `package-info.java` yok. Her dosyada tek üst seviye tip var (sütun 0'da tek tip bildirimi; aynı dosyada ikinci top-level tip yok). Main'de hiç yorum yok. Testte sadece metot gövdelerinde `// Arrange`, `// Act`, `// Assert` yorumları var, hiç `/* */` ya da `/** */` yok. Hiçbir dosyada CRLF ya da tab girinti yok. Proje git deposu değil.

**Yeni durum:** 157 dosyanın (98 main + 59 test) üst seviye tipinin hemen üstüne 1-2 satırlık Türkçe Javadoc eklenir. Başka hiçbir satır değişmez. `package-info.java` dosyalarına dokunulmaz (D2). Biçim ve içerik kuralları aşağıdaki "Yorum yazım kılavuzu"ndadır. İki rol (backend-coder main'i, tester testi yazar) bu kılavuzla aynı üslupta yazar.

**Değişen başka dosya:** `backend/CLAUDE.md` "Kurallar" bölümündeki "yorum yasağı" ifadesi "yorum politikası" olur (D11). Yeniden adlandırma yok.

**Bölme:** Önerilmiyor. Main ve test aynı kuralın iki yarısı; kullanıcı kapsamı main + test olarak seçti.

**Uygulama sırası:**
1. Kullanıcı onayı.
2. Lead, coder'ları başlatmadan önce başlangıç kopyasını alır (D8):
   ```
   mkdir -p scratch/TASK-007-baseline && cp -R backend/src scratch/TASK-007-baseline/src
   ```
3. backend-coder → tester → qa. Her rol bir öncekinin bitmesini bekler.
4. Kapanışta lead `scratch/TASK-007-baseline`, `scratch/TASK-007-*` dosyalarını siler.

## Çalışacak roller
- **planner:** bu plan. `docs/api/` değişmez.
- **backend-coder:** `backend/src/main` altındaki 98 dosya (`package-info.java` hariç) ve `backend/CLAUDE.md`'deki tek ifade (D11). Doğrulama: V1-V6 (main için), V8.
- **tester:** `backend/src/test` altındaki 59 dosya. Doğrulama: V1-V6 (main + test, 157 dosya), V7 (baseline'a göre), V8 (test sayısı).
- **qa:** V1-V9 ve 157 yorumun içerik kontrolü (Q1-Q3).
- **Atlanan: frontend-coder.** Frontend etkilenmiyor (TASK "Kapsam dışı").
- **Atlanan: devops.** Docker, `deployment/`, `README.md` ve `ollama.md` etkilenmiyor; yorumlar imajın davranışını değiştirmez. Bu yüzden qa, tester'a bağlanır.

## Kural eşlemesi
| # | Kural (TASK.md) | Nerede uygulanır | Hangi testle doğrulanır |
|---|-----------------|------------------|-------------------------|
| K1 | `backend/src/main` ve `backend/src/test` altındaki her `.java` dosyasının üst seviye tipi | 98 main (backend-coder) + 59 test (tester); `package-info.java` kapsam dışı (D2) | V1, V2 |
| K2 | Javadoc biçimi, tipin hemen üstünde, annotation'lardan önce | Kılavuz "Biçim" (D1) | V1, V3, V4 |
| K3 | Türkçe, en fazla 2 satır | Kılavuz "Biçim" ve "Dil" (D1, D4, D5) | V4, V6 (İngilizce sezgisel tarama), Q1 |
| K4 | Amaç söyler; alan, metot ya da adım saymaz; sınıf adını tekrar etmez | Kılavuz "İçerik" (D3, D6) | V5 (kendi adı), Q1 |
| K5 | `@author`, `@since` gibi tag yok | Kılavuz "Yasak karakterler" (D3) | V6 |
| K6 | Metot ve nested tiplere yorum yok | Sadece sütun 0'da, dosya başına tek blok | V3, V7 |
| K7 | Davranış değişmez; isim, import, satır sırası ya da biçim değişmez | Sadece blok satırları eklenir | V7, V8 |
| K8 | Yorum doğru: sınıfın gerçek işini anlatır | Yazan rol sınıfı okuyarak yazar; TASK örneğindeki yanlış cümle kullanılmaz (D7) | Q1, Q2 |
| S-1 | `SourceService.java`'nın en üstünde Türkçe amaç cümlesi | backend-coder; metin D7'deki doğru hâliyle | V1, Q2 |
| S-2 | IntelliJ'de `SourceService` üzerine gelince aynı açıklama görünür | Javadoc tipin hemen üstünde (D1) | V9 (javadoc aracı), kartta kullanıcının deneyeceği adım |
| S-3 | Test sınıfının en üstünde hangi sınıfın hangi davranışını doğruladığı | tester; kılavuz "Test sınıfları" (D6) | Q1, Q3 |
| AC-1 | Üst seviye tipinin üstünde Javadoc olmayan `.java` dosyası kalmaz | K1 | V1, V2 |
| AC-2 | Hiçbir yorum 2 satırı geçmez; hepsi Türkçe | K3 | V4, V6, Q1 |
| AC-3 | Diff'te yorum satırları dışında değişiklik yok | Baseline kopyası (D8) | V7 |
| AC-4 | `./mvnw verify` geçer, test sayısı task öncesiyle aynı | K7 | V8 |

## Gözden kaçabilecek durumlar
| # | Durum | Beklenen davranış | Karşılayan test # ya da karttaki soru # |
|---|-------|-------------------|------------------------------------------|
| G1 | 35 `package-info.java` dosyasında tip yok | Dokunulmaz; V2 bunlarda `/**` olmadığını doğrular (D2) | V2, V7 (bu dosyalar diff'te yok) |
| G2 | Yorumda Javadoc'un özel anlam verdiği karakterler: satır başında `@` (tag sayılır), `<`, `>`, `&` (HTML), `{@…}` (inline tag), `*/` (yorumu kapatır), `\u` (javac yorum içinde de unicode kaçışı işler, derleme bozulabilir) | Hiçbiri kullanılmaz (D3) | V6, V8 (derleme), V9 |
| G3 | Türkçe karakterler (ç, ğ, ı, İ, ö, ş, ü) | Dosyalar UTF-8 kalır (Spring Boot parent `project.build.sourceEncoding=UTF-8`); dosya sonundaki satır sonu korunur | V7 (`\ No newline` yok), V8, V9 (`-encoding UTF-8`) |
| G4 | Editör ya da araç dosyayı yeniden biçimlendirir (import düzenleme, boşluk) | Yasak; sadece blok satırları eklenir | V7 |
| G5 | TASK'taki `SourceService` örnek cümlesi koda uymuyor: sınıf kaynak eklemez, silmez, limit uygulamaz (ekleme `IngestionService` + `Workspace.addSource`'ta, silme endpoint'i yok) | K8 kazanır; doğru cümle yazılır (D7) | Q2 |

## Kararlar
| # | Karar | Gerekçe | Elenen alternatif ve nedeni |
|---|-------|---------|-----------------------------|
| D1 | **Biçim:** Her zaman çok satırlı blok: `/**`, 1 ya da 2 satır ` * <metin>`, ` */`. Blok sütun 0'dadır. Tipin ilk annotation satırının hemen üstüne (annotation yoksa `public class/record/...` satırının üstüne) boş satır bırakmadan eklenir. Importlardan sonraki mevcut boş satır olduğu gibi kalır; yeni boş satır eklenmez. Dosya başına eklenen satır 3 ya da 4'tür | TASK K2 ve skill örneği bu biçimde. Tek biçim otomatik kontrolü (V1, V4, V7) basitleştirir. IntelliJ ve javadoc aracı annotation'lardan önceki bloğu tipe bağlar | Tek satırlık `/** metin */`: iki ayrı biçim olur, V4/V7 desenleri ikiye çıkar |
| D2 | `package-info.java` (35 dosya) kapsam dışı | TASK K1 tip listeler (class, interface, record, enum, annotation); `package-info.java`'da tip yok. AC-1 "üst seviye tipinin üstünde Javadoc olmayan dosya" der; tipi olmayan dosya buna girmez. Paket Javadoc'u ayrı bir konudur | Paket Javadoc'u eklemek: kapsam dışı ve skill bölüm 2 sadece tipten söz eder |
| D3 | **Yasak:** Bütün tag'ler (`@author`, `@since`, `@see`…), inline tag'ler (`{@link}`, `{@code}`), HTML, `@`, `<`, `>`, `&`, `{`, `}`, `\u`, `*/` metin içinde kullanılmaz. Sınıfın kendi adı yorumda geçmez. Main sınıflarında kod adı (sınıf, metot, alan, ayar anahtarı) kullanılmaz; Türkçe alan kelimeleri kullanılır. Tek istisna test ve test destek sınıflarıdır: test edilen sınıfın adı düz metin olarak ve kesme işaretiyle eklenen Türkçe ekle yazılabilir (`SourceService'in`) | K4, K5, G2. S-3 testin "hangi sınıfı" doğruladığını söylemesini istiyor; düz ad bunu en net söyler | `{@link SourceService}`: tag yasağı (K5). Testte de adsız anlatım: "kaynak servisinin" belirsiz kalır (birden fazla servis kaynakla ilgileniyor) |
| D4 | **Dil ve üslup:** Geniş zaman, üçüncü tekil kişi, fiille biten cümle ("…yürütür.", "…taşır.", "…doğrular."). Nokta ile biter. İki fikir varsa tek cümlede `;` ile birleşir. Satır, ` * ` öneki dahil en fazla 120 karakter. İkinci satır sadece birinci sığmıyorsa kullanılır | Kısa ve tek üslup; paralel yazan iki rol ayrışmaz. 120 karakter mevcut kodun en uzun satırlarıyla uyumlu | Satır sınırı koymamak: tek satıra çok uzun cümle sığdırılıp K3'ün amacı (kısa) delinir |
| D5 | **Terimler:** workspace → çalışma alanı, source → kaynak, chunk → parça, embedding → embedding, ingestion job → içe alma işi, ingestion stage → içe alma aşaması, search event → arama kaydı, query → arama metni, search mode → arama modu, score → skor, distance → mesafe, keyword → kelime araması, hybrid → hibrit arama, rank fusion → sıra birleştirme (RRF). Olduğu gibi kalan teknik terimler: embedding, pgvector, PostgreSQL, HNSW, RRF, Ollama, REST, HTTP, JSON, PDF, CSV, Markdown, Flyway, Testcontainers, Problem Details, Spring, OpenAPI, Swagger | `frontend-conventions` bölüm 2 ve `backend-conventions` sözlüğüyle aynı; UI'da ve rehberde geçen kelimeler | Her rolün kendi çevirisi: "chunk" bir yerde "parça", bir yerde "bölüm" olur |
| D6 | **İçerik kalıpları** (zorunlu değil, üslup rehberi; aşağıdaki "Yorum yazım kılavuzu" tablosu). Test sınıfı yorumu iki şeyi söyler: hangi sınıfı ve hangi davranışı doğruladığını; entegrasyon testinde neyle (gerçek PostgreSQL/pgvector, sahte embedding istemcisi) çalıştığını. Test destek sınıfları (fixture, sahte istemci, taban sınıf, yapılandırma) testlerde ne için kullanıldığını söyler | S-3 | Kalıpsız serbest yazım: 157 yorumda üslup dağılır |
| D7 | `SourceService` için TASK'taki örnek cümle kullanılmaz; sınıfın gerçek işi birebir şu cümleyle yazılır: "Çalışma alanındaki kaynakları sayfa sayfa listeler ve tek bir kaynağı en son içe alma işiyle birlikte getirir." | K8 (doğruluk) örnek senaryodan önce gelir: sınıfta ekleme, silme ve limit yok (G5). Kart bunu kullanıcıya açıkça yazar | Örneği aynen yazmak: K8'i ve kabul kriterinin ruhunu bozar |
| D8 | **Başlangıç kopyası:** Lead, onaydan sonra ve backend-coder'ı başlatmadan önce `backend/src`'yi `scratch/TASK-007-baseline/src`'ye kopyalar. `scratch/` kök `.gitignore`'da ("Geçici"). qa ve tester V7'yi bu kopyaya karşı çalıştırır. Kapanışta lead siler | Proje git deposu değil (TASK-006 qa da diff yapamadı). AC-3 ancak önce/sonra karşılaştırmasıyla kanıtlanır. Kopyayı hiçbir şeyi değiştirmeyen tarafsız bir anda lead alır; hiçbir rolün yazma alanına girmez | `git init`: depoyu kurmak kullanıcının kararı, kök dosyalara dokunur. backend-coder'ın kopyalaması: `scratch/` onun yazma alanında değil. Scratchpad: oturuma özel, diğer rollerin oturumundan görünmeyebilir |
| D9 | Javadoc'u otomatik denetleyen yeni bir JUnit testi yazılmaz; doğrulama V1-V9 komutlarıyla yapılır | AC-4 test sayısının aynı kalmasını istiyor; yeni test sayıyı değiştirir. Kaynak dosyalarını tarayan test ayrıca dependency (ArchUnit vb.) ya da elle parser gerektirir | ArchUnit kuralı: yeni dependency + test sayısı değişir |
| D10 | **Test sayısı referansı 417'dir** (TASK-006 `03-test-report.md` ve `05-qa-report.md`: `./mvnw verify` → 417 test). backend-coder'ın `verify` koşusu test dosyaları değişmeden önce yapılır; onun sayısı ilk ölçümdür. 417'den farklıysa backend-coder bunu raporlar ve o sayı referans olur (main'de sadece yorum değiştiği V7 ile kanıtlanır). Ek olarak test annotation'larının statik sayısı baseline ile aynı olmalı (V8) | "Task öncesi" sayısı ölçülmüş bir değere dayanmalı. Yorum test sayısını değiştiremez; iki ölçüm (koşu sayısı + statik sayım) birbirini doğrular | Baseline kopyasında ayrıca `verify` koşmak: 5+ dakika Testcontainers, aynı bilgiyi verir |
| D11 | backend-coder `backend/CLAUDE.md` satır 79'daki "dil, yorum yasağı, isimlendirme" ifadesini "dil, yorum politikası (her üst seviye tipte Türkçe amaç Javadoc'u, metot yorumu yok), isimlendirme" yapar. Başka satır değişmez | Skill güncellendi; eski ifade gelecekteki rolleri yanıltır ve bu task'ın kuralıyla doğrudan çelişir. Dosya backend-coder'ın önceki task'larda güncellediği dosya | Dokunmamak: backend modülünün kendi notu yeni kuralla çelişir |
| D12 | Sıra backend-coder → tester → qa; tester backend-coder ile paralel çalışmaz | Kök `CLAUDE.md` tablosu: tester 3a'ya bağlı. tester'ın `verify` ve V7 koşusu main'deki son hâli kapsamalı | Paralel: tester'ın ölçümü main değişikliklerinden önce kalabilir |

## Sözleşme değişiklikleri
Yok. `docs/api/openapi.yaml` ve `docs/api/websocket.md` değişmez. Projede `therapi-runtime-javadoc` yok; springdoc Javadoc okumaz, `/v3/api-docs` çıktısı değişmez (`OpenApiDocumentationIntegrationTest` bunu zaten doğrular).

## Backend planı
Yüklenecek skill: `backend-conventions` (bölüm 1 ve 2 güncel). Yeni dependency ya da plugin yok; `pom.xml` değişmez.

### Dosyalar
- Hedef liste: `find backend/src/main -name '*.java' ! -name package-info.java | sort` → 98 dosya. Hepsine dokunulur, başka dosyaya dokunulmaz (D11 hariç).
- Her dosyada tek değişiklik: D1'deki blok, tipin ilk annotation satırının (yoksa tip satırının) hemen üstüne. Örnek yerleşim (`SourceService`):
  ```
  import org.springframework.transaction.annotation.Transactional;

  /**
   * Çalışma alanındaki kaynakları sayfa sayfa listeler ve tek bir kaynağı en son içe alma işiyle birlikte getirir.
   */
  @Service
  @Transactional(readOnly = true)
  public class SourceService {
  ```
- Yorumu yazmadan önce sınıfın tamamı okunur; yorum sadece sınıfın yaptığını söyler (K8). Başka sınıfa ait iş (ör. limit kontrolü `Workspace`'te) o sınıfın yorumuna yazılmaz.
- Nested tipler (ör. record içinde record, enum içinde enum) yorum almaz.
- Dosyalar Edit aracıyla değiştirilir; IDE ile biçimlendirme ya da import düzenleme yapılmaz (G4).

### Rapor (`handoff/02-backend-implementation.md`)
- Dokunulan dosya sayısı (98) ve V1-V6'nın main için çıktısı (her biri boş ya da beklenen sayı).
- `cd backend && ./mvnw verify` sonucu: BUILD SUCCESS ve surefire özet satırı (`Tests run: N, Failures: 0, Errors: 0, Skipped: 0`), D10'a göre N.
- `backend/CLAUDE.md`'de değişen satırın önceki ve sonraki hâli.
- Yorumu yazarken emin olamadığı sınıflar varsa listesi (dosya + neden); qa Q1'de öncelikle bunlara bakar.

## Frontend planı
Yok (frontend-coder atlanır).

## Yorum yazım kılavuzu
backend-coder ve tester aynı kılavuzu izler. Biçim D1, yasaklar D3, üslup D4, terimler D5.

| Tip türü | Kalıp (yol gösterir, kelimesi kelimesine değil) | Örnek (gerçek sınıf, doğrulanmış) |
|----------|--------------------------------------------------|-----------------------------------|
| Service | Hangi iş akışını yürüttüğü; hangi kuralı uyguladığı | `SourceService`: D7'deki cümle |
| Uygulama olayı / açılış işi | Ne zaman çalıştığı ve ne yaptığı | `IngestionRecovery`: "Uygulama açılırken yarıda kalmış içe alma işlerini bulur ve kesintiye uğradı hatasıyla kapatır." |
| Entity | Hangi kaydı temsil ettiği; kendi içinde uyguladığı kural | `Workspace`: "Bir çalışma alanını ve kaynaklarını temsil eder; kaynak eklerken kaynak sınırını ve ad tekilliğini uygular." |
| Enum | Neyin olası değerlerini belirttiği (değerler sayılmaz) | `SourceKind`: "Bir kaynağın hangi dosya ya da içerik türünden oluştuğunu belirtir." |
| Annotation | Hangi doğrulamayı ya da işaretlemeyi tanımladığı | `NotEmptyFile`: "Yüklenen dosyanın eksik ya da boş olmamasını isteyen doğrulama kuralını tanımlar." |
| Controller | Hangi işler için REST uçları sunduğu | (yazan rol) "… için REST uçlarını sunar." |
| Repository / projection | Hangi veriyi okuyup yazdığı; projection'da hangi sorgunun satırını taşıdığı | (yazan rol) |
| Request/Response record | Hangi isteğin ya da yanıtın gövdesini taşıdığı | (yazan rol) |
| Config / Properties | Neyi yapılandırdığı ya da hangi ayarları okuduğu | (yazan rol) |
| Exception | Hangi HTTP durumuna dönüşen hataları taşıdığı | (yazan rol) |
| Unit test | Test edilen sınıf + davranış + sahte bağımlılık | `SourceServiceTest`: "SourceService'in kaynak listeleme ve tek kaynak getirme davranışını sahte depolarla doğrular." |
| Entegrasyon testi | Test edilen akış + gerçek PostgreSQL/pgvector, gerekiyorsa sahte embedding istemcisi | (tester) |
| Test taban sınıfı | Hangi testlerin ortak zemini olduğu ve ne hazırladığı | `PostgresIntegrationTest`: "Gerçek PostgreSQL ile çalışan entegrasyon testlerinin ortak tabanıdır; her testten önce ve sonra tabloları boşaltır." |
| Sahte istemci / fixture | Testlerde neyin yerine ya da ne için kullanıldığı | `FakeEmbeddingClient`: "Testlerde gerçek model yerine aynı metne hep aynı vektörü döndürür; hata ve bekletme durumu kurulabilir." |

## Test senaryoları
Bu task'ta yeni otomatik test yazılmaz (D9). Senaryolar, aşağıdaki "Doğrulama komutları"ndaki V1-V9 ve qa'nın elle yaptığı Q1-Q3'tür. Komutlar repo kökünde çalıştırılır (V8 ve V9 `backend/` içinde). "Boş" beklenen komut hiçbir satır yazmamalıdır.

| # | Senaryo | Katman | Test adı (given_when_then) | Beklenen sonuç (somut değerle) |
|---|---------|--------|----------------------------|--------------------------------|
| V1 | Javadoc eksik ya da yanlış yerde dosya yok (K1, K2, AC-1) | statik kontrol (komut) | `givenTypeFiles_whenCheckLineAboveFirstDeclaration_thenEveryFileHasJavadocEnd` | Çıktı boş |
| V2 | Kapsam sayıları (K1, G1) | statik kontrol | `givenBackendSources_whenCountFilesWithJavadoc_then157AndNoPackageInfo` | main tip dosyası 98, test 59; `^/\*\*$` içeren dosya 157; `package-info.java`'da `/**` içeren dosya listesi boş |
| V3 | Dosya başına tek blok, sadece sütun 0'da (K2, K6) | statik kontrol | `givenBackendSources_whenCountJavadocBlocks_thenOnePerTypeFileAndNoneIndented` | İki komutun çıktısı da boş |
| V4 | Blok biçimi ve uzunluğu (K2, K3, AC-2) | statik kontrol | `givenJavadocBlocks_whenCheckShape_thenOneOrTwoTextLinesWithin120Chars` | İki komutun çıktısı da boş |
| V5 | Yorumda sınıfın kendi adı yok (K4) | statik kontrol | `givenJavadocBlocks_whenSearchOwnTypeName_thenNotFound` | Çıktı boş |
| V6 | Yasak karakter yok; İngilizce kelime taraması (K3, K5, G2) | statik kontrol | `givenJavadocTextLines_whenSearchForbiddenCharsAndEnglishWords_thenNoneFound` | Yasak karakter komutu boş. İngilizce kelime komutu boş; satır çıkarsa qa elle bakar (D5'teki teknik terimler kabul) |
| V7 | Baseline'a göre sadece blok satırları eklenmiş (K6, K7, AC-3, G1, G3, G4) | statik kontrol (diff) | `givenBaselineCopy_whenDiffBackendSources_thenOnlyJavadocLinesAdded` | `diff -r` satırı 157; ekleme hunk'ı 157; değişiklik/silme hunk'ı, `<` satırı, `Only in`, `\ No newline` yok; `>` satırları sadece blok satırı; `>` satır sayısı 471-628 arası (157×3 … 157×4) |
| V8 | `verify` geçer, test sayısı aynı (K7, AC-4) | build | `givenCommentOnlyChanges_whenMavenVerify_thenBuildSucceedsWith417Tests` | BUILD SUCCESS; `Tests run: 417, Failures: 0, Errors: 0, Skipped: 0` (ya da D10'a göre backend-coder'ın ölçtüğü sayı); JaCoCo "All coverage checks have been met"; statik test annotation sayısı baseline ile aynı |
| V9 | Javadoc aracı açıklamayı tipe bağlar (S-2, G2, G3) | build (javadoc aracı) | `givenMainSources_whenRunJavadocTool_thenSourceServicePageShowsDescription` | `javadoc` çıkış kodu 0; `SourceService.html`'de D7 cümlesinin "Çalışma alanındaki kaynakları sayfa sayfa listeler" parçası 1 kez ya da daha fazla geçer |
| Q1 | 157 yorumun içerik kontrolü (K3, K4, K8, AC-2) | qa elle | `givenEveryCommentedType_whenReadCommentAgainstBody_thenClaimsMatchBehavior` | 157 dosyanın her biri okunur: (a) yorumdaki her fiil sınıfta karşılığı olan bir işi anlatır, (b) alan/metot listesi yok, (c) Türkçe, D5 terimleri, (d) test yorumu test edilen sınıfı ve davranışı söyler. Rapor: kontrol edilen sayı 157, uyumsuzluk listesi (dosya, yorum, gerçek iş, önerilen metin) |
| Q2 | `SourceService` yorumu (S-1, G5, D7) | qa elle | `givenSourceService_whenOpenFile_thenAccurateTurkishPurposeAtTop` | Yorum D7 cümlesiyle birebir aynı (kart bu cümleyi kullanıcıya gösterdi, V9 bu metni arar); "ekleme", "silme", "limit" iddiası yok |
| Q3 | Örneklem tablosu (S-3, K8) | qa elle | `givenOneTypePerKind_whenCompareCommentAndBehavior_thenTableInReport` | Raporda 15 satırlık tablo: kılavuzdaki her tip türünden en az bir dosya (controller, service, repository, projection, entity, enum, annotation, request/response record, config, exception, parser, unit test, entegrasyon testi, controller testi, fixture ya da sahte istemci); sütunlar: dosya, yorum, sınıfın gerçek işi (bir cümle), uyumlu mu |

### Doğrulama komutları
**V1**
```
find backend/src -name '*.java' ! -name package-info.java | sort | while read -r f; do
  awk '/^(@|public |protected |final |abstract |sealed |non-sealed |class |record |interface |enum )/ && !found {found=1; if (prev != " */") print FILENAME} {prev=$0}' "$f"
done
```
**V2**
```
find backend/src/main -name '*.java' ! -name package-info.java | wc -l     # 98
find backend/src/test -name '*.java' ! -name package-info.java | wc -l     # 59
grep -rl --include='*.java' '^/\*\*$' backend/src | wc -l                  # 157
grep -rl --include='package-info.java' '/\*\*' backend/src                 # boş
```
**V3**
```
grep -rc --include='*.java' '/\*\*' backend/src | grep -v -e ':1$' -e 'package-info.java:0$'   # boş
grep -rnE --include='*.java' '^[[:space:]]+/\*\*' backend/src                                   # boş
```
**V4**
```
find backend/src -name '*.java' -print0 | xargs -0 awk '
  /^\/\*\*$/ {inb=1; n=0; next}
  inb && /^ \*\/$/ {inb=0; if (n<1 || n>2) print FILENAME ": " n " satir"; next}
  inb {n++; if ($0 !~ /^ \* [^ ]/) print FILENAME ": bicim: " $0}'                           # boş
LC_ALL=en_US.UTF-8 grep -rnE --include='*.java' '^ \* .{118,}' backend/src                     # boş
```
**V5**
```
find backend/src -name '*.java' ! -name package-info.java | while read -r f; do
  name=$(basename "$f" .java)
  sed -n '/^\/\*\*$/,/^ \*\/$/p' "$f" | grep -qw -- "$name" && echo "$f"
done                                                                                             # boş
```
**V6**
```
grep -rhE --include='*.java' '^ \* ' backend/src | grep -E '[@<>&{}]|\\u|\*/'                  # boş
grep -rhE --include='*.java' '^ \* ' backend/src | grep -iwE 'the|and|for|with|returns|this|of' # boş (çıkarsa qa bakar)
```
**V7** (D8'deki kopya gerekir)
```
diff -r -x .DS_Store scratch/TASK-007-baseline/src backend/src > scratch/TASK-007-diff.txt
grep -c '^diff -r' scratch/TASK-007-diff.txt                                   # 157
grep -cE '^[0-9]+a[0-9]+(,[0-9]+)?$' scratch/TASK-007-diff.txt                 # 157
grep -E '^[0-9]+(,[0-9]+)?[cd][0-9]' scratch/TASK-007-diff.txt                 # boş
grep -E '^(<|Only in|\\ No newline)' scratch/TASK-007-diff.txt                 # boş
grep '^>' scratch/TASK-007-diff.txt | grep -vE '^> (/\*\*| \* .+| \*/)$'       # boş
grep -c '^>' scratch/TASK-007-diff.txt                                         # 471-628
```
**V8** (`backend/` içinde)
```
./mvnw verify                                                                  # BUILD SUCCESS, Tests run: 417 …
grep -rhowE '@(Test|ParameterizedTest|RepeatedTest|TestFactory|TestTemplate)' ../scratch/TASK-007-baseline/src/test | wc -l
grep -rhowE '@(Test|ParameterizedTest|RepeatedTest|TestFactory|TestTemplate)' src/test | wc -l          # ikisi eşit
```
**V9** (`backend/` içinde)
```
./mvnw -q dependency:build-classpath -Dmdep.outputFile=../scratch/TASK-007-classpath.txt
javadoc -quiet -Xdoclint:none -encoding UTF-8 -docencoding UTF-8 -d ../scratch/TASK-007-javadoc \
  -cp "$(cat ../scratch/TASK-007-classpath.txt)" -sourcepath src/main/java -subpackages com.embedding.pgvector
grep -c 'Çalışma alanındaki kaynakları sayfa sayfa listeler' ../scratch/TASK-007-javadoc/com/embedding/pgvector/source/service/SourceService.html   # ≥ 1
```
`maven-dependency-plugin` zaten `pom.xml`'de; `build-classpath` goal'ü komut satırından çağrılır, `pom.xml` değişmez. `javadoc`, `./mvnw`'nin kullandığı JDK 26'nın aracıdır (`$JAVA_HOME/bin/javadoc`).

### Rol bazında
- **backend-coder:** V1 ve V3-V6'yı `backend/src` yerine `backend/src/main` yoluyla çalıştırır (beklenen: boş). V2'de main satırı 98, `grep -rl --include='*.java' '^/\*\*$' backend/src/main | wc -l` → 98, `package-info` satırı boş. V8'in `./mvnw verify` kısmı. V7'yi çalıştırmaz (test dosyaları henüz değişmedi; kopya karşılaştırmasını tester ve qa yapar).
- **tester:** 59 test dosyasına yorumları yazar, sonra V1-V8'i tam hâliyle (157 dosya) çalıştırır. Test kodunda yorum dışı hiçbir değişiklik yapmaz. `03-test-report.md`'ye her komutun çıktısını ve surefire özetini yazar. Main dosyalarında kural ihlali görürse düzeltmez; `NEEDS_FIX`, sorumlu backend-coder.
- **qa:** V1-V9'u kendisi yeniden çalıştırır, Q1-Q3'ü yapar. `backend-conventions` bölüm 2 geçerlidir; diğer skill'lerde kalan eski "sınıf üstüne yorum yok" ifadeleri (bkz. "Açık sorular ve riskler") ihlal sayılmaz. Q1'de uyumsuz yorum varsa `NEEDS_FIX`: main → backend-coder, test → tester; maddelerde dosya ve önerilen metin.

## Deployment planı
Yok. İmaj, port, env değişkeni değişmez; devops atlanır.

## Açık sorular ve riskler
- **Kullanıcıya soru yok.** Belirsiz noktalar kararlarda: `package-info.java` hariç (D2), TASK'taki `SourceService` örneği yerine doğru cümle (D7), test yorumunda test edilen sınıfın adı (D3).
- **Kapsam dışı not (lead/kullanıcı):** Bazı skill dosyalarında eski kural duruyor ve qa'yı yanıltabilir: `.claude/skills/java-code-review/SKILL.md` satır 21 ("yorum yok"), `.claude/skills/code-review-and-quality/SKILL.md` satır 54 ("metot/sınıf/component üstüne yorum yok"), `.claude/skills/spring-boot-openapi-documentation/SKILL.md` satır 167 ("projeye yazılan kodda yorum İngilizcedir"), `.claude/skills/spring-boot-openapi-documentation/references/dependency-setup.md` satır 15 ("projede metot/sınıf üstüne Javadoc yazılmaz"). Hepsi "`backend-conventions` kazanır" der, ama metinler çelişiyor. `.claude/**` hiçbir rolün değil; lead kullanıcıya sorup günceller.
- **Kapsam dışı not:** `backend-conventions` bölüm 2'deki örnek (`SourceService` için "Kaynak ekleme, listeleme ve silme…") koda uymuyor (G5). Örnek olarak durması zararsız, ama doğru bir sınıf örneğiyle değiştirilmesi iyi olur (lead/kullanıcı).
- **Risk:** 157 yorumun doğruluğu elle kontrol edilir (Q1). Yanlış ama makul görünen bir cümle gözden kaçabilir. Bunu azaltmak için yazan rol sınıfın tamamını okur, emin olamadığı dosyaları raporuna yazar; qa Q1'de önce bunlara bakar.
- **Risk:** V7 baseline kopyasına bağlıdır. Lead kopyayı backend-coder başlamadan almazsa AC-3 kanıtlanamaz; o durumda lead coder'ı başlatmadan önce kopyayı alır.

STATUS: DONE
