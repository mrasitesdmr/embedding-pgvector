# TASK-007: Backend sınıflarına Türkçe amaç yorumu

## Ne isteniyor
Backend'deki her sınıfın üstüne, o sınıfın ne işe yaradığını ve ne için kullanıldığını anlatan kısa bir Türkçe yorum eklenir. Kodu ilk kez açan kişi, sınıfın içini okumadan amacını anlar.

## Kurallar
1. Kapsam: `backend/src/main` ve `backend/src/test` altındaki her `.java` dosyasının üst seviye tipi (class, interface, record, enum, annotation).
2. Yorum Javadoc biçimindedir (`/** ... */`) ve tipin hemen üstünde, annotation'lardan önce yer alır.
3. Yorum Türkçedir, en fazla 2 satırdır.
4. Yorum sınıfın amacını söyler: ne işe yarar, ne için kullanılır. Alanları, metotları veya uygulama adımlarını tek tek saymaz; sınıf adını tekrar etmez.
5. `@author`, `@since` gibi tag kullanılmaz.
6. Metotlara ve iç içe (nested) tiplere yorum eklenmez.
7. Kodun davranışı değişmez: sadece yorum eklenir; isim, import, satır sırası veya biçimlendirme değişikliği yapılmaz.
8. Yorum içeriği doğru olmalı: sınıfın gerçekte yaptığı işi anlatır, yapmadığı bir şeyi iddia etmez.

## Örnek senaryolar
- Geliştirici `SourceService.java`'yı açar, en üstte "Kaynak ekleme, listeleme ve silme işlerini yürütür; çalışma alanı başına kaynak limitini uygular." yazısını görür.
- Geliştirici IntelliJ'de bir controller'da `SourceService` üzerine gelir, hover'da aynı Türkçe açıklamayı görür.
- Geliştirici bir test sınıfını açar, en üstte o testin hangi sınıfın hangi davranışını doğruladığını görür.

## Kabul kriterleri
- `backend/src/main` ve `backend/src/test` altında üst seviye tipinin üstünde Javadoc olmayan `.java` dosyası kalmaz.
- Hiçbir yorum 2 satırı geçmez; hepsi Türkçedir.
- Diff'te yorum satırları dışında değişiklik yoktur.
- `cd backend && ./mvnw verify` geçer, test sayısı task öncesiyle aynıdır.

## Kapsam dışı
- Metot, alan ve iç içe tip yorumları.
- Frontend, deployment ve SQL migration dosyaları.
- Mevcut kodda isim düzeltme veya refactor.
- `docs/api/` sözleşmeleri.

## Etkilenen katmanlar
- [x] Backend
- [ ] Frontend
- [ ] API sözleşmesi
- [ ] Veritabanı (migration, pgvector)
- [ ] WebSocket
- [ ] Docker
