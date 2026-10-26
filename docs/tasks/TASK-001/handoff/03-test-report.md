TASK: TASK-001
# Test raporu

## Kullanılan skill'ler
`writing-good-tests`, `backend-conventions`, `spring-testing-fundamentals`, `spring-mvc-testing`.

## Senaryo → test eşlemesi
Test dosyaları `backend/src/test/java/com/embedding/pgvector/` altında. Yardımcılar: `embedding/client/OllamaStubServer` (JDK `HttpServer`), `ApiTestClient` (JDK `HttpClient`).

| # | Senaryo / Kural | Test (dosya#metot) | Sonuç |
|---|-----------------|--------------------|-------|
| T1 | K5 alanlar ve süre | `embedding/service/EmbeddingServiceTest#givenClientReturnsEmbedding_whenEmbedText_thenReturnsModelDimensionsEmbeddingAndDuration` | geçti |
| T2 | K10 log | `EmbeddingServiceTest#givenTurkishText_whenEmbedText_thenLogsModelDimensionsDurationWithoutTextOrEmbedding` | geçti |
| T3 | S-4 kırpma | `embedding/domain/dto/EmbedTextRequestTest#givenTextWithSurroundingWhitespace_whenCreateRequest_thenStripsLeadingAndTrailingWhitespace` (3 parametre) + `givenTextWithTabsAndNewlines_...` | geçti |
| T4 | G2 içteki boşluk | `EmbedTextRequestTest#givenTextWithInnerDoubleSpace_whenCreateRequest_thenKeepsInnerWhitespace` (+ ek: `givenNullText_...`) | geçti |
| T5 | S-3 | `embedding/client/OllamaEmbeddingClientTest#givenSameTextTwice_whenEmbed_thenSendsIdenticalRequestBodies` | geçti |
| T6 | K3, K8 | `OllamaEmbeddingClientTest#givenTurkishText_whenEmbed_thenPostsConfiguredModelAndUtf8InputToApiEmbed` | geçti |
| T7 | S-7 | `OllamaEmbeddingClientTest#givenNothingListeningOnBaseUrl_whenEmbed_thenThrowsEmbeddingUnavailable` | geçti |
| T8 | S-8 | `OllamaEmbeddingClientTest#givenOllamaReturns404_whenEmbed_thenThrowsEmbeddingModelNotFound` | geçti |
| T8b | G5 | `OllamaEmbeddingClientTest#givenOllamaReturns500_whenEmbed_thenThrowsEmbeddingUnavailable` | geçti |
| T9 | G4 | `OllamaEmbeddingClientTest#givenOllamaDoesNotAnswerWithinReadTimeout_whenEmbed_thenThrowsEmbeddingUnavailable` | geçti |
| T9b | S-7 | `OllamaEmbeddingClientTest#givenOllamaStartedAfterFailedCall_whenEmbedAgain_thenReturnsEmbedding` | geçti |
| T10 | yanıt parse | `OllamaEmbeddingClientTest#givenOllamaReturnsEmbedding_whenEmbed_thenReturnsEmbeddingWithConfiguredModel` | geçti |
| T10b | G5 boş vektör | `OllamaEmbeddingClientTest#givenOllamaReturnsNoEmbeddings_whenEmbed_thenThrowsEmbeddingUnavailable` (`[]`, `[[]]`, alan yok) | geçti |
| T11-T14 | S-5, S-6, G3 | `embedding/controller/EmbeddingControllerTest#givenEmptyText_...`, `givenWhitespaceOnlyText_...`, `givenMissingText_...` (`{}` ve `null`), `givenTextOf2001Characters_...` | geçti |
| T15, T16 | G1 | `EmbeddingControllerTest#givenTextOf2000Characters_whenPostEmbeddings_thenReturns200`, `givenTextOf2000CharactersWithSurroundingSpaces_...thenServiceReceivesStrippedText` | geçti |
| T17, T17b | G3, K5 | `EmbeddingControllerTest#givenMalformedJson_...`, `givenServiceReturnsEmbedding_...` | geçti |
| T18, T18b | K9, AC-4 | `EmbeddingControllerTest#givenEmbeddingUnavailable_...`, `givenEmbeddingModelNotFound_...`, `givenUnexpectedException_...` | geçti |
| T19 | S-2, AC-3 | `embedding/EmbeddingIntegrationTest#givenOllamaStubReturns1024Values_whenPostEmbeddings_thenReturns200WithModelDimensionsEmbeddingAndDuration` | geçti |
| T20 | S-4, K8 | `EmbeddingIntegrationTest#givenTurkishTextWithSurroundingSpaces_whenPostEmbeddings_thenOllamaReceivesStrippedUtf8Text` | geçti |
| T21 | S-5 | `EmbeddingIntegrationTest#givenWhitespaceOnlyText_whenPostEmbeddings_thenReturns400AndOllamaReceivesNoRequest` | geçti |
| T22 | S-7, K9 | `embedding/EmbeddingOllamaRestartIntegrationTest#givenOllamaDownThenStarted_whenPostEmbeddingsTwice_thenFirstReturns503AndSecondReturns200` (ayrı sınıf: farklı base-url) | geçti |
| T23 | S-8 | `EmbeddingIntegrationTest#givenOllamaStubReturns404_whenPostEmbeddings_thenReturns503EmbeddingModelNotFound` | geçti |
| T24 | AC-5, D19 | `config/OpenApiDocumentationIntegrationTest#givenRunningApplication_whenGetApiDocs_thenProblemDetailSchemaHasErrorCodeViolationsAndContractInfo` | geçti |

