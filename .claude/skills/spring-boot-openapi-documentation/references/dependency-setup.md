# SpringDoc Dependency Kurulumu

## Maven Dependency'si

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>3.1.1</version>
</dependency>
```

- `webmvc-ui` starter'ı hem `/v3/api-docs` endpoint'ini hem Swagger UI'ı getirir. Sadece JSON/YAML üretilecekse (UI yok) `springdoc-openapi-starter-webmvc-api`.
- Proje Spring MVC kullanır; WebFlux starter'ı (`springdoc-openapi-starter-webflux-ui`) eklenmez.
- Javadoc'tan dokümantasyon üreten `therapi-runtime-javadoc` eklenmez: projede metot üstüne Javadoc yazılmaz, sınıf Javadoc'u sadece kısa bir Türkçe amaç açıklamasıdır (`backend-conventions`); API açıklamaları `@Operation` / `@Schema` ile verilir.

## Sürüm Seçimi

| Spring Boot | SpringDoc |
|-------------|-----------|
| 4.1.x | 3.1.x (3.1.1, Boot 4.1.0'a göre build edilmiş) |
| 4.0.x | 3.0.x |
| 3.x | 2.x |
| 2.x | 1.x |

- SpringDoc sürümünü Spring Boot BOM'u yönetmez; `pom.xml`'de açıkça yazılır (tercihen `<springdoc.version>` property'si ile).
- Boot minor sürümü yükseltildiğinde SpringDoc'un o sürüme göre build edilmiş sürümüne geç; güncel sürümü [Maven Central](https://central.sonatype.com/artifact/org.springdoc/springdoc-openapi-starter-webmvc-ui)'dan kontrol et.
