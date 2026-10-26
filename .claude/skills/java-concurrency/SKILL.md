---
name: java-concurrency
description: Java ve Spring Boot'ta thread safety, ExecutorService/thread pool yönetimi, virtual thread'ler, CompletableFuture, @Async, lock'lar, atomic'ler, ScopedValue, cancellation/interruption ve deadlock önleme için best practice'ler. Race condition düzeltirken, paylaşılan mutable state (ör. bellekte tutulan ingestion ilerlemesi) tasarlarken, embedding API'sine paralel ve sınırlı istek atan içe alma akışı kurarken, @Async veya @Scheduled eklerken, thread pool ya da virtual thread seçerken veya concurrency açısından kod incelerken kullan.
license: Apache-2.0
metadata:
  source: https://github.com/jabrena/plinth/tree/main/skills/125-java-concurrency
  author: Juan Antonio Breña Moral
---

# Java Concurrency

Modern `java.util.concurrent` araçları ve virtual thread'lerle thread safety, ölçeklenebilirlik ve bakım kolaylığı.

## Proje bağlamı

- Java 26, Spring Boot 4.1. Spring bean'leri singleton'dır ve aynı anda birçok request/WebSocket thread'i tarafından çağrılır: bean'ler **stateless** olmalı ya da state'leri thread-safe yapılarda tutulmalı.
- Bellekte tutulan paylaşılan state (kaynak başına ingestion ilerlemesi) için tek bir sahip belirle ve "kontrol et + değiştir" işlemlerini atomik yap. Örnek: ilerleme güncellemesinde "iş iptal edilmiş mi?" kontrolü ile sayaç artışı aynı atomik adımda olmalı.
- İçe alma, embedding API'sine çok sayıda I/O isteği atar: virtual thread uygundur, ama aynı anda giden istek sayısı sağlayıcının rate limit'ine göre `Semaphore` ile sınırlanır (virtual thread sınırsız eşzamanlılık demektir).
- Veritabanı tarafındaki eşzamanlı yazma çakışmaları için `spring-transactions` skill'ine bak (optimistic/pessimistic locking). In-memory lock, birden fazla instance'ta işe yaramaz.

## Varsayılan kararlar

| Durum | Kullan |
|-------|--------|
| I/O ağırlıklı request işleme | `spring.threads.virtual.enabled=true` (Tomcat, `@Async`, `TaskExecutor` virtual thread kullanır) |
| Elle çalıştırılan I/O görevleri | `Executors.newVirtualThreadPerTaskExecutor()` (try-with-resources ile) |
| CPU ağırlıklı iş | Boyutu sınırlı platform thread pool (`ThreadPoolTaskExecutor`, bounded queue) |
| Key bazlı atomik güncelleme | `ConcurrentHashMap.compute` / `computeIfAbsent` / `merge` |
| Sayaç | `AtomicInteger`, yüksek contention'da `LongAdder` |
| Okuma ağırlıklı küçük liste | `CopyOnWriteArrayList` |
| Kritik bölge, timeout'lu kilit | `ReentrantLock.tryLock(timeout)` |
| Thread'ler arası değişmez bağlam | `ScopedValue` (Java 25+ final), `ThreadLocal` yerine |
| Async pipeline | `CompletableFuture` + `orTimeout` / `exceptionally` |

```java
@Repository
public class IngestionJobRepository {

    private final ConcurrentMap<Long, IngestionJob> jobs = new ConcurrentHashMap<>();

    public Optional<IngestionJob> findBySourceId(long sourceId) {
        return Optional.ofNullable(jobs.get(sourceId));
    }

    public boolean saveIfAbsent(IngestionJob job) {
        return jobs.putIfAbsent(job.sourceId(), job) == null;
    }

    public Optional<IngestionJob> updateBySourceId(long sourceId, UnaryOperator<IngestionJob> update) {
        return Optional.ofNullable(jobs.computeIfPresent(sourceId, (key, job) -> update.apply(job)));
    }

    public void remove(long sourceId) {
        jobs.remove(sourceId);
    }
}
```

```java
// IngestionService
IngestionJob job = ingestionJobRepository.updateBySourceId(sourceId, current -> current.withProcessedChunks(processed))
        .orElseThrow(() -> new NotFoundException("SOURCE_NOT_FOUND", "No ingestion running for source: " + sourceId));
```

