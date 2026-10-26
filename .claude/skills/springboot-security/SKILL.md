---
name: springboot-security
description: Java Spring Boot servislerinde authn/authz, validation, CSRF, secret'lar, header'lar, rate limiting ve dependency security için Spring Security best practice'leri. Spring Security authn/authz, validation, CSRF, secret, header veya rate limiting incelerken ya da eklerken kullan.
metadata:
  origin: ECC
---

# Spring Boot Security Review

Authentication eklerken, input işlerken, endpoint oluştururken veya secret'larla uğraşırken kullan.

> **Proje durumu:** Projede hesap ve Spring Security yoktur; demo çalışma alanları herkese açıktır. Tek gizli bilgi embedding API anahtarıdır: sadece backend'de, ortam değişkeninden okunur, log'a ve response'a girmez. Bu projede şu an geçerli bölümler: Input Validation, SQL Injection, Secret Yönetimi, CORS (Spring Security olmadan `WebMvcConfigurer`), Rate Limiting, Logging ve Dependency Security. Authentication/Authorization/CSRF bölümleri Spring Security eklendiğinde uygulanır; ayrıntılı config `spring-boot-engineer/references/security.md`'de. İsimlendirme ve kod stili için `backend-conventions` geçerlidir.

## Ne Zaman Devreye Girer

- Authentication eklerken (JWT, OAuth2, session tabanlı)
- Authorization uygularken (@PreAuthorize, rol tabanlı erişim)
- Kullanıcı input'unu doğrularken (Bean Validation, custom validator'lar)
- CORS, CSRF veya security header'larını yapılandırırken
- Secret'ları yönetirken (environment variable'lar, secret manager)
- Rate limiting veya brute-force koruması eklerken
- Dependency'leri CVE'lere karşı tararken

## Authentication

- Stateless JWT'yi ya da revocation list'li opaque token'ları tercih et
- Session'lar için `httpOnly`, `Secure`, `SameSite=Strict` cookie'ler kullan
- Token'ları `OncePerRequestFilter` veya resource server ile doğrula
- Filter'ı `@Component` yapma; `SecurityFilterChain`'e `addFilterBefore(...)` ile ekle (bean olursa servlet container'a da kaydolup iki kez çalışır)

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
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            jwtService.authenticate(header.substring(BEARER_PREFIX.length()))
                    .ifPresent(authentication -> SecurityContextHolder.getContext().setAuthentication(authentication));
        }
        filterChain.doFilter(request, response);
    }
}
```

Tam `SecurityFilterChain` kurulumu: `spring-boot-engineer/references/security.md`.

## Authorization

- Method security'yi etkinleştir: `@EnableMethodSecurity`
- `@PreAuthorize("hasRole('ADMIN')")` veya `@PreAuthorize("@authz.canEditWorkspace(#workspaceId)")` kullan
- Varsayılan olarak reddet (deny by default); sadece gereken scope'ları aç

```java
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserService userService;

    public AdminController(UserService userService) {
        this.userService = userService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/users")
    public PageResponse<UserResponse> listUsers(@ParameterObject Pageable pageable) {
        return PageResponse.from(userService.listUsers(pageable));
    }

    @PreAuthorize("@authz.isOwner(#userId, authentication)")
    @DeleteMapping("/users/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable Long userId) {
        userService.deleteUser(userId);
    }
}
```

## Input Validation

- Controller'larda `@Valid` ile Bean Validation kullan
- Yükleme boyutu iki yerde sınırlanır: `spring.servlet.multipart.max-file-size` (backend, aşan dosya `FILE_TOO_LARGE`) ve nginx `client_max_body_size` (frontend container'ı); ikisi uyumlu tutulur (20 MB dosya, 21m gövde)
- DTO'lara constraint'ler uygula: `@NotBlank`, `@Email`, `@Size`, custom validator'lar
- Kullanıcı metni (arama sorgusu, kaynak adı) ve içe alınan doküman içeriği (parça metni) HTML olarak render edilmez; web'den taranan içerik özellikle güvenilmezdir. frontend metni escape ederek gösterir. HTML kabul etmek gerekirse whitelist ile sanitize et

```java
// KÖTÜ: validation yok, entity dönüyor (SearchController, @RequestMapping("/api/workspaces/{workspaceId}/search"))
@PostMapping
public List<Chunk> searchChunks(@PathVariable long workspaceId, @RequestBody Map<String, Object> body) {
    return searchService.searchChunks(workspaceId, (String) body.get("query"));
}

