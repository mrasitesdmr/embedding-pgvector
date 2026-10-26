# Build Entegrasyonu

Proje Maven kullanır (`./mvnw`). Sözleşmenin tek kaynağı `docs/api/openapi.yaml`'dır; build entegrasyonunun amacı koddan üretilen dokümanın bu sözleşmeyle **uyumunu kontrol etmektir**, sözleşmeyi koddan yeniden üretmek değil.

## Seçenek 1 (önerilen): Test ile Uyum Kontrolü

Uygulamayı ayrıca başlatmaya gerek yoktur; `@SpringBootTest` context'i `/v3/api-docs` endpoint'ini sunar:

```java
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OpenApiContractIntegrationTest {

    @Autowired
    private MockMvcTester mockMvcTester;

    @Test
    void givenRunningApplication_whenGetApiDocs_thenEveryContractPathIsImplemented() throws IOException {
        // Arrange
        Set<String> contractPaths = readContractPaths(Path.of("../docs/api/openapi.yaml"));

        // Act
        MvcTestResult result = mockMvcTester.get().uri("/v3/api-docs").exchange();

        // Assert
        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.paths").convertTo(Map.class)
                .satisfies(paths -> assertThat(paths.keySet()).containsAll(contractPaths));
    }
}
```

- Sınıf adı `backend-conventions` bölüm 5'e uyar: `@SpringBootTest` testleri `*IntegrationTest`.
- `readContractPaths`, YAML sözleşmedeki `paths` anahtarlarını okuyan küçük bir yardımcı metottur (Jackson 3 `tools.jackson.dataformat.yaml.YAMLMapper` ile; test scope'unda `tools.jackson.dataformat:jackson-dataformat-yaml` gerekir, sürümünü Boot'un Jackson BOM'u yönetir).
- Test path'lerin yanında method'ları, status kodlarını ve `errorCode` açıklamalarını da karşılaştıracak şekilde genişletilebilir.
- Bu test qa agent'ının "kod ↔ sözleşme" kontrolünü otomatikleştirir.

## Seçenek 2: Maven Plugin ile Dosya Üretmek

`springdoc-openapi-maven-plugin` çalışan bir uygulamadan dokümanı indirir; bu yüzden `spring-boot-maven-plugin`'in `start`/`stop` goal'leriyle birlikte `integration-test` fazında çalışır:

```xml
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
    <executions>
        <execution>
            <id>pre-integration-test</id>
            <goals>
                <goal>start</goal>
            </goals>
        </execution>
        <execution>
            <id>post-integration-test</id>
            <goals>
                <goal>stop</goal>
            </goals>
        </execution>
    </executions>
</plugin>
<plugin>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-maven-plugin</artifactId>
    <version>1.5</version>
    <executions>
        <execution>
            <id>integration-test</id>
            <goals>
                <goal>generate</goal>
            </goals>
        </execution>
    </executions>
    <configuration>
        <apiDocsUrl>http://localhost:8080/v3/api-docs.yaml</apiDocsUrl>
        <outputFileName>openapi-generated.yaml</outputFileName>
        <outputDir>${project.build.directory}</outputDir>
    </configuration>
</plugin>
```

- Üretilen dosya `target/openapi-generated.yaml`'dır; `docs/api/openapi.yaml`'ın üzerine **yazılmaz**. İki dosya CI'da diff aracıyla karşılaştırılır.
- `start` goal'ü uygulamanın veritabanına ulaşabilmesini gerektirir (Docker Compose veya Testcontainers profili); bu yüzden Seçenek 1 daha az altyapı ister.

## CI Entegrasyonu (GitHub Actions)

```yaml
name: backend

on:
  pull_request:
    branches: [main]

jobs:
  verify:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: backend
    steps:
      - uses: actions/checkout@v7
      - uses: actions/setup-java@v6
        with:
          java-version: '26'
          distribution: 'temurin'
          cache: maven
      - name: Verify (tests, coverage, OpenAPI contract test)
        run: ./mvnw -B verify
```

Uygulamayı arka planda başlatıp `sleep` ile beklemek yerine sözleşme kontrolü test olarak `verify` içinde çalışır. GitHub-hosted Ubuntu runner'larında Docker hazır olduğu için Testcontainers ek ayar istemez.

## Birden Fazla API Grubu

Projede API versiyonlama (`/api/v1`) yoktur (`backend-conventions`). İleride ayrı gruplar (ör. public/admin) gerekirse `GroupedOpenApi` bean'leri tanımlanır ([advanced-configuration.md](advanced-configuration.md)); her grup `/v3/api-docs/<grup>` altında sunulur.
