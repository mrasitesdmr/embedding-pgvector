# TASK-004: Arayüz: dosya yükleme ve içe alma ilerlemesi

## Ne isteniyor
TASK-003'te bölünerek sonraya bırakılan yükleme kısmı yapılır. Kullanıcı "yanıt." sitesinde Bilgi kaynakları sayfasından yeni bir kaynak ekler ya da dosyadan gelen bir kaynağa yeni sürüm yükler. İçe alma ilerlemesini kaynağın satırında `lab.html`'deki gibi izler. İş bitince kaynak Hazır olur, başarısız olursa nedeni Türkçe yazar.

Canlı güncelleme için WebSocket kullanılmaz. İlerleme, iş durumu sorgulanarak (polling) izlenir.

Bu task TASK-003'ün devamıdır. TASK-003'te yapılan yerleşim, sekmeler, çalışma alanı, arama, arama kayıtları, kaynak listesi ve kaynak detayı aynen kalır. Kuralları bu task'ta değişmez.

## Kurallar

### Yeni kaynak ekle
1. Bilgi kaynakları sayfasında "Yeni kaynak ekle" butonu bulunur. Butona basınca bir diyalog açılır. Diyalogda kaynak adı (zorunlu) ve dosya seçimi bulunur.
2. Kabul edilen dosyalar `.pdf`, `.txt`, `.md`, `.markdown` ve `.csv`'dir. Desteklenmeyen türde, 20 MB'tan büyük ya da boş bir dosya seçilirse yükleme yapılmaz ve alanın altında Türkçe bir uyarı çıkar. Kaynak adı boşsa ya da sadece boşluksa da yükleme yapılmaz.
3. Backend dosyayı yine de reddederse onun hatası da `errorCode`'a göre Türkçe gösterilir.
4. Yükleme başlayınca diyalog kapanır. Yeni kaynak listede "İşleniyor" olarak görünür.

### Yeni sürüm yükle
5. "Yeni sürüm yükle" butonu sadece dosyadan gelen kaynaklarda (PDF, Metin / Markdown, CSV) bulunur. Web sitesi ve Talep geçmişi kaynaklarında yoktur. Buton iki yerde bulunur:
   - Bilgi kaynakları tablosunda kaynağın satırında,
   - kaynak detay sayfasının başlığında.
6. Diyalogda şunlar bulunur:
   - kaynağın adı, mevcut sürümü ve parça sayısı,
   - seçilen dosyanın adı ve boyutu,
   - isteğe bağlı bir sürüm etiketi (ör. `3.2`),
   - "Yeni sürüm hazır olana kadar arama mevcut sürümün parçalarını döndürür" bilgisi.
7. Dosya kuralları Kural 2 ve 3'teki gibidir.
8. Bir kaynağın işi sürerken o kaynağın "Yeni sürüm yükle" butonu pasiftir. Yine de "iş sürüyor" hatası gelirse Türkçe mesajla gösterilir.

### İlerleme
9. Yükleme başlayınca kaynağın satırında `lab.html`'deki gibi aşama metni ve ilerleme çubuğu görünür. Durum sütununda "İşleniyor" rozeti bulunur. Aşama metinleri şunlardır:
   - "Sıraya alındı",
   - "Dosya okunuyor",
   - "Parçalara bölünüyor",
   - "Embed ediliyor · X/Y parça",
   - "Yazılıyor".
10. İş durumu yaklaşık 1 saniyede bir sorgulanır. İş bitince (`DONE` ya da `FAILED`) sorgulama durur.
11. İş `DONE` olunca:
    - kaynak listesi yenilenir,
    - satır kısa bir süre vurgulanır,
    - "<Kaynak adı> <sürüm> hazır · N parça" bildirimi (toast) çıkar.

    Kaynak detay sayfası açıksa sürüm, parça sayısı ve parçalar da yeni sürümü gösterir.
12. İş `FAILED` olunca nedeni satırda Türkçe yazar: "Son yükleme başarısız: <neden>". İş hata kodlarının hepsinin Türkçe karşılığı vardır (dosyada metin yok, PDF okunamıyor, embedding servisine ulaşılamıyor, model bulunamadı, model uyuşmazlığı, iş yarıda kaldı, beklenmeyen hata).
13. Kullanıcı başka bir sayfaya gidip dönerse, sayfayı yenilerse ya da adresi yeni sekmede açarsa süren işin ilerlemesi görünmeye devam eder. Bunun için backend'den kaynağın son yükleme işi öğrenilebilir.
14. Yükleme sürerken arama, arama kayıtları ve diğer sayfalar normal çalışır.

### Diyaloglar ve klavye
15. Diyalog açılınca odak diyaloğa gider. Esc ile ya da "Vazgeç" butonuyla kapanır. Kapanınca odak diyaloğu açan butona döner. Diyalog açıkken Tab ile odak diyaloğun dışına çıkmaz.
16. Diyaloglardaki her form alanının bir etiketi vardır. Uyarılar ekran okuyucuya da duyurulur.
17. Yükleme akışının tamamı sadece klavyeyle yapılabilir.

