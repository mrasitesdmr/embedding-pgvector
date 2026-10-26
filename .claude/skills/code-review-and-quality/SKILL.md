---
name: code-review-and-quality
description: Beş eksenli (correctness, readability, architecture, security, performance) code review süreci - önce testleri incele, bulguları severity ile etiketle (Critical/Required/Optional/Nit/FYI), structural remedy öner, change sizing, dead code hijyeni ve dependency disiplini. Bir değişikliği merge etmeden önce, coder agent'ın ya da başka bir modelin yazdığı kodu değerlendirirken, qa adımında, bir diff veya PR review edilirken (diff mesaja yapıştırılmış olsa bile) ve dependency eklenirken/yükseltilirken kullan. Java'ya özel kontroller için java-code-review ile birlikte kullanılır.
license: MIT
metadata:
  origin: addyosmani/agent-skills
  source: https://github.com/addyosmani/agent-skills/tree/main/skills/code-review-and-quality
  adapted: Repo dışı referans dosyaları yerine projedeki skill'lere bağlandı; yorum ve dead code kuralları proje politikasıyla hizalandı.
---

# Code Review ve Kalite

> **Proje kuralı:** İsimlendirme, yorum politikası, klasör yapısı ve test biçimi için tek doğru kaynak `frontend-conventions` ve `backend-conventions`'tır; review'da "proje konvansiyonlarına uyuyor mu?" sorusu bu iki skill'e göre cevaplanır. Java'ya özel kontroller (null safety, exception handling, concurrency) için `java-code-review`, UI kontrolleri için `web-design-guidelines` ve `frontend-a11y`.

## Genel bakış

Quality gate'li, çok boyutlu code review. Her değişiklik merge'den önce review edilir — istisna yok. Review beş ekseni kapsar: correctness, readability, architecture, security ve performance.

**Onay standardı:** Bir değişiklik mükemmel olmasa bile genel kod sağlığını kesin olarak iyileştiriyorsa onayla. Mükemmel kod yoktur — hedef sürekli iyileştirmedir. Bir değişikliği sırf senin yazacağın gibi olmadığı için bloklama. Codebase'i iyileştiriyor ve projenin konvansiyonlarına uyuyorsa onayla.

## Ne zaman kullanılır

- Herhangi bir PR'ı veya değişikliği merge etmeden önce
- Bir feature implementation'ını tamamladıktan sonra
- Başka bir agent'ın veya modelin ürettiği kodu değerlendirmen gerektiğinde (`frontend-coder`, `backend-coder` çıktıları)
- Mevcut kodu refactor ederken
- Herhangi bir bug fix'ten sonra (hem fix'i hem regression testini review et)

## Beş eksenli review

Her review kodu şu boyutlarda değerlendirir:

### 1. Correctness

Kod iddia ettiği şeyi yapıyor mu?

- Spec'e veya task gereksinimlerine (`docs/tasks/`, plan, kabul kriterleri) uyuyor mu?
- Edge case'ler ele alınmış mı (null, boş, sınır değerleri)?
- Hata yolları ele alınmış mı (sadece happy path değil)? Her `errorCode` için kullanıcıya Türkçe mesaj var mı?
- Tüm testleri geçiyor mu? Testler gerçekten doğru şeyi test ediyor mu?
- Off-by-one hataları, race condition'lar veya state tutarsızlıkları var mı?
- API çağrıları `docs/api/openapi.yaml` sözleşmesine uyuyor mu?

### 2. Readability ve Simplicity

Başka bir mühendis (veya agent) bu kodu yazarı açıklamadan anlayabilir mi?

