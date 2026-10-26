from pathlib import Path

from playwright.sync_api import expect, sync_playwright

# Örnek: tarayıcı otomasyonu sırasında console log'larını yakalamak

url = 'http://localhost:5173'  # Kendi URL'inle değiştir
output_path = Path('artifacts/console.log')

console_logs = []

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    page = browser.new_page(viewport={'width': 1280, 'height': 800})

    def handle_console_message(message):
        console_logs.append(f"[{message.type}] {message.text}")
        print(f"Console: [{message.type}] {message.text}")

    page.on("console", handle_console_message)

    page.goto(url)
    expect(page.get_by_role('button', name='Aramayı başlat')).to_be_visible()

    # Console log'u tetikleyecek bir etkileşim; sonucu sabit süre değil beklenen elemanla bekle
    page.get_by_label('Arama metni').fill('Şifremi unuttum')
    page.get_by_role('button', name='Aramayı başlat').click()
    expect(page.get_by_test_id('search-hit-score').first).to_be_visible()

    browser.close()

output_path.parent.mkdir(parents=True, exist_ok=True)
output_path.write_text('\n'.join(console_logs))

print(f"\nCaptured {len(console_logs)} console messages")
print(f"Logs saved to: {output_path}")