Notlar:
- Planda olmayan ek testler: `givenNullText_...` (null metin null kalır), controller testlerinde `type`/`title`/`instance` assertion'ları (PIT hayatta kalan mutantlarını kapatmak için).
- Integration testlerde `TestRestClient` kullanılamadı (`spring-boot-resttestclient` pom'da yok, pom'a dokunmam). Yerine JDK `HttpClient` + `@LocalServerPort` kullanıldı; dependency gerekmedi.
- T2 log testi `OutputCaptureExtension` ile; T9 `Thread.sleep` içermez (latch).

## Mutation
PIT 1.30.1 (`./mvnw test-compile org.pitest:pitest-maven:mutationCoverage`): 51 mutant, 45 öldü (%88), line coverage (mutate edilen sınıflar) %99. İlk koşuda 42 öldü; `type`/`title` assertion'ları eklenince 3 mutant daha kapandı. Hayatta kalan 6:
- `GlobalExceptionHandler` satır 69 `setTitle`, 70 `setInstance` (500 yolu): `ProblemDetail.forStatusAndDetail` title'ı, Spring de `instance`'ı zaten varsayılan olarak doldurduğu için çıktı değişmiyor; eşdeğer mutant (testte title/instance doğrulanıyor, yine de hayatta).
- `GlobalExceptionHandler.hasErrorCode` satır 77: `properties == null` dalı, pratikte erişilemez (NO_COVERAGE), kural dışı.
- `OpenApiConfig` satır 40, 44, 60: T24 aynı mutasyonlarda normalde kırılmalı; PIT altında hayatta kalmaları springdoc'un dokümanı önbelleğe almasıyla ilgili olabilir. Uygulama kodu elle bozulmadı. Kuralı etkileyen bir boşluk görmüyorum: T24 `errorCode` enum'u, `violations`, `Violation.required` ve `EmbeddingResponse.required`'ı gerçek uygulamada doğruluyor. qa isterse inceleyebilir.

## Komutlar ve sonuçları
- `cd backend && ./mvnw verify` → BUILD SUCCESS, 34 test (6 sınıf + 1 integration sınıfı: 7 sınıf), 0 fail, 0 error. JaCoCo "All coverage checks have been met"; satır coverage %97,8 (132/135; kapsanmayan: `EmbeddingPgvectorApplication.main`, `OpenApiConfig` erken dönüş dalı).
- `OLLAMA_BASE_URL=http://127.0.0.1:9 ./mvnw verify` → BUILD SUCCESS, 34 test geçti.
- Testlerde `11434` geçmez (`grep -rn 11434 backend/src/test` boş). Kullanıcının Ollama'sına dokunulmadı; testler kendi stub'ına ya da kapalı porta gider.
- Birden fazla çalıştırmada flaky görülmedi.

## Çalıştırılamayanlar
Yok (frontend ve e2e bu task'ta yok). Q1-Q4 canlı kontroller qa'nın.

## Düzeltme
Yok.

STATUS: DONE