- İsimler açıklayıcı ve proje konvansiyonlarıyla tutarlı mı? (Context'siz `temp`, `data`, `result` yok)
- Control flow basit mi (iç içe ternary'lerden, derin callback'lerden kaçınılmış mı)?
- Kod mantıklı organize edilmiş mi (ilgili kod gruplanmış, net modül sınırları)?
- Basitleştirilmesi gereken "zekice" numaralar var mı?
- **Daha az satırla yapılabilir mi?** (100 satırın yettiği yerde 1000 satır başarısızlıktır)
- **Abstraction'lar karmaşıklıklarını hak ediyor mu?** (Üçüncü kullanım vakasına kadar genelleştirme)
- Açıklama gerektiren bir yer varsa önce yeniden adlandırma veya küçük bir function'a ayırma denenmiş mi? (Proje yorum politikası: metot ve component üstüne yorum yok; backend'de her üst seviye tipin üstünde kısa Türkçe amaç Javadoc'u zorunludur (`backend-conventions` bölüm 2); gövdede sadece koddan çıkarılamayan bir *neden* için tek satır.)
- Dead code kalıntıları var mı: işlevsiz değişkenler (`_unused`), backward-compat shim'leri, `// removed` yorumları, yorum satırına alınmış kod, `console.log`, `debugger`?
- **Alakasız bir akışa yeni bir koşul eklenmiş mi?** Bu bir nit değil, design smell'dir — mantığı mevcut bir yola dolamak yerine kendi helper'ına, state'ine veya policy'sine taşı.
- **Aynı şekil üzerinde tekrarlanan koşullar var mı?** Eksik bir model veya dispatcher'a işaret eder. "Geçici" bir branch genelde kalıcı borçtur.

### 3. Architecture

Değişiklik sistemin tasarımına uyuyor mu?

- Mevcut pattern'leri mi izliyor, yeni bir tane mi getiriyor? Yeniyse gerekçeli mi?
- Temiz modül sınırlarını koruyor mu (frontend: feature-first klasör yapısı; backend: controller → service → repository)?
- Paylaşılması gereken code duplication var mı?
- Dependency'ler doğru yönde mi akıyor (circular dependency yok)?
- Abstraction seviyesi uygun mu (over-engineered değil, aşırı coupled değil)?
- **Bu refactor karmaşıklığı azaltıyor mu, yoksa sadece yerini mi değiştiriyor?** Değişikliği takip etmek için okuyucunun aklında tutması gereken kavramları say. "Daha temiz" versiyon bu sayıyı değiştirmiyorsa daha temiz değildir — aynı mantığı yeniden merkezileştiren yerine branch'leri, modları veya katmanları ortadan kaldıran yeniden yapılandırmayı tercih et. Bir abstraction'ı cilalamak yerine silmeyi tercih et.
- **Feature'a özel mantık paylaşılan veya genel amaçlı bir modüle sızıyor mu?** (ör. `search` mantığı — RRF birleştirme, skor formatı — `shared/` altına) Mantığı sahibi olan katmanda tut, neredeyse aynısı olan yeni bir helper yerine mevcut kanonik helper'ı kullan, mimari kaymayı normalleştirme.
- **Type sınırları açık mı?** Gereksiz `any`/`unknown`/optional/cast'leri ve belirsiz bir invariant'ı örten sessiz fallback'leri sorgula — sınırı açık hale getirmek çoğu zaman çevresindeki control flow'u da basitleştirir.

### 4. Security

Ayrıntılı rehber için frontend'de `frontend-security`, backend'de `springboot-security`. Değişiklik bir açık getiriyor mu?

- Kullanıcı girdisi validate ve sanitize ediliyor mu?
- Secret'lar koddan, log'lardan ve version control'den uzak tutuluyor mu? (embedding API anahtarı ortam değişkeninden geliyor, frontend'e ve log'a girmiyor; sorgu metni log'lanmıyor)
- Gereken yerde authentication/authorization kontrol ediliyor mu?
- SQL sorguları parameterized mı (string birleştirme yok)?
- Çıktılar XSS'e karşı encode ediliyor mu (`dangerouslySetInnerHTML` yok)?
- Dependency'ler güvenilir kaynaklardan mı ve bilinen açıkları yok mu?
- Dış kaynaklardan gelen veri (API, STOMP mesajları, URL parametreleri, `sessionStorage`) untrusted kabul ediliyor mu?
- Dış veri akışları mantıkta veya render'da kullanılmadan önce sistem sınırlarında validate ediliyor mu?

### 5. Performance

Ayrıntılı profiling ve optimizasyon için frontend'de `frontend-performance` ve `react-best-practices`, backend'de `sql-optimization-patterns` ve `jpa-patterns`. Değişiklik bir performans sorunu getiriyor mu?

- N+1 sorgu pattern'i var mı?
- Sınırsız döngüler veya sınırsız veri çekme var mı?
- Async olması gereken senkron işlemler var mı?
- UI component'lerinde gereksiz re-render var mı?
- List endpoint'lerinde pagination eksik mi (`PageResponse`)?
- Hot path'lerde büyük nesneler oluşturuluyor mu?

