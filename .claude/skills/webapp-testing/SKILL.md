---
name: webapp-testing
description: Local web uygulamalarıyla Playwright üzerinden etkileşim kurmak ve onları test etmek için araç seti. Frontend işlevselliğini doğrulamayı, UI davranışını debug etmeyi, tarayıcı screenshot'ı almayı ve tarayıcı log'larını görmeyi destekler. Çalışan uygulamada bir ekranı gerçekten denemek, "tarayıcıda kontrol et", "screenshot al" veya "console hatası var mı bak" istendiğinde kullan.
metadata:
  origin: anthropics/skills
  source: https://github.com/anthropics/skills/tree/main/skills/webapp-testing
---

# Web Uygulaması Testi

Local web uygulamalarını test etmek için native Python Playwright script'leri yaz.

Gereksinim: `pip install playwright && playwright install chromium`

**Kullanılabilir yardımcı script'ler**:
- `${CLAUDE_SKILL_DIR}/scripts/with_server.py` - Server lifecycle'ını yönetir (birden fazla server destekler)

**Script'leri her zaman önce `--help` ile çalıştır** ve kullanımı gör. Script'i önce çalıştırmayı deneyip özelleştirilmiş bir çözümün kesinlikle gerekli olduğunu görmeden kaynak kodunu OKUMA. Bu script'ler çok büyük olabilir ve context window'unu kirletebilir. Context window'una alınmak için değil, doğrudan black-box script olarak çağrılmak için vardırlar.

## Karar ağacı: yaklaşımını seç

```
Kullanıcı görevi → Statik HTML mi?
    ├─ Evet → Selector'ları bulmak için HTML dosyasını doğrudan oku
    │         ├─ Başarılı → Selector'ları kullanarak Playwright script'i yaz
    │         └─ Başarısız/Eksik → Dinamik gibi davran (aşağıda)
    │
    └─ Hayır (dinamik webapp) → Server zaten çalışıyor mu?
        ├─ Hayır → Çalıştır: python ${CLAUDE_SKILL_DIR}/scripts/with_server.py --help
        │        Sonra helper'ı kullan + sadeleştirilmiş Playwright script'i yaz
        │
        └─ Evet → Önce keşif, sonra eylem:
            1. Sayfaya git ve beklenen bir elemanın görünmesini bekle
            2. Screenshot al ya da DOM'u incele
            3. Render edilmiş durumdan selector'ları belirle
            4. Bulunan selector'larla eylemleri çalıştır
```

## Örnek: with_server.py kullanımı

Bir server başlatmak için önce `--help` çalıştır, sonra helper'ı kullan:

**Tek server (Vite dev server):**
```bash
python ${CLAUDE_SKILL_DIR}/scripts/with_server.py --server "cd frontend && npm run dev" --port 5173 -- python your_automation.py
```

**Birden fazla server (backend + frontend):** Spring Boot'un ilk açılışı (dependency indirme dahil) 60 saniyeyi geçebilir; `--timeout`'u artır. Server çıktıları log dosyalarına yazılır, yolları script çıktısında görünür.
```bash
python ${CLAUDE_SKILL_DIR}/scripts/with_server.py --timeout 180 \
  --server "cd backend && ./mvnw spring-boot:run" --port 8080 \
  --server "cd frontend && npm run dev" --port 5173 \
  -- python your_automation.py
```

Bir otomasyon script'i oluştururken sadece Playwright mantığını ekle (server'lar otomatik yönetilir):
```python
from playwright.sync_api import sync_playwright

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True) # Chromium'u her zaman headless modda başlat
    page = browser.new_page()
    page.goto('http://localhost:5173') # Server zaten çalışıyor ve hazır
    page.get_by_role('button', name='Aramayı başlat').wait_for() # KRİTİK: React render'ının bitmesini beklenen elemanla bekle
    # ... otomasyon mantığın
    browser.close()
```

## Önce keşif, sonra eylem pattern'i

1. **Render edilmiş DOM'u incele**:
   ```python
   page.screenshot(path='artifacts/inspect.png', full_page=True)
   content = page.content()
   page.locator('button').all()
   ```

2. İnceleme sonuçlarından **selector'ları belirle**

3. Bulunan selector'larla **eylemleri çalıştır**

## Yaygın tuzak

❌ Dinamik uygulamada render bitmeden DOM'u **inceleme**
✅ İncelemeden önce beklenen bir elemanı **bekle**: `page.get_by_role(...).wait_for()` veya `expect(locator).to_be_visible()`

`page.wait_for_load_state('networkidle')` sadece WebSocket'i olmayan statik sayfalarda işe yarar; ingestion ilerlemesini izleyen ekranda açık STOMP bağlantısı yüzünden hiç gelmeyebilir ve Playwright da önermez (`e2e-testing` ile aynı kural).

## Best practice'ler

- **Paketlenmiş script'leri black box olarak kullan** - Bir görevi yaparken `scripts/` içindeki script'lerden birinin işe yarayıp yaramayacağını düşün. Bu script'ler yaygın ve karmaşık iş akışlarını context window'u doldurmadan güvenilir şekilde yönetir. Kullanımı görmek için `--help` çalıştır, sonra doğrudan çağır.
- Senkron script'ler için `sync_playwright()` kullan
- İş bitince tarayıcıyı her zaman kapat
- Locator önceliği `e2e-testing` ile aynı: `get_by_role` > `get_by_label` > `get_by_text` > `get_by_test_id`; Türkçe UI metniyle sorgula
- Bekleme koşula bağlı olsun: `locator.wait_for()`, `expect(locator).to_be_visible()`; sabit `wait_for_timeout()` kullanma
- Screenshot ve log çıktılarını proje içine değil `artifacts/` veya scratchpad gibi geçici bir dizine yaz

## Referans dosyalar

- **examples/** - Yaygın pattern'leri gösteren örnekler:
  - `examples/element_discovery.py` - Bir sayfadaki buton, link ve input'ları keşfetme
  - `examples/static_html_automation.py` - Local HTML için file:// URL kullanma
  - `examples/console_logging.py` - Otomasyon sırasında console log'larını yakalama
