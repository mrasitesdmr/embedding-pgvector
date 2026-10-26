# TASK-002: Lab'ın backend ve PostgreSQL (pgvector) tarafı: kaynak yükleme, parçalama, embed etme ve arama

## Ne isteniyor
`lab.html`'deki (RAG Retrieval Lab) arka plan akışı gerçek bir backend ve PostgreSQL + pgvector üzerinde çalışacak. Lab'da simüle edilen her şey burada gerçekten yapılır:
- **Yazma anı:** bir bilgi kaynağı (dosya) yüklenir; backend dosyayı okur, parçalara böler, parçaları lokal modelle (Ollama, `bge-m3`, TASK-001) toplu olarak embed eder ve eski parçaların yerine tek transaction'da yazar. İlerleme iş durumundan izlenir.
- **Okuma anı:** arama metni aynı modelle embed edilir. Kelime (PostgreSQL full-text search, `turkish`), vektör (pgvector, HNSW) ya da ikisinin RRF ile birleştiği hybrid arama yapılır. Her arama kaydedilir.

Lab'daki tablolar (çalışma alanı, kaynaklar, parçalar, içe alma işleri, arama kayıtları), index'ler (HNSW, GIN, tenant) ve sorgular (SQL ve `SET LOCAL hnsw.*` ayarları) lab'daki "Kod", "Veritabanı", "Index" ve "Sıralama" sekmelerinde görünen davranışla aynı olur. Lab'da "bu serviste YOK" diye gösterilen model uyuşmazlığı kontrolü burada **vardır**.

Bu task'ta arayüz yok. Her şey REST üzerinden (curl, Swagger UI) denenir. Arayüz sonraki task'ta bu API'nin üzerine kurulur.

## Kurallar

### Çalışma alanı
1. Her kaynak, parça, içe alma işi ve arama kaydı bir çalışma alanına (workspace) aittir. Bütün okuma ve yazma işlemleri çalışma alanıyla sınırlıdır. Bir çalışma alanında yapılan arama başka bir alanın parçasını hiçbir modda döndürmez.
2. Çalışma alanları listelenebilir ve tek tek görüntülenebilir: adı, kaynak sayısı, parça sayısı. Çalışma alanı oluşturma ve silme bu task'ta yok; alanlar başlangıç verisiyle gelir (Kural 22).
3. Olmayan bir çalışma alanına, kaynağa ya da işe yapılan istek ayrı ve sabit bir `errorCode` ile "bulunamadı" hatası döner. Başka bir çalışma alanının kaynağına kendi çalışma alanı üzerinden erişmek de "bulunamadı" sayılır.

### Kaynaklar ve yükleme
4. Kaynak listesi her kaynak için şunları döndürür: adı, türü, sürümü, parça sayısı, durumu (`INGESTING`, `READY`, `FAILED`), son senkron zamanı ve dosya adı.
5. Yüklenebilen dosya türleri: **PDF**, **TXT / Markdown** ve **CSV**. Başka bir tür reddedilir ve sabit bir `errorCode` ile hata döner. Tür, dosya uzantısına ve içeriğine göre belirlenir; uzantısı `.pdf` olup PDF olmayan dosya da reddedilir.
6. Bir dosya en fazla **20 MB** olabilir. Daha büyük dosya reddedilir. Boş dosya da reddedilir.
7. İki yükleme işlemi vardır: (a) yeni kaynak oluşturma (ad + dosya), (b) mevcut bir kaynağa yeni sürüm yükleme (dosya + isteğe bağlı sürüm etiketi, ör. `3.2`). İkisi de işi beklemeden **202 Accepted** ile iş numarasını ve iş durumunu sorgulama adresini döner. Asıl iş arka planda yapılır.
8. Bir kaynağın işi bitmeden aynı kaynağa ikinci bir sürüm yüklenirse istek reddedilir (409, sabit `errorCode`). Farklı kaynaklar aynı anda içe alınabilir.