// İYİ: doğrulanmış request record'u, response DTO
public record SearchChunksRequest(
        @NotBlank(message = "must not be blank") @Size(max = 500, message = "size must be at most 500 characters") @Nullable String query,
        @Pattern(regexp = "(?i)keyword|vector|hybrid", message = "must be one of KEYWORD, VECTOR, HYBRID") @Nullable String mode,
        @Min(value = 1, message = "must be between 1 and 50") @Max(value = 50, message = "must be between 1 and 50") @Nullable Integer limit) {
}

@PostMapping
public SearchResultResponse searchChunks(@PathVariable long workspaceId, @Valid @RequestBody SearchChunksRequest request) {
    return searchService.searchChunks(workspaceId, request);
}

// Dosya yükleme (multipart): DTO yok, constraint'ler @RequestPart parametresinde; dosya türü uzantıya
// ve içeriğe bakılarak service'te belirlenir (FileTypeDetector), istemcinin Content-Type başlığına güvenilmez.
@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ResponseEntity<IngestionJobResponse> createSource(@PathVariable long workspaceId,
        @RequestPart(name = "name", required = false) @NotBlank @Pattern(regexp = NAME_SIZE_PATTERN) @Nullable String name,
        @RequestPart(name = "file", required = false) @NotEmptyFile @Nullable MultipartFile file) {
    IngestionJobResponse job = ingestionService.createSource(workspaceId, Objects.requireNonNull(name), Objects.requireNonNull(file));
    return ResponseEntity.accepted().location(URI.create(job.statusUrl())).body(job);
}
```

## SQL Injection Önleme

- Spring Data repository'lerini veya parameterized query'leri kullan
- Native query'lerde `:param` binding kullan; asla string birleştirme yapma

```java
// KÖTÜ: Native query'de string birleştirme
@Query(value = "SELECT * FROM users WHERE name = '" + name + "'", nativeQuery = true)

// İYİ: Parameterized native query
@Query(value = "SELECT * FROM users WHERE name = :name", nativeQuery = true)
List<User> findByName(@Param("name") String name);

// İYİ: Spring Data derived query (otomatik parameterized)
List<User> findByEmailAndActiveTrue(String email);
```

## Password Encoding

- Password'leri her zaman BCrypt veya Argon2 ile hash'le — asla plaintext saklama
- Manuel hash'leme değil, `PasswordEncoder` bean'i kullan; proje standardı `DelegatingPasswordEncoder`'dır (varsayılan BCrypt, algoritma geçişine izin verir; `spring-boot-engineer/references/security.md`)

```java
@Bean
PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
}

// Service içinde
public UserResponse registerUser(RegisterUserRequest request) {
    String hashedPassword = passwordEncoder.encode(request.password());
    return UserResponse.from(userRepository.save(new User(request.email(), hashedPassword)));
}
```

## CSRF Koruması

- Browser session uygulamalarında CSRF'i açık tut; token'ı form/header'larda gönder
- Bearer token kullanan saf API'lerde CSRF'i kapat ve stateless auth'a güven

```java
http
    .csrf(csrf -> csrf.disable())
    .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
```

## Secret Yönetimi

- Kaynak kodda secret olmaz; environment variable'dan yükle
- `application.properties`'i credential'lardan arındır; placeholder kullan
- Token'ları ve DB credential'larını düzenli olarak rotate et

```properties
# KÖTÜ: application.properties içine gömülü
spring.datasource.password=mySecretPassword123

# İYİ: environment variable placeholder'ı
spring.datasource.password=${DB_PASSWORD}
```

## Security Header'ları

```java
http
    .headers(headers -> headers
        .contentSecurityPolicy(csp -> csp
            .policyDirectives("default-src 'self'"))
        .frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin)
        .referrerPolicy(rp -> rp.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)));
