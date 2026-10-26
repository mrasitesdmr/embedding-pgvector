TASK: TASK-001
# QA raporu

## Kullanılan skill'ler
`code-review-and-quality`, `writing-good-tests` (mutation değerlendirmesi için bakış açısı), `java-code-review` / `springboot-verification` kontrol listeleri (kod okuma ve `verify`). Frontend ve devops bu task'ta yok; `frontend-conventions`, `web-design-guidelines`, `frontend-a11y`, `e2e-testing` uygulanmadı.

## Kural kontrolü
Canlı testlerde gerçek Ollama (0.40.2) ve gerçek `bge-m3` kullanıldı.

| # | Kural / Kabul kriteri | Kanıt (kod + test) | Sonuç |
|---|---|---|---|
| K1 | Lokal Ollama, ücretli servis ve API anahtarı yok | `OllamaEmbeddingClient` sadece `embedding.base-url`'e gider. `grep -i "api.?key\|openai"` backend'de boş. | ✅ |
| K2 | Çok dilli model (`bge-m3`) | `application.properties` varsayılanı `bge-m3`. Canlı: `dimensions=1024`; `ollama show bge-m3` → `embedding length 1024`. | ✅ |
| K3 | Model adı ve adres yapılandırmadan; varsayılan `http://localhost:11434` | `EmbeddingProperties`, `${OLLAMA_BASE_URL:http://localhost:11434}`, `${EMBEDDING_MODEL:bge-m3}`. Canlı: `OLLAMA_BASE_URL` ve `EMBEDDING_MODEL` ile süreç davranışı değişti. Test: `OllamaEmbeddingClientTest#givenTurkishText_...ConfiguredModel...` | ✅ |
| K4 | `ollama.md` güncel, backend bölümü dolu, komutlar çalışıyor | Dosya okundu. Q1 (aşağıda) ve bölüm 5 komutları canlı çalıştı. Dosyada ve `deployment/` altında Docker atfı yok. | ✅ |
| K5 | Yanıtta model, boyut, vektör, süre | Canlı: `bge-m3 1024 1024 63 ms`. Test: `EmbeddingServiceTest#...ModelDimensionsEmbeddingAndDuration`, `EmbeddingIntegrationTest#...1024Values...` | ✅ |
| K6 | Boş / boşluk metin reddedilir, baş-son boşluk kırpılır | Canlı: `"   "`, `""`, `{}`, `{"text":null}` → 400 `VALIDATION_FAILED`. Boşluk/sekme/satır sonlu metin ile boşluksuz metin aynı vektör hash'ini verdi (`d244a16ed61539c3`). Test: `EmbedTextRequestTest`, `EmbeddingControllerTest`, `EmbeddingIntegrationTest#givenWhitespaceOnlyText...OllamaReceivesNoRequest` | ✅ |
| K7 | En fazla 2000 karakter | Canlı: 2001 karakter → 400 `size must be at most 2000 characters`; boşlukla çevrili 2000 karakter → 200. Test: `EmbeddingControllerTest` (2000 / 2001 / 2000+boşluk) | ✅ |
| K8 | Türkçe karakterler bozulmadan | Canlı: backend'in `modem ışığı kırmızı yanıyor` vektörü, doğrudan Ollama'ya gönderilenle aynı hash (`d244a16ed61539c3`). Test: `EmbeddingIntegrationTest#...OllamaReceivesStrippedUtf8Text` | ✅ |
| K9 | Ollama yok / model yok → ayrı `errorCode`, backend açık kalır | Canlı: ulaşılamayan adres → 503 `EMBEDDING_UNAVAILABLE` (0,13 sn); `EMBEDDING_MODEL=olmayan-model` → 503 `EMBEDDING_MODEL_NOT_FOUND`; iki durumda da `/actuator/health` `UP`. Test: `OllamaEmbeddingClientTest`, `EmbeddingIntegrationTest#...404...`, `EmbeddingOllamaRestartIntegrationTest` | ✅ |
| K10 | Loga model, boyut, süre; metin ve vektör yok | Canlı log: `Text embedded model=bge-m3 dimensions=1024 durationMs=63 textLength=45`. Log dosyasında `modem`, `Şifremi` ve vektör değeri geçmiyor (grep 0). Test: `EmbeddingServiceTest#...LogsModelDimensionsDurationWithoutText...` | ✅ |
| K11 | Docker isteğe bağlı (kullanıcı: hayır) | `deployment/` altında sadece `CLAUDE.md`. `ollama.md`'de `docker` geçmiyor. | ✅ |
| S-3 | Aynı metin iki kez → aynı vektör | Canlı: 4 çağrı (boşluklu/boşluksuz dahil) aynı hash. Test: `givenSameTextTwice_...IdenticalRequestBodies` | ✅ |
| S-7 | Ollama kapalıyken hata, açılınca restart'sız başarı | Canlı, aynı backend süreci: kapalı adres → 503 (iki istek); aynı adreste gerçek Ollama'ya ileten proxy açıldı → 200 `bge-m3 1024`; proxy kapatılınca → yeniden 503. Test: `EmbeddingOllamaRestartIntegrationTest` | ✅ |
| AC-1 | `ollama.md` komutları çalışıyor | Q1 aşağıda | ✅ |
| AC-2 | `./mvnw verify` geçiyor | Komutlar bölümü | ✅ |
| AC-3 | Onaylanan model boyutunda vektör | Canlı 1024 | ✅ |
| AC-4 | K6/K7/K9 hataları sözleşmedeki `errorCode` ile RFC 9457 | Canlı: `application/problem+json`, `errorCode`, `type`, `instance`, `violations`. 405 `METHOD_NOT_ALLOWED`, 415 `UNSUPPORTED_MEDIA_TYPE`, bozuk JSON 400 `BAD_REQUEST` da sözleşme enum'unda ve doğru döndü. | ✅ |
| AC-5 | Endpoint sözleşmede, backend birebir uyumlu | Sözleşme uyumu bölümü | ✅ |
| AC-6 | Testler gerçek Ollama olmadan geçiyor | `OLLAMA_BASE_URL=http://127.0.0.1:9 ./mvnw verify` geçti; `grep 11434 backend/src/test` boş | ✅ |

