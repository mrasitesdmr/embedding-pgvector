---
name: frontend-design
description: Yeni bir UI oluştururken ya da mevcut bir UI'ı yeniden şekillendirirken özgün ve bilinçli görsel tasarım rehberi. Estetik yön, typography ve template varsayılanı gibi görünmeyen seçimler yapmaya yardım eder. frontend/ altında ekran, sayfa veya component görünümü tasarlarken kullan.
metadata:
  origin: anthropics/skills
  source: https://github.com/anthropics/skills/tree/main/skills/frontend-design
---

# Frontend Design

> **Proje kuralı:** Bu skill'in ürettiği palette/type/spacing kararları `src/index.css`'teki CSS custom property token'larına dönüşür; component'ler sadece token kullanır, stil CSS Modules ile yazılır, UI metni Türkçedir (`frontend-conventions` bölüm 2 ve 12). "Müşteriyle doğrula" adımı ekip akışında planner'ın onay kartıdır (`handoff/00-onay-karti.md`). Font'lar CDN'den (Google Fonts vb.) yüklenmez: CSP `font-src 'self'`'tir ve üçüncü taraf font eklemek onay gerektirir (`frontend-security`); seçilen font dosyaları projeye dahil edilip kendi origin'imizden sunulur, gereken paket/dosya eklemek plan kararıdır. Hedef kitle ve cihaz önceliği ürün dokümanında (`embedding-pgvector.md`) tanımlanır; tanımlı değilse planner bunu onay kartına soru olarak yazar. Her durumda ekran en dar 360 px'te de kullanılabilir olmalıdır. Görsel dilin başlangıç referansı kökteki anlatım sayfaları ve `lab.html`'dir.

Bu işe, her müşterisine başkasınınkiyle karıştırılmayacak özgün bir görsel kimlik vermesiyle tanınan bir tasarım stüdyosunun design lead'i gibi yaklaş. Bu müşteri klişe ya da template gibi duran teklifleri zaten reddetti ve özgün bir bakış açısı için para ödüyor: palette, typography ve layout konusunda bu brief'e özel, bilinçli ve net görüşlü seçimler yap; gerekçesi varsa estetik risk al.

## Tasarımı konunun kendisine dayandır

Brief ürünün veya konunun ne olduğunu söylemiyorsa, tasarlamadan önce bunu kendin belirle ve müşteriyle doğrula. Öneri olarak somut bir konu, tasarımın hedef kitlesi ve tasarımın birincil işi ortaya koyabilirsin. Hafızanda müşterinin tercihleri ya da ne inşa ettiğiyle ilgili bilgi varsa bunu ipucu olarak kullan. Özgün görsel seçimler konunun sektöründen, içeriğinden, malzemelerinden ve kendine has dilinden çıkar; 8–11 yaş kız çocukları için bir oyuncağın tasarımı, finans analistleri için bir dashboard'dan estetik olarak çok farklı olacaktır. Baştan sona brief'in gerçek içeriği ve konusuyla çalış.

## Tasarım ilkeleri

Web tasarımlarında hero, izleyicinin ilk gördüğü şeydir. Konunun dünyasındaki en karakteristik şeyle, en uygun biçimde aç: bir başlık, bir görsel, bir animation, canlı bir demo, interaktif bir an ya da başka bir yaklaşım. Seçimini bilinçli yap: küçük bir label'lı büyük bir sayı, destekleyici istatistikler ve gradient bir vurgu varsayılan yaklaşımdır; bunu sadece gerçekten en iyi seçenekse kullan.

Sayfanın kişiliğini typography taşır. Display/başlık metni ile gövde içeriği için farklı bir typeface kullanman gerekmez: bir ya da iki family kullan; iki tane kullanıyorsan birbirinden açıkça ayrışsınlar.

Typeface'leri bilinçli seç; başka herhangi bir projede de uzanacağın varsayılan family'leri değil. The Elements of Typographic Style'ın varsayılan önerilerini izleyerek bilinçli weight, width ve spacing ile net bir type scale kur. Yazı bir başlık ya da görsel öğe olarak kullanıldığında, yazının işlenişini içeriği taşıyan nötr bir araç olarak değil, tasarımın aktif bir parçası olarak kullan.

Varsayılan olarak satır uzunluğunu 80 karakterin altında tut. Serif typeface'lerde satır biraz daha uzun olabilir; serif gövde metnine sans-serif'e göre biraz daha fazla line-height ver.