### İçe alma işi (yazma anı)
9. İş şu aşamalardan geçer: `QUEUED` → `PARSING` → `CHUNKING` → `EMBEDDING` → `WRITING` → `DONE`. Hata olursa `FAILED` olur. İş durumu sorgulanınca aşama, ilerleme (0–1), embed edilen ve toplam parça sayısı, başlangıç ve bitiş zamanı döner. Hata varsa sabit bir hata kodu da döner.
10. Parçalama:
    - PDF sayfa sayfa okunur. Parçanın başlığı sayfa numarasını içerir (lab'daki gibi `s. 31 · Modemi yeni adreste kurma`).
    - TXT / Markdown'da başlık satırları (Markdown `#` başlıkları) parçanın başlığı olur.
    - CSV'de her satır bir parçadır. Başlık ilk sütundan, içerik sütun adlarıyla birlikte satırın tamamından oluşur.
    - Parça hedefi lab'daki gibi yaklaşık **400 token**'dır. Planner bunun karakter karşılığını ve parçalar arası örtüşmeyi belirler. Hiçbir parça boş olmaz ve modelin sınırını aşmaz.
    - Her parçanın sıra numarası (`chunk_no`, 0'dan başlar), başlığı, içeriği ve yaklaşık token sayısı saklanır.
11. Parçalar Ollama'ya tek tek değil, **en fazla 96'lık gruplar** halinde toplu istekle embed edilir. Her grup bitince iş ilerlemesi güncellenir (lab: 214 parça → 3 istek).
12. Eski sürümün parçalarının silinmesi, yeni parçaların yazılması ve kaynağın güncellenmesi (sürüm, parça sayısı, `READY`, senkron zamanı) **tek transaction**'da yapılır. Commit olana kadar aramalar eski parçaları görür. İş herhangi bir aşamada başarısız olursa eski parçalar ve kaynağın eski sürümü olduğu gibi kalır.
13. Her parçaya onu üreten modelin etiketi yazılır (`embedding_model`, ör. `bge-m3@1024`).
14. Dosyadan hiç metin çıkmazsa (ör. sadece görselden oluşan PDF) ya da Ollama'ya ulaşılamazsa iş `FAILED` olur ve nedeni sabit bir kodla iş durumunda görünür. Backend çökmez. Yeni oluşturulan bir kaynağın ilk işi başarısız olursa kaynak `FAILED` durumunda kalır. Var olan bir kaynağın yeni sürümü başarısız olursa kaynak eski sürümüyle `READY` kalır.
15. Kaynağın parçaları sayfalı olarak listelenebilir. `chunk_no` sırasıyla id, sıra numarası, başlık, içerik, token sayısı ve model etiketi döner.

### Arama (okuma anı)
16. Arama parametreleri, varsayılanlarıyla:
    - **Arama metni:** başındaki ve sonundaki boşluklar kırpılır. Boş olamaz, en fazla 500 karakter olabilir.
    - **Mod:** `keyword`, `vector`, `hybrid`. Varsayılan `hybrid`.
    - **Filtre stratejisi:** `post_filter` (`hnsw.iterative_scan = off`) ya da `iterative_scan` (`relaxed_order`). Varsayılan `iterative_scan`.
    - **`ef_search`:** varsayılan 40, en az 10, en fazla 1000.
    - **Sonuç sayısı:** varsayılan 10, en fazla 50.
    - **Kanal** (isteğe bağlı): arama kaydına yazılır, ör. `Yardım merkezi`, `Temsilci paneli`.
    
    Varsayılanlar koda gömülmez, yapılandırmadan değiştirilebilir. Geçersiz parametre (`VALIDATION_FAILED`) hatası döner.
17. Kelime kolu PostgreSQL full-text search ile çalışır: `turkish` yapılandırması, `plainto_tsquery` (sorgudaki bütün kökler parçada geçmeli), GIN index ve `ts_rank_cd` sıralaması. Başlık ve içerik birlikte aranır. Bu kolda embedding çağrısı yapılmaz.
18. Vektör kolu, arama metnini parçalarla aynı modelle embed eder. pgvector cosine mesafesi (`<=>`), HNSW index ve çalışma alanı filtresiyle en yakın parçaları bulur. `ef_search` ve filtre stratejisi sadece o sorgunun transaction'ında (`SET LOCAL`) geçerlidir, başka sorguları etkilemez. Filtre sonrası istenen sayıda parça kalmazsa sorgu hata vermez, kısa liste döner ve bu durum yanıtta ve logda (WARN) belirtilir.
19. Hybrid modda iki kol aynı SQL'de çalışır ve sıralarına göre RRF ile birleşir (`1 / (60 + sıra)`, k = 60 yapılandırmadan değişir; sadece bir listede olan parça o listenin puanını alır). Kelime listesi boşsa sonuç vektör listesinin aynısıdır.
20. Arama yanıtı şunları içerir:
    - **Genel:** kırpılmış sorgu, mod, filtre stratejisi, `ef_search`, istenen ve dönen sonuç sayısı, kelime kolunun ve vektör kolunun bulduğu satır sayısı, vektör kolunun kısa kalıp kalmadığı.
    - **Süreler:** embedding süresi, SQL süresi ve toplam süre (ms).
    - **Her sonuç için:** sıra, parça id'si, kaynak id'si ve adı, başlık, içerik, hangi kol(lar)ın bulduğu (`keyword`, `semantic`), kelime kolundaki sırası ve `ts_rank_cd` puanı, vektör kolundaki sırası ve cosine mesafesi, hybrid'de RRF puanı. Kelime kolunun bulduğu parçada sorgu kökleriyle eşleşen kelimelerin konumları da döner (lab'daki vurgulama). HTML döndürülmez; vurgulamayı arayüz yapar.
    - **İsteğe bağlı plan:** istenirse vektör kolunun sorgu planı (`EXPLAIN (ANALYZE, BUFFERS)` çıktısı) da döner. Plandan HNSW index'inin kullanıldığı ve filtreyle elenen satır sayısı görülür (lab'daki "Index" sekmesi).
