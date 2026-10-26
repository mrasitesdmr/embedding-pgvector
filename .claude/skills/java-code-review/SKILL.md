---
name: java-code-review
description: Java kodu için sistematik kod incelemesi (code review) - null safety, exception handling, concurrency ve performans kontrolleri. Kullanıcı "kodu incele", "review code", "bu PR'ı kontrol et", "code review" dediğinde ya da değişiklikler merge edilmeden önce kullan.
license: MIT
---

# Java Kod İnceleme Skill'i

> **Proje kuralı:** İsimlendirme, yorum politikası, Lombok yasağı ve test yapısı (given_when_then + AAA, BDDMockito `given/then`) için `backend-conventions` skill'i geçerlidir. Bu dosyadaki örneklerle çelişirse `backend-conventions` kazanır.

Java projeleri için sistematik kod inceleme kontrol listesi.

## Ne Zaman Kullanılır
- Kullanıcı "bu kodu incele" / "bu PR'ı kontrol et" / "code review" dediğinde
- Bir PR merge edilmeden önce
- Bir özellik tamamlandıktan sonra

## İnceleme Stratejisi

1. **Hızlı tarama** - Amacı anla, kapsamı belirle (`git diff`), görev dosyası ve `docs/api/openapi.yaml` ile karşılaştır
2. **Proje kuralları turu** - `backend-conventions` kontrol listesini uygula (isimlendirme, sınıf üstünde Türkçe amaç Javadoc'u ve metot yorumu yok, Lombok yok, given_when_then + AAA, `PageResponse`, `errorCode`'lar)
3. **Kontrol listesi turu** - Aşağıdaki her kategoriyi tek tek gez; transaction için `spring-transactions`, concurrency için `java-concurrency`, hata response'ları için `problem-details-rfc9457`
4. **Özet** - Bulguları severity'ye göre listele (Critical → Required → Optional → Nit → FYI); ölçek `code-review-and-quality` ile aynıdır

## Çıktı Formatı

```markdown
## Kod İncelemesi: [dosya/özellik adı]

### Critical
- [Güvenlik açığı, veri kaybı, bozuk işlevsellik, sözleşme ihlali + `dosya:satır` + öneri]

### Required
- [Muhtemel bug, proje kuralı ihlali, eksik test/validation + `dosya:satır` + öneri]

### Optional
- [Code smell, daha sade alternatif + gerekçe]

### Nit
- [Stil, küçük öneriler]

### FYI
- [Aksiyon gerektirmeyen bilgi]

### Gözlenen İyi Uygulamalar
- [Olumlu geri bildirim - motivasyon için önemli]
```

---

## İnceleme Kontrol Listesi

### 1. Null Safety

**Kontrol et:**
```java
// ❌ NPE riski
String name = user.getName().toUpperCase();

// ✅ Güvenli (erken dönüş)
if (user.getName() == null) {
    return "";
}
return user.getName().toUpperCase();

// ✅ Daha iyi: alanın null olamayacağını tip seviyesinde garanti et
// (package @NullMarked, nullable alan açıkça @Nullable; Bean Validation ile girişte @NotBlank)
```

**İşaretle:**
- Null kontrolü olmadan zincirlenmiş metot çağrıları
- Package'ta eksik `@NullMarked` veya null olabilecek değerde eksik `@Nullable` (JSpecify: `org.jspecify.annotations`; `org.springframework.lang` kullanılmaz)
- `Optional.get()` (yerine `orElseThrow` / `map`); `Optional`'ı parametre veya field olarak kullanmak
- `Optional` ya da boş koleksiyon dönebilecek metotlardan `null` dönmek

**Öner:**
- Olmayabilecek dönüş değerleri için `Optional` kullan
- Constructor/metot parametreleri için `Objects.requireNonNull()` kullan
- Null yerine boş koleksiyon dön: `Collections.emptyList()`

### 2. Exception Handling