Şu varsayılan typography uygulamalarından kaçın; üretilmiş (generated) bir sayfanın en yaygın ele veren işaretleri bunlardır:
- Başlıkta tek bir kelimeyi ya da ifadeyi vurgulamak (bir kelimeyi italic/bold yapmak veya farklı renge boyamak gibi).
- Label'lar için tamamen büyük harf (all caps) kullanmak.
- İçeriğin üstüne gereksiz typography label'ları eklemek.

Görsel yapı bilgidir. Outline, border, numaralandırma, eyebrow, divider, label gibi yapısal araçlar içeriği süslemez; içerik hakkında işe yarar bilgi kodlar. Pek çok jenerik tasarım numaralı işaretler (01 / 02 / 03) kullanır, ama bu sadece içerik gerçekten bir sıra ise uygundur; adım adım bir süreç ya da bir zaman çizelgesi gibi. Numaralı işaret eklemeden önce içeriğin gerçekten bir sıra olduğunu kontrol et.

Kullanıcının tetiklemediği hareketi (motion) az ve bilinçli kullan, sadece dikkat çekmek için. Tek bir orkestre edilmiş an (bir page-load sekansı ya da bir reveal), dağınık efektlerden daha iyi etki bırakır; her section'da fade-and-slide-up girişleri ve her card'da hover transition'ları jenerik varsayılandır ve AI üretimi gibi okunur. Bir kişinin eylemine cevap veren hareket (açma, genişletme, onaylama) neyin değiştiğini gösteriyorsa memnuniyetle kullanılabilir.

Yazılı içeriği dikkatle düşün. Tasarım brief'leri çoğu zaman gerçek içerik içermez; metni (copy) ve placeholder içeriği üretmek sana kalır. Metin, bir tasarımı tasarımın kendisi kadar template gibi hissettirebilir. Daha fazla rehberlik için aşağıdaki yazım bölümüne bak.

## Süreç: planla, brief'e karşı gözden geçir, inşa et, eleştir

