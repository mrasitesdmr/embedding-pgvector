# API Dokümantasyonu için Security Yapılandırması

> **Proje durumu:** Projede hesap ve Spring Security yoktur; API'de security scheme tanımlanmaz. Embedding API anahtarı backend'in dış servise giden isteğindedir, bizim API'mizin bir parçası değildir ve dokümana girmez. Aşağıdaki bölümler auth eklendiğinde geçerlidir (`spring-boot-engineer/references/security.md`). Security scheme'leri `OpenApiConfig` içindeki `OpenAPI` bean'ine eklenir; ayrı bir `OpenAPI` bean'i daha tanımlanmaz.

## API Key (yönetim uçları eklenirse)

Toplu yeniden embedding gibi maliyetli yönetim uçları eklenirse en basit koruma bir API key'dir:

```java
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Bean
OpenAPI embeddingPgvectorOpenApi() {
    return new OpenAPI()
            .info(new Info().title("Embedding ve pgvector API").version("0.1.0"))
            .components(new Components()
                    .addSecuritySchemes("admin-key", new SecurityScheme()
                            .type(SecurityScheme.Type.APIKEY)
                            .in(SecurityScheme.In.HEADER)
                            .name("X-Admin-Key")
                            .description("Key for administrative operations")));
}
```

Key gerektiren operation'a uygulama:

```java
@Operation(summary = "Re-embed all chunks of a workspace with the configured model",
        security = @SecurityRequirement(name = "admin-key"))
@PostMapping("/api/admin/workspaces/{workspaceId}/reembed")
@ResponseStatus(HttpStatus.ACCEPTED)
public void reembedWorkspace(@PathVariable long workspaceId) {
    ingestionService.reembedWorkspace(workspaceId);
}
```

## JWT Bearer Authentication (auth eklenirse)

```java
new Components().addSecuritySchemes("bearer-jwt", new SecurityScheme()
        .type(SecurityScheme.Type.HTTP)
        .scheme("bearer")
        .bearerFormat("JWT")
        .description("Paste the access token without the 'Bearer' prefix"))
```

Controller'ın tamamına:

```java
@RestController
@RequestMapping("/api/admin/workspaces")
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Workspace administration")
public class WorkspaceAdminController {
}
```

Sınıf seviyesindeki gereksinimi tek bir metotta kaldırmak için boş `@SecurityRequirements` kullanılır (`security = {}` bunu yapmaz):

```java
@SecurityRequirements
@Operation(summary = "Public workspace list")
@GetMapping("/public")
public PageResponse<WorkspaceResponse> listPublicWorkspaces(@ParameterObject Pageable pageable) {
    return PageResponse.from(workspaceService.listWorkspaces(pageable));
}
```

## OAuth2 Authorization Code (auth sunucusu kullanılırsa)

URL'ler hardcode edilmez; `@ConfigurationProperties` record'undan gelir:

```java
@ConfigurationProperties(prefix = "app.openapi.oauth2")
public record OpenApiOAuth2Properties(String authorizationUrl, String tokenUrl) {
}

@Bean
OpenAPI embeddingPgvectorOpenApi(OpenApiOAuth2Properties oauth2Properties) {
    return new OpenAPI()
            .components(new Components()
                    .addSecuritySchemes("oauth2", new SecurityScheme()
                            .type(SecurityScheme.Type.OAUTH2)
                            .flows(new OAuthFlows()
                                    .authorizationCode(new OAuthFlow()
                                            .authorizationUrl(oauth2Properties.authorizationUrl())
                                            .tokenUrl(oauth2Properties.tokenUrl())
                                            .scopes(new Scopes().addString("sources:write", "Manage knowledge sources"))))));
}
```

```properties
app.openapi.oauth2.authorization-url=${OAUTH2_AUTHORIZATION_URL}
app.openapi.oauth2.token-url=${OAUTH2_TOKEN_URL}
```

Model sınıfları `io.swagger.v3.oas.models.security.*` paketindendir.

## Security Scheme Best Practice'leri

1. Description'da token'ın nereden alındığını ve formatını yaz
2. Her OAuth scope'unun neye izin verdiğini açıkla
3. Örneklerde gerçek token veya secret kullanma
4. Spring Security varsa Swagger yollarını `permitAll` yap ([troubleshooting.md](troubleshooting.md))
5. Dokümandaki security gereksinimi gerçek security kuralıyla aynı olmalı; sadece dokümana eklenen gereksinim koruma sağlamaz