**Kontrol et:**
```java
// ❌ Exception'ı yutmak
try {
    process();
} catch (Exception exception) {
}

// ❌ Çok geniş yakalama
catch (Exception exception) { }
catch (Throwable throwable) { }

// ❌ Stack trace'i kaybetmek
catch (IOException exception) {
    throw new RuntimeException(exception.getMessage());
}

// ❌ Hem loglayıp hem fırlatmak (aynı stack trace iki kez loglanır)
catch (IOException exception) {
    log.error("Source import failed fileName={}", fileName, exception);
    throw new SourceImportException(fileName, exception);
}

// ✅ Context ekleyip cause ile zincirle; loglamayı global handler'a bırak
catch (IOException exception) {
    throw new UncheckedIOException("Source import failed: " + fileName, exception);
}
```

**İşaretle:**
- Boş catch blokları
- `Exception` veya `Throwable`'ı geniş kapsamlı yakalamak
- Orijinal exception'ı kaybetmek (zincirlememek)
- Exception'ları akış kontrolü için kullanmak
- Checked exception'ların API sınırlarından dışarı sızması

**Öner:**
- Ya logla ya fırlat; ikisini birden yapma (log tek yerde: `GlobalExceptionHandler`, bkz. `observability-tracing`)
- Spesifik exception tipleri kullan
- Exception'ları `cause` ile zincirle
- Domain hataları için kategori exception'larını (`NotFoundException`, `ConflictException` vb.) `errorCode` ile kullan

### 3. Koleksiyonlar ve Stream'ler

**Kontrol et:**
```java
// ❌ Gezinirken değiştirmek
for (Item item : items) {
    if (item.isExpired()) {
        items.remove(item);  // ConcurrentModificationException
    }
}

// ✅ removeIf kullan
items.removeIf(Item::isExpired);

// ❌ Yan etki için stream
chunks.stream().forEach(chunk -> progressBroadcaster.publish(chunk));

// ✅ Yan etki için döngü (veya doğrudan chunks.forEach(progressBroadcaster::publish))
for (Chunk chunk : chunks) {
    progressBroadcaster.publish(chunk);
}

// ❌ Stream.toList() sonucunu değiştirmek
List<String> names = users.stream()
    .map(User::getName)
    .toList();
names.add("extra");  // UnsupportedOperationException

// ✅ Değiştirilecekse açıkça mutable liste
List<String> names = users.stream()
    .map(User::getName)
    .collect(Collectors.toCollection(ArrayList::new));
```

**İşaretle:**
- Gezinme sırasında koleksiyonu değiştirmek
- Basit işlemler için stream'leri aşırı kullanmak
- `Stream.toList()` (değiştirilemez) ile `Collectors.toList()` (değiştirilebilirliği garanti değil) farkını gözetmemek
- Immutable koleksiyonlar için `List.of()`, `Set.of()`, `Map.of()` kullanmamak
- Etkilerini anlamadan parallel stream kullanmak

**Öner:**
- Defensive copy'ler için `List.copyOf()`
- Iterator ile silmek yerine `removeIf()`
- Dönüşümler için stream, yan etkiler için döngü

### 4. Concurrency

**Kontrol et:**
```java
// ❌ Thread-safe değil
private Map<String, User> cache = new HashMap<>();

// ✅ Thread-safe
private Map<String, User> cache = new ConcurrentHashMap<>();

// ❌ Kontrol-et-sonra-yap (check-then-act) race condition
if (!map.containsKey(key)) {
    map.put(key, computeValue());
}

// ✅ Atomik işlem
map.computeIfAbsent(key, k -> computeValue());

// ❌ Double-checked locking (volatile olmadan bozuk)
if (instance == null) {
    synchronized(this) {
        if (instance == null) {
            instance = new Instance();
        }
    }
}
```

**İşaretle:**
- Senkronizasyonsuz shared mutable state
- Atomik olmayan check-then-act pattern'leri
- Paylaşılan değişkenlerde eksik `volatile`
- Final olmayan nesneler üzerinde `synchronized`
- Thread-safe olmayan lazy initialization

