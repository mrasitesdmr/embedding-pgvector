---
name: spring-websocket
description: Spring Boot 4'te STOMP over WebSocket ile gerçek zamanlı mesajlaşma - @EnableWebSocketMessageBroker yapılandırması, destination isimlendirme, @MessageMapping controller'ları, SimpMessagingTemplate ile sunucudan yayın, kullanıcıya özel mesaj (@SendToUser), heartbeat, bağlantı kopma yönetimi, hata frame'leri ve ölçekleme. WebSocket endpoint'i eklerken, içe alma (ingestion) ilerlemesi gibi canlı güncellemeler yayınlarken, STOMP mesaj sözleşmesi tasarlarken veya bağlantı sorunlarını çözerken kullan.
license: MIT
metadata:
  sources:
    - https://github.com/Jeffallan/claude-skills/tree/main/skills/websocket-engineer
    - https://docs.spring.io/spring-framework/reference/web/websocket/stomp.html
  adapted-for: Spring Boot 4.1 / Spring Framework 7 (Socket.IO yerine STOMP)
---

# Spring WebSocket (STOMP)

Proje modeli: istemci REST ile kaynak ekler ya da yeniden işlemeyi başlatır, sonra STOMP ile kaynağın topic'ine abone olur. Sunucu içe alma ilerlemesini (parçalama, embedding üretme, yazma) topic'e yayınlar. Arama REST'tir; WebSocket'e taşınmaz. İstemciden sunucuya STOMP komutu şu an gerekmez; gerekirse `/app/...` üzerinden gelir.


> **Proje durumu:** Projede WebSocket henüz yok; içe alma ilerlemesi iş durumu sorgulanarak (polling) izlenir (TASK-004, `frontend-conventions` bölüm 8). Bu skill, canlı güncellemeyi getiren ilk task için hedef tasarımdır; dependency (`spring-boot-starter-websocket`) ve `docs/api/websocket.md` o task'ın planında eklenir.
## Destination sözleşmesi

| Yön | Pattern | Örnek |
|-----|---------|-------|
| İstemci → sunucu | `/app/sources/{sourceId}/<action>` | `/app/sources/301/cancel-ingestion` |
| Sunucu → kaynağı izleyenler | `/topic/sources/{sourceId}/<event>` | `/topic/sources/301/ingestion` |
| Sunucu → tek istemci | `/user/queue/<event>` | `/user/queue/errors` |

- Path segmentleri kebab-case, çoğul isim (`sources`, `workspaces`).
- Her payload bir Java `record` (`<Noun>Message`, ör. `IngestionProgressMessage(long jobId, long sourceId, IngestionStage stage, double progress, int embeddedChunks, @Nullable Integer totalChunks, @Nullable IngestionErrorCode errorCode)`); alan adları camelCase. Alanlar REST'teki `IngestionJobResponse`'un aynı adlı alanlarıyla aynı tiptedir (`backend-conventions` bölüm 3); frontend ikisini aynı query cache girdisine (`sourceKeys.ingestionJob`) yazar. Çıplak `List`/`String` gönderilmez. Sözleşme değişirse frontend ile birlikte güncellenir (planner `docs/api/websocket.md`'yi günceller).
- Parça içeriği, embedding vektörü ve sorgu metni topic'e gönderilmez; ilerleme mesajı sadece sayaç ve durum taşır.
- İlerleme mesajları seyreltilir: her parçada değil, batch başına (ör. her 64 parçada bir) ve durum değiştiğinde yayınlanır.

## Yapılandırma

```java
@Configuration(proxyBeanMethods = false)
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final SourceSubscriptionInterceptor sourceSubscriptionInterceptor;
    private final WebSocketProperties webSocketProperties;
    private final TaskScheduler messageBrokerTaskScheduler;

    public WebSocketConfig(SourceSubscriptionInterceptor sourceSubscriptionInterceptor,
            WebSocketProperties webSocketProperties,
            @Lazy @Qualifier("messageBrokerTaskScheduler") TaskScheduler messageBrokerTaskScheduler) {
        this.sourceSubscriptionInterceptor = sourceSubscriptionInterceptor;
        this.webSocketProperties = webSocketProperties;
        this.messageBrokerTaskScheduler = messageBrokerTaskScheduler;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(webSocketProperties.allowedOrigins().toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[] {10_000, 10_000})
                .setTaskScheduler(messageBrokerTaskScheduler);
        registry.setPreservePublishOrder(true);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(sourceSubscriptionInterceptor);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(16 * 1024)
                .setSendBufferSizeLimit(512 * 1024)
                .setSendTimeLimit(10_000);
    }
}
```

- `WebSocketProperties`, `app.websocket.allowed-origins` gibi bir prefix'e bağlı `@ConfigurationProperties` record'udur. `*` origin sadece local profile'ın properties dosyasında; kodda hardcode edilmez.
- `messageBrokerTaskScheduler` Spring'in WebSocket altyapısının kendi bean'idir; döngüsel bağımlılık olmaması için `@Lazy` ile enjekte et. Config sınıfında `new ThreadPoolTaskScheduler()` döndüren bir metodu doğrudan çağırma (`proxyBeanMethods = false` iken initialize edilmemiş, yönetilmeyen bir instance döner).
- SockJS ekleme; modern tarayıcılar native WebSocket destekler. Frontend `@stomp/stompjs` kullanır.
- `setPreservePublishOrder(true)`: aynı session'a giden mesajların sırası korunur (ilerleme güncellemeleri sırasız gelmesin; %80'den sonra %60 görünmesin).
- Heartbeat, ölü bağlantıları tespit etmek için zorunlu; simple broker'da `TaskScheduler` verilmezse heartbeat çalışmaz.
- Boot, inbound/outbound kanallar için `applicationTaskExecutor`'u kullanır; `spring.threads.virtual.enabled=true` ise bunlar virtual thread olur.

