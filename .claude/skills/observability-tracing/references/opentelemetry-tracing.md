# Java'da OpenTelemetry ile Distributed Tracing

> Kaynak: [jabrena/plinth — 183-java-observability-tracing-opentelemetry](https://github.com/jabrena/plinth/tree/main/skills/183-java-observability-tracing-opentelemetry) (Apache-2.0, yazar: Juan Antonio Breña Moral). Örnekler vendor-agnostic OpenTelemetry API'sini kullanır.
>
> **Proje notları (öncelikli, `SKILL.md` ile çelişirse `SKILL.md` geçerlidir):**
> 0. Tracing projede henüz kurulmadı (`SKILL.md` "Proje durumu"); aşağıdaki notlar kurulduğunda geçerlidir. `order`/`checkout` örnekleri genel OpenTelemetry illüstrasyonudur; projede karşılığı içe alma işi (`ingestion.job`, attribute `job.id`, `source.id`) ve arama (`search`, attribute `search.mode`) olur.
> 1. Bu projede span'ler Micrometer Observation (`@Observed`, `Observation`) ile oluşturulur; OpenTelemetry SDK'sı Spring Boot tarafından auto-configure edilir. Örnek 4'teki gibi SDK'yı elle kurma ve `buildAndRegisterGlobal()` çağırma; sampling `management.tracing.sampling.probability` ile, export `management.opentelemetry.tracing.export.otlp.endpoint` (HTTP, 4318) ile ayarlanır.
> 2. Async context propagation için Örnek 2'deki elle `Context.wrap` yerine `ContextPropagatingTaskDecorator` kullanılır (`SKILL.md` bölüm 6).
> 3. `@WithSpan` (Örnek 6) OpenTelemetry instrumentation annotation'ıdır ve Java agent/ek modül ister; projede karşılığı `@Observed`'dır.
> 4. Kardinalite: yüksek kardinaliteli iş kimlikleri (kaynak id'si, çalışma alanı id'si gibi) **metric tag'i olamaz**, ama PII değilse span attribute'u olabilir — Micrometer'da `highCardinalityKeyValue` sadece span'e yazılır, `lowCardinalityKeyValue` hem span'e hem metriğe. Kişisel veri, token, payload, kullanıcının yazdığı sorgu metni ve embedding vektörü hiçbir zaman attribute olmaz.
> 5. Tracing testlerinde Micrometer `TestObservationRegistry` (`io.micrometer:micrometer-observation-test`) tercih edilir; Örnek 5 OTel SDK seviyesinde test gösterir. Test isimleri ve AAA yapısı `backend-conventions`'a uyar.

## Rol

Java yazılım geliştirmede geniş deneyime sahip bir Senior software engineer'sın.

## Amaç

Java'da OpenTelemetry ile etkili distributed tracing; net span sınırları, güvenilir context propagation, semantik tutarlılık ve sıkı gizlilik kontrolleri gerektirir.
Trace'ler aşırı overhead yaratmadan latency'yi, hata nedenlerini ve servisler arası bağımlılıkları teşhis etmeye yardım etmelidir.

## Kısıtlar

Tracing verisi güvenli ve operasyonel olarak faydalı kalmalıdır. Hatalı propagation veya hassas attribute'lar telemetry'ye olan güveni azaltır.

- **CONTEXT SÜREKLİLİĞİ**: Trace context'in HTTP/messaging/async sınırları boyunca propagate edildiğinden emin ol
- **SEMANTİK TUTARLILIK**: Stabil span isimleri ve uygun yerlerde OpenTelemetry semantic convention'larını kullan
- **VERİ MİNİMİZASYONU**: Span/event'lerde yüksek kardinaliteli ve hassas attribute'lardan kaçın
- **SAMPLING STRATEJİSİ**: Maliyet ile teşhis derinliğini dengelemek için sampling'i ortama göre yapılandır

## Örnekler

### İçindekiler

- Örnek 1: Net Sınırlarla Span Oluşturma
- Örnek 2: Context'i Propagate Et ve Attribute'ları Güvenli Tut
- Örnek 3: OpenTelemetry Semantic Convention'larını Uygulama
- Örnek 4: Ortama Göre Sampling Stratejisi Yapılandırma
- Örnek 5: Span Doğruluğunu Testlerle Doğrulama
- Örnek 6: Service Metotları için Annotation Tabanlı Instrumentation

### Örnek 1: Net Sınırlarla Span Oluşturma

Başlık: Request ve dependency çağrılarını parent/child span'ler olarak modelle

**İyi örnek:**

```java
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

public final class CheckoutTracing {

    private final Tracer tracer;

    public CheckoutTracing(OpenTelemetry openTelemetry) {
        this(openTelemetry.getTracer("checkout-service"));
    }

    CheckoutTracing(Tracer tracer) {
        this.tracer = tracer;
    }

    public void checkout(String orderId) {
        Span parent = tracer.spanBuilder("checkout.process")
                .setSpanKind(SpanKind.INTERNAL)
                .startSpan();
        try (Scope ignored = parent.makeCurrent()) {
            parent.setAttribute("order.id.present", orderId != null);

            callInventory();
            callPayment();

            parent.setStatus(StatusCode.OK);
        } catch (RuntimeException exception) {
            parent.recordException(exception);
            parent.setStatus(StatusCode.ERROR, "Checkout failed");
            throw exception;
        } finally {
            parent.end();
        }
    }

    private void callInventory() {
        Span span = tracer.spanBuilder("inventory.reserve").setSpanKind(SpanKind.CLIENT).startSpan();
        try (Scope ignored = span.makeCurrent()) {
            // HTTP çağrısı / client SDK çağrısı
            span.setStatus(StatusCode.OK);
        } catch (RuntimeException exception) {
            span.recordException(exception);
            span.setStatus(StatusCode.ERROR);
            throw exception;
        } finally {
            span.end();
        }
    }

    private void callPayment() {
        Span span = tracer.spanBuilder("payment.charge").setSpanKind(SpanKind.CLIENT).startSpan();
        try (Scope ignored = span.makeCurrent()) {
            span.setStatus(StatusCode.OK);
        } catch (RuntimeException exception) {
            span.recordException(exception);
            span.setStatus(StatusCode.ERROR, "Payment charge failed");
            throw exception;
        } finally {
            span.end();
        }
    }
}
```

**Kötü örnek:**

```java
public final class CheckoutTracingBad {
    public void checkout(String userEmail, String token) {
        // KÖTÜ: span/context yok, akıştaki gecikmeyi teşhis etmek imkânsız
        // KÖTÜ: ekipler sonradan özensiz attribute eklediğinde hassas değerler sızar
        System.out.println("processing " + userEmail + " with token " + token);
    }
}
```

### Örnek 2: Context'i Propagate Et ve Attribute'ları Güvenli Tut

Başlık: Sürekliliği sağla, kardinalite/gizlilik sorunlarından kaçın

**İyi örnek:**

```java
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import java.util.concurrent.Executor;

public final class AsyncFlow {
    private final Executor executor;

    public AsyncFlow(Executor executor) {
        this.executor = executor;
    }

    public void submitTask() {
        Context captured = Context.current();
        executor.execute(captured.wrap(() -> {
            Span.current().addEvent("async.task.started");
            // yakalanan parent trace context'iyle işi yap
        }));
    }
}
```

**Kötü örnek:**

```java
import io.opentelemetry.api.trace.Span;

public final class AsyncFlowBad {
    public void submitTask(String userId, String jwtToken) {
        // KÖTÜ: context propagation yok, kopuk span'ler
        // KÖTÜ: hassas attribute'lar (token, kişisel kimlik)
        Span.current().setAttribute("user.id", userId);
        Span.current().setAttribute("auth.token", jwtToken);
    }
}
```

### Örnek 3: OpenTelemetry Semantic Convention'larını Uygulama

Başlık: HTTP, Database ve Messaging span'leri için standart attribute isimleri kullan

**İyi örnek:**

```java
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

public final class OrderRepository {

    private static final String DB_SYSTEM_NAME = "db.system.name";
    private static final String DB_NAMESPACE = "db.namespace";
    private static final String DB_OPERATION_NAME = "db.operation.name";
    private static final String DB_COLLECTION_NAME = "db.collection.name";

    private final Tracer tracer;

    public OrderRepository(OpenTelemetry openTelemetry) {
        this.tracer = openTelemetry.getTracer("order-service");
    }

    public void insertOrder(String orderId, int quantity) {
        Span span = tracer.spanBuilder("INSERT orders")
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute(DB_SYSTEM_NAME, "postgresql")
                .setAttribute(DB_NAMESPACE, "shop")
                .setAttribute(DB_OPERATION_NAME, "INSERT")
                .setAttribute(DB_COLLECTION_NAME, "orders")
                .startSpan();
        try (Scope ignored = span.makeCurrent()) {
            // INSERT'i çalıştır
            span.setStatus(StatusCode.OK);
        } catch (RuntimeException exception) {
            span.recordException(exception);
            span.setStatus(StatusCode.ERROR, "DB insert failed");
            throw exception;
        } finally {
            span.end();
        }
    }
}
```

**Kötü örnek:**

```java
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;

public final class OrderRepositoryBad {

    public void insertOrder(String orderId) {
        Span span = GlobalOpenTelemetry
                .getTracer("repo")
                .spanBuilder("insert")  // KÖTÜ: belirsiz isim, kind yok
                .startSpan();
        try {
            // INSERT'i çalıştır
            // KÖTÜ: semantic convention yerine uydurma attribute isimleri
            span.setAttribute("db", "postgres");
            span.setAttribute("query_type", "INSERT");
            // Not: iş kimliği span attribute'u olabilir (metric tag'i olamaz); burada sorun isimlendirmenin convention dışı olması
            span.setAttribute("order.id", orderId);
        } finally {
            span.end(); // KÖTÜ: status set edilmiyor, exception kaydedilmiyor
        }
    }
}
```

### Örnek 4: Ortama Göre Sampling Stratejisi Yapılandırma

Başlık: Teşhis derinliğini maliyet ve overhead'e karşı dengele

**İyi örnek:**

```java
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;

public final class TracingConfig {

    public static OpenTelemetry buildForEnvironment(String environment) {
        Sampler sampler = switch (environment) {
            case "production" -> Sampler.traceIdRatioBased(0.05);  // prod'da %5
            case "staging"    -> Sampler.traceIdRatioBased(0.50);  // staging'de %50
            default           -> Sampler.alwaysOn();               // dev/test'te %100
        };

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .setSampler(Sampler.parentBased(sampler))
                .addSpanProcessor(BatchSpanProcessor.builder(
                        OtlpGrpcSpanExporter.builder()
                                .setEndpoint("http://otel-collector:4317")
                                .build())
                        .build())
                .build();

        return OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .buildAndRegisterGlobal();
    }
}
```

**Kötü örnek:**

```java
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.samplers.Sampler;

public final class TracingConfigBad {

    public static SdkTracerProvider build() {
        // KÖTÜ: production'da always-on sampling maliyeti patlatır
        // KÖTÜ: BatchSpanProcessor yok — senkron export request thread'lerini bloklar
        return SdkTracerProvider.builder()
                .setSampler(Sampler.alwaysOn())
                .build();
    }
}
```

### Örnek 5: Span Doğruluğunu Testlerle Doğrulama

Başlık: In-Memory Exporter kullanarak span name, kind, status ve attribute'ları assert et

**İyi örnek:**

```java
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CheckoutTracingTest {

    private InMemorySpanExporter exporter;
    private CheckoutTracing checkoutTracing;

    @BeforeEach
    void setUp() {
        exporter = InMemorySpanExporter.create();
        SdkTracerProvider provider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(provider)
                .build();
        checkoutTracing = new CheckoutTracing(openTelemetry);
    }

    @Test
    void givenSuccessfulCheckout_whenCheckout_thenRecordsInternalSpanWithOkStatus() {
        // Act
        checkoutTracing.checkout("order-42");

        // Assert
        List<SpanData> spans = exporter.getFinishedSpanItems();
        assertThat(spans).hasSizeGreaterThanOrEqualTo(1);

        SpanData root = spans.stream()
                .filter(span -> span.getName().equals("checkout.process"))
                .findFirst()
                .orElseThrow();

        assertThat(root.getKind()).isEqualTo(SpanKind.INTERNAL);
        assertThat(root.getStatus().getStatusCode()).isEqualTo(StatusCode.OK);
        assertThat(root.getAttributes().get(AttributeKey.booleanKey("order.id.present"))).isTrue();
        assertThat(root.getAttributes().asMap()).doesNotContainKey(AttributeKey.stringKey("order.id"));
    }
}
```

**Kötü örnek:**

```java
class CheckoutTracingTestBad {
    // KÖTÜ: span assertion'ı yok; tracing test edilmeden sessizce bozulabilir.
    // KÖTÜ: testlerde GlobalOpenTelemetry paylaşılan state ve flaky sonuç üretir.
}
```


### Örnek 6: Service Metotları için Annotation Tabanlı Instrumentation

Başlık: Kısa ve tutarlı tracing için @WithSpan ve @SpanAttribute'tan yararlan (bu projede karşılığı Micrometer `@Observed`'dir)

