# WebSocket / STOMP Test Pattern'leri

Spring WebSocket + STOMP endpoint'lerini test etmek için hızlı referans. WebSocket testleri **gerçek bir server** gerektirir — `MockMvc`/`MockMvcTester` ve `WebTestClient` WebSocket handshake'ini tamamlayamaz.

> **Proje durumu:** WebSocket henüz yok (ilerleme polling ile izlenir); bu dosya canlı güncellemeyi getiren task için hedeftir. Payload alanları `IngestionJobResponse` ile aynıdır (`spring-websocket`).
>
> Proje kuralları: test isimleri `given_when_then`, gövde AAA (`backend-conventions`); destination'lar ve payload record'ları `spring-websocket`'teki sözleşmeye uyar (`/app/sources/{sourceId}/...`, `/topic/sources/{sourceId}/...`, `/user/queue/...`). Payload'lar `record` olduğu için alanlara accessor ile erişilir (`message.processedChunks()`, `getProcessedChunks()` değil). Testlerde `EmbeddingClient` deterministik sahte implementasyonla değiştirilir; içe alma gerçek API'ye gitmez.

---

## RANDOM_PORT Neden Gerekli

```java
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

// MockMvc sahte bir DispatcherServlet üzerinde çalışır — gerçek TCP soketi yok.
// WebTestClient (bind edilmiş modda) sahte filter chain üzerinde çalışır — gerçek TCP soketi yok.
// WebSocket upgrade'i, WS'e yükseltilebilen gerçek bir HTTP bağlantısı gerektirir.

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IngestionWebSocketIntegrationTest {

    @LocalServerPort
    int port;
}
```

Rastgele bir port'ta gerçek bir server başlatılır. `@LocalServerPort` port numarasını inject eder; testler WebSocket URL'ini bununla oluşturur.

---

## StompClient Kurulumu

```java
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IngestionProgressWebSocketIntegrationTest {

    @LocalServerPort int port;
    WebSocketStompClient stompClient;
    final List<StompSession> sessions = new ArrayList<>();

    @BeforeEach
    void setUp() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new JacksonJsonMessageConverter());  // Jackson 3; Jackson 2'ye ait MappingJackson2MessageConverter kullanılmaz
        stompClient.setDefaultHeartbeat(new long[] {0, 0});  // testte istemci heartbeat'ini kapat — flaky davranışı önler
    }

    @AfterEach
    void tearDown() {
        sessions.stream().filter(StompSession::isConnected).forEach(StompSession::disconnect);
        stompClient.stop();  // ZORUNLU: testler arasında executor thread sızıntısını önler
    }
}
```

**`@AfterEach` içinde her zaman açılan session'ları kapat ve `stompClient.stop()` çağır** — `WebSocketStompClient`, durdurulmazsa testten sonra da yaşayan executor thread'leri açar ve sonraki testlerde flaky hatalara yol açar. Session'lar test gövdesinin sonunda değil `@AfterEach`'te kapatılır: gövdedeki bir assertion fail ederse sonraki `disconnect()` satırı hiç çalışmaz. Bu yüzden her session `connectAs(...)` yardımcısıyla açılır ve `sessions` listesine eklenir (aşağıda).

---

## BlockingQueue Pattern'i — Gönder + Al

Alınan STOMP mesajlarını assert etmenin standart pattern'i. `Thread.sleep()` yerine timeout'lu `BlockingQueue.poll()` kullan:

```java
import java.lang.reflect.Type;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;

@Test
void givenSubscribedToSourceIngestion_whenVersionUploaded_thenReceivesProgressEndingWithDone() throws Exception {
    // Arrange
    BlockingQueue<IngestionProgressMessage> received = new LinkedBlockingQueue<>();
    long sourceId = 302L;
    StompSession session = connect();
    session.subscribe("/topic/sources/" + sourceId + "/ingestion", new StompFrameHandler() {
        @Override
        public Type getPayloadType(StompHeaders headers) {
            return IngestionProgressMessage.class;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            received.offer((IngestionProgressMessage) payload);
        }
    });

    // Act
    uploadVersion(42L, sourceId, markdownWithChunks(3));

    // Assert
    IngestionProgressMessage last = pollUntilStage(received, IngestionStage.DONE);
    assertThat(last.embeddedChunks()).isEqualTo(3);
    assertThat(last.totalChunks()).isEqualTo(3);
}
```

`uploadVersion` / `markdownWithChunks` / `pollUntilStage` test sınıfındaki küçük yardımcı metotlardır; yükleme multipart REST çağrısıdır (`RestTestClient`), `pollUntilStage` kuyruktan timeout'lu `poll` ile beklenen aşamayı görene kadar okur. Abonelik ile aksiyon arasında yarış olmaması için aksiyon, `subscribe` çağrısından sonra yapılır; gerekirse önce sunucudan bir ilk mesaj (snapshot) beklenir.

