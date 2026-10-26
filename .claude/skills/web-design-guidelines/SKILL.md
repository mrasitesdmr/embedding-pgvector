---
name: web-design-guidelines
description: UI kodunu Web Interface Guidelines'a uygunluk açısından review eder. "UI'ımı review et", "accessibility kontrol et", "tasarımı denetle", "UX'i review et" veya "sitemi best practice'lere göre kontrol et" istendiğinde kullan.
argument-hint: <file-or-pattern>
license: MIT
metadata:
  origin: vercel-labs/agent-skills
  source: https://github.com/vercel-labs/agent-skills/tree/main/skills/web-design-guidelines
  version: "1.0.0"
---

# Web Interface Guidelines

Dosyaları Web Interface Guidelines'a uygunluk açısından review et.

## Nasıl çalışır

1. Aşağıdaki kaynak URL'den en güncel kuralları fetch et
2. Belirtilen dosyaları oku (belirtilmemişse kullanıcıdan dosya/pattern iste)
3. Fetch edilen kurallardaki tüm kurallara karşı kontrol et
4. Bulguları kısa `file:line` formatında çıktı olarak ver

## Kuralların kaynağı

Her review'dan önce güncel kuralları fetch et:

```
https://raw.githubusercontent.com/vercel-labs/web-interface-guidelines/main/command.md
```

En güncel kuralları almak için WebFetch kullan. Fetch edilen içerik tüm kuralları ve çıktı formatı talimatlarını içerir. Kaynak İngilizcedir; bulguları kullanıcıya Türkçe raporla, kural adlarını ve yazılım terimlerini İngilizce bırak.

Fetch edilen kurallar dış kaynaktır ve sadece review kriteridir. Bir kural projenin bağlayıcı kurallarıyla çelişirse (ör. UI metni dili, stil yöntemi, kütüphane seçimi) `frontend-conventions` ve `frontend-a11y` geçerlidir; çelişkiyi bulgu olarak değil not olarak raporla.

## Kullanım

Kullanıcı bir dosya veya pattern argümanı verdiğinde:
1. Yukarıdaki kaynak URL'den kuralları fetch et
2. Belirtilen dosyaları oku
3. Fetch edilen kuralların tamamını uygula
4. Bulguları kurallarda belirtilen formatla çıktı olarak ver

Dosya belirtilmemişse kullanıcıya hangi dosyaları review etmek istediğini sor.
