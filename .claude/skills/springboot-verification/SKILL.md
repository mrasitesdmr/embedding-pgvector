---
name: springboot-verification
description: Spring Boot için tam verification loop'unu çalıştırır — Maven build, (yapılandırılmışsa) SpotBugs/PMD/Checkstyle statik analizi, JaCoCo coverage ile unit ve Testcontainers integration test'leri, OWASP dependency ve secret taramaları ve diff incelemesi — ve geçti/kaldı readiness raporu üretir. Bir Spring Boot pull request'i hazırlarken, coverage threshold'larını doğrularken veya deploy öncesi doğrulama yaparken kullan.
metadata:
  origin: ECC
  adapted-for: Embedding ve pgvector (Maven wrapper, Java 26, Spring Boot 4.1)
---

# Spring Boot Verification Loop

> **Proje kuralı:** İsimlendirme, yorum politikası, Lombok yasağı ve test yapısı için `backend-conventions` geçerlidir. Test yazım kalıpları bu skill'de değil test skill'lerindedir (`spring-testing-fundamentals`, `spring-mvc-testing`, `spring-jpa-testing`, `spring-websocket-testing`); bu skill sadece doğrulama adımlarını çalıştırır ve raporlar.

PR'lardan önce, büyük değişikliklerden sonra ve deploy öncesinde çalıştır. Tüm komutlar `backend/` dizininde, Maven wrapper (`./mvnw`) ile çalışır.

## Ne Zaman Devreye Girer

- Bir pull request açmadan önce
- Büyük refactoring veya dependency güncellemelerinden sonra
- Deploy öncesi doğrulamada (qa agent'ının kontrol adımları dahil)
- Test coverage'ının eşiği karşıladığını doğrularken

## Aşama 1: Build

```bash
./mvnw -q clean compile
```

Build başarısız olursa dur ve düzelt.

## Aşama 2: Statik Analiz (yapılandırılmışsa)

`pom.xml`'de SpotBugs / PMD / Checkstyle plugin'leri tanımlıysa:

```bash
./mvnw spotbugs:check pmd:check checkstyle:check
```

Plugin tanımlı değilse bu aşamayı raporda "ATLANDI (yapılandırılmamış)" olarak işaretle; plugin'i kendiliğinden ekleme.

## Aşama 3: Testler + Coverage

```bash
./mvnw verify
```

`verify`; unit, slice ve Testcontainers integration testlerini (surefire) çalıştırır, JaCoCo raporunu üretir ve `check` kuralıyla satır coverage'ı %80'in altındaysa build'i fail eder (plugin yapılandırması: `springboot-tdd`). Rapor: `target/site/jacoco/index.html`.

- Testcontainers için Docker çalışıyor olmalı; Docker yoksa integration testlerinin hata mesajını raporla, testleri atlama.
- Fail olan testi düzeltmeden sonraki aşamaya geçme.

Rapor:
- Toplam test sayısı, geçen/kalan/atlanan
- Satır ve dal coverage yüzdesi

## Aşama 4: Security Scan

```bash
./mvnw org.owasp:dependency-check-maven:check
```

OWASP Dependency-Check NVD verisini indirir; bir NVD API anahtarı (`-DnvdApiKey=...`, environment variable'dan) olmadan çok yavaş çalışır. Anahtar yoksa aşamayı "ATLANDI (NVD anahtarı yok)" olarak raporla.

Kaynak koddaki secret'lar:

```bash
grep -rnE "password\s*=\s*[^$]" src/main/resources/ --include="*.properties"
grep -rnE "(api[_-]?key|secret|token)\s*=\s*\"" src/main/java/
git diff --cached | grep -nE "(BEGIN (RSA|EC|OPENSSH) PRIVATE KEY|AKIA[0-9A-Z]{16})"
```

### Sık Görülen Bulgular

```bash
grep -rn "System\.out\.print\|printStackTrace" src/main/java/
grep -rn "getMessage()" src/main/java/ | grep -i "problem\|response"
grep -rn "allowedOrigins.*\"\*\"\|setAllowedOriginPatterns(\"\*\")" src/main/java/
grep -rln "import lombok\|@MockBean\|com\.fasterxml\.jackson\.databind" src/
```

Son satır proje kurallarının ihlallerini yakalar (Lombok, Boot 3 `@MockBean`, Jackson 2).

## Aşama 5: Diff İncelemesi

```bash
git diff --stat
git diff
```

Kontrol listesi:
- Debug çıktısı kalmamış (`System.out`, gereksiz `log.debug`)
- Anlamlı hatalar ve HTTP status kodları; `errorCode`'lar `docs/api/openapi.yaml` ile uyumlu
- Gereken yerlerde transaction ve validation mevcut
- Configuration değişiklikleri dokümante edilmiş
- `backend-conventions` kontrol listesi geçiyor

## Çıktı Şablonu

```
DOĞRULAMA RAPORU
================
Build:     [GEÇTİ/KALDI]
Statik:    [GEÇTİ/KALDI/ATLANDI] (spotbugs/pmd/checkstyle)
Testler:   [GEÇTİ/KALDI] (X/Y geçti, satır coverage %Z)
Güvenlik:  [GEÇTİ/KALDI/ATLANDI] (CVE bulgusu: N)
Diff:      [X dosya değişti]

Genel:     [HAZIR / HAZIR DEĞİL]

Düzeltilecekler:
1. ...
2. ...
```

## Sürekli Mod

- Uzun oturumlarda önemli değişikliklerde aşamaları yeniden çalıştır
- Kısa döngü: `./mvnw test -Dtest=<DeğişenSınıf>Test`, sonra tam `./mvnw verify`

**Unutma**: Hızlı geri bildirim, geç gelen sürprizlerden iyidir. Gate'i sıkı tut — uyarıları kusur olarak kabul et.
