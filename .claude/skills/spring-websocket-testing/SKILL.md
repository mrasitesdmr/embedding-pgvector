---
name: spring-websocket-testing
description: "WebSocket testleri @SpringBootTest(RANDOM_PORT) ve asenkron mesaj alma gerektirir; @WebMvcTest çalışmaz. Timeout ve session leak tuzakları yaygındır. STOMP veya WebSocket test etmeden önce oku. Tetikleyiciler: WebSocket integration test, WebSocketStompClient, StompSession, @MessageMapping, @SendToUser, BlockingQueue, /topic/, /queue/, CountDownLatch, session.subscribe, session.send."
license: Apache-2.0
metadata:
  source: https://github.com/spring-ai-community/spring-testing-skills/tree/main/skills/spring-websocket-testing
  version: 0.1.0
---

# Spring WebSocket Testing

**Sinyaller**: `WebSocketStompClient`, `StompSession`, `StompFrameHandler`, `BlockingQueue`, `RANDOM_PORT`, `session.subscribe`, `session.send`, `/topic/`, `/queue/`, `/app/`, `@MessageMapping`, `@SendToUser`

## Proje kuralı (öncelikli)

- Test isimlendirmesi ve AAA yapısı: `backend-conventions`.
- Destination'lar (`/app/...`, `/topic/...`) ve mesaj payload'ları `spring-websocket` skill'indeki proje sözleşmesine uymalı.
- `Thread.sleep()` yasak; `BlockingQueue.poll(timeout)` veya `CountDownLatch.await(timeout)` kullan.
- STOMP istemcisinde Jackson 3 `JacksonJsonMessageConverter` kullanılır (`MappingJackson2MessageConverter` Jackson 2'dir).

## Test edildiği sürümler

- Proje: Spring Boot 4.1.1, Spring Framework 7.0 (WebSocket dahil), JUnit Jupiter 6
- Test dependency'si: `spring-boot-starter-websocket-test`

## Bu skill'i KULLANMA

- REST controller testi → `spring-mvc-testing`
- `@MessageMapping` controller'ının delegasyon unit testi ve service kuralları → `spring-testing-fundamentals` (altyapısız, düz unit test)

## Referansları ne zaman oku

| Durum | Oku |
|-------|-----|
| WebSocket için neden `@SpringBootTest(webEnvironment = RANDOM_PORT)` gerekli | `references/websocket-stomp-testing-patterns.md` |
| `WebSocketStompClient` kurulumu, `StompSession`, connect/subscribe/send pattern'i | `references/websocket-stomp-testing-patterns.md` |
| STOMP mesajlarını almak için `BlockingQueue` pattern'i | `references/websocket-stomp-testing-patterns.md` |
| `@MessageMapping` handler'larını doğrudan unit test etme (hızlı yol) | `references/websocket-stomp-testing-patterns.md` |
| `@SendToUser` (kullanıcıya özel mesaj) testi | `references/websocket-stomp-testing-patterns.md` |
| Tek event assertion'ı için `CountDownLatch` pattern'i | `references/websocket-stomp-testing-patterns.md` |
| Error frame'leri ve STOMP hata yönetimi testi | `references/websocket-stomp-testing-patterns.md` |
| Spring Security ile WebSocket (session cookie auth) | `references/websocket-stomp-testing-patterns.md` |
| Anti-pattern'ler: `Thread.sleep()`, çok kısa timeout, session leak | `references/websocket-stomp-testing-patterns.md` |
