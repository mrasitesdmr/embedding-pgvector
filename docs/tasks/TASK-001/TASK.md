# TASK-001: Lokal embedding modeli (Ollama) ve ilk embedding isteği

## Ne isteniyor
`lab.html` (RAG Retrieval Lab) projenin şablonudur ve çalışan bir versiyonu yapılacak. Ekran tasarımı lab'ın sol yarısına (bilgi bankası ürünü: Arama, Arama kayıtları, Bilgi kaynakları) benzeyecek. Sağ yarıdaki adım adım akış ve sistem konsolu ekrana gelmeyecek; o akış (embed etme, parçalama, vektör arama, sıralama) arka planda backend'in gerçekten yaptığı iş olacak.

Bu ilk task'ta arayüz yok. Sadece backend iskeleti kurulacak ve embedding modeli Ollama ile **lokal bilgisayarda** çalıştırılacak. Backend'e bir metin gönderilecek, backend bu metni lokal modelle embed edip vektörü geri döndürecek. Lab'daki ilk adımın ("Arama metni bir vector'e çevrildi") gerçek hali budur.

## Kurallar
1. Embedding modeli Ollama ile lokal bilgisayarda (macOS, Apple Silicon) çalışır. Dışarıdaki ücretli bir embedding servisine (ör. OpenAI) istek atılmaz, API anahtarı gerekmez.
2. Model Türkçe metni desteklemelidir (çok dilli model). Planner Ollama'daki embedding modellerini karşılaştırır (aday örnekleri: `bge-m3`, `qwen3-embedding`, `embeddinggemma`, `nomic-embed-text`) ve onay kartında bir model önerir: adı, vektör boyutu, indirme boyutu, Türkçe desteği ve neden seçildiği sade dille yazılır. Model kullanıcı onayıyla kesinleşir.
3. Modelin adı ve Ollama'nın adresi koda gömülmez, yapılandırmadan değiştirilebilir. Varsayılan adres Ollama'nın lokal adresidir (`http://localhost:11434`).
4. Lokal kurulum ve çalıştırma rehberi olarak kökteki `ollama.md` kullanılır; ayrı bir rehber yazılmaz. Dosyanın Ollama bölümleri hazırdır. Ekip, onaylanan modele göre model adlarını günceller ve "Backend üzerinden istek atma" bölümünü sözleşmedeki gerçek endpoint ile doldurur (Kural 11'de Docker'a evet denirse Docker bölümü de kesinleşir). `ollama.md`'yi hangi rolün güncelleyeceğini planner belirler. Rehber, kullanıcının terminalde kendisi çalıştırabileceği komutları sırasıyla verir ve her adımın ne yaptığını anlatır:
   - Ollama'nın kurulması ve kurulu olduğunun kontrolü,
   - Ollama sunucusunun başlatılması ve çalıştığının kontrolü,
   - modelin indirilmesi ve indirilen modellerin listelenmesi,
   - backend olmadan, doğrudan Ollama'ya terminalden bir embedding isteği atılıp çıktının (vektör ve boyutu) görülmesi,
   - backend'in başlatılıp backend üzerinden aynı isteğin atılması,
   - sık karşılaşılan hatalar (sunucu kapalı, model indirilmemiş, port dolu) ve çözümleri.