**Timeout önerisi**: `poll()` / `await()` çağrılarında 5–10 saniye kullan. WebSocket testlerinin gerçek bir açılış maliyeti vardır — 1 saniyelik timeout'lar CI'da flaky hatalara yol açar.

---

## @MessageMapping Handler'larını Doğrudan Unit Test Etme

Handler mantığını izole test etmek için — hızlı, altyapı yok, network yok:

```java
@ExtendWith(MockitoExtension.class)
class IngestionMessageControllerTest {

    @Mock
    private IngestionService ingestionService;

    @InjectMocks
    private IngestionMessageController ingestionMessageController;

    @Test
    void givenSourceId_whenCancelIngestion_thenDelegatesToIngestionService() {
        // Act
        ingestionMessageController.cancelIngestion(301L);

        // Assert
        then(ingestionService).should().cancelIngestion(301L);
    }
}
```

**Test stratejisi**: `@MessageMapping` controller'ı ince olduğu için (iş kuralı service'te) unit testi sadece delegasyonu doğrular; asıl kurallar service unit testlerindedir. `@SpringBootTest(RANDOM_PORT)` + `StompClient`'ı sadece STOMP routing, broadcast ve subscription teslimatını doğrulayan integration testlerinde kullan.

---

## @SendToUser Testi (Kullanıcıya Özel Mesajlar)

```java
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.web.socket.WebSocketHttpHeaders;

@Test
void givenTwoSessions_whenOneCancelsUnknownSource_thenOnlyThatSessionReceivesError() throws Exception {
    // Arrange
    BlockingQueue<ErrorMessage> senderReceived = new LinkedBlockingQueue<>();
    BlockingQueue<ErrorMessage> otherReceived = new LinkedBlockingQueue<>();
    StompSession senderSession = connect();
    StompSession otherSession = connect();
    senderSession.subscribe("/user/queue/errors", frameHandler(ErrorMessage.class, senderReceived));
    otherSession.subscribe("/user/queue/errors", frameHandler(ErrorMessage.class, otherReceived));

    // Act
    senderSession.send("/app/sources/999999/cancel-ingestion", new byte[0]);

    // Assert
    assertThat(senderReceived.poll(5, TimeUnit.SECONDS)).isNotNull();
    assertThat(otherReceived.poll(1, TimeUnit.SECONDS)).isNull();
}
```

Hesap olmadığı için `/user/...` hedefleri session'a göre çözülür: Spring her bağlantıya kendi `Principal`'ı yoksa session id'sini kullanır; `@SendToUser(broadcast = false)` cevabı sadece isteği gönderen session'a iletir.

---

## CountDownLatch Pattern'i (Tek Event için Alternatif)

Tam olarak bir event bekliyorsan ve değerini assert etmek istiyorsan `CountDownLatch` kullan:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

@Test
void givenConnectedClient_whenCancelUnknownSource_thenReceivesSourceNotFoundError() throws Exception {
    // Arrange
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<ErrorMessage> received = new AtomicReference<>();
    StompSession session = connect();
    session.subscribe("/user/queue/errors", new StompFrameHandler() {
        @Override
        public Type getPayloadType(StompHeaders headers) {
            return ErrorMessage.class;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            received.set((ErrorMessage) payload);
            latch.countDown();
        }
    });

    // Act
    session.send("/app/sources/999999/cancel-ingestion", new byte[0]);

    // Assert
    assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(received.get().errorCode()).isEqualTo("SOURCE_NOT_FOUND");
}
```

Birden fazla mesaj için `BlockingQueue`, beklenen tek bir event için `CountDownLatch + AtomicReference` kullan.

Ortak yardımcılar (test sınıfında private metot):

```java
private StompSession connect() throws Exception {
    StompSession session = stompClient.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(),
                    new StompHeaders(), new StompSessionHandlerAdapter() {})
            .get(5, TimeUnit.SECONDS);
    sessions.add(session);
    return session;
}

private <T> StompFrameHandler frameHandler(Class<T> payloadType, BlockingQueue<T> queue) {
    return new StompFrameHandler() {
        @Override
        public Type getPayloadType(StompHeaders headers) {
            return payloadType;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            queue.offer(payloadType.cast(payload));
        }
    };
}
```

---

## Error Frame'lerini Test Etme

```java
import org.springframework.messaging.simp.stomp.StompCommand;