## Sözleşme uyumu
Çalışan backend'in `/v3/api-docs.yaml` çıktısı ile `docs/api/openapi.yaml` Ruby ile yapısal karşılaştırıldı:
- `openapi` 3.1.0, `info.title`/`version`, tek path `/api/embeddings` (POST), `operationId: embedText`, tag `Embeddings`, `requestBody.required: true` aynı.
- Yanıt kodları `200/400/500/503` ve content type'lar aynı (`application/json`, hatalarda `application/problem+json`).
- Şemalar `EmbedTextRequest`, `EmbeddingResponse`, `ProblemDetail`, `Violation`: alan adları ve `required` kümeleri aynı. Alan bazında `type`, `format`, `maxLength`, `minimum`, `enum` farkı yok (9 değerli `errorCode` enum'u dahil).
- Canlı yanıt alan adları (`embeddingModel`, `dimensions`, `embedding`, `durationMs`) ve hata gövdeleri sözleşmedeki örneklerle aynı.
- Fark: canlıda sadece `servers` (otomatik) ve `description`/`example` metinleri var. Bunlar sözleşme uyumunu etkilemez.

## Komutlar (kendi çalıştırdıkların)
| Komut | Sonuç | Raporla tutarlı mı |
|---|---|---|
| `cd backend && ./mvnw verify` | BUILD SUCCESS. 34 test (7 sınıf), 0 fail, 0 error. JaCoCo kontrolü geçti. | Evet (34 test) |
| `OLLAMA_BASE_URL=http://127.0.0.1:9 ./mvnw verify` | BUILD SUCCESS, 34 test, coverage kontrolü geçti | Evet |
| `./mvnw test-compile org.pitest:pitest-maven:mutationCoverage` | 51 mutant, 45 öldü (%88), test strength %90, line coverage %99 | Evet |
| `npm run build/lint/test` | Uygulanmaz (frontend yok) | — |
| Q1: `ollama --version`, `curl localhost:11434`, `ollama list`, `ollama show bge-m3`, doğrudan `/api/embed` | `0.40.2`; `Ollama is running`; `bge-m3:latest 1.2 GB`; `embedding length 1024`; vektör uzunluğu `1024`; toplu istek `2 vektör, 1024 boyut`; olmayan model → Ollama `model "yok-model" not found` hatası | `ollama.md` çıktılarıyla uyumlu |
| Q2-Q4: `./mvnw spring-boot:run` ile backend, `/actuator/health`, `/api/embeddings`, hata senaryoları (yukarıdaki tablo) | Hepsi beklenen sonuçta | Evet |

`brew install ollama`, `brew services start ollama` ve `ollama pull bge-m3` yeniden çalıştırılmadı: Ollama kurulu, açık ve model inmiş durumda. Kullanıcının Ollama'sı durdurulmadı.

## Mutation testi
Araç PIT 1.30.1, eşik tanımlı değil (raporlama). Skor %88 (45/51), test strength %90.

Hayatta kalan 6 mutant:
| Mutant | Değerlendirme |
|---|---|
| `GlobalExceptionHandler` 69 `setTitle`, 70 `setInstance` (500 yolu) | Eşdeğer: Spring bu alanları zaten varsayılan olarak dolduruyor, çıktı değişmiyor. |
| `GlobalExceptionHandler` 77 `properties != null` dalı (NO_COVERAGE) | Pratikte erişilemez savunma dalı. Kabul. |
| `OpenApiConfig` 40 (`problemDetail == null` koşulu), 44 (`customizeProblemDetail` çağrısı), 60 (`violationSchema` null dönüşü) | Tester'ın "önbellek" açıklamasını **bağımsız denedim**: backend'in kopyasında (scratchpad, asıl dosyalara dokunulmadı) üç mutasyonu elle uyguladım ve `OpenApiDocumentationIntegrationTest`'i tek başına çalıştırdım. Taban hâl geçti. Satır 44 mutasyonu: test **kırıldı** (`errorCode` yok). Satır 40 mutasyonu: test **kırıldı**. Satır 60 (`Violation` şeması null): test **NullPointerException ile kırıldı**. Yani test bu mutantları yakalıyor. PIT altında hayatta kalmaları, tester'ın tahmin ettiği gibi PIT'in çalıştırma biçimine (springdoc'un OpenAPI dokümanını önbelleğe alması / paylaşılan context) bağlı bir artefakt, test boşluğu değil. Açıklama kabul. |
| `OllamaEmbeddingClient` 69, 70 (`TIMED_OUT`) | Mutant öldü sayılır (zaman aşımı, test `readTimeout` davranışını yakalıyor). |

