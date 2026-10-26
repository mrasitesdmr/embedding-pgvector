---
name: systematic-debugging
description: Bug, test failure veya beklenmeyen davranışta fix önermeden önce root cause'u bulmak için dört fazlı sistematik debugging süreci (root cause investigation, pattern analysis, hypothesis testing, implementation). Frontend veya backend'de bir test kırıldığında, build bozulduğunda, ekranda beklenmeyen davranış görüldüğünde, flaky test ya da WebSocket/STOMP mesajlarının gelmemesi gibi entegrasyon sorunlarında ve "bir fix daha deneyeyim" noktasına gelindiğinde kullan.
license: MIT
metadata:
  origin: obra/superpowers
  source: https://github.com/obra/superpowers/tree/main/skills/systematic-debugging
  adapted: Ekip iş akışına (coder test yazmaz) ve Vitest/RTL/Playwright yığınına uyarlandı; projeye özel olmayan örnek dosyalar çıkarıldı.
---

# Systematic Debugging

## Genel bakış

**Temel ilke:** Fix denemeden ÖNCE her zaman root cause'u bul. Semptomu düzelten fix başarısızlıktır.

**Bu sürecin lafzını çiğnemek, debugging'in ruhunu çiğnemektir.**

## Demir Kural

```
ÖNCE ROOT CAUSE ARAŞTIRMASI, SONRA FIX — İSTİSNA YOK
```

Faz 1'i tamamlamadıysan fix öneremezsin.

## Ne zaman kullanılır

HER teknik sorun için:
- Test failure'ları
- Production'daki bug'lar
- Beklenmeyen davranış
- Performans sorunları
- Build failure'ları
- Entegrasyon sorunları (REST, STOMP/WebSocket, veritabanı)

**ÖZELLİKLE şu durumlarda kullan:**
- Zaman baskısı altındayken (acil durumlar tahmin yürütmeyi cazip kılar)
- "Hızlı bir fix" bariz göründüğünde
- Zaten birden fazla fix denediysen
- Önceki fix işe yaramadıysa
- Sorunu tam anlamadıysan

**Şu durumlarda atlama:**
- Sorun basit görünüyorsa (basit bug'ların da root cause'u vardır)
- Acelen varsa (acele etmek yeniden iş yapmayı garantiler)
- Biri "hemen düzelt" diyorsa (sistematik olmak, deneme-yanılma ile savrulmaktan daha hızlıdır)

## Dört faz

Bir sonraki faza geçmeden önce her fazı TAMAMLAMAN gerekir.

### Faz 1: Root Cause Investigation

**HERHANGİ bir fix denemeden ÖNCE:**

1. **Hata mesajlarını dikkatle oku**
   - Hata ve warning'leri atlama
   - Çoğu zaman çözümün kendisini içerirler
   - Stack trace'leri sonuna kadar oku
   - Satır numaralarını, dosya yollarını, hata kodlarını (`errorCode`) not et

2. **Tutarlı şekilde reproduce et**
   - Güvenilir şekilde tetikleyebiliyor musun?
   - Adımlar tam olarak ne?
   - Her seferinde oluyor mu?
   - Reproduce edilemiyorsa → daha fazla veri topla, tahmin yürütme

3. **Son değişiklikleri kontrol et**
   - Buna ne sebep olmuş olabilir?
   - `git diff`, son commit'ler
   - Yeni dependency'ler, config değişiklikleri
   - Ortam farkları (local / Docker / CI)