**Öner:**
- Immutable nesneleri tercih et
- `java.util.concurrent` sınıflarını kullan
- Basit durumlar için `AtomicReference`, `AtomicInteger`
- Bellekteki paylaşılan state (ingestion ilerlemesi) için `java-concurrency`'deki `ConcurrentHashMap.compute` kalıbı; embedding istekleri için `Semaphore` ile sınırlı eşzamanlılık

### 5. Java Idiom'ları

**equals/hashCode:**
```java
// ❌ hashCode olmadan sadece equals
@Override
public boolean equals(Object other) { ... }
// hashCode eksik!

// ❌ hashCode içinde mutable alanlar
@Override
public int hashCode() {
    return Objects.hash(id, mutableField);  // HashMap'i bozar
}

// ✅ Immutable alanları kullan, ikisini de uygula
@Override
public boolean equals(Object other) {
    if (this == other) {
        return true;
    }
    if (!(other instanceof User user)) {
        return false;
    }
    return Objects.equals(id, user.id);
}

@Override
public int hashCode() {
    return Objects.hash(id);
}
```

**toString:**
```java
// ❌ Eksik - debug etmesi zor
// toString() yok

// ❌ Hassas veri içeriyor
return "User{password='" + password + "'}";

// ✅ Debug için faydalı
@Override
public String toString() {
    return "User{id=" + id + ", name='" + name + "'}";
}
```

**Çok parametreli nesneler:**
```java
// ✅ İlgili parametreleri bir record'da topla (Lombok @Builder kullanılmaz)
public record SearchSettings(SearchMode mode, int limit, int efSearch) {
}

// ✅ Anlamlı varyantlar için isimli statik factory
Source source = Source.pending(workspace, name, SourceKind.PDF, Instant.now(clock));
```

