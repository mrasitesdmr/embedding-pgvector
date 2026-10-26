---
name: writing-good-tests
description: Test kalitesi kuralları - her test yakaladığı kırılmayı adlandırır, beklenen değerler test edilen koddan bağımsız türetilir, mock'a değil gerçek davranışa assertion yapılır, mock doğru seviyede ve gerçek veri şekliyle kurulur, test-only kod production'a girmez, bitirmeden önce mutation check yapılır. Frontend (Vitest/RTL/MSW) veya backend (JUnit/Mockito) test yazarken ya da değiştirirken, mock veya test helper eklerken, testleri review ederken ve mutation testi (Stryker/PIT) hayatta kalan mutant'ları kapatırken kullan.
license: MIT
metadata:
  origin: obra/superpowers
  source: https://github.com/obra/superpowers/blob/main/skills/test-driven-development/writing-good-tests.md
  adapted: superpowers'ın test-driven-development skill'inin referans dosyası bağımsız skill yapıldı; örnekler proje test kurallarına (given_when_then, AAA, MSW) uyarlandı.
---

# İyi Test Yazmak

> **Proje kuralı:** Test isimlendirmesi (`given<Precondition>_when<Action>_then<ExpectedResult>`), AAA gövde yapısı, MSW zorunluluğu ve query önceliği `frontend-conventions` bölüm 11 ve `backend-conventions`'ta tanımlıdır. Bu skill *hangi testin yazılmaya değer olduğunu* ve *testin dürüst olup olmadığını* belirler; biçim kuralları o skill'lerden gelir.

## Genel bakış

Bir test, belirli bir kırılmayı yakalamak için vardır. Her şeyi iki ilke yönetir:

```
1. Her test yakaladığı kırılmayı adlandırır
2. Her test gerçek şeyi çalıştırır
```

Önce yazılıp gerçek koda karşı fail ettiği görülen bir test, fail edebileceğini zaten kanıtlamıştır; mock'u da ancak gerçek dependency yavaş veya harici olduğunda hak eder.

## İlke 1: Kırılmayı adlandır

Test gövdesini yazmadan önce cevapla: **Hangi production değişikliği bu testi fail ettirmeli — ve o değişiklik bir bug mı, yoksa bir karar mı?** Bir test; yanlış branch'i, eksik side effect'i, yanlış argümanı, sınır durumunu veya bozuk bir sözleşmeyi yakalayarak yerini hak eder.

**Beklenen değerleri bağımsız türet.** Literal'ler ve elle kontrol edilmiş fixture'lar kullan; literal `expected` değerli table-driven testler tercih edilen şekildir (`it.each`, JUnit `@ParameterizedTest`). Test edilen kodun — ya da onun helper'larının — hesapladığı bir beklenti, o kod ne yaparsa yapsın geçer:

```typescript
// ❌ Ayna assertion: aynı builder iki tarafı da hesaplıyor — her zaman true
const expected = formatScore(0.8731)
expect(formatScore(0.8731)).toBe(expected)

// ✅ Elle türetilmiş literal
expect(formatScore(0.8731)).toBe('%87,3')
```

**Change detector yok.** Bir testi sadece bilinçli kararlar fail ettirebiliyorsa — bir sabitin değeri, mesajın kelimesi kelimesine metni, private yapı — redesign'da alarm verir, bug'larda uyur. Kararın kendisini değil, karara bağlı davranışı test et: `expect(MAX_SOURCES_PER_WORKSPACE).toBe(10)` değil, "10 kaynaklı çalışma alanına 11. kaynak eklenemez ve `SOURCE_LIMIT_REACHED` mesajı gösterilir."

> **Not:** Bu projede kullanıcıya gösterilen Türkçe metin bir sözleşmedir: her `errorCode` için doğru Türkçe mesajın gösterildiği test edilir (`frontend-conventions`). Bu bir change detector değildir; metin, kullanıcının gördüğü davranıştır.

**Metin değil, davranış.** Bir script'in veya config'in belirli bir satırı içerdiğine assertion yapmak, sadece kaynağın kaynak olduğunu kanıtlar. Script'leri kontrollü girdilerle çalıştır ve çıktıya, side effect'lere veya exit code'a assertion yap.

