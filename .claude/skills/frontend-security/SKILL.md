---
name: frontend-security
description: "React SPA için security hardening - threat model (trust boundary'ler, STRIDE), XSS önleme (dangerouslySetInnerHTML, javascript: URL'leri, içe alınan doküman ve web sayfası içeriği, arama sonucu vurgulama), tarayıcıda saklanan veri, Vite env variable'larının public olması, CSP ve security header'lar, client-side validation'ın güvenlik sınırı olmaması, hata detaylarının gizlenmesi, npm dependency ve supply chain güvenliği. frontend/ altında kullanıcı girdisi, URL parametresi, REST/STOMP'tan gelen veri (özellikle parça içeriği) işleyen kod yazarken veya review ederken, embedding API anahtarı gibi secret'ların frontend'e sızmamasını kontrol ederken, .env veya CSP/header ayarlarını değiştirirken, yeni npm paketi eklerken ya da npm audit bulgularını triage ederken kullan."
license: MIT
metadata:
  origin: addyosmani/agent-skills
  source: https://github.com/addyosmani/agent-skills/tree/main/skills/security-and-hardening
  adapted: Frontend'e (React 19 + Vite SPA) daraltıldı; backend/Node.js'e özel bölümler (SQL injection, SSRF, rate limiting, password hashing, dosya sistemi) çıkarıldı — backend için springboot-security.
---

# Frontend Security ve Hardening

