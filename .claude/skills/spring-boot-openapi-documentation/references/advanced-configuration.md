# İleri SpringDoc Yapılandırması

Proje config sınıfı `config.OpenApiConfig`'tir (`@Configuration(proxyBeanMethods = false)`); aşağıdaki bean'ler oraya eklenir. Proje Spring MVC ve Java kullanır; WebFlux ve Kotlin yapılandırması kapsam dışıdır.

## OpenAPI Bean'i

```java
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;

@Bean
OpenAPI embeddingPgvectorOpenApi(OpenApiProperties openApiProperties) {
    return new OpenAPI()
            .info(new Info()
                    .title("Embedding ve pgvector API")
                    .description("REST API for knowledge sources and semantic search. Ingestion progress is delivered over STOMP at /ws.")
                    .version("0.1.0"))
            .addServersItem(new Server().url(openApiProperties.serverUrl()).description("Current environment"));
}
```

- `title` / `version` `docs/api/openapi.yaml`'daki `info` ile aynıdır.
- Server URL hardcode edilmez; `@ConfigurationProperties(prefix = "app.openapi") record OpenApiProperties(String serverUrl)` ile profile'a göre `application-<profile>.properties`'ten gelir. Server tanımlanmazsa SpringDoc isteğin geldiği adresi kullanır; çoğu durumda bu yeterlidir.

## Birden Fazla API Grubu

İleride ayrı dokümanlar gerekirse (ör. arama API'si ve yönetim API'si):

```java
import org.springdoc.core.models.GroupedOpenApi;

@Bean
GroupedOpenApi searchApi() {
    return GroupedOpenApi.builder()
            .group("search")
            .pathsToMatch("/api/workspaces/**")
            .build();
}

@Bean
GroupedOpenApi adminApi() {
    return GroupedOpenApi.builder()
            .group("admin")
            .pathsToMatch("/api/admin/**")
            .build();
}
```

Her grup `/v3/api-docs/<grup>` altında sunulur ve Swagger UI'da seçilebilir. Grup tanımlanınca varsayılan `/v3/api-docs` sadece grupsuz doküman olmaktan çıkar; sözleşme testi ([build-integration.md](build-integration.md)) grup URL'lerini kullanacak şekilde güncellenir.

## OperationCustomizer

Tüm operation'lara uygulanacak değişiklikler:

```java
import org.springdoc.core.customizers.OperationCustomizer;

@Bean
OperationCustomizer deprecatedOperationCustomizer() {
    return (operation, handlerMethod) -> {
        if (handlerMethod.hasMethodAnnotation(Deprecated.class)) {
            operation.setDeprecated(true);
        }
        return operation;
    };
}
```

## OpenApiCustomizer

Dokümanın tamamı üzerinde çalışır (ör. ortak hata response'ları, `ProblemDetail` extension alanları). Proje örneği: [exception-handling.md](exception-handling.md).

## Endpoint'leri Gizleme

```java
@Operation(hidden = true)
@GetMapping("/internal/health-details")
public HealthDetailsResponse getHealthDetails() {
    ...
}

@Hidden
@RestController
@RequestMapping("/internal")
public class InternalController {
}
```

Profile'a göre gizlemek için annotation yerine path filtresi kullan:

```properties
# application-prod.properties
springdoc.paths-to-exclude=/internal/**
```

## Özel Tag'ler

Tag'ler controller'da `@Tag(name = ..., description = ...)` ile tanımlanır. Sıra ve açıklamalar merkezi yönetilecekse:

```java
@Bean
OpenApiCustomizer tagOrderCustomizer() {
    return openApi -> openApi.setTags(List.of(
            new io.swagger.v3.oas.models.tags.Tag().name("Sources").description("Knowledge sources of a workspace"),
            new io.swagger.v3.oas.models.tags.Tag().name("Search").description("Keyword, vector and hybrid search"),
            new io.swagger.v3.oas.models.tags.Tag().name("Chunks").description("Chunks and their embeddings")));
}
```

## Özel Extension'lar

```java
@Operation(summary = "Search chunks",
        extensions = @Extension(name = "x-rate-limit", properties = @ExtensionProperty(name = "requestsPerMinute", value = "100")))
```

Extension'lar sözleşmede de varsa eklenir; sözleşmede olmayan bilgi koddan dokümana eklenmez.

## Response Header'larını Belgeleme

```java
@ApiResponse(responseCode = "201", description = "Source created",
        headers = @Header(name = "Location", description = "URL of the created source",
                schema = @Schema(type = "string", example = "/api/workspaces/42/sources/301")))
```