**Framework'ü değil, kendi kodunu test et.** Kodunun sınırlarında verdiği sözü test et — yaptığın istek, ürettiğin payload, gösterdiğin state. Upstream mekanikleri onların maintainer'larının testidir (klasik örnek: router'ın kayıtlı handler'ı çağırdığına assertion yapmak — bu framework'ün testidir, seninki değil; TanStack Query'nin cache'lediğini test etmek de öyle). Upstream davranışı seni gerçekten şaşırttıysa, varsayımı adlandıran tek, dar bir characterization testi yaz. Aynı sınır kodunun içinde de geçerli: constructor'lar, getter'lar, sabitler ve basit yönlendirmeler ancak validate ettiklerinde, normalize ettiklerinde, default verdiklerinde, türettiklerinde, kural uyguladıklarında veya side effect yarattıklarında test hak eder — yoksa onlara bağlı ilk tüketici-görünür sonuca assertion yap.

### Gate function

```
Test gövdesini yazmadan ÖNCE:
  Bu testi fail ettirecek production değişikliğini adlandır.

  Adlandıramıyorsan          → gözlemlenebilir bir davranış etrafında yeniden tasarla
  "Kaynak metin değişti"     → artifact'i çalıştır ve etkilerine assertion yap
  Sadece bilinçli kararlar   → change detector; karara bağlı davranışı test et

  Beklenen değerin test edilen kod kullanılmadan türetildiğini doğrula.
  EĞER kodun mantığını veya helper'larını yeniden kullanıyorsa:
    Literal veya elle kontrol edilmiş fixture ile değiştir
```

## İlke 2: Gerçek şeyi çalıştır

**Mock assertion hak etmez.** Mock'a yapılan bir assertion, mock varken geçer ve yokken fail eder — component hakkında hiçbir şey söylemez. Gerçek component'in davranışına assertion yap; kontrol ettiğin şey mock ise ya mock'u kaldır ya da assertion'ı sil.

```typescript
// ✅ Gerçek davranış
expect(screen.getByRole('navigation')).toBeInTheDocument()

// ❌ Mock'un varlığı
expect(screen.getByTestId('sidebar-mock')).toBeInTheDocument()
```

**Kullanıcının düzeltmesi:** "Bir mock'un davranışını mı test ediyoruz?"

**Doğru seviyede mock'la.** Gerçek metodu değiştirmeden önce tüm side effect'lerini öğren; yavaş veya harici işlemi mock'la, testin bağlı olduğu şeyi gerçek bırak. Emin değilsen testi önce gerçek implementation'a karşı çalıştır ve gerçekte ne olması gerektiğini gözlemle.

```typescript
// ❌ API modülünü mock'lamak: httpClient'ın ProblemDetail → ApiError dönüşümü,
//    errorCode → Türkçe mesaj eşlemesi ve URL/payload test dışında kalır
vi.mock('../api/sourceApi.ts', () => ({ createSource: vi.fn().mockResolvedValue(source) }))

// ✅ Sadece network sınırını mock'la (MSW); httpClient, hata eşlemesi ve component gerçek
server.use(http.post('/api/workspaces/:workspaceId/sources', () => problem(409, 'SOURCE_LIMIT_REACHED')))
```

Backend'de karşılığı: repository *sorgularının* davranışı mock'la değil Testcontainers ile gerçek PostgreSQL'e karşı `@DataJpaTest` ile test edilir (`spring-jpa-testing`). Service unit testlerinde repository I/O sınırı olduğu için mock'lanır (`backend-conventions` bölüm 5); ama assertion mock'a değil service'in gözlemlenebilir sonucuna (dönüş, exception, kaydedilen değer) yapılır.

**Test double'larını spesifik yap.** Argümanlar, çağrı sayıları veya sıralama sözleşmenin parçasıysa onlara assertion yap — her şeyi kabul eden bir fake hiçbir şeyi doğrulamaz. Her branch'e (başarı, hata, bozuk veri) kendi fixture'ını veya spy'ını ver ki yanlış branch beklentiyi karşılayamasın.

**Gerçek veriyi eksiksiz yansıt.** Mock'u gerçekte var olduğu haliyle tam yapıda kur — sadece testin okuduğu alanları değil, dokümante edilen tüm alanları (`docs/api/openapi.yaml`). Kısmi mock'lar, downstream kod atlanan bir alanı okuduğunda sessizce fail eder: test geçer, entegrasyon kırılır.

**Production sınıfları sadece production metotları taşır.** Sadece testlerin ihtiyaç duyduğu cleanup test utility'lerinde yaşar (`src/test/`, backend'de test source set), production sınıfında `reset()` / `destroy()` olarak değil. Sor: Bu metot sadece testlerden mi çağrılıyor? Bu sınıf bu kaynağın yaşam döngüsünün sahibi mi? Yanlış cevaplar → test utility.