## Structural remedy'ler

Yapısal bir sorun işaretlediğinde sadece sorunu değil, hamleyi de öner. Sadece "bu karmaşık" diyen bir review yazarı tahmin yürütmeye bırakır. İsmi konmuş bir yeniden yapılandırmaya başvur:

- **Koşul zincirini** typed bir model veya açık bir dispatcher ile değiştir.
- **Tekrarlanan branch'leri** tek, daha net bir akışta birleştir.
- **Orkestrasyonu business logic'ten ayır** ki her biri kendi başına okunabilsin.
- **Feature'a özel mantığı** paylaşılan modülden, kavramın sahibi olan feature'a taşı.
- Neredeyse aynısı olan özel bir kopya yerine **kanonik helper'ı kullan**.
- Downstream branch'leme ortadan kalksın diye **type sınırını açık hale getir**.
- API'yi netleştirmeden dolaylılık ekleyen **pass-through wrapper'ı sil**.
- **Bir helper çıkar** ya da büyük bir dosyayı odaklı modüllere **böl**.

Aynı karmaşıklığı etrafa yayan değil, hareketli parçaları azaltan remedy'yi tercih et.

## Change sizing

Küçük, odaklı değişiklikler daha kolay review edilir, daha hızlı merge edilir ve daha güvenli deploy edilir. Hedef boyutlar:

```
~100 satır değişiklik   → İyi. Tek oturuşta review edilebilir.
~300 satır değişiklik   → Tek bir mantıksal değişiklikse kabul edilebilir.
~1000 satır değişiklik  → Çok büyük. Böl.
```

**Sadece diff boyutunu değil, dosya boyutunu da izle.** Küçük bir diff bile bir dosyayı sağlıklı sınırın ötesine itebilir — tek dosyada toplam ~1000 satır (yukarıdaki ~1000 *değişen* satır eşiğinden farklı) yaygın bir inceleme sinyalidir, katı bir sınır değil. Bir değişiklik zaten büyük olan bir dosyayı ciddi şekilde büyütüyorsa, üstüne eklemeden *önce* helper, subcomponent veya modül çıkarmayı sor. Önce ayrıştır, sonra ekle.

**"Tek değişiklik" nedir:** Tek bir şeyi ele alan, ilgili testleri içeren ve gönderildikten sonra sistemi çalışır halde bırakan, kendi içinde bütün bir değişiklik. Feature'ın tamamı değil, bir parçası.

**Değişiklik çok büyükse bölme stratejileri:**

| Strateji | Nasıl | Ne zaman |
|----------|-------|----------|
| **Stack** | Küçük bir değişiklik gönder, sıradakini onun üzerine kur | Sıralı dependency'ler |
| **Dosya grubuna göre** | Farklı reviewer gerektiren gruplar için ayrı değişiklikler | Cross-cutting concern'ler |
| **Yatay** | Önce paylaşılan kod/stub'lar, sonra tüketiciler | Katmanlı mimari |
| **Dikey** | Feature'ı daha küçük full-stack dilimlere böl | Feature çalışması |

**Büyük değişikliklerin kabul edilebilir olduğu durumlar:** Dosyaların tamamen silinmesi ve reviewer'ın her satırı değil sadece niyeti doğrulaması gereken otomatik refactoring.

**Refactoring'i feature çalışmasından ayır.** Mevcut kodu refactor eden ve yeni davranış ekleyen bir değişiklik iki değişikliktir — ayrı gönder. Küçük temizlikler (değişken yeniden adlandırma) reviewer'ın takdirine bağlı olarak dahil edilebilir.

## Değişiklik açıklamaları

Her değişikliğin version control geçmişinde tek başına anlaşılır bir açıklaması olmalı.

**İlk satır:** Kısa, emir kipinde, tek başına anlamlı. "Deleting the FizzBuzz RPC" değil, "Delete the FizzBuzz RPC". Geçmişte arama yapan biri diff'i okumadan değişikliği anlayabilmeli.

**Gövde:** Neyin değiştiği ve neden. Kodda görünmeyen context'i, kararları ve gerekçeleri ekle. İlgili task numarasına (`TASK-001`), benchmark sonuçlarına veya tasarım dokümanlarına bağlantı ver. Yaklaşımın eksikleri varsa kabul et.

