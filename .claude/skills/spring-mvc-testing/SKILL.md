---
name: spring-mvc-testing
description: "Spring Boot 4+ MockMvc assertion'ları yerine MockMvcTester getirdi; eğitim verisindeki eski API uyumsuz. Herhangi bir @WebMvcTest yazmadan önce oku. Tetikleyiciler: controller testi, @WebMvcTest, MockMvc, MockMvcTester, @RestController, jsonPath, @GetMapping, @PostMapping, @AutoConfigureMockMvc, status().isOk(), @RestControllerAdvice, validation hata testi."
license: Apache-2.0
metadata:
  source: https://github.com/spring-ai-community/spring-testing-skills/tree/main/skills/spring-mvc-testing
  version: 0.1.0
---

# Spring MVC Testing

**Sinyaller**: `@WebMvcTest`, `MockMvc`, `MockMvcTester`, `jsonPath`, `@AutoConfigureMockMvc`, `@RestController`, `MockMvcRequestBuilders`, `MockMvcResultMatchers`

## Proje kuralı (öncelikli)

- Test isimlendirmesi ve AAA yapısı: `backend-conventions`.
- Controller testleri `MockMvcTester` ile yazılır (`MockMvc` + Hamcrest `jsonPath` değil).
- MVP'de Spring Security yoktur; security testleri Security eklendiğinde geçerlidir.
- Hata response'ları RFC 9457 `ProblemDetail` formatındadır (`problem-details-rfc9457`); hata testlerinde status, `application/problem+json` content type'ı ve sabit `errorCode`'u doğrula (`detail` metnine bağlanma).
- Endpoint path'leri ve response şekilleri `docs/api/openapi.yaml` ile birebir aynı olmalı.

## Test edildiği sürümler

- Proje: Spring Boot 4.1.1, Spring Framework 7.0, JUnit Jupiter 6
- Spring Security 7 (classpath'teyse `@WebMvcTest` içinde auto-configure edilir)

## Bu skill'i KULLANMA

- Authentication, authorization, CSRF, JWT → `springboot-security`
- JPA repository veya persistence → `spring-jpa-testing`
- WebSocket / STOMP → `spring-websocket-testing`
- Spring context'siz saf unit test → `spring-testing-fundamentals`

## Referansları ne zaman oku

| Durum | Oku |
|-------|-----|
| `@WebMvcTest` kurulumu, `MockMvcTester`, static import'lar | `references/mvc-rest-testing-patterns.md` |
| GET, POST, PUT, DELETE request pattern'leri | `references/mvc-rest-testing-patterns.md` |
| JSON assertion'ları (`bodyJson().extractingPath`), response header'ları | `references/mvc-rest-testing-patterns.md` |
| `@Valid` bean validation ve `@RestControllerAdvice` exception handler testleri | `references/mvc-rest-testing-patterns.md` |
| `MockMvc` → `MockMvcTester` karşılıkları, `RestTestClient` | `references/mvc-rest-testing-patterns.md` |
| Anti-pattern'ler ve sık yapılan hatalar | `references/mvc-rest-testing-patterns.md` |