21. **Model koruması:** Sorgu modeli (yapılandırmadaki model ve vektör boyutu), çalışma alanındaki parçaların `embedding_model` etiketiyle uyuşmazsa vector ve hybrid arama yapılmaz. Sabit bir `errorCode` ile hata döner; keyword arama çalışmaya devam eder. Aynı uyuşmazlıkta o çalışma alanına yükleme de reddedilir; farklı modellerin vektörleri aynı alana karışmaz. Uygulama açılışında uyuşmazlık varsa WARN log yazılır, uygulama yine açılır.

### Başlangıç verisi
22. İlk açılışta veritabanı boşsa başlangıç verisi yüklenir. Bu işlem bir kez yapılır; sonraki açılışlarda tekrarlanmaz, var olan veriyi bozmaz.
    - **Dalga Fiber çalışma alanı** (lab'daki gibi id `42`): lab'daki 4 kaynak (Yardım merkezi · Web sitesi, Dalga T5 modem kullanım kılavuzu · PDF v3.1, Modem hata kodları tablosu · CSV, Çözülmüş destek talepleri · Talep geçmişi) ve lab'daki 42 parçanın başlık ve metinleri (`lab.html` → `CH`), kaynakları ve sıra numaralarıyla. Parça sayıları gerçek satır sayısıdır; lab'daki 742, 11.830 gibi sayılar değildir.
    - **Diğer 36 çalışma alanı** (lab'daki Kıyı Net, Nar Mobil, Lodos Enerji vb. ve diğerleri): müşteri hizmetleri tarzında, lab'daki "foreign" başlıklar gibi benzer konulara (parola sıfırlama, hata kodları, adres değişikliği vb.) değinen parçalar. Dalga Fiber'in parçaları tüm parçaların yaklaşık **%3**'ü olur.
    - Bütün başlangıç parçaları gerçekten `bge-m3` ile embed edilir; rastgele vektör kullanılmaz.
    - Ollama kapalıysa backend yine açılır, eksik başlangıç verisini WARN ile loglar ve sonraki açılışta tamamlar.
23. Lab'daki 3 önerilen arama başlangıç verisiyle denenebilir:
    - "Şifremi unuttum, online işlemlere giremiyorum"
    - "TS-999 hata kodu"
    - "Taşınıyorum, internetimi yeni eve nasıl aldırırım?"

### Arama kayıtları
24. Sonuçsuz olanlar dahil her arama kaydedilir: zaman, kanal, sorgu, mod, filtre stratejisi, dönen sonuç sayısı, ilk sonucun parça id'si, toplam süre. Kayıtlar çalışma alanı bazında, yeniden eskiye sayfalı listelenir (varsayılan sayfa boyutu 20). Listede ilk sonucun başlığı da döner.
25. Arama kaydı yazılamazsa arama yanıtı yine döner; hata loglanır.

### Genel
26. Veritabanı şeması Flyway migration'larıyla kurulur. `vector` eklentisi migration'da oluşturulur. Vektör kolonu `vector(1024)` olur. Index'ler: HNSW (`vector_cosine_ops`), `tsvector` üzerinde GIN, çalışma alanı kolonları üzerinde B-tree. `tsvector` kolonu başlık + içerikten otomatik üretilir (generated column).
27. Bütün hatalar RFC 9457 Problem Details ve sabit `errorCode` ile döner (TASK-001 kuralı). Hata detayına Ollama'nın, PostgreSQL'in ya da dosyanın içeriği kopyalanmaz.
28. Loglar lab'daki "Loglar" sekmesindeki olayları kapsar: iş başladı/aşama/grup embed edildi/bitti (süre ve parça sayılarıyla), arama özeti (mod, kol sonuç sayıları, `ef_search`, filtre, süre), kısa vektör kolu WARN, sıfır sonuç WARN, model uyuşmazlığı WARN. Arama metninin, parça içeriğinin ve vektörlerin tamamı loga yazılmaz.
29. TASK-001'deki `POST /api/embeddings` olduğu gibi çalışmaya devam eder.
30. Docker: `docker compose -f deployment/docker-compose.yml up --build -d` ile PostgreSQL (pgvector'lü, sabit sürüm) ve backend ayağa kalkar. Ollama Mac'te lokal çalışır; container'daki backend ona host üzerinden ulaşır. `POSTGRES_PASSWORD` zorunludur; `.env` yoksa compose başlamaz. Geliştirmede backend `./mvnw spring-boot:run` ile lokal çalışabilir, veritabanı compose'daki PostgreSQL olur.
31. Rehber: `ollama.md`'nin backend bölümü yeni akışla güncellenir (planner hangi rolün güncelleyeceğini belirler). Rehber şunları sırasıyla anlatır: sistemi Docker ile başlatma, başlangıç verisinin yüklendiğini kontrol etme, dosya yükleme ve iş durumunu izleme, üç modda arama ve `ef_search` / filtre denemesi, `psql` ile tabloları ve index'leri görme, sık karşılaşılan hatalar.

## Örnek senaryolar
- Sistem ilk kez başlatılır. Başlangıç verisi embed edilip yazılır. Dalga Fiber'in kaynak listesinde 4 kaynak `READY` görünür.
- "Şifremi unuttum, online işlemlere giremiyorum" `keyword` modda aranır ve 0 sonuç döner: yardım merkezi "parola" diyor, sorgu "şifre" diyor. Aynı arama `hybrid` modda yapılınca "Online İşlemler parolanı sıfırlama" parçası ilk sonuçlar arasında döner ve vektör kolu tarafından bulunduğu görünür.
- "TS-999 hata kodu" `hybrid` modda aranır. TS-999 parçası kelime kolunda da bulunduğu için RRF'de öne geçer.
- "Taşınıyorum, internetimi yeni eve nasıl aldırırım?" `post_filter` ve `ef_search = 40` ile aranır. Vektör kolu 10'dan az satır döndürür, yanıtta "kısa" işareti ve logda WARN olur. Aynı arama `iterative_scan` ile yapılınca vektör kolu 10 satır döndürür.
- Aynı arama `explain` ile yapılır. Planda `Index Scan using ... embedding ... idx` ve filtreyle elenen satır sayısı görünür.
- Modem kılavuzunun yeni PDF sürümü (`3.2`) yüklenir. Yanıt 202 ve iş numarasıdır. İş durumu sorgulandıkça `PARSING` → `CHUNKING` → `EMBEDDING` (embed edilen parça sayısı artar) → `WRITING` → `DONE` görülür. Bitince kaynak `3.2` sürümünde, yeni parça sayısıyla `READY` olur. Yeni parçalar aramada çıkar, eski parçalar çıkmaz.
- İş sürerken aynı kaynağa bir sürüm daha yüklenir: 409.
- Ollama kapatılıp bir sürüm yüklenir. İş `FAILED` olur, kaynak eski sürümüyle `READY` kalır, eski parçalar aramada görünmeye devam eder.
- `.docx` dosyası yüklenir: desteklenmeyen tür hatası. 25 MB'lık PDF yüklenir: dosya çok büyük hatası.
- Backend `EMBEDDING_MODEL=embeddinggemma` ile başlatılır. Açılışta WARN yazılır. Vector ve hybrid arama model uyuşmazlığı hatası döner; keyword arama çalışır.
- Kıyı Net çalışma alanında "Şifremi unuttum" aranır. Sadece Kıyı Net'in parçaları döner; Dalga Fiber'in hiçbir parçası dönmez.
- Arama kayıtları listelenir. Son yapılan aramalar sonuç sayıları ve ilk sonucun başlığıyla, yeniden eskiye görünür. Sonuçsuz arama 0 ile görünür.

## Kabul kriterleri
- `cd backend && ./mvnw verify` geçiyor. Veritabanı testleri gerçek PostgreSQL + pgvector üzerinde (Testcontainers) çalışıyor. Testler gerçek bir Ollama'ya ihtiyaç duymuyor.
- Flyway migration'ları boş bir veritabanında hatasız uygulanıyor. Tablolar, `vector` eklentisi ve index'ler (HNSW, GIN, B-tree) `psql` ile görülüyor.
- `docker compose ... up --build -d` sonrası `postgres` ve `backend` `healthy`. Backend lokal Ollama ile embed ediyor.
- Örnek senaryoların hepsi çalışan sistemde (gerçek Ollama + PostgreSQL) uçtan uca deneniyor ve sonuçları qa raporunda yazıyor. Özellikle:
  - post-filter ile kısa vektör kolu, iterative scan ile tam vektör kolu,
  - parola aramasında keyword 0 sonuç, hybrid'de parola parçası ilk 10'da,
  - yeni sürüm yüklemesinde eski parçaların tek transaction'da değişmesi,
  - başarısız işte eski parçaların kalması,
  - model uyuşmazlığı hatası,
  - çalışma alanı izolasyonu.
- Bütün endpoint'ler `docs/api/openapi.yaml`'da tanımlı ve backend sözleşmeyle birebir uyumlu. Tüm hata durumları sözleşmedeki `errorCode` ile RFC 9457 formatında dönüyor.
- `ollama.md` güncel; rehberdeki komutlar sırasıyla çalıştırılınca anlatılan sonuçlar alınıyor.

## Kapsam dışı
- Frontend / arayüz (sonraki task).
- WebSocket / canlı ilerleme. İlerleme, lab'daki gibi iş durumunu sorgulayarak (polling) izlenir.
- Web sitesi tarama (crawl) ve talep geçmişi aktarımı. Bu kaynak türleri sadece başlangıç verisinde vardır, dosyadan yüklenmez.
- Kaynak silme, çalışma alanı oluşturma ve silme.
- Kimlik doğrulama / yetkilendirme.
- Arama sonuçlarının LLM ile cevaba dönüştürülmesi (RAG'in "generation" kısmı).
- Model değiştirme ve bütün parçaları yeniden embed etme (sadece uyuşmazlık koruması var).

## Etkilenen katmanlar
- [x] Backend
- [ ] Frontend
- [x] API sözleşmesi
- [x] Veritabanı (migration, pgvector)
- [ ] WebSocket
- [x] Docker