**Anti-pattern'ler:** "Fix bug", "Fix build", "Add patch", "Moving code from A to B", "Phase 1", "Add convenience functions".

## Review süreci

### Adım 1: Context'i anla

Koda bakmadan önce niyeti anla:

```
- Bu değişiklik neyi başarmaya çalışıyor?
- Hangi spec'i veya task'ı uyguluyor? (docs/tasks/, handoff/ dosyaları)
- Beklenen davranış değişikliği nedir?
```

### Adım 2: Önce testleri review et

Testler niyeti ve coverage'ı ortaya koyar:

```
- Değişiklik için test var mı?
- Testler davranışı mı test ediyor (implementation detaylarını değil)?
- Edge case'ler kapsanmış mı?
- Test isimleri given_when_then biçiminde ve açıklayıcı mı?
- Kod değişirse testler regression'ı yakalar mı?
```

Son soruyu okuyarak değil, deneyerek cevapla. Değişikliğin eklediği bir koşulu ters çevir (bir negation'ı kaldır, `&&`'yi `||` yap), test suite'i çalıştır, sonra dosyayı yedeğinden geri yükle. Yeşil kalan bir mutasyon bir bulgudur: eksik olan test vakasını adlandır. Proje genelinde mutation score için mutation testing aracını (frontend: Stryker; backend: PIT, eklendiğinde) çalıştır. Test kalitesi kriterleri için `writing-good-tests`.

### Adım 3: Implementation'ı review et

Kodu beş ekseni akılda tutarak gez:

```
Değişen her dosya için:
1. Correctness: Bu kod testin söylediğini yapıyor mu?
2. Readability: Yardım almadan anlayabiliyor muyum?
3. Architecture: Sisteme uyuyor mu?
4. Security: Açık var mı?
5. Performance: Darboğaz var mı?
```

### Adım 4: Bulguları kategorize et

Yazarın neyin zorunlu neyin opsiyonel olduğunu bilmesi için her yorumu severity ile etiketle:

| Prefix | Anlamı | Ne zaman | Yazarın aksiyonu |
|--------|--------|----------|------------------|
| **Critical:** | Merge'ü bloklar | Güvenlik açığı, veri kaybı, bozuk işlevsellik, API sözleşmesi (`openapi.yaml`, `errorCode`) ihlali | Merge'den önce düzeltilir |
| **Required:** | Zorunlu değişiklik | Muhtemel bug, proje kuralı (`*-conventions`) ihlali, eksik test/validation, ölçülmüş ciddi performans sorunu | Merge'den önce ele alınır ya da gerekçesiyle açıkça ertelenir |
| **Optional:** / **Consider:** | Öneri | Code smell, daha sade alternatif | Düşünmeye değer ama zorunlu değil |
| **Nit:** | Küçük, opsiyonel | Format, stil tercihleri | Yazar görmezden gelebilir |
| **FYI:** | Sadece bilgi | Gelecek için context | Aksiyon gerekmez |

Her yorum bu beş etiketten birini taşır; etiketsiz yorum yazılmaz. Bu, yazarların tüm geri bildirimi zorunlu sayıp opsiyonel önerilere zaman harcamasını önler. `java-code-review` ve `qa` raporu aynı ölçeği kullanır; Critical veya çözülmemiş Required varsa sonuç `STATUS: NEEDS_FIX`'tir.

**Önemli olanla başla.** Bulguları etkisine göre sırala: önce correctness ve security, sonra yapısal regression'lar ve kaçırılmış sadeleştirmeler, sonra geri kalan her şey. Gerçek bir sorunu kozmetik nit'lerin altına gömme — birkaç yüksek kanaatli yorum uzun bir listeden iyidir. Bir yapısal sorunun ve on nit'in varsa, review *o yapısal sorundur*.

### Adım 5: Doğrulamayı doğrula

Yazarın doğrulama hikayesini kontrol et:

```
- Hangi testler çalıştırıldı?
- Build geçti mi? (frontend: npm run lint && npm run build; backend: springboot-verification)
- Değişiklik manuel test edildi mi?
- UI değişiklikleri için screenshot var mı? (webapp-testing)
- Önce/sonra karşılaştırması var mı?
```

## Multi-model review pattern'i

Farklı review perspektifleri için farklı modeller kullan:

```
Model A kodu yazar
    │
    ▼
Model B correctness ve architecture için review eder
    │
    ▼
Model A geri bildirimi ele alır
    │
    ▼
Son kararı insan verir
```