> **Proje kuralı:** Backend güvenliği (authn/authz, CORS, rate limiting, header'ların backend tarafı) `springboot-security`'nin konusudur. Projede hesap, oturum ve token yoktur; tarayıcıda sadece gizli olmayan lab tercihleri `localStorage`'da tutulur (`frontend-conventions` bölüm 9). Embedding API anahtarı sadece backend'dedir ve frontend'e hiçbir yoldan ulaşmaz. Bu skill'deki kuralların asıl hedefi, içe alınan (web'den taranmış ya da yüklenmiş) içeriğin ekranda gösterilirken kod çalıştırmamasıdır.

## Genel bakış

Web uygulamaları için güvenliği önceleyen geliştirme pratikleri. Her dış girdiyi düşmanca, her secret'ı kutsal, her yetki kontrolünü zorunlu kabul et. Güvenlik bir faz değildir — kullanıcı verisine, kimliğe veya dış sistemlere dokunan her kod satırı üzerinde bir kısıttır.

Frontend'de unutulmaması gereken gerçek: **tarayıcıdaki kod kullanıcının kontrolündedir.** Frontend güvenlik sınırı değildir; görevi XSS'i önlemek, secret sızdırmamak, kullanıcının oturumunu korumak ve backend'in kurallarını atlatmaya çalışmamaktır.

## Ne zaman kullanılır

- Kullanıcı girdisi kabul eden her şeyi yazarken (arama metni, kaynak adı, URL parametresi)
- İçe alınan içeriği gösterirken (parça metni, kaynak adı, web sayfasından gelen başlık ve linkler)
- Arama sonucunda eşleşen kelimeleri vurgularken (HTML üretme cazibesi)
- REST veya STOMP üzerinden gelen veriyi işlerken
- Embedding/LLM sağlayıcısıyla ilgili bir ayarı frontend'e taşımak gündeme geldiğinde
- `.env`, Vite config, CSP veya header ayarlarını değiştirirken
- Yeni bir npm paketi eklerken veya `npm audit` bulgularını değerlendirirken

## Süreç: Önce threat model

Threat model olmadan eklenen kontroller tahmindir. Hardening'den önce beş dakika saldırgan gibi düşün:

1. **Trust boundary'leri haritala.** Untrusted veri sisteme nereden giriyor? Frontend için: form alanları, URL/route parametreleri (`/workspaces/:workspaceId/sources/:sourceId`), query string, REST response'ları (**parça içeriği**), STOMP mesajları, `localStorage` içeriği (aynı origin'deki herhangi bir script yazabilir), clipboard, üçüncü taraf script'ler. Güven, değeri hangi kanalın getirdiğine değil, *kimin yazdığına* bağlıdır: backend'den gelen bir parça metnini, taranan web sayfasının yazarı yazmıştır. Her sınır saldırı yüzeyidir.
2. **Varlıkları adlandır.** Çalınmaya veya bozulmaya değer ne var? embedding API anahtarı ve kotası (sızarsa fatura başkasının kullanımıyla dolar), kullanıcının yazdığı sorgu metinleri (kişisel/hassas olabilir), çalışma alanlarının birbirinin içeriğini görmemesi, kullanıcının tarayıcısında kod çalıştırma.
3. **Her sınır üzerinde STRIDE çalıştır** — tören değil, hızlı bir mercek:

| Tehdit | Sor | Tipik önlem |
|---|---|---|
| **S**poofing | Biri başka bir çalışma alanı adına istek atabilir mi? | Çalışma alanı erişimi backend'de kontrol edilir; URL'deki id'ye güvenilmez |
| **T**ampering | Veri transit veya saklama sırasında değiştirilebilir mi? | HTTPS/WSS, kurallar backend'de uygulanır |
| **R**epudiation | Bir eylem sonradan inkar edilebilir mi? | Backend'de audit log (frontend sorumluluğu değil) |
| **I**nformation disclosure | Veri sızabilir mi? | Sorgu metni log'lanmaz/analytics'e gitmez, `detail`/stack trace gösterilmez, API anahtarı bundle'da yok |
| **D**enial of service | Bunaltılabilir mi? (her arama ücretli bir embedding çağrısıdır) | Girdi uzunluk sınırları, gönderim sırasında buton kilidi, her tuşta arama yok, backend rate limit |
| **E**levation of privilege | Biri sahip olmaması gereken işlemi (kaynak silme, toplu yeniden embedding) yapabilir mi? | Butonu gizlemek yetki değildir; yetki backend'de kontrol edilir |

4. **Kullanım senaryolarının yanına kötüye kullanım senaryolarını yaz.** Her feature için "bunu nasıl kötüye kullanırdım?" diye sor — örn. içe alınan bir web sayfasına `<img src=x onerror=alert(1)>` koymak ve sonucun arama ekranında render edilmesini beklemek, parçadaki linke `javascript:` URL'i koymak, URL'deki `workspaceId`'yi değiştirip başka kiracının kaynaklarını istemek, arama kutusuna 10 000 karakter yapıştırmak — sonra bunu `tester` için bir test senaryosu yap.

Bir feature'ın trust boundary'lerini sayamıyorsan, onu güvenli hale getirmeye hazır değilsin. Bu OWASP Top 10:2025'te **A06: Insecure Design**'dır — çoğu ihlal kodda değil tasarımda başlar.

## Üç katmanlı sınır sistemi

### Her zaman yap (istisnasız)

- **React'in otomatik escaping'ini kullan** — kullanıcı içeriğini her zaman `{value}` olarak render et
- **Kullanıcı kaynaklı URL'leri** `href`/`src`'ye koymadan önce protokol allowlist'inden geçir (`http:`/`https:`)
- **Tüm iletişimde HTTPS/WSS** kullan (production'da `ws://` yok)
- **Gelen veriyi untrusted kabul et** — REST ve STOMP payload'larının şeklini kullanmadan önce doğrula
- **Kullanıcıya sadece `errorCode`'dan üretilmiş Türkçe mesaj göster** — `detail`, stack trace, ham response asla
- **Lockfile'ı commit'le ve CI'da `npm ci` kullan**; her release öncesi `npm audit` çalıştır

### Önce sor (insan onayı gerekir)

- Tarayıcıda yeni bir veri saklamak (özellikle sorgu geçmişi) ya da saklanan verinin kapsamını genişletmek
- Yeni bir runtime dependency eklemek (zaten plan kararıdır)
- Üçüncü taraf script, font, analytics veya CDN eklemek
- CSP veya diğer security header'ları değiştirmek
- `dangerouslySetInnerHTML` gerektiren herhangi bir özellik
- Kullanıcı verisini (sorgu metni dahil) üçüncü bir servise göndermek; embedding'i tarayıcıdan doğrudan sağlayıcıya göndermek

### Asla yapma

- **Asla secret'ı frontend koduna veya `VITE_` ile başlayan env variable'a koyma** — bundle'a gömülür ve herkes okuyabilir
- **Asla embedding/LLM API anahtarını frontend'e taşıma** — "sadece demo" için bile; tarayıcıdan sağlayıcıya doğrudan istek atılmaz
- **Asla sorgu metnini log'lama**, URL'ye koyma (paylaşılabilir arama linki plan kararıdır) veya hata raporuna ekleme
- **Asla client-side validation'a güvenlik sınırı olarak güvenme** — sadece UX içindir
- **Asla kullanıcı verisiyle `eval()`, `new Function()`, `innerHTML` veya `dangerouslySetInnerHTML` kullanma**
- **Asla UI'da bir butonu gizlemeyi yetkilendirme sanma** — yetki backend'de kontrol edilir
- **Asla `npm audit fix --force`'u körlemesine çalıştırma**

## Hardening kontrolleri

Aşağıdaki kurallar iş akışıdır; her birinin somut implementation'ı [references/hardening-patterns.md](references/hardening-patterns.md) dosyasındadır. İhtiyacın olan bölümü o koda geldiğinde aç, önceden değil.

### XSS

- React, `{value}` ile render edilen string'leri otomatik escape eder; bu varsayılanı bypass etme.
- `dangerouslySetInnerHTML` bu projede kullanılmaz. Ham HTML gerçekten kaçınılmazsa (plan kararı), önce allowlist tabanlı bir sanitizer (DOMPurify) ile temizle.
- React `href`/`src` içindeki `javascript:` URL'lerini her durumda engellemez: kullanıcı kaynaklı her URL'yi `new URL()` ile parse et ve protokolü allowlist'le.
- Ref üzerinden DOM'a doğrudan yazarken (`ref.current.innerHTML`) aynı kurallar geçerlidir; `textContent` kullan.
- İçe alınan her metin (parça içeriği, web sayfası başlığı, kaynak adı) XSS payload'ı olabilir — frontend her zaman düz metin olarak render eder.
- Arama sonucunda eşleşen kelimeyi vurgulamak için HTML string üretilmez (`<mark>` içeren string + `dangerouslySetInnerHTML` yasak); metin parçalara bölünür ve eşleşen parça `<mark>{part}</mark>` olarak React elemanıyla render edilir.
- Markdown kaynaklarının içeriği render edilecekse (plan kararı) HTML'e izin vermeyen bir renderer ve allowlist'li link protokolü kullanılır.

Pattern'ler: [XSS](references/hardening-patterns.md#xss), [URL güvenliği](references/hardening-patterns.md#kullanıcı-kaynaklı-urller).

### Tarayıcıda saklanan veri

- Arama ayarları sadece `features/search/model/searchSettings.ts` üzerinden okunur/yazılır; başka hiçbir yer `localStorage`'a doğrudan erişmez.
- Web storage'daki her değer, sayfadaki herhangi bir script tarafından okunabilir ve yazılabilir; bu yüzden storage'a gizli bilgi yazılmaz ve okunan değer untrusted kabul edilir: parse et, şeklini doğrula (bilinmeyen arama modu, aralık dışı `ef_search`), bozuksa varsayılana dön.
- Sorgu geçmişi saklanmaz; saklanacaksa (plan kararı) kapsamı, süresi ve temizleme yolu kullanıcıya gösterilir.

Pattern: [Tarayıcıda saklama](references/hardening-patterns.md#tarayıcıda-saklama).

### Header'lar, CSP ve origin

- Security header'lar (CSP, `X-Content-Type-Options`, `Referrer-Policy`, `frame-ancestors`) production'da statik dosyaları sunan reverse proxy tarafından eklenir; Vite dev server production değildir.
- CSP `default-src 'self'`'ten başlar ve gevşetilmez, sıkılaştırılır. WebSocket için CSP Level 3'te `connect-src 'self'` aynı host'taki `ws:`/`wss:`'yi kapsar, ama eski tarayıcılar (özellikle eski Safari) kapsamaz; bu yüzden WebSocket adresini `connect-src`'ye açıkça ekle (`wss://<host>`).
- Frontend ve backend aynı origin'den sunulur (dev'de Vite proxy, production'da reverse proxy); bu yüzden CORS'a ihtiyaç yoktur. CORS'u `*` ile açmak çözüm değildir.

Pattern: [CSP ve header'lar](references/hardening-patterns.md#csp-ve-security-headerlar).

### Girdi ve gelen veri

- Form validation (`<feature>Rules.ts`) kullanıcıya hızlı geri bildirim içindir; backend aynı kuralları kendisi uygular.
- REST ve STOMP'tan gelen veriyi type assertion (`as T`) ile "doğrulanmış" sayma; beklenmeyen şekil uygulamayı çökertebilir veya yanlış yetkiyle UI gösterebilir. Kritik payload'lar (ör. arama sonucu, ingestion ilerlemesi) için type guard ile şekil kontrolü yap ve geçersizse yok say + `console.error`.
- Girdi uzunluklarını sınırla (`maxLength`), backend sınırlarıyla aynı değerleri kullan.

Pattern: [Gelen veriyi doğrulama](references/hardening-patterns.md#gelen-veriyi-doğrulama).

### Hata gösterimi

- Kullanıcıya sadece `messageFor(errorCode)` sonucu gösterilir (`frontend-conventions` bölüm 7). Backend `detail`'i, stack trace, ham response body veya teknik hata metni ekrana basılmaz.
- Error boundary fallback'i genel bir Türkçe mesajdır; hata detayı sadece `console.error`'a gider.

### Secret'lar ve environment variable'lar

- `VITE_` prefix'li her değişken build sırasında client bundle'a gömülür ve **public**'tir. Frontend'de secret yoktur; secret gerektiren her şey backend'dedir.
- Proxy hedefi gibi sadece config'te okunan değerler `VITE_` prefix'i almaz (`vite-patterns`).
- `.env.example` placeholder'larla commit'lenir; gerçek `.env*` dosyaları gitignore'dadır. Commit'ten önce staged diff'i grep'le. **Remote'a ulaşan bir secret o an ele geçirilmiş sayılır: önce rotate et, sonra geçmişten temizle.**

Pattern: [Secret yönetimi](references/hardening-patterns.md#secret-yönetimi).

### Dependency'ler ve supply chain

1. **Tek package manager, tek lockfile.** `frontend/package-lock.json` tek doğru kaynaktır; başka lockfile (`yarn.lock`, `pnpm-lock.yaml`) yoktur. CI ve Docker build'inde `npm install` değil `npm ci`.
2. **Install script'lerine dikkat.** Yeni bir paket eklerken `postinstall` gibi script'lerini incele; gerektiğinde `npm ci --ignore-scripts` ile kur ve sadece gerekenleri bilinçli çalıştır. Asla toplu onay verme.
3. **Her release öncesi `npm audit` çalıştır.** Critical/high bulguları **erişilebilirliğe** (runtime bundle mı, sadece dev/build/test mi) ve fix olup olmamasına göre triage et. `npm audit fix --force`'u asla otomatik uygulama — tanımlı versiyon aralıklarını aşabilir; önizle, changelog'u oku, her yükseltmeyi test et. Ertelenen her bulguyu gerekçesi ve gözden geçirme tarihiyle kaydet.
4. **Audit sadece bilinen advisory'leri yakalar.** Yeni zararlı veya typosquat paketleri (`cross-env` vs `crossenv`) yakalamaz. Yeni dependency'leri, lockfile diff'lerini birlikte review et: sahiplik, bakım, yayın yaşı, provenance, transitive graph. `npm audit signatures` ile registry imzalarını doğrula; imza yokluğunu otomatik ele geçirilme kanıtı değil, araştırma sinyali say.

Triage karar ağacı: [Dependency audit triage](references/hardening-patterns.md#dependency-audit-triage).

### Kişisel veri ve gizlilik

Hardening "saldırgan okuyabilir mi?" diye sorar. Gizlilik "bunu hiç tutmalı mıyız, ne kadar süre?" diye sorar. Korunması en ucuz veri hiç toplanmamış veridir.

- Kullanıcının yazdığı sorgu kişisel veya hassas bilgi içerebilir ("şifremi unuttum, TC kimlik no..."); amaç dışında toplama, tarayıcıda saklama.
- Sorgu metnini analytics'e, hata raporlama servisine veya üçüncü taraflara göndermek paylaşımdır — plan kararı gerektirir.
- `localStorage`'da sadece gizli olmayan tercihler durur; yeni bir kalıcı saklama (IndexedDB, sorgu geçmişi) yeni bir karar gerektirir.

## Yaygın bahaneler

| Bahane | Gerçek |
|---|---|
| "Bu sadece bir demo, güvenlik önemli değil" | Taranan bir web sayfasındaki script, demo ekranında da kullanıcının tarayıcısında çalışır. Sızan API anahtarı demo olsa da gerçek fatura üretir. |
| "Güvenliği sonra ekleriz" | Sonradan güvenlik eklemek 10 kat zordur. Şimdi ekle. |
| "Kimse bunu sömürmeye çalışmaz" | Otomatik tarayıcılar bulur. Gizlilikle sağlanan güvenlik, güvenlik değildir. |
| "React XSS'i zaten önlüyor" | `{value}` için evet; `dangerouslySetInnerHTML`, `javascript:` URL'leri ve ref ile DOM yazımı için hayır. |
| "Butonu gizledik, kimse kaynağı silemez" | DevTools'tan istek göndermek bir saniye sürer. Yetki backend'de kontrol edilir. |
| "Frontend'de validate ediyoruz" | Client-side validation UX'tir; isteği curl ile gönderen biri onu hiç görmez. |
| "Bu env variable'ı sadece frontend kullanıyor" | `VITE_` ile başlıyorsa bundle'dadır ve herkes okuyabilir. |
| "Audit geçti, dependency güvenli" | Audit bilinen advisory'leri eşler. Yeni zararlı bir paketi yakalamaz. |

## Kırmızı bayraklar

- Kullanıcı veya backend kaynaklı içerik `dangerouslySetInnerHTML`, `innerHTML`, `eval` veya `new Function`'a gidiyor
- Kullanıcı kaynaklı URL protokol kontrolü olmadan `href`/`src`'de
- Parça içeriği ya da vurgulama HTML string'i olarak render ediliyor
- Sorgu metni log'da, URL'de veya hata raporunda
- `localStorage`'a `searchSettings.ts` dışından erişim
- Frontend'den embedding sağlayıcısına doğrudan istek
- `VITE_` prefix'li bir env variable'da secret, API key veya şifre
- Backend `detail`'i veya stack trace kullanıcıya gösteriliyor
- Yetki kontrolünün sadece UI'da (buton gizleme) yapılması
- Production'da `ws://` veya `http://`
- Birden fazla lockfile, CI'da `npm install`, toplu onaylanmış install script'leri
- Review edilmeden eklenmiş üçüncü taraf script/CDN
- STOMP/REST payload'ının hiçbir kontrol olmadan `as T` ile kullanılıp yetki kararı verilmesi

## Doğrulama

Güvenlikle ilgili kodu yazdıktan sonra:

- [ ] `grep -rn "dangerouslySetInnerHTML\|innerHTML\|eval(\|new Function" frontend/src` boş
- [ ] Kullanıcı kaynaklı URL'ler protokol allowlist'inden geçiyor
- [ ] Parça içeriği ve vurgulama düz metin/React elemanıyla render ediliyor; sorgu metni log'a ve URL'ye gitmiyor
- [ ] Kodda ve `VITE_` env variable'larında secret yok; `.env*` gitignore'da
- [ ] Hata durumlarında kullanıcı sadece Türkçe `errorCode` mesajını görüyor
- [ ] `npm audit` unmitigated, erişilebilir critical/high bulgu göstermiyor; ertelenenler gerekçeli
- [ ] CI/Docker build'i `npm ci` kullanıyor, tek lockfile var
- [ ] Production response'larında CSP ve security header'lar mevcut (tarayıcı DevTools > Network ile kontrol et)
- [ ] XSS kötüye kullanım senaryoları (içe alınan içerikte HTML/script, parçada `javascript:` linki) test senaryosu olarak plana eklendi