## Abonelik doğrulaması (hesap yok)

Projede hesap yoktur; bağlantı kimlik doğrulaması gerekmez. Yine de istemci sadece var olan bir kaynağın topic'ine abone olabilmelidir; aksi halde rastgele id'lerle abonelik açılıp broker belleği şişirilebilir. `SUBSCRIBE` frame'i inbound interceptor'da doğrulanır:

```java
@Component
public class SourceSubscriptionInterceptor implements ChannelInterceptor {

    private static final Pattern SOURCE_TOPIC = Pattern.compile("^/topic/sources/(\\d+)/ingestion$");

    private final SourceRepository sourceRepository;

    public SourceSubscriptionInterceptor(SourceRepository sourceRepository) {
        this.sourceRepository = sourceRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            Matcher matcher = SOURCE_TOPIC.matcher(String.valueOf(accessor.getDestination()));
            if (matcher.matches() && !sourceRepository.existsById(Long.parseLong(matcher.group(1)))) {
                throw new NotFoundException("SOURCE_NOT_FOUND", "Source not found: " + matcher.group(1));
            }
        }
        return message;
    }
}
```

Interceptor'dan fırlayan exception'da Spring istemciye `ERROR` frame gönderir ve bağlantıyı kapatır. Bilinmeyen destination pattern'lerine abonelik de reddedilir. İleride hesap eklenirse kimlik `CONNECT` aşamasında doğrulanır ve abonelikte çalışma alanı üyeliği kontrol edilir.

## Mesaj controller'ı

```java
@Controller
public class IngestionMessageController {

    private final IngestionService ingestionService;

    public IngestionMessageController(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @MessageMapping("/sources/{sourceId}/cancel-ingestion")
    public void cancelIngestion(@DestinationVariable long sourceId) {
        ingestionService.cancelIngestion(sourceId);
    }

    @MessageExceptionHandler(BaseException.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public ErrorMessage handleBaseException(BaseException exception) {
        return ErrorMessage.from(exception);
    }
}
```

- Controller ince; iş kuralı service'te. Controller'da `@Transactional` yok.
- `@Valid @Payload` ihlali `org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException` fırlatır (MVC'deki sınıftan farklı paket); bunu da `@MessageExceptionHandler` ile `ErrorMessage("VALIDATION_FAILED", ...)` olarak `/user/queue/errors`'a gönder. `@MessageExceptionHandler` metotları birden çok controller'da gerekiyorsa `@ControllerAdvice` sınıfına taşınır.
- Sunucudan yayın `SimpMessagingTemplate.convertAndSend(...)` / `convertAndSendToUser(...)` ile yapılır; service domain event'i yayınlar (`IngestionProgressedEvent`), event listener `SimpMessagingTemplate`'i çağırır (örnek: `spring-transactions` → `IngestionProgressBroadcaster`).
- DB değişikliğinden sonra yapılan yayınlar `@TransactionalEventListener(phase = AFTER_COMMIT)` ile tetiklenir; ilerleme sadece bellekte tutuluyorsa transaction olmadığı için düz `@EventListener` yeterlidir.
- Hata mesajları `ErrorMessage(errorCode, detail)` şemasıyla gönderilir; `errorCode` değerleri REST'teki ProblemDetail `errorCode`'larıyla aynıdır (`SOURCE_NOT_FOUND`, `INGESTION_IN_PROGRESS`, ...; bkz. `problem-details-rfc9457`).

## Bağlantı yaşam döngüsü

- Bağlantı kopması içe alma işini etkilemez: iş sunucuda sürer, istemci yokken de ilerleme DB'ye (ya da belleğe) yazılır.
- Yeniden bağlanan istemci güncel durumu almak için önce REST ile kaynağın snapshot'ını çeker (`GET .../sources/{sourceId}`), sonra topic'e abone olur; aradaki mesaj kaçsa da snapshot + sonraki mesajlar doğru durumu verir.
- İş bitince (`READY`/`FAILED`) son mesaj yayınlanır; bellekteki `IngestionJob` kaydı temizlenir (memory leak).

## Ölçekleme

- Simple broker in-memory'dir: **tek instance** varsayımı (MVP için yeterli). Bunu kararlarda açıkça belirt.
- Birden fazla instance gerekirse `enableStompBrokerRelay(...)` ile RabbitMQ/ActiveMQ STOMP relay'e geç ve bellekteki ingestion ilerlemesini paylaşılan depoya (DB) taşı; aksi halde worker'ın çalıştığı instance'a bağlı olmayan istemciler mesaj almaz. Load balancer'da WebSocket upgrade desteği ve sticky session gerekir.

## Güvenlik ve dayanıklılık

- Tüm gelen payload'ları `@Valid` ile doğrula; boyut limitleri yukarıdaki transport ayarlarında.
- Bağlantı başına abonelik ve mesaj hızını sınırla — inbound interceptor'da basit token bucket.
- Log'larda payload'ı loglama; `sourceId`, `sessionId`, `command` yeterli (bkz. `observability-tracing`).

## Test

Integration ve unit test pattern'leri için `spring-websocket-testing`.