Bu, tek bir modelin kaçırabileceği sorunları yakalar — farklı modellerin farklı kör noktaları vardır. Ekip akışında coder agent'lar `sonnet` ile çalışır; `qa` adımı bağımsız bir gözdür.

**Review agent'ı için örnek prompt:**
```
Bu değişikliği correctness, security ve proje konvansiyonlarına
(frontend-conventions / backend-conventions) uygunluk açısından review et.
Spec şunu söylüyor: [X]. Değişiklik şunu yapmalı: [Y].
Sorunları Critical, Required, Optional, Nit veya FYI olarak işaretle.
```

## Dead code hijyeni

Herhangi bir refactoring veya implementation değişikliğinden sonra sahipsiz kalmış kodu kontrol et:

1. Artık erişilemeyen veya kullanılmayan kodu belirle
2. Açıkça listele
3. **Silmeden önce sor:** "Artık kullanılmayan şu elemanları kaldırayım mı: [liste]?"

Dead code'u ortada bırakma — gelecekteki okuyucuların ve agent'ların kafasını karıştırır. Ama emin olmadığın şeyleri sessizce de silme. Şüphedeysen sor.

```
DEAD CODE TESPİT EDİLDİ:
- src/shared/format/formatLegacyDate.ts — formatDate ile değiştirildi
- src/features/search/OldHitCard.tsx — SearchHitList ile değiştirildi
- src/shared/config.ts içindeki LEGACY_API_URL — referansı kalmadı
→ Bunları kaldırmak güvenli mi?
```

## Review hızı

Yavaş review'lar bütün ekibi bloklar. Review'a context switch yapmanın maliyeti, başkalarına yüklenen bekleme maliyetinden azdır.

- **En geç bir iş günü içinde yanıt ver** — bu hedef değil, üst sınırdır
- **İdeal tempo:** Derin odaklı kodlama içinde değilsen, review isteği geldikten kısa süre sonra yanıt ver. Tipik bir değişiklik tek günde birden fazla review turunu tamamlayabilmeli
- Hızlı nihai onay yerine **hızlı bireysel yanıtlara öncelik ver**. Birden fazla tur gerekse bile hızlı geri bildirim bıkkınlığı azaltır
- **Büyük değişiklikler:** Tek bir devasa değişikliği review etmek yerine yazardan bölmesini iste

## Anlaşmazlıkları çözmek

Review anlaşmazlıklarını çözerken şu hiyerarşiyi uygula:

1. **Teknik gerçekler ve veriler** görüşlerin ve tercihlerin önüne geçer
2. **Stil rehberleri** (`frontend-conventions`, `backend-conventions`) stil konularında mutlak otoritedir
3. **Yazılım tasarımı** kişisel tercihe göre değil, mühendislik ilkelerine göre değerlendirilmelidir
4. **Codebase tutarlılığı**, genel sağlığı bozmuyorsa kabul edilebilir

**"Sonra temizlerim"i kabul etme.** Deneyim, ertelenen temizliğin nadiren yapıldığını gösterir. Gerçek bir acil durum değilse temizliği gönderimden önce iste. Çevredeki sorunlar bu değişiklikte ele alınamıyorsa, kendine atanmış bir kayıt (task) açılmasını iste.

## Review'da dürüstlük

Kodu review ederken — ister senin, ister başka bir agent'ın, ister bir insanın yazdığı olsun:

- **Lastik damga vurma.** Review kanıtı olmadan "LGTM" kimseye yardım etmez.
- **Gerçek sorunları yumuşatma.** Production'a çıkacak bir bug için "bu küçük bir endişe olabilir" demek dürüst değildir.
- **Mümkün olduğunda sorunları sayıyla ifade et.** "Bu N+1 sorgu listedeki her öğe için ~50ms ekleyecek", "bu yavaş olabilir"den iyidir.
- **Açık sorunları olan yaklaşımlara itiraz et.** Dalkavukluk review'da bir hata modudur. Implementation'da sorun varsa doğrudan söyle ve alternatif öner.
- **Override'ı zarafetle kabul et.** Yazar tam context'e sahipse ve katılmıyorsa onun kararına saygı göster. Kişiye değil koda yorum yap — kişisel eleştirileri kodun kendisine odaklanacak şekilde yeniden çerçevele.

## Dependency disiplini