@Test
void givenUnknownSourceId_whenSubscribeToIngestion_thenServerSendsErrorFrame() throws Exception {
    // Arrange
    CountDownLatch errorLatch = new CountDownLatch(1);
    StompSessionHandlerAdapter handler = new StompSessionHandlerAdapter() {
        @Override
        public void handleTransportError(StompSession session, Throwable exception) {
            errorLatch.countDown();
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            errorLatch.countDown();
        }
    };

    StompSession session = stompClient.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(),
            new StompHeaders(), handler).get(5, TimeUnit.SECONDS);
    sessions.add(session);

    // Act
    session.subscribe("/topic/sources/999999/ingestion", frameHandler(IngestionProgressMessage.class, new LinkedBlockingQueue<>()));

    // Assert
    assertThat(errorLatch.await(5, TimeUnit.SECONDS)).isTrue();
}
```

---

## Spring Security ile WebSocket

Authentication içeren tam integration testleri için:

```java
import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.WebSocketHttpHeaders;

// Adım 1: HTTP login ile session cookie'si al
// Adım 2: cookie'yi WebSocketHttpHeaders içinde gönder

WebSocketHttpHeaders wsHeaders = new WebSocketHttpHeaders();
wsHeaders.add(HttpHeaders.COOKIE, "SESSION=" + sessionId);

StompSession session = stompClient
    .connectAsync("ws://localhost:" + port + "/ws",
                   wsHeaders,
                   new StompHeaders(),
                   new StompSessionHandlerAdapter() {})
    .get(5, TimeUnit.SECONDS);
```

`sessionId`'yi, WebSocket testinden önce `RestTestClient` ile `/login`'e HTTP POST yaparak al. (Bu bölüm Spring Security eklenirse geçerlidir.)

> Proje notu: Bu projede hesap yok; bağlantı kimlik doğrulaması yapılmaz, sadece abonelik (`SUBSCRIBE`) var olan bir kaynağa mı diye doğrulanır (bkz. `spring-websocket` "Abonelik doğrulaması"). Testte bağlantı yukarıdaki `connect()` yardımcısıyla açılır.

---

## Neyi Nerede Test Etmeli

| Test Türü | Neyi Kapsar | Altyapı |
|---|---|---|
| `@MessageMapping` controller üzerinde unit test | Service'e delegasyon, `@MessageExceptionHandler` dönüşü | Yok — `MockitoExtension` |
| `@SpringBootTest(RANDOM_PORT)` + StompClient | Tam WS handshake, STOMP routing, broadcast, `@SendToUser` | Rastgele port'ta gerçek server |
| Güvenilir test etmesi zor | SockJS fallback güvenilirliği, CI'da broker relay (ActiveMQ/RabbitMQ) | Harici broker gerekir |

---

## Boot 3.x → 4.x WebSocket Test Değişiklikleri

| Alan | Boot 3.x | Boot 4.x |
|---|---|---|
| WebSocket namespace | `jakarta.websocket.*` | Aynı |
| `StandardWebSocketClient` | `org.springframework.web.socket.client.standard` | Aynı |
| `WebSocketStompClient` | `org.springframework.web.socket.messaging` | Aynı |
| Security WebSocket config | `AbstractSecurityWebSocketMessageBrokerConfigurer` | Kaldırıldı; `@EnableWebSocketSecurity` + `AuthorizationManager<Message<?>>` bean'i kullan |
| STOMP JSON converter | `MappingJackson2MessageConverter` (Jackson 2) | `JacksonJsonMessageConverter` (Jackson 3) |

---

## Anti-Pattern'ler

| Anti-Pattern | Çözüm |
|---|---|
| WebSocket testleri için `MockMvc` kullanmak | `RANDOM_PORT` + `StompClient` kullan — `MockMvc` WebSocket'e upgrade edemez |
| `poll()` / `await()` içinde çok kısa timeout (1s) | 5–10s kullan — açılış maliyeti gerçek, özellikle CI'da |
| Session'ları test gövdesinin sonunda kapatmak ya da hiç kapatmamak | Her session'ı `connectAs(...)` ile açıp listeye ekle, `@AfterEach`'te `disconnect()` et (assertion fail etse de çalışır) |
| `@AfterEach` içinde `stompClient`'ı durdurmamak | Executor thread'leri sızar, sonraki testlerde flaky hatalara yol açar |
| Farklı session'lar arasında mesaj sırasını varsaymak | Sıra sadece aynı session içinde ve `setPreservePublishOrder(true)` ile garanti; session'lar arası karşılaştırmada sıradan bağımsız assertion kullan |
| Senkronizasyon için `Thread.sleep()` | `BlockingQueue.poll(timeout)` veya `CountDownLatch.await(timeout)` kullan |