**İşaretle:**
- `hashCode` olmadan `equals`
- `hashCode` içinde mutable alanlar
- Domain nesnelerinde eksik `toString`
- 3-4'ten fazla parametreli constructor'lar (parametre record'u veya isimli statik factory öner)
- `instanceof` pattern matching kullanmamak (Java 16+)

### 6. Resource Management

**Kontrol et:**
```java
// ❌ Resource leak
FileInputStream inputStream = new FileInputStream(file);
// ... close'dan önce exception fırlatabilir

// ✅ Try-with-resources
try (FileInputStream inputStream = new FileInputStream(file)) {
    // ...
}

// ❌ Birden çok kaynak, yanlış sıra
try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
    // BufferedWriter başarısız olursa FileWriter kapanmayabilir
}

// ✅ Ayrı tanımlamalar
try (FileWriter fileWriter = new FileWriter(file);
     BufferedWriter writer = new BufferedWriter(fileWriter)) {
    // İkisi de düzgün kapanır
}
```

**İşaretle:**
- `Closeable`/`AutoCloseable` için try-with-resources kullanmamak
- Açılıp try-with-resources içinde olmayan kaynaklar
- Düzgün kapatılmayan veritabanı bağlantıları/statement'ları

### 7. API Tasarımı

**Kontrol et:**
```java
// ❌ Boolean parametreler
process(data, true, false);  // Bunlar ne anlama geliyor?

// ✅ Enum kullan (çok parametre varsa parametre record'u; builder sınıfı açılmaz)
process(data, ProcessMode.ASYNC, ErrorHandling.STRICT);

// ❌ "Bulunamadı" için null dönmek
public User findUser(Long id) {
    return users.get(id);  // bulunamazsa null
}

// ✅ Optional dön (find<Noun> → Optional, get<Noun> → yoksa exception; bkz. backend-conventions)
public Optional<User> findUser(Long id) {
    return Optional.ofNullable(users.get(id));
}

// ❌ Null koleksiyon kabul etmek
public void process(List<Item> items) {
    if (items == null) items = Collections.emptyList();
}

// ✅ Null olmamasını şart koş, boşu kabul et
public void process(List<Item> items) {
    Objects.requireNonNull(items, "items must not be null");
}
```

**İşaretle:**
- Boolean parametreler (enum tercih et)
- 3'ten fazla parametreli metotlar (parametre nesnesi düşün)
- Benzer metotlar arasında tutarsız null handling
- Public API girdilerinde eksik validation

### 8. Performans

**Kontrol et:**
```java
// ❌ Döngüde String birleştirme
String result = "";
for (String part : parts) {
    result += part;  // Her turda yeni String oluşturur
}

// ✅ StringBuilder ya da String.join / Collectors.joining
StringBuilder builder = new StringBuilder();
for (String part : parts) {
    builder.append(part);
}

// ❌ Döngüde regex derleme
for (String line : lines) {
    if (line.matches("pattern.*")) { }  // Regex'i her seferinde derler
}

// ✅ Önceden derlenmiş pattern
private static final Pattern PATTERN = Pattern.compile("pattern.*");
for (String line : lines) {
    if (PATTERN.matcher(line).matches()) { }
}

// ❌ Döngüde N+1
for (Source source : sources) {
    List<Chunk> chunks = chunkRepository.findBySourceId(source.getId());
}

// ✅ Tek sorguda getir (JOIN FETCH / @EntityGraph / IN sorgusu, bkz. jpa-patterns)
List<SourceChunkCount> counts = chunkRepository.countBySourceIds(sourceIds);
```

**İşaretle:**
- Döngüde String birleştirme
- Döngüde regex derleme
- N+1 sorgu pattern'leri
- Sıkı döngülerde yeniden kullanılabilecek nesneleri tekrar tekrar oluşturmak
- Primitive stream'leri (`IntStream`, `LongStream`) kullanmamak

### 9. Test İpuçları

**Şunlar için test öner:**
- Görev dosyasındaki her kural ve örnek senaryo
- Null / boş girdiler (validation)
- Sınır değerler (kaynak adı 1/2/80/81 karakter, çalışma alanında 10. ve 11. kaynak, arama `limit` 50/51)
- Exception durumları ve karşılık gelen `errorCode`
- Concurrent erişim (aynı anda kaynak ekleme, aynı kaynağı iki worker'ın içe alması)
- Arama: boş sonuç, eşit skorlu sonuçların sırası, farklı embedding modelinin satırlarının sonuca girmemesi

Testlerin `given_when_then` + AAA + BDDMockito kalıbına uyduğunu da kontrol et (`backend-conventions`).

---

## Severity Rehberi

Ölçek `code-review-and-quality` ile aynıdır (iki skill birlikte kullanıldığında tek rapor üretilir):

| Severity | Kriter |
|--------|--------|
| **Critical** | Güvenlik açığı, veri kaybı riski, production'da çökme, API sözleşmesini (`openapi.yaml`, `errorCode`) bozar |
| **Required** | Muhtemel bug, proje kuralı ihlali, eksik test/validation, ölçülmüş ciddi performans sorunu |
| **Optional** | Code smell, bakım zorluğu, eksik en iyi uygulama |
| **Nit** | Stil, küçük optimizasyon |
| **FYI** | Sadece bilgi |

## Token Optimizasyonu

- Değişen satırlara odaklan (`git diff` kullan)
- Bariz sorunları tekrarlama - benzer bulguları grupla
- Kodun tamamını alıntılamak yerine satır numarası ver
- Otomatik üretilmiş dosyaları ve test fixture'larını atla

## Hızlı Başvuru Kartı

| Kategori | Temel Kontroller |
|----------|------------------|
| Null Safety | Zincirli çağrılar, Optional'ın yanlış kullanımı, null dönüşler |
| Exception'lar | Boş catch, geniş catch, kaybolan stack trace |
| Koleksiyonlar | Gezinirken değiştirme, stream vs döngü |
| Concurrency | Shared mutable state, check-then-act |
| Idiom'lar | equals/hashCode çifti, toString, parametre record'ları |
| Proje kuralları | `backend-conventions` kontrol listesi, `errorCode`'lar, `PageResponse` |
| Resource'lar | try-with-resources, connection leak'ler |
| API | Boolean parametreler, null handling, validation |
| Performans | String birleştirme, döngüde regex, N+1 |