Ayar noktası olarak: AI üretimi tasarımlar şu sıralar bazı özelliklerin etrafında kümeleniyor:
1. yüksek kontrastlı serif display ve terracotta ya da sıcak kil tonu vurgu ile sıcak krem arka plan (#F4F1EA civarı) (vurgu çoğunlukla #D97757 civarındadır; bu Anthropic'in kendi Claude etkileşim vurgusudur, dolayısıyla bir kullanıcının brief'inde ele veren bir işaret olarak okunur);
2. tek bir parlak asit yeşili ya da vermilyon vurgulu, siyaha yakın arka plan;
3. ince çizgiler (hairline rule), sıfır border-radius ve gazete benzeri yoğun kolonlarla broadsheet tarzı bir layout;
4. SaaS-card kiti: içeriğin birbirinin aynısı yuvarlatılmış card'lara bölünmesi, hiyerarşiden bağımsız olarak her şeyde tek bir border-radius, her birinin altında aynı yumuşak gri shadow (rgba(0,0,0,.1)) ve dekorasyon olarak gradient geçişler;
5. konu ne olursa olsun görünen template süslemeleri: her başlığın üstünde harf aralığı açılmış TAMAMEN BÜYÜK HARF eyebrow label; orta noktayla birleştirilmiş meta metinler ('A · B · C'); boşluklu em dash ile 'KELİME — parça' şeklinde kurulmuş label'lar; siyah yerine kullanılan renk tonu verilmiş siyaha yakın renkler (#0B0B0B, #111); küçük veri label'ları için monospace font; link ve buton metnine eklenmiş '→'.

Bu özelliklerin hepsi bazı brief'ler için meşrudur, ama bunlar seçim değil varsayılandır ve konudan bağımsız olarak ortaya çıkarlar. Brief görsel bir yönü netleştiriyorsa tam olarak onu izle; brief'in kendi sözleri her zaman kazanır, bu görünümlerden birini istediğinde bile. Brief bir ekseni serbest bırakıyorsa, o serbestliği bu varsayılanlardan birine harcama. İşe alınmış bir insan tasarımcıda olduğu gibi, iyi olduğun şeyi yapmakla her projeyi deneme ve öğrenme fırsatı olarak görmek arasında çoğu zaman hassas bir denge vardır.

İki geçişte çalış. Önce müşterinin tasarım brief'ine dayanarak kısa bir tasarım planı için beyin fırtınası yap: color, type, layout ve ilkelerden oluşan kompakt bir token sistemi oluştur.
- Color: temel palette'i 4–6 isimlendirilmiş hex değeri olarak tanımla.
- Type: typeface'ler ve rolleri.
- Layout: tek cümlelik düz yazı açıklamaları ve fikir üretip karşılaştırmak için ASCII wireframe'lerle bir layout konsepti. Hizalama rehberliği de ekle; içerik sola mı, ortaya mı hizalanmalı, iki yana yaslanmalı mı?
- İlkeler: bu sayfayı özgün kılan şeye dair üst düzey rehberlik.

Ardından inşa etmeden önce bu planı brief'e karşı gözden geçir: planın herhangi bir kısmı bu brief'e özel yapılmış bir seçim değil de benzer herhangi bir sayfa için üreteceğin jenerik varsayılan gibi okunuyorsa (benzer bir prompt üzerinden geçerek benzer bir yere varıp varmadığına bak) o kısmı revize et, neyi neden değiştirdiğini söyle. Kodu yazmaya, tasarım planının görece özgünlüğünü doğruladıktan sonra ve revize edilmiş planı izleyerek başla.

Kodu yazarken CSS selector specificity'lerini nasıl yapılandırdığına dikkat et. Birbirini iptal eden CSS class'ları üretmek kolaydır (özellikle .section gibi tipe dayalı bir selector ile .cta gibi elemana dayalı bir selector bir arada olduğunda). Bu durum section'lar arasındaki padding/margin'de sık yaşanır.

## Ölçülülük ve öz eleştiri

Cesaretini tek bir yerde harca. Akılda kalan şey tek bir öğe olsun, etrafındaki her şeyi sakin ve disiplinli tut, brief'e hizmet etmeyen her süslemeyi çıkar. Bunu ilan etmeden bir kalite tabanına göre inşa et: mobile kadar responsive, görünür keyboard focus, reduced motion'a saygı, görsel olarak erişilebilir, uyumlu renk palette'leri. İnşa ederken kendi işini eleştir; ortamın destekliyorsa gözden geçirmek için screenshot al, bir resim 1000 token'a bedeldir. Chanel'in tavsiyesini düşün: evden çıkmadan önce aynaya bak ve bir aksesuarı çıkar. İnsan yaratıcıların hafızası vardır ve hep yeni bir şey yapmaya çalışırlar; denediklerin hakkında hızlıca not alabileceğin bir alan varsa, bu sonraki geçişlerde sana yardımcı olabilir.

## Tasarımda yazım hakkında daha fazlası

Kelimeler bir tasarımda tek bir sebeple yer alır: anlamayı ve kullanmayı kolaylaştırmak. Onlar dekorasyon değil, tasarım içeriğidir. Copywriting'e de spacing ve renge gösterdiğin bilinçliliği ve sadeliği göster. Bir şey yazmadan önce tasarımın ne söylemesi gerektiğini ve kişinin deneyimde yolunu bulmasına yardım etmek için bunun en iyi nasıl söylenebileceğini sor.

Son kullanıcının bakış açısından yaz. Şeyleri sistemin nasıl kurulduğuna göre değil, kullanıcıların sade bir dille anlayacağı şekilde adlandır. Kullanıcı webhook config'ini değil, bildirimlerini yönetir. Bir şeyin ne olduğunu ya da ne yaptığını satmaya çalışmadan sade bir dille anlat. Yeni kullanıcılar için net ve okunaklı olmak, zekice olmaktan her zaman iyidir.

Varsayılan olarak etken çatı kullan. Bir CTA kullanıldığında tam olarak ne olacağını söyler: "Gönder" değil, "Değişiklikleri kaydet". Bir eylem tüm akış boyunca aynı adı korur; "Yayınla" diyen buton "Yayınlandı" diyen bir toast üretir. Bir arayüzün kelime dağarcığı, üründe yolunu bulan kişi için yön tabelasıdır. İnsanlar yollarını bütünlük ve tutarlılık sayesinde öğrenir.

Hatayı ve boşluğu bir ruh hali değil, yön gösterme anı olarak ele al. Neyin yanlış gittiğini ve nasıl düzeltileceğini bir kişinin değil arayüzün sesiyle açıkla. Hatalar özür dilemez ve ne olduğu konusunda asla belirsiz değildir. Boş bir ekran harekete geçme davetidir.

Tonu sohbet havasında tut: sade fiiller, sentence case, dolgu yok; ton markaya ve hedef kitleye uygun olsun. Her yazılı öğe tam olarak tek bir iş yapsın.
