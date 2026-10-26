import os

from playwright.sync_api import expect, sync_playwright

# Örnek: file:// URL ile local statik HTML dosyalarını otomatikleştirmek
# (ör. repo kökündeki lab.html demosu; dosya yolunu kendi sayfanla değiştir)

html_file_path = os.path.abspath('path/to/your/file.html')
file_url = f'file://{html_file_path}'

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    page = browser.new_page(viewport={'width': 1280, 'height': 800})

    page.goto(file_url)
    page.screenshot(path='artifacts/static_page.png', full_page=True)

    # Elemanlarla etkileşim (statik sayfadaki label ve buton metinleriyle)
    page.get_by_label('Arama metni').fill('Şifremi unuttum')
    page.get_by_role('button', name='Aramayı başlat').click()

    # Sonucu sabit süre yerine görünür olmasını bekleyerek doğrula
    expect(page.get_by_role('alert')).to_be_visible()
    page.screenshot(path='artifacts/after_submit.png', full_page=True)

    browser.close()

print("Static HTML automation completed!")