```

## CORS Configuration

- Spring Security varsa CORS'u controller bazında değil, security filter seviyesinde yapılandır (aşağıdaki örnek). Spring Security yoksa (bu projenin şu anki durumu) `WebMvcConfigurer.addCorsMappings` kullanılır (`spring-boot-engineer/references/web.md`); WebSocket endpoint'i için `setAllowedOriginPatterns` ayrıca ayarlanır (`spring-websocket`).
- İzin verilen origin'leri properties'ten oku ve kısıtla — production'da asla `*` kullanma

```java
@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(corsProperties.allowedOrigins());
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    config.setAllowCredentials(true);
    config.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", config);
    return source;
}

// SecurityFilterChain içinde:
http.cors(cors -> cors.configurationSource(corsConfigurationSource()));
```

## Rate Limiting

- Maliyetli endpoint'lere Bucket4j veya gateway seviyesinde limit uygula
- Ani artışları (burst) logla ve alert üret; `Retry-After` ile 429 dön, body `ProblemDetail` (`problem-details-rfc9457`: `type` + `errorCode` her zaman dolu). Filter, controller advice'ın dışında çalıştığı için body'yi kendisi yazar; yeni `errorCode` (`RATE_LIMITED`) frontend'deki `errorMessages.ts`'e de eklenir
- Bucket map'i sınırsız büyümemeli: IP başına bucket'ları süreli bir cache'te (ör. Caffeine `expireAfterAccess`) tut; düz `ConcurrentHashMap` uzun yaşayan süreçte bellek sızıntısıdır
- WebSocket mesaj hızı ayrıca inbound channel interceptor'ında sınırlanır (`spring-websocket`)

```java
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long REQUESTS_PER_MINUTE = 100;

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(10))
            .maximumSize(100_000)
            .build();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Bucket bucket = buckets.get(request.getRemoteAddr(), clientIp -> createBucket());
        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, "60");
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("""
                {"type":"https://embedding-pgvector.example/errors/rate-limited","title":"Too Many Requests","status":429,\
                "detail":"Too many requests","instance":"%s","errorCode":"RATE_LIMITED"}""".formatted(request.getRequestURI()));
    }

    private Bucket createBucket() {
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(REQUESTS_PER_MINUTE).refillIntervally(REQUESTS_PER_MINUTE, Duration.ofMinutes(1)))
                .build();
    }
}
```

Reverse proxy arkasında `getRemoteAddr()` proxy'nin IP'sidir; `server.forward-headers-strategy=native` ile gerçek istemci IP'si alınır (sadece güvenilen proxy arkasında).

## Dependency Security

- CI'da OWASP Dependency Check / Snyk çalıştır
- Spring Boot ve Spring Security'yi desteklenen sürümlerde tut
- Bilinen CVE'lerde build'i fail et

## Logging ve PII

- Secret, token, password veya tam kart numarası (PAN) asla loglama
- Hassas alanları redact et; structured JSON logging kullan (`observability-tracing`)

## File Upload'lar

- Boyutu, content type'ı ve uzantıyı doğrula
- Web root dışında sakla; gerekirse tara

## Release Öncesi Kontrol Listesi

- [ ] Auth token'ları doğru şekilde doğrulanıyor ve expire oluyor
- [ ] Her hassas path'te authorization guard'ı var
- [ ] Tüm input'lar doğrulanıyor ve sanitize ediliyor
- [ ] String birleştirmeyle oluşturulmuş SQL yok
- [ ] CSRF ayarı uygulama tipine uygun
- [ ] Secret'lar dışarıda; hiçbiri commit'lenmemiş
- [ ] Security header'ları yapılandırılmış
- [ ] API'lerde rate limiting var
- [ ] Dependency'ler taranmış ve güncel
- [ ] Log'larda hassas veri yok

**Unutma**: Deny by default, input'ları doğrula, least privilege uygula ve önce secure-by-configuration.
