# Güvenlik - Spring Security 7

> **Proje durumu:** Projede hesap ve Spring Security yoktur; demo çalışma alanları herkese açıktır. Tek gizli bilgi embedding API anahtarıdır ve sadece backend'de, ortam değişkeninden okunur. Bu dosya, ileride `spring-boot-starter-security` eklenirse uygulanacak kalıpları içerir. Genel güvenlik kontrol listesi (validation, secret, header, rate limiting): `springboot-security`.

## Security Configuration

```java
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter,
            CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**", "/actuator/health").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(problemDetailAuthenticationEntryPoint())
                        .accessDeniedHandler(problemDetailAccessDeniedHandler()))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    JwtAuthenticationFilter jwtAuthenticationFilter(JwtService jwtService) {
        return new JwtAuthenticationFilter(jwtService);
    }

    @Bean
    FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistration(JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
```

- Bearer token kullanan stateless API'de CSRF kapalıdır; cookie/session tabanlı auth'ta açık tutulur.
- Filter bir `@Bean` ise Boot onu servlet container'a da kaydeder ve iki kez çalışır; bunu `FilterRegistrationBean.setEnabled(false)` ile kapat ya da filter'ı bean yapmadan `new` ile ekle.
- 401/403 response'ları controller advice'a ulaşmaz; `AuthenticationEntryPoint` ve `AccessDeniedHandler` da `ProblemDetail` (`application/problem+json`) yazmalıdır (`problem-details-rfc9457`).
- `PasswordEncoderFactories.createDelegatingPasswordEncoder()` varsayılan olarak BCrypt kullanır ve algoritma geçişine izin verir.

## JWT Authentication Filter

```java
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            jwtService.authenticate(header.substring(BEARER_PREFIX.length()))
                    .ifPresent(authentication -> SecurityContextHolder.getContext().setAuthentication(authentication));
        }
        filterChain.doFilter(request, response);
    }
}
```

Token geçersizse filter exception fırlatmaz, authentication set etmez; erişim kararını `authorizeHttpRequests` verir ve entry point 401 döner.

## JWT Ayarları

JJWT 0.12+ API'si (`io.jsonwebtoken:jjwt-api`; sürümü Spring Boot yönetmez, kendin belirt). Alternatif: kendi token'ını üretmek yerine bir authorization server + `spring-boot-starter-oauth2-resource-server` (aşağıda).

```java
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, Duration accessTokenTtl, Duration refreshTokenTtl) {
}
```

```properties
app.jwt.secret=${JWT_SECRET}
app.jwt.access-token-ttl=15m
app.jwt.refresh-token-ttl=7d
```

- Secret environment variable'dan gelir; en az 256 bit (HS256) rastgele değer.
- `@Value` ile tek tek alan enjekte etme; `@ConfigurationProperties` record'u kullan.
- Token üretimi/doğrulaması için `JwtService` `Clock` enjekte eder (test edilebilir süre kontrolü).

## Method Security

```java
@Service
public class WorkspaceAdminService {

    @PreAuthorize("hasRole('ADMIN')")
    public List<SourceResponse> listAllSources() {
        ...
    }

    @PreAuthorize("hasRole('ADMIN') or @workspaceAccess.isMember(#workspaceId, authentication)")
    public SourceResponse getSource(long workspaceId, long sourceId) {
        ...
    }
}
```

`@PreAuthorize` controller veya service metodunda kullanılabilir; sahiplik gibi iş kuralına bağlı yetki kontrolleri service'te (veya `@authz` gibi ayrı bir bean'de) tutulur, controller'da iş kuralı yazılmaz.

## OAuth2 Resource Server (JWT)

```java
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class ResourceServerConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }
}
```

```properties
spring.security.oauth2.resourceserver.jwt.issuer-uri=${OAUTH2_ISSUER_URI}
```

`JwtDecoder` bean'ini elle yazma; Boot `issuer-uri`'den auto-configure eder.

## Hızlı Başvuru

| Anotasyon | Amaç |
|------------|---------|
| `@EnableWebSecurity` | Spring Security'yi etkinleştirir |
| `@EnableMethodSecurity` | Metot seviyesi güvenlik anotasyonlarını etkinleştirir |
| `@PreAuthorize` | Metot çalışmadan önce authorization'ı kontrol eder |
| `@PostAuthorize` | Metot çalıştıktan sonra authorization'ı kontrol eder |
| `@WithMockUser` | Testlerde mock authenticated user (`spring-boot-starter-security-test`) |
| `@AuthenticationPrincipal` | Mevcut kullanıcıyı controller'a enjekte eder |

## Güvenlik İçin Kurallar

- Production'da her zaman HTTPS
- Secret'lar environment variable'da
- Password'ler `DelegatingPasswordEncoder` (BCrypt) ile
- Access token kısa ömürlü, refresh token mekanizması ve iptal listesi
- Authentication endpoint'lerine rate limiting
- Güvenlik olaylarını logla; token, password ve secret loglama