Code review'un bir parçası dependency review'dur. Bu projede listede olmayan bir runtime dependency eklemek plan kararıdır; coder kendi başına eklemez (`frontend-conventions` bölüm 1).

**Herhangi bir dependency eklemeden önce:**
1. Mevcut yığın bunu çözüyor mu? (Çoğu zaman çözer.)
2. Dependency ne kadar büyük? (Bundle etkisini kontrol et.)
3. Aktif olarak bakımı yapılıyor mu? (Son commit, açık issue'lar.)
4. Bilinen açıkları var mı? (`npm audit`)
5. Lisansı ne? (Projeyle uyumlu olmalı.)

**Kural:** Yeni dependency yerine standart kütüphaneyi ve mevcut utility'leri tercih et. Her dependency bir yüktür.

**Mevcut bir dependency'yi yükseltmek** diğerleri gibi bir kod değişikliğidir ve en riskli yükseltmeler "bump deps" gibi bir mesajla toplu merge edilenlerdir. Aynı disiplinle review et:

1. **Sadece versiyon numarasını değil, changelog'u oku.** Semver, maintainer'ın tutmamış olabileceği bir sözdür — bir "patch" davranış değişikliği taşıyabilir. Major bump için migration notlarını oku ve neyin kırıldığını bul.
2. **Değişiklik başına tek dependency.** Tek tek (veya küçük ilgili gruplar halinde) yükselt ve merge et. Toplu bir bump build'i kırdığında hangi paketin yaptığını kaybedersin; tek paketlik bir değişiklik sebebi bariz, revert'i temiz kılar.
3. **Kararı testler versin.** Yükseltme "kuruldu" ile değil, öncesinde *ve* sonrasında yeşil bir test suite ile doğrulanır. Dependency'nin davranışı etrafındaki coverage zayıfsa asıl bulgu o boşluktur — önce test ekle.
4. **Transitive graph'a dikkat et.** Kurulu paketlerin çoğu kimsenin doğrudan seçmediği paketlerdir. Sadece `package.json`'ı değil lockfile diff'ini review et; tek bir doğrudan bump onlarca dolaylı değişikliği beraberinde getirebilir.
5. **Lockfile'ı dürüst tut.** Commit'le, diff'ini review et, asla elle düzenleme. Gerçekte neyin ship edildiğini sabitleyen şey lockfile'dır.

`npm audit` bulgularının triage'ı ve supply chain riski (typosquatting, ele geçirilmiş maintainer'lar) için `frontend-security` skill'ini izle — bu bölüm yükseltme *iş akışını*, o skill güvenlik *kararını* kapsar.

## Review kontrol listesi

```markdown
## Review: [PR/Değişiklik başlığı]

### Context
- [ ] Bu değişikliğin ne yaptığını ve neden yaptığını anlıyorum

### Correctness
- [ ] Değişiklik spec/task gereksinimlerine uyuyor
- [ ] Edge case'ler ele alınmış
- [ ] Hata yolları ele alınmış
- [ ] Testler değişikliği yeterince kapsıyor

### Readability
- [ ] İsimler net ve tutarlı
- [ ] Mantık basit
- [ ] Gereksiz karmaşıklık yok

### Architecture
- [ ] Mevcut pattern'leri izliyor
- [ ] Gereksiz coupling veya dependency yok
- [ ] Uygun abstraction seviyesi
- [ ] Refactor'lar karmaşıklığı taşımıyor, azaltıyor
- [ ] Paylaşılan modüllerde feature mantığı yok; dosya sağlıklı boyutta

### Security
- [ ] Kodda secret yok
- [ ] Girdi sınırlarda validate ediliyor
- [ ] Injection açığı yok
- [ ] Auth kontrolleri yerinde
- [ ] Dış veri kaynakları untrusted kabul ediliyor

### Performance
- [ ] N+1 pattern yok
- [ ] Sınırsız işlem yok
- [ ] List endpoint'lerinde pagination var

### Verification
- [ ] Testler geçiyor
- [ ] Build başarılı
- [ ] Manuel doğrulama yapıldı (uygunsa)

### Karar
- [ ] **Onayla** — Merge'e hazır
- [ ] **Değişiklik iste** — Sorunlar ele alınmalı
```

## Yaygın bahaneler

