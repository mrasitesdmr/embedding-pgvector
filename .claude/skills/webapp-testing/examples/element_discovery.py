from playwright.sync_api import sync_playwright

# Örnek: bir sayfadaki buton, link ve input'ları keşfetmek

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    page = browser.new_page()

    # Sayfaya git ve render'ın bittiğini gösteren ilk elemanı bekle
    page.goto('http://localhost:5173')
    page.locator('main').wait_for()

    buttons = page.get_by_role('button').all()
    print(f"Found {len(buttons)} buttons:")
    for index, button in enumerate(buttons):
        text = button.inner_text() if button.is_visible() else "[hidden]"
        print(f"  [{index}] {text}")

    links = page.get_by_role('link').all()
    print(f"\nFound {len(links)} links:")
    for link in links[:5]:
        text = link.inner_text().strip()
        href = link.get_attribute('href')
        print(f"  - {text} -> {href}")

    inputs = page.locator('input, textarea, select').all()
    print(f"\nFound {len(inputs)} input fields:")
    for input_element in inputs:
        name = input_element.get_attribute('name') or input_element.get_attribute('id') or "[unnamed]"
        input_type = input_element.get_attribute('type') or 'text'
        print(f"  - {name} ({input_type})")

    # Görsel referans için screenshot
    page.screenshot(path='artifacts/page_discovery.png', full_page=True)
    print("\nScreenshot saved to artifacts/page_discovery.png")

    browser.close()