### Backend ve sözleşme
18. Sayfa yenilenince süren işi bulabilmek ve başarısızlık nedenini gösterebilmek için kaynağın son yükleme işinin bilgisi backend'e ve `docs/api/openapi.yaml`'a eklenir. Var olan endpoint'lerin davranışı değişmez; sadece alan ya da endpoint eklenebilir.

### TASK-003'ten kalan düzeltme
19. Üretim build'inde site açılınca tarayıcı konsolunda yazı tipi kaynaklı güvenlik politikası (CSP `font-src`) hatası çıkmaz. TASK-003 QA raporunda Optional bulgu olarak yazılmıştı. Güvenlik politikası gevşetilmeden çözülmesi tercih edilir.

## Örnek senaryolar
- Kullanıcı modem kılavuzuna "Yeni sürüm yükle" ile `3.2` etiketli bir PDF yükler. Satırda sırasıyla "Dosya okunuyor", "Parçalara bölünüyor", "Embed ediliyor · 96/…" ve "Yazılıyor" görünür. Bu sırada sayfa yenilenir ve ilerleme kaldığı yerden görünmeye devam eder. İş bitince kaynak `3.2` sürümünde ve yeni parça sayısıyla Hazır olur, "hazır" bildirimi çıkar.
- Yükleme sürerken kullanıcı aynı kılavuzu arar. Sonuçlar eski sürümün parçalarından gelir. İş bitince yapılan arama yeni sürümün parçalarını döndürür.
- Kullanıcı "Yeni kaynak ekle" ile bir `.md` dosyası yükler. Yeni kaynak listede "İşleniyor" olarak çıkar, sonra Hazır olur. Kaynakta geçen bir kelime arandığında kaynağın parçası sonuçlarda görünür.
- `.docx` ya da 25 MB'lık bir PDF seçilir. Yükleme yapılmaz ve Türkçe bir uyarı çıkar. Boş bir `.txt` dosyası için de aynısı olur.
- Ollama kapatılır ve bir dosya yüklenir. İş Başarısız olur ve satırda "Son yükleme başarısız: embedding servisine ulaşılamıyor" benzeri Türkçe bir neden yazar. Kaynağın önceki sürümü aranabilir kalır.
- Sadece görsel içeren bir PDF yüklenir. İş "dosyada metin yok" nedeniyle Başarısız olur.
- Kullanıcı bir kaynağın işi sürerken o kaynağın "Yeni sürüm yükle" butonunu pasif görür.
- Web sitesi ve Talep geçmişi kaynaklarında "Yeni sürüm yükle" butonu görünmez.
- Kullanıcı diyaloğu klavyeyle açar ve Esc'e basar. Diyalog kapanır, odak açan butona döner.

## Kabul kriterleri
- Komutlar hatasız geçiyor:
  - `cd frontend && npm run build`
  - `cd frontend && npm run lint`
  - frontend testleri
  - `cd backend && ./mvnw verify`
- Uçtan uca testler (Playwright, gerçek sisteme karşı) en az şunları deniyor:
  - yeni sürüm yükleme ve ilerleme, yenilemeden sonra ilerlemenin devam etmesi dahil,
  - yeni kaynak ekleme,
  - geçersiz dosya uyarısı.

  TASK-003'ün uçtan uca testleri de geçmeye devam ediyor.
- `docker compose -f deployment/docker-compose.yml up --build -d` sonrası `postgres`, `backend` ve `frontend` servislerinin hepsi `healthy`.
- Örnek senaryoların hepsi çalışan sistemde (gerçek Ollama + PostgreSQL) tarayıcıdan deneniyor; sonuçları qa raporunda yazıyor.
- Yeni ve değişen endpoint'ler `docs/api/openapi.yaml`'da tanımlı. Backend ve frontend sözleşmeyle birebir uyumlu.
- Sözleşmedeki bütün `errorCode`'ların, iş hata kodları dahil, sitede bir Türkçe karşılığı var.
- Üretim build'inde site açılınca konsolda CSP hatası yok.

## Kapsam dışı
- WebSocket / STOMP ile canlı güncelleme. Başka bir sekmede başlatılan yükleme, sayfa yenilenince görünür.
- Kaynak silme, yüklemeyi iptal etme, eski sürüme geri dönme.
- Web sitesi tarama ve talep geçmişi aktarımı.
- Aynı anda birden fazla dosya yükleme, sürükle-bırak.
- Çalışma alanı oluşturma ve silme, model değiştirme ve yeniden embed etme.
- LLM ile cevap üretme, kimlik doğrulama, mobil yerleşim.
- TASK-003'te yapılan ekranlarda yükleme dışında değişiklik.

## Etkilenen katmanlar
- [x] Backend
- [x] Frontend
- [x] API sözleşmesi
- [ ] Veritabanı (migration, pgvector) — gerekirse planner belirler
- [ ] WebSocket
- [x] Docker — gerekirse planner belirler (ör. CSP ayarı)