Önemli, kuralı etkileyen hayatta kalan mutant yok.

## Uçtan uca
Frontend ve Playwright yok. TASK'taki örnek senaryolar gerçek `bge-m3` ile canlı denendi:
- Doğrudan Ollama'ya `"Şifremi unuttum, online işlemlere giremiyorum"` → 1024 sayılık vektör.
- Backend'e aynı metin → `bge-m3`, 1024, 1024 uzunluk, 63 ms.
- Aynı metin iki kez → vektörler aynı.
- `"  modem ışığı kırmızı yanıyor  "` → boşluksuz metinle aynı vektör.
- Boş / `"   "` → 400 `VALIDATION_FAILED`. 2001 karakter → 400 `VALIDATION_FAILED` (`size must be at most 2000 characters`).
- Ollama erişilemez → 503 `EMBEDDING_UNAVAILABLE`; erişilir olunca aynı süreçte 200.
- İndirilmemiş model → 503 `EMBEDDING_MODEL_NOT_FOUND`.
- Swagger UI `/swagger-ui.html` 200 döndü.

Not: "Ollama kapalı" durumu, kullanıcının Ollama'sı durdurulmadan, backend'i ulaşılamayan adrese yönlendirerek denendi (plandaki yöntem). "Yeniden açılma" için aynı adreste gerçek Ollama'ya ileten geçici bir proxy açıp kapattım. Backend ve proxy işin sonunda durduruldu; 8080 boş, kullanıcının Ollama'sı (`11434`) açık.

## Kod review bulguları
| Severity | Dosya:satır | Bulgu | Sorumlu rol |
|---|---|---|---|
| FYI | `application.properties` (springdoc) | Swagger UI ve `/v3/api-docs` varsayılan olarak açık (log uyarısı veriyor). Eğitim projesi ve lokal çalıştırma için uygun. Üretime çıkılırsa kapatılmalı. | — |
| FYI | `EmbeddingResponse` | `float[]` içeren record'un `equals`/`hashCode`/`toString`'i dizi içeriğine bakmaz. Şu an kimse bunları kullanmıyor (log'a vektör girmiyor), sorun yok. | — |
| Nit | `ollama.md` bölüm 5, 400 örnek çıktısı | Örnekteki `violations` alan sırası (`field`, `message`) gerçek çıktıdan (`message`, `field`) farklı. JSON'da sıra anlamsız. | backend-coder |
| FYI | `backend/CLAUDE.md:57` | `Dockerfile` satırı rol tablosunda (devops sahibi), Docker atfı değil, task kapsamında değil. | — |

Critical veya Required bulgu yok. Katman ayrımı (controller → service → client), `@NullMarked`, yorum politikası, sabit Türkçesiz hata metinleri, sınır doğrulaması ve log kuralı `backend-conventions` ile uyumlu görünüyor. Ek dependency yok (planda listelenenler). Metin SQL'e gitmiyor (veritabanı yok), secret yok.

## Düzeltme
Yok.

STATUS: DONE