**İyi örnek:**

```java
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;

public final class ShippingService {

    @WithSpan("shipping.create.label")
    public String createLabel(
            @SpanAttribute("shipping.provider") String provider,
            @SpanAttribute("shipping.expedited") boolean expedited) {
        // iş mantığı
        return "LBL-123";
    }
}
```

**Kötü örnek:**

```java
import io.opentelemetry.api.trace.Span;

public final class ShippingServiceBad {

    public String createLabel(String provider, String customerEmail, String requestId) {
        // KÖTÜ: bu iş operasyonu için açık bir span sınırı yok
        // KÖTÜ: hassas attribute'lar (e-posta) ve convention dışı isimler
        Span.current().setAttribute("customer.email", customerEmail);
        Span.current().setAttribute("request.id", requestId);
        Span.current().setAttribute("provider", provider);
        return "LBL-123";
    }
}
```


## Çıktı Formatı

- **ANALİZ ET**: Span modelleme, propagation ve semantik tutarlılıktaki tracing boşluklarını
- **UYGULA**: Net parent/child ilişkileri ve stabil isimlerle OpenTelemetry instrumentation'ını
- **SAĞLAMLAŞTIR**: Attribute/event/status'ları; hassas veya yüksek kardinaliteli veriyi kaldır
- **DOĞRULA**: Testlerde ve runtime backend'lerinde (Jaeger) uçtan uca trace sürekliliğini


## Güvenlik Önlemleri

- **ÖNCE GİZLİLİK**: Trace payload'larına asla secret veya PII koyma
- **PROPAGATION KONTROLÜ**: traceId/spanId sürekliliğini servis sınırları boyunca doğrula
- **KARDİNALİTE KONTROLÜ**: Attribute değerlerini sınırlı ve sorgulanabilir tut
- **PERFORMANS FARKINDALIĞI**: Sıcak döngülerde gereksiz span'lerden kaçın ve sampling'i uygun yapılandır