- `IngestionJob` immutable bir `record`'dur; `withProcessedChunks` sayacın geri gitmemesini ve iptal edilmiş işin güncellenmemesini kontrol eder, ihlalde exception fırlatır. Remapping fonksiyonundan fırlayan exception map'i değiştirmez ve çağırana iletilir.
- Repository sadece atomik güncellemeyi sağlar; iş kuralı `IngestionJob`'da, "iş yoksa" kararı service'tedir (`backend-conventions` bölüm 3 "Veritabanı" tablosu).
- Remapping fonksiyonu kısa ve yan etkisiz olmalı (aynı key için diğer güncellemeleri bloklar); I/O, WebSocket yayını veya başka bir key'i güncellemek fonksiyonun dışında, işlem bittikten sonra yapılır.
- Yeni iş `saveIfAbsent` ile eklenir; `false` dönerse aynı kaynak için iş zaten çalışıyordur ve service `ConflictException("INGESTION_IN_PROGRESS", ...)` fırlatır. İş bitince (`READY`/`FAILED`) kayıt `remove` ile silinir.

Embedding isteklerini sınırlı eşzamanlılıkla göndermek:

```java
private final Semaphore embeddingPermits;

List<float[]> embedBatches(List<List<String>> batches) throws InterruptedException, ExecutionException, TimeoutException {
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
        List<Future<List<float[]>>> futures = batches.stream()
                .map(batch -> executor.submit(() -> {
                    embeddingPermits.acquire();
                    try {
                        return embeddingClient.embed(batch);
                    } finally {
                        embeddingPermits.release();
                    }
                }))
                .toList();
        List<float[]> vectors = new ArrayList<>();
        for (Future<List<float[]>> future : futures) {
            vectors.addAll(future.get(embeddingProperties.requestTimeout().toMillis(), TimeUnit.MILLISECONDS));
        }
        return vectors;
    }
}
```

- İzin sayısı (`new Semaphore(embeddingProperties.maxConcurrentRequests())`) ve timeout `EmbeddingProperties`'ten gelir; sabit kodlanmaz.
- Sonuçlar batch sırasıyla toplanır; parça sırası (`position`) korunur.

## Kurallar

- `new Thread()` ile elle thread açma; executor veya virtual thread kullan.
- Virtual thread'leri pool'lama; görev başına bir tane aç.
- Paylaşılan veriyi immutable tut (`record`, `List.copyOf`); mutable state'i bir sahibin arkasına sakla.
- `InterruptedException`'ı yutma: `Thread.currentThread().interrupt()` ile durumu geri koy ve çık.
- Bekleyen her çağrıya timeout ver (`Future.get(timeout)`, `orTimeout`, `tryLock(timeout)`, `poll(timeout)`).
- Birden fazla lock alınıyorsa global sabit sırayla al.
- Thread pool'lara anlamlı isim ver (`setThreadNamePrefix`) ve sınırlı queue + rejection policy kullan.
- `@Async` metotları aynı sınıf içinden çağrılırsa proxy devreye girmez; ayrı bean'e taşı.
- `parallelStream()` sadece saf CPU işi için; I/O veya transaction içinde kullanma.
- Test'te `Thread.sleep()` ile senkronizasyon yapma; `CountDownLatch`, `Awaitility` veya `BlockingQueue.poll(timeout)` kullan.

## Java sürümüne bağlı notlar

- **Virtual thread pinning**: Java 24+ (JEP 491) ile `synchronized` bloklar artık carrier thread'i pin'lemiyor. Java 26'da `synchronized` kullanmak virtual thread'ler için sorun değil; yine de timeout veya fairness gerekiyorsa `ReentrantLock` tercih et.
- **Structured Concurrency (`StructuredTaskScope`)**: Java 26'da hâlâ **preview**. `--enable-preview` gerektirir; proje bunu açmadıkça kullanma.
- **ScopedValue**: Java 25'te final oldu; kullanılabilir.

## İş akışı

1. Değişiklik öncesi `./mvnw compile` çalıştır; derlenmiyorsa dur.
2. Paylaşılan state'i, thread sınırlarını ve "check-then-act" noktalarını belirle.
3. Uygun pattern'i [references/java-concurrency.md](references/java-concurrency.md)'den seç ve uygula.
4. Eşzamanlı senaryoyu test et (ör. aynı kaynak için iki içe alma isteğinin aynı anda gelmesi; 11 kaynağın aynı anda 10 kaynak limitli çalışma alanına eklenmesi) ve `./mvnw verify` çalıştır.

## Referans

[references/java-concurrency.md](references/java-concurrency.md) (~2000 satır, başında içindekiler tablosu var). Bölümler:

- Örnek 1-2: Thread safety temelleri, thread pool yönetimi
- Örnek 3: Producer-Consumer, Publish-Subscribe
- Örnek 4: `CompletableFuture`
- Örnek 5-6: Immutability, safe publication, performans
- Örnek 7: Concurrent kodu test etme ve debug
- Örnek 8, 13: Virtual thread'ler ve pinning
- Örnek 9: `ScopedValue`
- Örnek 10-11: Cancellation, interruption, backpressure
- Örnek 12, 14-16: ForkJoin, Phaser, CyclicBarrier, Exchanger
- Örnek 17: Structured Concurrency (preview)

Belirli bir konu için: `grep -n "### Örnek" references/java-concurrency.md`
