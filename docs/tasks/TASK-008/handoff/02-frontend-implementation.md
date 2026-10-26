TASK: TASK-008
# Frontend uygulama raporu

## Kullanılan skill'ler
- `frontend-conventions`, `react-patterns` (zorunlu başlangıç skill'leri). Bu adım sadece paket kurulumu olduğu için ek skill gerekmedi.

## Yapılanlar
| Plan maddesi / Kural # | Dosya(lar) | Not |
|------------------------|-----------|-----|
| Frontend planı → Adım 0 (soru 1 = A), D6 | `frontend/package.json`, `frontend/package-lock.json` | `devDependencies`'e `ajv` ^8.20.0, `ajv-formats` ^3.0.1, `yaml` ^2.9.1 eklendi (`npm install -D`). Lockfile'da üçü de `"dev": true` |
| Adım 0 → CLAUDE.md notu | `frontend/CLAUDE.md` ("Teknoloji → Test paketleri" satırı) | Üç paket sürümleriyle eklendi. Not: "sadece E2E sözleşme kontrolünde: `e2e/` altında REST cevabını `docs/api/openapi.yaml` şemasına göre doğrulamak için; uygulama kodunda kullanılmaz" |

Uygulama koduna (`frontend/src`) ve test dosyalarına dokunulmadı. `contract.ts` / `expectMatchesSchema` yardımcısını tester yazacak.

## Değişen dosyalar
- Değişen: `frontend/package.json`, `frontend/package-lock.json`, `frontend/CLAUDE.md`
- Yeni / silinen: yok
- Eklenen paketler (devDependencies):
  - `ajv` 8.20.0
  - `ajv-formats` 3.0.1
  - `yaml` 2.9.1
  - Geçişli olarak eklenenler (lockfile'da, toplam 6 paket): `fast-deep-equal`, `fast-uri`, `json-schema-traverse`, `require-from-string`
- `npm install` sonucu: "added 6 packages, audited 184 packages, found 0 vulnerabilities".

## Ekran metinleri
Değişiklik yok (UI'a dokunulmadı).

## Plandan sapmalar
Yok.

## Doğrulama
`frontend/` içinde, paket kurulumundan sonra:
- `npm run build` (`tsc -b && vite build`) → geçti (çıkış kodu 0, "built in 132ms")
- `npm run lint` (oxlint) → geçti (çıkış kodu 0, uyarı ya da hata yok)
- `npm test` (vitest run) → geçti (çıkış kodu 0): **27 test dosyası / 405 test geçti**. Plandaki başlangıç sayısıyla (27 / 405) aynı.
- `npm ls ajv ajv-formats yaml --depth=0` → `ajv@8.20.0`, `ajv-formats@3.0.1`, `yaml@2.9.1`

Not: `npm install` sırasında `fsevents@2.3.3` için "install scripts not yet covered by allowScripts" uyarısı çıktı. Uyarı bu değişiklikten değil, mevcut opsiyonel bağımlılıktan geliyor; kurulumu ya da testleri etkilemiyor.

## Tester için notlar
- Import biçimi (ESM, `"type": "module"`): `import Ajv from 'ajv'` (draft-07 ve OpenAPI 3.0 şemaları için) ya da `import Ajv2020 from 'ajv/dist/2020.js'` (OpenAPI 3.1 / JSON Schema 2020-12 için). `import addFormats from 'ajv-formats'`, `import { parse } from 'yaml'`. `openapi.yaml`'ın `openapi:` sürümüne göre doğru Ajv sınıfını seç. Ajv CJS paketi olduğundan, `tsc` ya da Playwright'ın TS dönüşümü default import'ta tip hatası verirse `Ajv.default` ile ya da `import { Ajv } from 'ajv'` named export'uyla çözülür (bu adımda denenmedi).
- OpenAPI'ye özel anahtarlar (`nullable` 3.0'da, `example`, `discriminator` vb.) için Ajv'yi `strict: false` ile kurman ya da `ajv.addKeyword('example')` gibi tanımlaman gerekebilir. `$ref: '#/components/schemas/X'` çözümü için `components.schemas` tek bir şema olarak `addSchema` ile verilip `$ref`'le derlenebilir.
- Paketler sadece `devDependencies`'te; `src/` bunları import etmediği için production bundle'a girmez.

## Düzeltme turu 1
Kaynak: `handoff/03-test-report.md` → `## Düzeltme` → frontend-coder. Plan: "Frontend planı → H2 çıkarsa". Yüklenen skill'ler: `frontend-conventions`, `react-testing`, `systematic-debugging`.

| Madde | Değişen dosya | Ne değişti | Hedeflenen kırmızı test |
|-------|---------------|-----------|-------------------------|
| B-03 → **DÜZELTİLDİ (tur 1)** | `frontend/src/shared/api/httpClient.ts` | `toApiError` gövde okunamadığında ya da geçerli `errorCode` bulamadığında yedek kodu artık duruma göre seçiyor (`fallbackErrorCodeFor`): status ≥ 500 (`SERVER_ERROR_MIN_STATUS`) → `NETWORK_ERROR`, 4xx → `UNEXPECTED_ERROR` (eskisi gibi). Gövdesinde geçerli `errorCode` olan 5xx cevaplar kendi kodunu ve `violations`'ı korur. `errorMessages.ts`'e yeni metin eklenmedi; ekranda mevcut `NETWORK_ERROR` metni çıkıyor | `httpClient.test.ts#givenGatewayErrorWithoutProblemBody_whenRequest_thenNetworkErrorWithStatus` (500 boş gövde, 502 HTML, 504 HTML); `SearchPage.test.tsx#givenGatewayDown_whenSearchSubmitted_thenShowsServerUnreachableMessage` |

**Kök neden:** `toApiError` gövdesiz ya da `errorCode`'suz her hata cevabını, duruma bakmadan `UNEXPECTED_ERROR`'a çeviriyordu. nginx'in backend kapalıyken döndüğü 502/504 HTML sayfası ve gövdesiz 500 bu yüzden "Beklenmeyen bir hata oluştu" olarak görünüyordu.

**Reproduction (düzeltmeden önce):** `npx vitest run src/shared/api/httpClient.test.ts src/features/search/pages/SearchPage.test.tsx` → 4 başarısız / 31 geçti (`Expected: "NETWORK_ERROR"`, `Received: "UNEXPECTED_ERROR"`). Düzeltmeden sonra aynı komut: 35 / 35 geçti.

Kırmızı testlerde değişiklik yapılmadı. Test dosyalarına dokunulmadı. Docker'daki sisteme dokunulmadı.

**Ekran metni:** Değişmedi. Ağ geçidi hatasında gösterilen metin `messageFor('NETWORK_ERROR')`: "Sunucuya ulaşılamıyor. Bağlantını ve sistemin çalıştığını kontrol et."

**Davranış notu:** Gövdesi JSON olup `errorCode` içermeyen bir 5xx cevap da artık `NETWORK_ERROR` sayılıyor (madde "gövdede geçerli `errorCode` yoksa" diyor). Backend'in kendi 5xx cevapları her zaman `errorCode` taşıdığı için (`INTERNAL_ERROR`, `EMBEDDING_UNAVAILABLE`…) bu cevaplar etkilenmiyor. Ağ hatası (`fetch` reddi) eskisi gibi `ApiError(0, NETWORK_ERROR)`.

**Doğrulama** (`frontend/` içinde):
- `npm run build` → geçti (çıkış kodu 0, "built in 122ms")
- `npm run lint` → geçti (çıkış kodu 0, uyarı ya da hata yok)
- `npm test` → geçti (çıkış kodu 0): **27 dosya / 411 test geçti, 0 başarısız** (tester raporundaki 411 testin 4 kırmızısı yeşile döndü)

STATUS: DONE