**Karmaşık mock'lar yerine gerçek component'leri tercih et.** Mock kurulumu test mantığını aştığında, mock'lar gerçek component'lerin sahip olduğu metotları kaçırdığında veya mock değişince testler kırıldığında, gerçek component'lerle bir integration testine geç. **Kullanıcının sorusu:** "Burada gerçekten mock kullanmamız gerekiyor mu?"

### Gate function

```
Bir mock veya test helper eklemeden ÖNCE:
  Gerçek metodun side effect'lerini listele; testin bağlı olduklarını
  gerçek bırak — altındaki yavaş/harici seviyeyi mock'la.

  Mock response'ları gerçek yapının tamamını yansıtır.

  Sadece testlerin çağırdığı metot test utility'lerinde yaşar, production'da değil.

  Mock'un kendisine assertion yapmak üzeresin?
    Mock'u kaldır ya da assertion'ı sil.
```

## Testler implementation ile birlikte gelir

Davranışın ihtiyaç duyduğu testler ve sadece onlar: önemsiz kod ve insanlar için yazılmış metin test hak etmez; süreci tatmin etmek için yazılmış bir test sonsuza kadar bakım maliyeti çıkarır. Ekip akışında testleri `tester` agent yazar; plandaki her test senaryosu bir kırılmayı adlandırmalıdır.

## Mutation check

Bitirmeden önce production kodunu zihninde mutasyona uğrat; her gerçekçi mutasyon için en az bir test fail etmeli:

- Yanlış sabit veya argüman
- Yanlış branch handler'ı
- Eksik state değişikliği veya side effect
- Boş veya default return
- Sıfır, boş, null, yetkisiz veya bozuk girdi için eksik validation

Hiçbir testin yakalamadığı bir mutasyon, o davranışı korumasız — ya da testi totolojik — olarak işaretler. Aynı kontrolü araçla yapmak için mutation testing çalıştır (frontend: Stryker; backend'de yapılandırılmışsa PIT); hayatta kalan her mutant ya eksik bir test vakasıdır ya da ölü koddur.

## Hızlı referans

| Şunu yaparken... | Şunu yap |
|------------------|----------|
| Herhangi bir test yazarken | Yakaladığı kırılmayı adlandır — karar değil, bug |
| Beklenen değer oluştururken | Elle türet; asla test edilen kodla değil |
| Script veya config test ederken | Çalıştır; asla metnini grep'leme |
| Dependency testi yazmak isterken | Onların dokümante mekaniğini değil, kendi sınır sözleşmeni test et |
| Mock'lanmış bir elemana assertion yapmak isterken | Gerçek component'i test et veya mock'u kaldır |
| Bir metodu mock'lamak üzereyken | Side effect'lerini öğren; yavaş/harici seviyeyi mock'la |
| Mock response kurarken | Gerçek yapıyı (OpenAPI şeması) eksiksiz yansıt |
| Sadece testlerin kullandığı cleanup gerektiğinde | Test utility'lerine koy |
| Mock kurulumu şiştiğinde | Gerçek component'lerle integration testine geç |
| Bir test dosyasını bitirirken | Mutation check yap |

## Uyarı işaretleri

- Setup ve assertion aynı nesneyi paylaşıyor, eşitliği garanti ediyor
- Test sadece panic, crash veya eksik selector yüzünden fail edebiliyor
- Test her bilinçli değişiklikte fail ediyor, kazara kırılmada asla
- Beklenen değerler döngülerin, builder'ların veya helper'ların arkasına gizlenmiş
- Test kaynak metni grep'liyor ya da silinmiş bir sembolün silinmiş kaldığına assertion yapıyor
- Sadece framework kalsa bile test anlamını korurdu
- Test coverage için var, hiçbir side effect'i veya sonucu kontrol etmiyor
- Bir assertion `*-mock` test ID'sini kontrol ediyor ya da mock kaldırılınca fail ediyor
- Bir metot sadece test dosyalarından çağrılıyor
- Mock kurulumu testin yarısından fazla, ya da mock'un neden gerektiğini açıklayamıyorsun
- "Ne olur ne olmaz" diye mock'lamak
