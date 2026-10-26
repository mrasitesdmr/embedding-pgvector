# SpringDoc Yapılandırması

## Temel Yapılandırma (`application.properties`)

```properties
springdoc.api-docs.path=/v3/api-docs
springdoc.swagger-ui.path=/swagger-ui.html
springdoc.swagger-ui.operations-sorter=method
springdoc.swagger-ui.tags-sorter=alpha
springdoc.swagger-ui.try-it-out-enabled=true
springdoc.paths-to-match=/api/**
springdoc.packages-to-scan=com.embedding.pgvector
springdoc.default-produces-media-type=application/json
```

- `api-docs.path` varsayılan `/v3/api-docs` olarak kalır (sözleşme uyum kontrolü bu yolu kullanır).
- Property isimleri kebab-case yazılır (`operations-sorter`); Boot relaxed binding ile camelCase de çalışır ama proje kebab-case kullanır.
- Projede YAML config kullanılmaz (`backend-conventions`).

## Erişim Endpoint'leri

- **OpenAPI JSON**: `http://localhost:8080/v3/api-docs`
- **OpenAPI YAML**: `http://localhost:8080/v3/api-docs.yaml`
- **Swagger UI**: `http://localhost:8080/swagger-ui/index.html`

## Profile'a Göre Açma/Kapama

```properties
# application-prod.properties
springdoc.api-docs.enabled=false
springdoc.swagger-ui.enabled=false
```

## Sıralama Seçenekleri

- **operations-sorter**: `method` (HTTP method'a göre), `alpha` (alfabetik)
- **tags-sorter**: `alpha`
- **default-models-expand-depth**: UI'da model'lerin ne kadar açık gösterileceği (`-1` modelleri gizler)

## Path Filtreleme

```properties
springdoc.paths-to-match=/api/**
springdoc.paths-to-exclude=/api/internal/**
```

Actuator endpoint'leri varsayılan olarak dokümana girmez (`springdoc.show-actuator=false`).