5. Backend, gönderilen tek bir metni embed eder ve şunları döndürür: kullanılan model adı, vektörün boyutu (sayı adedi), vektörün kendisi ve embed işleminin süresi (milisaniye).
6. Metin boş olamaz; sadece boşluktan oluşan metin de boş sayılır. Metnin başındaki ve sonundaki boşluklar embed edilmeden önce kırpılır.
7. Metin en fazla 2.000 karakter olabilir. Daha uzun metin reddedilir.
8. Türkçe karakterler (ç, ğ, ı, İ, ö, ş, ü) bozulmadan modele iletilir.
9. Ollama'ya ulaşılamazsa ya da istenen model indirilmemişse backend anlaşılır ve birbirinden ayrı bir hata döner (sabit `errorCode` ile). Bu durumlarda backend çökmez, açık kalır.
10. Embed işleminin sonucu (model, boyut, süre) backend loguna yazılır; metnin tamamı ve vektör loga yazılmaz.
11. Docker: Ollama'nın Docker'da çalıştırılması bu task'ta isteğe bağlıdır. Planner onay kartında "Ollama Docker'da da çalışsın mı?" diye sorar ve Mac'te Docker'ın GPU kullanamadığını (modelin daha yavaş çalışacağını) sade dille belirtir. Kullanıcı evet derse rehbere Docker ile çalıştırma adımları da eklenir.

## Örnek senaryolar
- Kullanıcı rehberdeki komutlarla Ollama'yı kurar, sunucuyu başlatır ve modeli indirir. Terminalden Ollama'ya doğrudan "Şifremi unuttum, online işlemlere giremiyorum" metniyle istek atar; çıktıda modelin boyutu kadar sayıdan oluşan bir vektör görür.
- Kullanıcı backend'i başlatır ve aynı metni backend'e gönderir. Yanıtta model adı, boyut (ör. 1024), vektör ve süre (ör. 85 ms) gelir.
- Aynı metin iki kez gönderilir; iki yanıttaki vektörler aynıdır.
- `"  modem ışığı kırmızı yanıyor  "` gönderilir; baştaki ve sondaki boşluklar kırpılarak embed edilir.
- Boş metin ya da `"   "` gönderilir; boş metin hatası döner, Ollama'ya istek atılmaz.
- 2.001 karakterlik metin gönderilir; çok uzun metin hatası döner.
- Ollama sunucusu kapalıyken istek atılır; "embedding servisine ulaşılamıyor" hatası döner. Ollama yeniden açılınca, backend yeniden başlatılmadan aynı istek başarılı olur.
- Yapılandırmadaki model indirilmemişken istek atılır; "model bulunamadı" hatası döner.

## Kabul kriterleri
- `ollama.md` onaylanan modele göre güncel, backend bölümü dolu; rehberdeki komutlar sırasıyla çalıştırılınca Ollama ayağa kalkıyor, model iniyor ve terminalden doğrudan Ollama'ya atılan embedding isteği vektör döndürüyor.
- Backend `./mvnw verify` ile derleniyor ve testleri geçiyor.
- Backend'e atılan istek, onaylanan modelin boyutunda bir vektör döndürüyor; yanıtta model adı, boyut ve süre var.
- Kural 6, 7 ve 9'daki hatalar sözleşmede tanımlı `errorCode` ile RFC 9457 formatında dönüyor.
- Endpoint `docs/api/openapi.yaml`'da tanımlı ve backend sözleşmeyle birebir uyumlu.
- Testler gerçek bir Ollama sunucusuna ihtiyaç duymadan çalışıyor (lokal model kapalıyken de `./mvnw verify` geçiyor).

## Kapsam dışı
- Frontend / arayüz (lab'ın sol yarısına benzeyen ekran sonraki task'larda).
- Veritabanı, pgvector, Flyway ve vektörlerin kaydedilmesi.
- Doküman yükleme, parçalama (chunking), toplu embed etme.
- Vektör benzerliği hesaplama ve arama (keyword, vector, hybrid, RRF).
- WebSocket / canlı ilerleme.
- Backend'in Docker imajı ve tüm sistemin docker compose ile çalıştırılması (Kural 11'deki isteğe bağlı Ollama container'ı hariç).

## Etkilenen katmanlar
- [x] Backend
- [ ] Frontend
- [x] API sözleşmesi
- [ ] Veritabanı (migration, pgvector)
- [ ] WebSocket
- [ ] Docker (sadece kullanıcı Kural 11'de evet derse: Ollama container'ı)