4. **Çok bileşenli sistemlerde kanıt topla**

   **Sistemde birden fazla bileşen varsa (tarayıcı → Vite proxy → Spring Boot → PostgreSQL, ya da STOMP client → broker → `@MessageMapping`):**

   **Fix önermeden ÖNCE diagnostic instrumentation ekle:**
   ```
   HER bileşen sınırı için:
     - Bileşene hangi verinin girdiğini log'la
     - Bileşenden hangi verinin çıktığını log'la
     - Environment/config'in aktarıldığını doğrula
     - Her katmandaki state'i kontrol et

   NEREDE kırıldığını gösteren kanıtı toplamak için bir kez çalıştır
   SONRA kanıtı analiz edip hatalı bileşeni belirle
   SONRA o bileşeni araştır
   ```

   **Örnek (çok katmanlı sistem — "ingestion ilerlemesi ekranda güncellenmiyor"):**
   ```
   Katman 1: Tarayıcı      → DevTools Network > WS sekmesi: SUBSCRIBE frame'i doğru destination'a gidiyor mu?
   Katman 2: Vite proxy    → vite.config.ts'te /ws için ws: true var mı? Upgrade isteği 101 dönüyor mu?
   Katman 3: Backend       → Log'da yayın yapılan destination ve payload ne? (traceId ile Jaeger'da ara)
   Katman 4: Client state  → Gelen mesaj TanStack Query cache'ine / state'e yazılıyor mu?
   ```

   **Ortaya çıkardığı şey:** Hangi katmanın bozulduğu (frame gidiyor ✓, backend yayınlıyor ✓, state'e yazılmıyor ✗)

5. **Data flow'u izle**

   **Hata call stack'in derinlerindeyse:**

   Geriye doğru izleme tekniğinin tamamı için bu dizindeki [root-cause-tracing.md](root-cause-tracing.md) dosyasına bak.

   **Kısa versiyon:**
   - Hatalı değer nereden geliyor?
   - Bunu hatalı değerle kim çağırdı?
   - Kaynağı bulana kadar yukarı doğru izlemeye devam et
   - Semptomda değil, kaynakta düzelt

### Faz 2: Pattern Analysis

**Düzeltmeden önce pattern'i bul:**

1. **Çalışan örnekleri bul**
   - Aynı codebase'de benzer, çalışan kodu bul
   - Bozuk olana benzeyen ve çalışan ne var?

2. **Referanslarla karşılaştır**
   - Bir pattern uyguluyorsan, referans implementation'ı TAMAMEN oku
   - Göz gezdirme — her satırı oku
   - Uygulamadan önce pattern'i tamamen anla

3. **Farkları belirle**
   - Çalışan ile bozuk arasında ne fark var?
   - Ne kadar küçük olursa olsun her farkı listele
   - "Bunun önemi olamaz" diye varsayma

4. **Dependency'leri anla**
   - Bu kodun ihtiyaç duyduğu diğer bileşenler neler?
   - Hangi ayarlar, config, environment?
   - Hangi varsayımlarda bulunuyor?

### Faz 3: Hypothesis and Testing

**Bilimsel yöntem:**

1. **Tek bir hipotez kur**
   - Açıkça söyle: "Root cause'un X olduğunu düşünüyorum, çünkü Y"
   - Yaz
   - Belirsiz değil, spesifik ol

2. **Minimal test et**
   - Hipotezi test etmek için mümkün olan EN KÜÇÜK değişikliği yap
   - Her seferinde tek değişken
   - Aynı anda birden fazla şeyi düzeltme

3. **Devam etmeden doğrula**
   - İşe yaradı mı? Evet → Faz 4
   - Yaramadı mı? YENİ bir hipotez kur
   - Üstüne başka fix EKLEME

4. **Bilmediğinde**
   - "X'i anlamıyorum" de
   - Biliyormuş gibi yapma
   - Yardım iste
   - Daha fazla araştır

### Faz 4: Implementation

**Semptomu değil, root cause'u düzelt:**

1. **Başarısız bir reproduction oluştur**
   - Mümkün olan en basit reproduction
   - Mümkünse otomatik test, test framework'ü yoksa tek seferlik bir script
   - Fix'ten ÖNCE olması ŞART
   - **Ekip akışında:** `frontend-coder` / `backend-coder` test yazmaz. Coder olarak çalışıyorsan reproduction adımlarını (girdi, beklenen, gerçekleşen) handoff dosyana yaz; regression testini `tester` agent yazar. Ekip akışı dışında (doğrudan kullanıcıyla) çalışıyorsan başarısız testi kendin yaz — test kalitesi için `writing-good-tests` skill'ine bak.

2. **Tek bir fix uygula**
   - Belirlenen root cause'u ele al
   - Her seferinde TEK değişiklik
   - "Hazır buradayken" iyileştirmeleri yok
   - Fix'e bulaştırılmış refactoring yok

3. **Fix'i doğrula**
   - Reproduction artık geçiyor mu?
   - Başka test kırılmadı mı? (frontend: `npm run lint`, `npm run build`, `npx vitest run`; backend: `springboot-verification`)
   - Sorun gerçekten çözüldü mü?

4. **Fix işe yaramazsa**
   - DUR
   - Say: Kaç fix denedin?
   - < 3 ise: Faz 1'e dön, yeni bilgiyle yeniden analiz et
   - **≥ 3 ise: DUR ve mimariyi sorgula (aşağıdaki 5. adım)**
   - Mimari tartışma yapmadan 4. fix'i DENEME

5. **3+ fix başarısız olduysa: Mimariyi sorgula**

   **Mimari soruna işaret eden pattern:**
   - Her fix farklı bir yerde yeni bir shared state / coupling / sorun ortaya çıkarıyor
   - Fix'ler "büyük refactoring" gerektiriyor
   - Her fix başka bir yerde yeni semptomlar yaratıyor

   **DUR ve temelleri sorgula:**
   - Bu pattern temelde sağlam mı?
   - "Sırf alışkanlıkla mı devam ediyoruz"?
   - Semptomları düzeltmeye devam etmek yerine mimariyi mi refactor etmeliyiz?

   **Daha fazla fix denemeden önce kullanıcıyla tartış**

   Bu başarısız bir hipotez DEĞİLDİR — bu yanlış bir mimaridir.

## Kırmızı bayraklar — DUR ve sürece uy

Kendini şunları düşünürken yakalarsan:
- "Şimdilik hızlı bir fix, sonra araştırırım"
- "X'i değiştirip işe yarıyor mu bakayım"
- "Birden fazla değişiklik yapıp testleri çalıştırayım"
- "Testi atlayayım, manuel doğrularım"
- "Muhtemelen X'tir, onu düzelteyim"
- "Tam anlamadım ama bu işe yarayabilir"
- "Pattern X diyor ama ben farklı uyarlayacağım"
- "Ana sorunlar şunlar: [araştırma yapmadan fix listesi]"
- Data flow'u izlemeden çözüm önermek
- **"Bir fix denemesi daha" (zaten 2+ deneme yapılmışken)**
- **Her fix farklı bir yerde yeni bir sorun ortaya çıkarıyor**

**BUNLARIN HEPSİ şu anlama gelir: DUR. Faz 1'e dön.**

**3+ fix başarısız olduysa:** Mimariyi sorgula (Faz 4, 5. adım).

## Kullanıcının yanlış yolda olduğunu gösteren sinyalleri

**Şu yönlendirmelere dikkat et:**
- "Bu olmuyor mu?" — Doğrulamadan varsaydın
- "Bize ... gösterecek mi?" — Kanıt toplama eklemeliydin
- "Tahmin yürütmeyi bırak" — Anlamadan fix öneriyorsun
- "Bunu derinlemesine düşün" — Sadece semptomları değil, temelleri sorgula
- "Takıldık mı?" (bıkkın) — Yaklaşımın işe yaramıyor

**Bunları gördüğünde:** DUR. Faz 1'e dön.

## Yaygın bahaneler

| Bahane | Gerçek |
|--------|--------|
| "Sorun basit, sürece gerek yok" | Basit sorunların da root cause'u vardır. Süreç basit bug'larda hızlıdır. |
| "Acil durum, sürece vakit yok" | Sistematik debugging, deneme-yanılma ile savrulmaktan DAHA HIZLIDIR. |
| "Önce bunu deneyeyim, sonra araştırırım" | İlk fix pattern'i belirler. Baştan doğru yap. |
| "Fix'in çalıştığını görünce testi yazarım" | Test edilmemiş fix'ler kalıcı olmaz. Önce test, kanıtlar. |
| "Birden fazla fix aynı anda zaman kazandırır" | Neyin işe yaradığını ayıramazsın. Yeni bug'lara yol açar. |
| "Referans çok uzun, pattern'i uyarlarım" | Kısmi anlama bug'ı garantiler. Tamamını oku. |
| "Sorunu görüyorum, düzelteyim" | Semptomu görmek ≠ root cause'u anlamak. |
| "Bir fix denemesi daha" (2+ başarısızlıktan sonra) | 3+ başarısızlık = mimari sorun. Pattern'i sorgula, tekrar düzeltmeye çalışma. |

## Hızlı referans

| Faz | Ana aktiviteler | Başarı kriteri |
|-----|-----------------|----------------|
| **1. Root Cause** | Hataları oku, reproduce et, değişiklikleri kontrol et, kanıt topla | NE ve NEDEN'i anla |
| **2. Pattern** | Çalışan örnekleri bul, karşılaştır | Farkları belirle |
| **3. Hypothesis** | Teori kur, minimal test et | Doğrulanmış ya da yeni hipotez |
| **4. Implementation** | Reproduction oluştur, düzelt, doğrula | Bug çözüldü, testler geçiyor |

## Süreç "root cause yok" sonucunu verirse

Sistematik araştırma sorunun gerçekten ortamsal, zamanlamaya bağlı ya da harici olduğunu gösteriyorsa:

1. Süreci tamamlamışsındır
2. Neleri araştırdığını dokümante et
3. Uygun handling'i uygula (retry, timeout, kullanıcıya Türkçe hata mesajı)
4. Gelecekteki araştırmalar için monitoring/logging ekle

**Ama:** "Root cause yok" vakalarının %95'i eksik araştırmadır.

## Destekleyici teknikler

Bu teknikler systematic debugging'in parçasıdır ve bu dizinde bulunur:

- **[root-cause-tracing.md](root-cause-tracing.md)** — Orijinal tetikleyiciyi bulmak için bug'ı call stack boyunca geriye doğru izle
- **[defense-in-depth.md](defense-in-depth.md)** — Root cause'u bulduktan sonra birden fazla katmana validation ekle
- **[condition-based-waiting.md](condition-based-waiting.md)** — Testlerdeki keyfi timeout'ları koşul bazlı beklemeyle değiştir (flaky testler)
- **`scripts/find-polluter.sh`** — Hangi test dosyasının istenmeyen bir dosya/state bıraktığını bulmak için testleri tek tek çalıştırır: `bash ${CLAUDE_SKILL_DIR}/scripts/find-polluter.sh <kontrol-edilecek-yol> '<test-pattern>'`