| Bahane | Gerçek |
|--------|--------|
| "Çalışıyor, bu kadarı yeterli" | Okunamayan, güvensiz veya mimari olarak yanlış çalışan kod, katlanarak büyüyen borç yaratır. |
| "Ben yazdım, doğru olduğunu biliyorum" | Yazarlar kendi varsayımlarına kördür. Her değişiklik ikinci bir gözden fayda görür. |
| "Sonra temizleriz" | O "sonra" hiç gelmez. Review quality gate'tir — kullan. Temizliği merge'den sonra değil, önce iste. |
| "AI'ın ürettiği kod muhtemelen iyidir" | AI kodu daha az değil, daha fazla inceleme gerektirir. Yanlışken bile kendinden emin ve makul görünür. |
| "Testler geçiyor, o zaman iyi" | Testler gerekli ama yeterli değil. Mimari sorunları, güvenlik sorunlarını veya okunabilirlik kaygılarını yakalamazlar. |
| "Refactor kodu daha temiz yapıyor" | Karmaşıklığın yerini değiştirmek onu azaltmak değildir. Okuyucu hâlâ aynı sayıda kavram tutuyorsa yapı iyileşmemiştir — branch'lerin kaybolduğu versiyonu ara. |
| "Bu dosyaya küçük bir ekleme sadece" | Küçük diff'ler de dosyaları sağlıklı boyutun ötesine iter ve alakasız akışlara branch ekler. Diff boyutunu değil, ortaya çıkan yapıyı değerlendir. |
| "Sadece bir versiyon yükseltmesi" | Bump, senin yazmadığın bir davranış değişikliğidir. Changelog'u oku; semver kırılmama garantisi vermez. |
| "Zaman kazanmak için hepsini tek PR'da yükselteyim" | Build'i kıran toplu bir bump hangi paketin yaptığını gizler. Değişiklik başına tek dependency sebebi ve revert'i temiz tutar. |

## Kırmızı bayraklar

- Hiç review edilmeden merge edilen PR'lar
- Sadece testlerin geçip geçmediğine bakan review (diğer eksenler yok sayılmış)
- Gerçek bir review kanıtı olmadan "LGTM"
- Güvenlik odaklı review yapılmamış güvenliğe duyarlı değişiklikler
- "Düzgün review edilemeyecek kadar büyük" PR'lar (böl)
- Bug fix PR'larında regression testi yok
- Severity etiketi olmayan review yorumları — neyin zorunlu neyin opsiyonel olduğunu belirsizleştirir
- "Sonra düzeltirim"i kabul etmek — asla olmaz
- Okuyucunun tutması gereken kavram sayısını azaltmadan kodu yer değiştiren bir refactor
- Zaten büyük olan bir dosyayı ayrıştırmak yerine büyüten bir değişiklik
- Alakasız code path'lere dağıtılmış yeni koşullar (eksik bir abstraction)
- Mevcut kanonik bir helper'ın neredeyse aynısı olan özel bir helper ya da paylaşılan modüle konmuş feature mantığı
- Changelog review'u ve paket bazında izolasyon olmadan yapılmış toplu "bump dependencies" PR'ı
- Elle düzenlenmiş, commit'lenmemiş veya diff'i review edilmeden merge edilmiş lockfile değişikliği

## Doğrulama

Review tamamlandıktan sonra:

- [ ] Tüm Critical sorunlar çözüldü
- [ ] Tüm Required değişiklikler çözüldü veya gerekçesiyle açıkça ertelendi
- [ ] Testler geçiyor
- [ ] Build başarılı
- [ ] Doğrulama hikayesi dokümante edildi (ne değişti, nasıl doğrulandı) — ekip akışında ilgili `handoff/` dosyasında
- [ ] Dependency yükseltmeleri changelog'larına göre review edildi, paket bazında izole edildi ve lockfile diff'i review edilmiş yeşil bir suite ile doğrulandı

**Varsayılan blocker'lar:** Bunların her biri için daha basit tasarımı ortaya koy ve öner; değişiklik yapıyı aktif olarak kötüleştiriyorsa Required'a yükselt: karmaşıklığı azaltmak yerine yer değiştiren refactor; bir dosyayı ayrıştırmadan boyut sınırının ötesine iten değişiklik; paylaşılan modüle eklenmiş feature mantığı; mevcut kanonik bir helper'ın neredeyse aynısı; belirsiz bir invariant'ı gizleyen sessiz fallback.
