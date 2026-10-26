# Concurrency Nesneleri için Java Kuralları

> Kaynak: [jabrena/plinth — 125-java-concurrency](https://github.com/jabrena/plinth/tree/main/skills/125-java-concurrency) (Apache-2.0, yazar: Juan Antonio Breña Moral, sürüm 0.19.0).
>
> **Proje notları (Java 26):** (1) Kaynak, Structured Concurrency için Java 27 preview API'sini (JEP 533) anlatır; bu proje Java 26 kullanır ve `--enable-preview` açık değildir, bu yüzden `StructuredTaskScope` kullanılmaz. (2) Java 24+ (JEP 491) ile `synchronized` artık virtual thread'leri carrier thread'e pin'lemez; aşağıdaki pinning uyarıları (ilke 13, Örnek 13) eski JDK'lar içindir. Timeout veya fairness gerekiyorsa yine de `ReentrantLock` tercih et. (3) Test örnekleri proje formatındadır (given_when_then + AAA + AssertJ); "Kötü örnek" bloklarındaki test isimleri bilerek değiştirilmemiştir. (4) Örneklerdeki `System.out.println` çağrıları sadece bağımsız, çalıştırılabilir demo içindir; proje kodunda SLF4J logger kullanılır (`observability-tracing`). Örneklerdeki `Thread.sleep` iş simülasyonudur; testlerde senkronizasyon için kullanılmaz. (5) Proje kodunda executor'lar Spring bean'i (`ThreadPoolTaskExecutor`, `applicationTaskExecutor`) olarak yönetilir; örneklerdeki elle oluşturulan `ExecutorService`'ler kavramı göstermek içindir.

## Rol

Java yazılım geliştirmede geniş deneyime sahip bir Senior software engineer'sın.

## Amaç

Etkili Java concurrency; thread safety temellerini anlamaya, `java.util.concurrent` araçlarını kullanmaya ve thread pool'ları `ExecutorService` ile yönetmeye dayanır. Temel pratikler arasında Producer-Consumer gibi concurrent design pattern'leri uygulamak, asenkron görevler için `CompletableFuture`'dan yararlanmak ve immutability ile safe publication üzerinden thread safety sağlamak vardır. Lock contention ve memory consistency gibi performans yönleri dikkate alınmalıdır. Stress test ve thread dump analizi dahil kapsamlı test kritik önemdedir. Modern Java, daha iyi ölçeklenebilirlik için virtual thread'leri ve thread-local'lere alternatif olarak thread'ler arası paylaşılan veriyi daha güvenli taşıyan scoped value'ları sunar.

### Bu İlkeleri Uygulamak

Bu rehber şu temel ilkeler üzerine kuruludur:

1.  **Thread Safety Temellerinde Ustalaş**: Veri bütünlüğünü sağlamak ve race condition ile deadlock'ları önlemek için senkronizasyon (lock'lar, condition'lar), atomic işlemler (`java.util.concurrent.atomic`), thread-safe collection'lar (`java.util.concurrent`), immutability ve Java Memory Model gibi temel kavramları doğru uygula.
2.  **Verimli Thread Pool Yönetimi**: Sağlam thread yönetimi için `ExecutorService` kullan. Uygun thread pool implementasyonunu seç ve iş yüküne göre boyut, keep-alive süresi, queue kapasitesi ve rejection policy ile yapılandır. Graceful shutdown prosedürleri uygula.
3.  **Concurrent Design Pattern'lerden Yararlan**: Concurrent uygulamaları decoupling, ölçeklenebilirlik ve bakım kolaylığı sağlayacak şekilde yapılandırmak için Producer-Consumer (`BlockingQueue` ile) ve Publish-Subscribe gibi yerleşik pattern'leri uygula.
4.  **`CompletableFuture` ile Asenkron Programlamayı Benimse**: Asenkron hesaplamaları non-blocking şekilde birleştirmek ve yönetmek için `CompletableFuture` kullan. Bağımlı görevleri zincirle, birden fazla future'ın sonucunu birleştir ve exception'ları düzgün yönet.
5.  **Immutability ve Safe Publication'a Öncelik Ver**: Doğal olarak thread safety sağlamak için sınıfları mümkün olduğunca immutable tasarla. Paylaşılan mutable object'lerin state'inin tüm thread'lere tutarlı görünmesi için safe publish edildiğinden emin ol (ör. `volatile`, static initializer veya doğru senkronizasyon ile).
6.  **Concurrency Maliyetlerini Gözeterek Performansı Optimize Et**: Lock contention (kapsamı küçült, daha ince taneli lock'lar kullan), memory consistency (happens-before'u anla, gerektiğinde `volatile` kullan), context switching maliyeti (thread pool boyutunu dikkatli seç) ve false sharing gibi performans etkilerine dikkat et.
7.  **Kapsamlı Test ve Debug**: Concurrent kodu titizlikle test et. Thread-safe bileşenler için unit test, etkileşimler için integration test ve race condition ya da deadlock'ları ortaya çıkarmak için stress test yaz. Thread dump analizi, doğru loglama ve concurrency test araçlarını kullan.
8.  **Daha İyi Geliştirme için Modern Java Concurrency Özelliklerini Benimse**:
*   **Virtual Thread'ler (Project Loom)**: I/O ağırlıklı görevlerde minimum kaynak maliyetiyle ölçeklenebilirliği ciddi artırmak için `Executors.newVirtualThreadPerTaskExecutor()` ile virtual thread'leri kullan. Virtual thread'leri pool'lama.
*   **Structured Concurrency**: Bir görev, tek bir birim olarak başarılı olması, fail olması, iptal edilmesi ve gözlemlenmesi gereken ilişkili alt görevler fork ediyorsa `StructuredTaskScope` kullan. Java 27'de JEP 533'ten gelen yedinci preview API'dir ve `--enable-preview` gerektirir.
*   **Scoped Value'lar**: Dinamik olarak sınırlı bir kapsam içinde görevler arasında immutable veriyi sağlam ve verimli paylaşmak için, özellikle virtual thread'lerle çalışırken, `ThreadLocal` yerine `ScopedValue` tercih et.
9.  **Kooperatif İptal ve Interruption Disiplini**: Görevleri iptal edilebilir ve interruption'a hızlı tepki verecek şekilde tasarla. `InterruptedException`'ı yutma; ya propagate et ya da `Thread.currentThread().interrupt()` ile interrupt flag'ini geri koy. Süre sınırlı işlemleri tercih et (`orTimeout`, `completeOnTimeout`, blocking çağrılarda timeout), `Future.cancel(true)` kullan, uygun yerlerde `Lock.lockInterruptibly()`/`tryLock(timeout, unit)` tercih et ve iptalde temizliği garanti et.
10. **Backpressure ve Aşırı Yük Koruması**: Bounded queue'lar, uygun rejection policy'ler (ör. kontrollü yük atmak için `CallerRunsPolicy`), concurrency'yi sınırlayan semaphore/bulkhead'ler, request rate limiting ve stream backpressure gerektiğinde `Flow` (Reactive Streams) API'si ile sınırsız iş kuyruklarını ve zincirleme hataları önle.
11. **Deadlock'tan Kaçınma ve Lock Hijyeni**: Global bir lock sırası belirle, lock kapsamını küçült, lock tutarken güvenilmeyen koda çağrı yapma, pratik olduğunda non-blocking algoritmaları veya timeout'lu `tryLock`'u tercih et ve birbiriyle ilgisiz bileşenler arasında iç içe senkronizasyondan kaçın.
12. **ForkJoin ve Parallel Stream'lerin Doğru Kullanımı**: ForkJoin/parallel stream'leri CPU ağırlıklı, kısa süreli, yan etkisiz görevlere ayır. Common pool içinde blocking I/O'dan kaçın; blocking kaçınılmazsa `ForkJoinPool.ManagedBlocker` veya ayrı bir executor kullan. Ölçülüp gerekçelendirilmedikçe request kapsamındaki kod yollarında `parallelStream()`'e güvenme.
13. **Virtual Thread Pinning'den Kaçın**: Virtual thread'lerde, carrier thread'e pin'lenebilecek blocking çağrıların etrafında intrinsic lock (`synchronized`) tutmaktan kaçın. Parking ile uyumlu çalışan `ReentrantLock`'u tercih et, kritik bölgeleri küçük tut ve pinning noktalarını tespit etmek için JFR (VirtualThreadPinned) kullan. (Java 24+ için yukarıdaki proje notuna bak.)
14. **Concurrency için Observability**: Thread'leri tutarlı isimlendir, `UncaughtExceptionHandler` ayarla, metrikleri açığa çıkar (queue derinlikleri, pool boyutları, görev latency'leri, rejection sayıları), bağlamsal veriyi `ThreadLocal` yerine `ScopedValue` ile taşı ve teşhis için thread dump ve JFR ile instrument et.
15. **Timeout'lar, Retry'lar ve Idempotency**: Remote çağrıları her zaman timeout ile sınırla; uygun yerlerde sınırlı ve jitter'lı retry uygula ve retry/iptal durumunda çift yan etki olmaması için işlemlerin idempotent olduğundan emin ol.
16. **Amaca Uygun Concurrency Primitive'leri Kullan**: Yüksek contention'lı sayaçlarda `LongAdder/LongAccumulator`, çoğunlukla okunan observer listelerinde `CopyOnWriteArrayList`, okuma ağırlıklı veride `StampedLock`/`ReadWriteLock` ve koordinasyonu elle `wait/notify`'dan daha net modelleyen yerlerde üst seviye araçları (`Semaphore`, `CountDownLatch`, `Phaser`) tercih et.

## Kısıtlar

Herhangi bir öneriyi uygulamadan önce Maven derlemesini çalıştırarak projenin geçerli durumda olduğundan emin ol. Derleme hatası, ilerlemeyi engelleyen BLOKLAYICI bir durumdur.

- **ZORUNLU**: Herhangi bir değişiklikten önce `./mvnw compile` veya `mvn compile` çalıştır
- **ÖN KOŞUL**: Proje, herhangi bir optimizasyondan önce başarıyla derlenmeli ve temel doğrulamalardan geçmeli
- **KRİTİK GÜVENLİK**: Derleme fail olursa HEMEN DUR ve önerilere DEVAM ETME
- **BLOKLAYICI DURUM**: Devam etmeden önce derleme hataları kullanıcı tarafından çözülmeli
- **İSTİSNA YOK**: Derlenmeyen bir projeye hiçbir koşulda tasarım önerisi uygulanmaz

## Örnekler

### İçindekiler

- Örnek 1: Thread Safety Temelleri
- Örnek 2: Thread Pool Yönetimi
- Örnek 3: Concurrent Design Pattern'ler
- Örnek 4: CompletableFuture ile Asenkron Programlama
- Örnek 5: Thread Safety Kuralları (Immutability ve Safe Publication)
- Örnek 6: Concurrency'de Performans
- Örnek 7: Concurrent Kodu Test Etme ve Debug
- Örnek 8: Ölçeklenebilirlik için Virtual Thread'ler
- Örnek 9: Thread'ler Arası Paylaşılan Veriyi Scoped Value'larla Yönetme
- Örnek 10: Kooperatif İptal ve Interruption
- Örnek 11: Aşırı Yük Koruması ve Backpressure
- Örnek 12: Blocking İşlemler için ForkJoin ve ManagedBlocker
- Örnek 13: Virtual Thread'lerde Pinning'den Kaçınma
- Örnek 14: Phaser ile Aşamalı Çalıştırma
- Örnek 15: CyclicBarrier ile Senkronizasyon
- Örnek 16: Exchanger ile Veri Değişimi
- Örnek 17: StructuredTaskScope ile Structured Concurrency

### Örnek 1: Thread Safety Temelleri

Başlık: Temel Thread Safety Kavramlarını Anla ve Uygula
Açıklama: Thread-safe veri yapıları ve uygun senkronizasyon mekanizmalarıyla çok thread'li ortamlarda veri bütünlüğünü ve doğru davranışı sağla. - Eski synchronized wrapper'lar yerine `java.util.concurrent` collection'larını tercih et. - Eşzamanlı değişiklik risklerini ortadan kaldırmak için immutable object'ler kullan. - Tek bir thread'e ait state için thread-local değişkenler kullan. - Tek değişken üzerinde lock-free işlemler için atomic sınıfları (`java.util.concurrent.atomic`) kullan. - Daha karmaşık senaryolar için `ReentrantLock` veya `ReadWriteLock` ile esnek kilitleme seç. - Elle `wait()/notify()` yerine `java.util.concurrent` araçlarını tercih et.

**İyi örnek:**

```java
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

// Bağlam için örnek sınıflar
class Task {}
class Event {}
class State {
    public State(String initialState) {} // Başlangıç state'i için constructor
}
class UserContext {}
class Item {}

class ThreadSafetyExamples {
    // Tercih edilen concurrent collection'lar
    Map<String, String> concurrentMap = new ConcurrentHashMap<>();
    Queue<Task> taskQueue = new ConcurrentLinkedQueue<>();
    BlockingQueue<Event> eventQueue = new LinkedBlockingQueue<>();

    // Atomic değişkenler
    AtomicInteger counter = new AtomicInteger(0);
    AtomicReference<State> stateRef = new AtomicReference<>(new State("initial"));

    // Thread-local depolama
    private static final ThreadLocal<UserContext> userContext =
        ThreadLocal.withInitial(UserContext::new);

    // ReentrantLock kullanımı
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notFull = lock.newCondition(); // Örnek koşul
    private int itemCount = 0; // Örnek paylaşılan kaynak
    private final int MAX_ITEMS = 10; // Örnek kapasite

    private boolean isFull() {
        return itemCount >= MAX_ITEMS;
    }
     private boolean isEmpty() { // Örnek yardımcı metot
        return itemCount <= 0;
    }


    public void addItem(Item item) throws InterruptedException { // await için throws eklendi
        lock.lock();
        try {
            while (isFull()) {
                System.out.println("Queue is full, waiting to add item...");
                notFull.await(); // Queue doluysa bekle
            }
            // Eleman ekleme mantığı burada
            itemCount++;
            System.out.println("Item added. Count: " + itemCount);
            // Gerekirse diğer koşulları sinyalle (ör. notEmpty.signalAll())
        } finally {
            lock.unlock();
        }
    }

    // ReadWriteLock kullanımı
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Lock readLock = rwLock.readLock();
    private final Lock writeLock = rwLock.writeLock();
    private String sharedData = "Initial Data";

    public String readData() {
        readLock.lock();
        try {
            return sharedData;
        } finally {
            readLock.unlock();
        }
    }

    public void writeData(String data) {
        writeLock.lock();
        try {
            sharedData = data;
            System.out.println("Data written: " + data);
        } finally {
            writeLock.unlock();
        }
    }
}
```

**Kötü örnek:**

```java
// KAÇIN: Kötü thread safety pratikleri
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UnsafeCounter {
    // KÖTÜ: Thread-safe olmayan collection'lar kullanmak
    private Map<String, String> unsafeMap = new HashMap<>();
    private List<String> unsafeList = new ArrayList<>();

    // KÖTÜ: Senkronizasyonsuz düz int kullanmak
    private int counter = 0;
    private String status = "INIT";

    public void incrementCounter() {
        // RACE CONDITION: Birden fazla thread aynı değeri okuyabilir
        counter++; // Atomik değil - güncellemeler kaybolabilir
        unsafeMap.put("lastCount", String.valueOf(counter)); // Map'i bozabilir
    }

    // KÖTÜ: Tutarsız senkronizasyon
    public synchronized void updateCounter(int value) {
        counter = value; // Senkronize
    }

    public int getCounter() {
        return counter; // Senkronize DEĞİL - bayat değer okuyabilir
    }

    // KÖTÜ: Mutable nesne üzerinde senkronizasyon
    private String lockObject = "lock";

    public void badSynchronization() {
        synchronized (lockObject) { // YANLIŞ: string değiştirilebilir
            // Kritik bölge
        }
        lockObject = "newLock"; // Artık senkronizasyon bozuk!
    }
}
```

### Örnek 2: Thread Pool Yönetimi

Başlık: ExecutorService ile Thread Pool'ları Etkili Yönet
Açıklama: Sağlam thread yönetimi için ExecutorService kullan. Uygun thread pool implementasyonlarını seç, doğru yapılandır ve graceful shutdown prosedürleri uygula.

**İyi örnek:**

```java
// İYİ: Doğru thread pool yönetimi
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ThreadPoolManager {
    private final ExecutorService fixedPool;
    private final ScheduledExecutorService scheduler;
    private final ThreadPoolExecutor customPool;

    public ThreadPoolManager() {
        // İsimli thread'lerle sabit thread pool
        this.fixedPool = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors(),
            new CustomThreadFactory("worker")
        );

        // Periyodik görevler için scheduled thread pool
        this.scheduler = Executors.newScheduledThreadPool(
            1,
            new CustomThreadFactory("scheduler")
        );

        // İnce ayarlı özel thread pool
        this.customPool = new ThreadPoolExecutor(
            2,                                    // core pool boyutu
            4,                                    // maksimum pool boyutu
            60L,                                  // keep-alive süresi
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(100),       // sınırlı queue
            new CustomThreadFactory("custom"),
            new ThreadPoolExecutor.CallerRunsPolicy() // rejection policy
        );
    }

    public void submitTask(Runnable task) {
        fixedPool.submit(task);
    }

    public void schedulePeriodicTask(Runnable task, long period) {
        scheduler.scheduleAtFixedRate(task, 0, period, TimeUnit.SECONDS);
    }

    public void shutdown() {
        shutdownExecutorService(fixedPool, "FixedPool");
        shutdownExecutorService(scheduler, "Scheduler");
        shutdownExecutorService(customPool, "CustomPool");
    }

    private void shutdownExecutorService(ExecutorService executor, String name) {
        executor.shutdown(); // Yeni görev kabulünü kapat
        try {
            // Mevcut görevlerin bitmesini bekle
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow(); // Çalışan görevleri iptal et

                // Görevlerin iptale tepki vermesini bekle
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    System.err.println("Pool " + name + " did not terminate");
                }
            }
        } catch (InterruptedException ie) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // Daha iyi thread isimlendirme ve hata yönetimi için özel thread factory
    private static class CustomThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;

        CustomThreadFactory(String namePrefix) {
            this.namePrefix = namePrefix + "-thread-";
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, namePrefix + threadNumber.getAndIncrement());
            t.setDaemon(false);
            t.setUncaughtExceptionHandler((thread, ex) -> {
                System.err.println("Thread " + thread.getName() + " threw exception: " + ex);
            });
            return t;
        }
    }
}
```

**Kötü örnek:**

```java
// KAÇIN: Kötü thread pool yönetimi
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

public class PoorThreadPoolManager {
    // KÖTÜ: Sınırsız thread pool'lar kaynak tükenmesine yol açar
    private ExecutorService cachedPool = Executors.newCachedThreadPool();

    // KÖTÜ: Sınırsız queue'lu tek thread
    private ExecutorService singleThread = Executors.newSingleThreadExecutor();

    public void submitTask(Runnable task) {
        // KÖTÜ: Hata yönetimi ve kaynak yönetimi yok
        cachedPool.submit(task);
    }

    public void submitManyTasks() {
        for (int i = 0; i < 10000; i++) {
            // KÖTÜ: Sistemi boğabilir
            cachedPool.submit(() -> {
                try {
                    Thread.sleep(10000); // Uzun süren görev
                } catch (InterruptedException e) {
                    // KÖTÜ: Interruption'ı yok saymak
                }
            });
        }
    }

    // KÖTÜ: Düzgün shutdown yok
    public void shutdown() {
        cachedPool.shutdown(); // Görevler bitmezse ne olacak?
        singleThread.shutdown();
        // Sonlanma beklenmiyor
        // InterruptedException ele alınmıyor
        // Graceful shutdown başarısız olursa zorla kapatma yok
    }

    // KÖTÜ: Her görev için yeni thread açmak
    public void executeTask(Runnable task) {
        new Thread(task).start(); // Maliyetli ve kontrolsüz
    }

    // KÖTÜ: Thread isimlendirmesi ve hata yönetimi yok
    // Varsayılan thread isimleri açıklayıcı değil
    // Yakalanmayan exception'lar thread'leri sessizce sonlandırır
}
```

### Örnek 3: Concurrent Design Pattern'ler

Başlık: Producer-Consumer ve Publish-Subscribe Uygula
Açıklama: Concurrent uygulamaları decoupling, ölçeklenebilirlik ve bakım kolaylığı sağlayacak şekilde yapılandırmak için Producer-Consumer ve Publish-Subscribe gibi yerleşik pattern'lerden yararlan.

**İyi örnek:**

```java
// İYİ: BlockingQueue ile Producer-Consumer pattern'i
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

// Producer-Consumer implementasyonu
public class ProducerConsumerExample {
    private final BlockingQueue<Task> queue = new LinkedBlockingQueue<>(100);
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private volatile boolean running = true;

    public void startProcessing() {
        // Birden fazla consumer başlat
        for (int i = 0; i < 2; i++) {
            executor.submit(this::consumer);
        }
    }

    public void produce(Task task) throws InterruptedException {
        if (running) {
            queue.put(task); // Queue doluysa bloklar
        }
    }

    private void consumer() {
        while (running || !queue.isEmpty()) {
            try {
                Task task = queue.poll(1, TimeUnit.SECONDS);
                if (task != null) {
                    processTask(task);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void processTask(Task task) {
        System.out.println("Processing: " + task + " on " + Thread.currentThread().getName());
        // İşi simüle et
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void shutdown() {
        running = false;
        executor.shutdown();
    }

    private static class Task {
        private final String data;
        public Task(String data) { this.data = data; }
        @Override public String toString() { return "Task(" + data + ")"; }
    }
}

// Publish-Subscribe implementasyonu
public class EventBus {
    private final ConcurrentHashMap<String, Set<EventListener>> listeners = new ConcurrentHashMap<>();
    private final ExecutorService notificationExecutor = ForkJoinPool.commonPool();

    public void subscribe(String topic, EventListener listener) {
        listeners.computeIfAbsent(topic, k -> ConcurrentHashMap.newKeySet())
                 .add(listener);
    }

    public void unsubscribe(String topic, EventListener listener) {
        Set<EventListener> topicListeners = listeners.get(topic);
        if (topicListeners != null) {
            topicListeners.remove(listener);
        }
    }

    public void publish(String topic, Event event) {
        Set<EventListener> topicListeners = listeners.get(topic);
        if (topicListeners != null && !topicListeners.isEmpty()) {
            // Listener'ları asenkron bilgilendir
            topicListeners.forEach(listener ->
                notificationExecutor.submit(() -> {
                    try {
                        listener.onEvent(event);
                    } catch (Exception e) {
                        System.err.println("Error notifying listener: " + e.getMessage());
                    }
                })
            );
        }
    }

    private static class Event {
        private final String data;
        public Event(String data) { this.data = data; }
        @Override public String toString() { return "Event(" + data + ")"; }
    }

    @FunctionalInterface
    private interface EventListener {
        void onEvent(Event event);
    }
}
```

**Kötü örnek:**

```java
// KAÇIN: Kötü concurrent pattern implementasyonu
import java.util.ArrayList;
import java.util.List;

public class BadProducerConsumer {
    // KÖTÜ: Thread-safe olmayan collection kullanmak
    private List<String> tasks = new ArrayList<>();
    private boolean running = true;

    public void produce(String task) {
        // RACE CONDITION: Birden fazla producer listeyi bozabilir
        synchronized (this) {
            tasks.add(task);
            notify(); // KÖTÜ: notifyAll() kullanılmalı
        }
    }

    public void consume() {
        while (running) {
            String task = null;
            synchronized (this) {
                while (tasks.isEmpty() && running) {
                    try {
                        wait(); // KÖTÜ: Bildirimler kaçabilir
                    } catch (InterruptedException e) {
                        // KÖTÜ: Interruption'ı doğru ele almamak
                        return;
                    }
                }
                if (!tasks.isEmpty()) {
                    task = tasks.remove(0); // KÖTÜ: Baştan verimsiz silme
                }
            }

            if (task != null) {
                // KÖTÜ: synchronized blok içinde işlem yapmak daha da kötü olurdu
                processTask(task);
            }
        }
    }

    // KÖTÜ: Düzgün shutdown mekanizması yok
    public void stop() {
        running = false; // Consumer'lar uyanmayabilir
    }
}

// KÖTÜ: Senkron event işleme
public class BadEventBus {
    private Map<String, List<EventListener>> listeners = new HashMap<>();

    public void subscribe(String topic, EventListener listener) {
        // KÖTÜ: Thread-safe değil
        listeners.computeIfAbsent(topic, k -> new ArrayList<>()).add(listener);
    }

    public void publish(String topic, String event) {
        List<EventListener> topicListeners = listeners.get(topic);
        if (topicListeners != null) {
            // KÖTÜ: Senkron bildirim publisher'ı bloklar
            for (EventListener listener : topicListeners) {
                try {
                    listener.onEvent(event);
                } catch (Exception e) {
                    // KÖTÜ: Fail olan bir listener diğerlerini etkiliyor
                    throw new RuntimeException("Event handling failed", e);
                }
            }
        }
    }
}
```

### Örnek 4: CompletableFuture ile Asenkron Programlama

Başlık: Non-blocking Asenkron İşlemleri Birleştir
Açıklama: Asenkron hesaplamaları non-blocking şekilde birleştirmek ve yönetmek için CompletableFuture kullan. Bağımlı görevleri zincirle, birden fazla future'ın sonucunu birleştir ve exception'ları düzgün yönet.

**İyi örnek:**

```java
// İYİ: Asenkron işlem için CompletableFuture
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class AsyncService {
    private final ExecutorService customExecutor = Executors.newFixedThreadPool(4);

    public CompletableFuture<String> processDataAsync(String input) {
        return CompletableFuture
            .supplyAsync(() -> validateInput(input), customExecutor)
            .thenApplyAsync(this::transformData, customExecutor)
            .thenApply(this::formatResult)
            .exceptionally(this::handleError)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    System.err.println("Processing failed for: " + input);
                } else {
                    System.out.println("Successfully processed: " + input);
                }
            });
    }

    public CompletableFuture<List<String>> processMultipleAsync(List<String> inputs) {
        List<CompletableFuture<String>> futures = inputs.stream()
            .map(this::processDataAsync)
            .collect(Collectors.toList());

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList()))
            .exceptionally(ex -> {
                System.err.println("Batch processing failed: " + ex.getMessage());
                return List.of("ERROR");
            });
    }

    public CompletableFuture<String> combineResults(String input1, String input2) {
        CompletableFuture<String> future1 = processDataAsync(input1);
        CompletableFuture<String> future2 = processDataAsync(input2);

        return future1.thenCombine(future2, (result1, result2) ->
            "Combined: " + result1 + " + " + result2);
    }

    public CompletableFuture<String> getFirstSuccessful(List<String> inputs) {
        CompletableFuture<String>[] futures = inputs.stream()
            .map(this::processDataAsync)
            .toArray(CompletableFuture[]::new);

        return CompletableFuture.anyOf(futures)
            .thenApply(result -> (String) result);
    }

    private String validateInput(String input) {
        if (input == null || input.trim().isEmpty()) {
            throw new IllegalArgumentException("Input cannot be null or empty");
        }
        // Doğrulama işini simüle et
        try { Thread.sleep(100); } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
        return input.trim();
    }

    private String transformData(String input) {
        // Dönüşüm işini simüle et
        try { Thread.sleep(200); } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
        return "transformed_" + input;
    }

    private String formatResult(String input) {
        return "[" + input + "]";
    }

    private String handleError(Throwable throwable) {
        System.err.println("Error occurred: " + throwable.getMessage());
        return "ERROR: " + throwable.getClass().getSimpleName();
    }

    public void shutdown() {
        customExecutor.shutdown();
        try {
            if (!customExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                customExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            customExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
```

**Kötü örnek:**

```java
// KAÇIN: Blocking işlemler ve zayıf hata yönetimi
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BadAsyncService {

    public String processDataBlocking(String input) {
        // KÖTÜ: CompletableFuture kullanıp hemen bloklamak
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            return processInput(input);
        });

        try {
            return future.get(); // BLOCKING! Amacı boşa çıkarır
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public List<String> processMultipleBlocking(List<String> inputs) {
        List<String> results = new ArrayList<>();

        // KÖTÜ: Paralel yerine sıralı işlem
        for (String input : inputs) {
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() ->
                processInput(input));

            try {
                results.add(future.get()); // Döngüde BLOCKING
            } catch (Exception e) {
                // KÖTÜ: Tek hata her şeyi durduruyor
                throw new RuntimeException("Processing failed", e);
            }
        }

        return results;
    }

    public CompletableFuture<String> badErrorHandling(String input) {
        return CompletableFuture.supplyAsync(() -> {
            if ("fail".equals(input)) {
                throw new RuntimeException("Simulated failure");
            }
            return processInput(input);
        });
        // KÖTÜ: Hata yönetimi yok - exception'lar yayılır
    }

    public void badChaining(String input) {
        // KÖTÜ: Doğru zincirlememek, iç içe future üretmek
        CompletableFuture<CompletableFuture<String>> nestedFuture =
            CompletableFuture.supplyAsync(() -> {
                return CompletableFuture.supplyAsync(() -> {
                    return processInput(input);
                });
            });

        // Artık iç içe bir CompletableFuture var - üzerinde çalışmak zor
    }

    // KÖTÜ: Kaynak sızıntısı - executor kapatılmıyor
    private final ExecutorService executor = Executors.newFixedThreadPool(10);

    public CompletableFuture<String> processWithLeakedExecutor(String input) {
        return CompletableFuture.supplyAsync(() -> processInput(input), executor);
        // KÖTÜ: Executor hiç kapatılmıyor
    }

    private String processInput(String input) {
        try {
            Thread.sleep(1000); // İşi simüle et
        } catch (InterruptedException e) {
            // KÖTÜ: Interruption'ı doğru ele almamak
        }
        return "processed_" + input;
    }
}
```

### Örnek 5: Thread Safety Kuralları (Immutability ve Safe Publication)

Başlık: Immutability ve Safe Publication ile Thread Safety Sağla
Açıklama: Sınıfları immutable tasarlayarak ve paylaşılan object'lerin safe publish edilmesini sağlayarak concurrency risklerini en aza indir. - **Immutability**: - Mümkün olduğunda field'ları `final` yap. - Tüm field'ların constructor'da initialize edildiğinden emin ol. - Mutable state için setter sağlama. - Constructor'a verilen veya getter'dan dönen mutable bileşenler (`List`, `Date` gibi) için defensive copy yap ya da unmodifiable collection kullan. - Karmaşık immutable object'ler için builder pattern'i değerlendir. - **Safe Publication**: - Paylaşılan object'lerin diğer thread'lere doğru publish edildiğinden emin ol (ör. static initializer'da initialize ederek, `volatile` field'larda saklayarak veya doğru senkronizasyonla). - `java.util.concurrent` collection'ları ve atomic'ler kendi state'leri için safe publication'ı içeride hallederler.

**İyi örnek:**

```java
// İYİ: Güvenli publish edilen immutable sınıf
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Immutable sınıf
public final class ImmutableValue {
    private final int value;
    private final List<String> items;

    public ImmutableValue(int value, List<String> items) {
        this.value = value;
        // Immutability için defensive copy
        this.items = List.copyOf(items);
    }

    public int getValue() {
        return value;
    }

    public List<String> getItems() {
        return items; // Zaten immutable
    }
}

// Güvenli publication örneği
class SafePublicationExample {
    private static final Map<String, String> cache = new ConcurrentHashMap<>();

    public static String getData(String key) {
        return cache.computeIfAbsent(key, k -> {
            // ConcurrentHashMap üzerinden güvenli publication
            return "Data for " + k;
        });
    }
}
```

**Kötü örnek:**

```java
// KAÇIN: Güvensiz publish edilen mutable sınıf
import java.util.ArrayList;
import java.util.List;

public class MutableValue {
    private int value;
    private List<String> items; // Mutable field

    public MutableValue(int value, List<String> items) {
        this.value = value;
        this.items = items; // Doğrudan referans - güvenli değil
    }

    public List<String> getItems() {
        return items; // Mutable referans döner
    }

    // KÖTÜ: Güvensiz publication
    public static MutableValue instance;

    public static void initialize() {
        // Güvensiz publication - diğer thread'ler yarım oluşturulmuş nesneyi görebilir
        instance = new MutableValue(42, new ArrayList<>());
    }
}
```

### Örnek 6: Concurrency'de Performans

Başlık: Concurrent Kodu Performans için Optimize Et
Açıklama: Concurrent uygulamalarda performans etkilerine dikkat et. - **Lock Contention**: Lock kapsamlarını daraltarak, daha ince taneli lock'lar (lock striping) kullanarak veya optimistic locking ya da lock-free veri yapılarını değerlendirerek contention'ı azalt. - **Memory Consistency**: Java Memory Model'i (JMM) anla. Tek değişkenlerin thread'ler arası görünürlüğü için `volatile` kullan. Senkronizasyonun kurduğu happens-before ilişkilerinin farkında ol. - **Context Switching**: Aşırı thread sayısı yüksek context switching maliyetine yol açar. Thread pool'ları uygun boyutlandır. - **False Sharing**: Farklı thread'lerin eriştiği mutable field'lar aynı cache line'da bulunduğunda false sharing'e dikkat et.

**İyi örnek:**

```java
// İYİ: Performans için optimize edilmiş concurrent kod
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

// Daha iyi performans için lock striping
class StripedMap<K, V> {
    private static final int STRIPE_COUNT = 16;
    private final List<Lock> stripes;
    private final List<Map<K, V>> buckets;

    public StripedMap() {
        stripes = new ArrayList<>(STRIPE_COUNT);
        buckets = new ArrayList<>(STRIPE_COUNT);
        for (int i = 0; i < STRIPE_COUNT; i++) {
            stripes.add(new ReentrantLock());
            buckets.add(new HashMap<>());
        }
    }

    private int getStripeIndex(K key) {
        return Math.abs(key.hashCode() % STRIPE_COUNT);
    }

    public V get(K key) {
        int index = getStripeIndex(key);
        Lock lock = stripes.get(index);
        lock.lock();
        try {
            return buckets.get(index).get(key);
        } finally {
            lock.unlock();
        }
    }

    public V put(K key, V value) {
        int index = getStripeIndex(key);
        Lock lock = stripes.get(index);
        lock.lock();
        try {
            return buckets.get(index).put(key, value);
        } finally {
            lock.unlock();
        }
    }
}

// volatile ile memory consistency
class MemoryConsistencyExample {
    private volatile boolean flag = false;
    private int data = 0;

    public void writer() {
        data = 42;
        flag = true; // volatile yazma happens-before ilişkisi kurar
    }

    public void reader() {
        while (!flag) {
            // Spin wait (meşgul bekleme)
        }
        // data = 42'yi görmesi garanti
        System.out.println("Data: " + data);
    }
}
```

**Kötü örnek:**

```java
// KAÇIN: Performansı düşüren concurrent kod
import java.util.HashMap;
import java.util.Map;

public class PoorPerformance {
    private final Map<String, String> map = new HashMap<>();

    // KÖTÜ: Kaba taneli (coarse-grained) lock
    public synchronized String get(String key) {
        return map.get(key);
    }

    public synchronized void put(String key, String value) {
        map.put(key, value);
    }

    // KÖTÜ: Senkronizasyonsuz, volatile olmayan field
    private boolean flag = false;
    private int data = 0;

    public void writer() {
        data = 42;
        flag = true; // Diğer thread'ler bu değişikliği görmeyebilir
    }

    public void reader() {
        int attempts = 0;
        while (!flag && attempts++ < 1_000) {
            Thread.onSpinWait();
        }
        if (!flag) {
            throw new IllegalStateException("Update was not visible before the bounded wait ended");
        }
        // Bayat veri görebilir
        System.out.println("Data: " + data);
    }
}
```

### Örnek 7: Concurrent Kodu Test Etme ve Debug

Başlık: Concurrent Uygulamaları Titizlikle Test Et ve Debug Et
Açıklama: Race condition, deadlock ve diğer concurrency sorunlarını bulmak için concurrent kodu uygun araç ve tekniklerle kapsamlı test et. - Thread-safe bileşenler için doğru senkronizasyon testi içeren unit testler yaz. - Yük altında race condition ve deadlock'ları ortaya çıkarmak için stress test kullan. - Bileşenler arası concurrent etkileşimler için integration testler yaz. - Debug için thread dump analizi ve profiling araçlarını kullan. - Concurrent ortamlar için doğru loglama stratejileri uygula. - Parallel execution yeteneği olan JUnit 5 gibi test framework'lerini kullan.

**İyi örnek:**

```java
// İYİ: Concurrent kodun doğru test edilmesi
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ConcurrentTestExample {

    @RepeatedTest(100) // Race condition'ları yakalamak için birçok kez çalıştır
    void givenTenThreads_whenIncrementConcurrently_thenCountsEveryIncrement() throws InterruptedException {
        // Arrange
        ThreadSafeCounter counter = new ThreadSafeCounter();
        int numberOfThreads = 10;
        int incrementsPerThread = 1000;
        CountDownLatch latch = new CountDownLatch(numberOfThreads);

        // Act
        try (ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads)) {
            for (int threadIndex = 0; threadIndex < numberOfThreads; threadIndex++) {
                executor.submit(() -> {
                    try {
                        for (int increment = 0; increment < incrementsPerThread; increment++) {
                            counter.increment();
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        }

        // Assert
        assertThat(counter.getValue()).isEqualTo(numberOfThreads * incrementsPerThread);
    }

    @Test
    void givenBoundedQueue_whenProducerAndConsumerRun_thenAllItemsAreConsumed() throws InterruptedException {
        // Arrange
        BlockingQueue<String> queue = new LinkedBlockingQueue<>(10);
        List<String> consumed = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch latch = new CountDownLatch(2);

        // Act
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            executor.submit(() -> {
                try {
                    for (int index = 0; index < 5; index++) {
                        queue.put("item-" + index);
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
            executor.submit(() -> {
                try {
                    for (int index = 0; index < 5; index++) {
                        consumed.add(queue.poll(5, TimeUnit.SECONDS));
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        }

        // Assert
        assertThat(consumed).containsExactly("item-0", "item-1", "item-2", "item-3", "item-4");
    }

    private static class ThreadSafeCounter {
        private final AtomicInteger count = new AtomicInteger(0);

        public void increment() {
            count.incrementAndGet();
        }

        public int getValue() {
            return count.get();
        }
    }
}
```

**Kötü örnek:**

```java
// KAÇIN: Concurrent kodun yetersiz test edilmesi
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PoorConcurrentTesting {

    @Test
    void testCounter() {
        // KÖTÜ: Sadece tek thread'li senaryoyu test etmek
        UnsafeCounter counter = new UnsafeCounter();
        counter.increment();
        assertThat(counter.getValue()).isEqualTo(1);

        // Bu test geçer ama thread safety'yi doğrulamaz
    }

    @Test
    void testConcurrentAccess() throws InterruptedException {
        UnsafeCounter counter = new UnsafeCounter();

        // KÖTÜ: Thread açıp bitmesini beklememek
        new Thread(() -> {
            for (int i = 0; i < 1000; i++) {
                counter.increment();
            }
        }).start();

        new Thread(() -> {
            for (int i = 0; i < 1000; i++) {
                counter.increment();
            }
        }).start();

        // KÖTÜ: Thread'lerin bitmesini beklememek
        Thread.sleep(100); // Güvenilmez zamanlama

        // Bu assertion rastgele geçebilir veya fail olabilir
        // assertThat(counter.getValue()).isEqualTo(2000);
    }

    private static class UnsafeCounter {
        private int count = 0;

        public void increment() {
            count++; // Thread-safe değil
        }

        public int getValue() {
            return count;
        }
    }
}
```

### Örnek 8: Ölçeklenebilirlik için Virtual Thread'ler

Başlık: I/O Ağırlıklı Görevlerde Virtual Thread Kullan
Açıklama: I/O ağırlıklı görevlerde minimum kaynak maliyetiyle ölçeklenebilirliği ciddi artırmak için virtual thread'lerden (Project Loom) yararlan. Virtual thread'leri pool'lama.

**İyi örnek:**

```java
// İYİ: Ölçeklenebilir I/O işlemleri için virtual thread'ler
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScopedValue;
import java.util.stream.IntStream;

public class VirtualThreadExample {

    // I/O ağırlıklı görevler için virtual thread executor kullan
    private final ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public void handleManyIORequests() {
        List<Future<String>> futures = IntStream.range(0, 10000)
            .mapToObj(i -> virtualExecutor.submit(() -> performIOOperation("task-" + i)))
            .toList();

        // Sonuçları topla
        futures.forEach(future -> {
            try {
                String result = future.get();
                System.out.println("Completed: " + result);
            } catch (Exception e) {
                System.err.println("Task failed: " + e.getMessage());
            }
        });
    }

    // Virtual thread'ler blocking I/O işlemleri için idealdir
    private String performIOOperation(String taskId) {
        try {
            // I/O işlemini simüle et (veritabanı çağrısı, web isteği vb.)
            Thread.sleep(1000); // Bu bir platform thread'i bloklardı
            return "Result for " + taskId;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Interrupted: " + taskId;
        }
    }

    // Virtual thread'lerle scoped value kullanımı (Java 25+ final)
    private static final ScopedValue<String> USER_ID = ScopedValue.newInstance();

    public void processWithScopedValue(String userId, List<String> tasks) {
        // Scoped value ile çalıştır
        ScopedValue.where(USER_ID, userId)
            .run(() -> {
                tasks.parallelStream().forEach(task -> {
                    virtualExecutor.submit(() -> {
                        // Scoped value'ya güvenli erişim
                        String currentUserId = USER_ID.get();
                        performTaskForUser(currentUserId, task);
                    });
                });
            });
    }

    private void performTaskForUser(String userId, String task) {
        System.out.println("Processing task " + task + " for user " + userId +
                          " on thread " + Thread.currentThread());
    }

    public void shutdown() {
        virtualExecutor.shutdown();
    }
}
```

**Kötü örnek:**

```java
// KAÇIN: Virtual thread'leri yanlış kullanmak
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocal;

public class BadVirtualThreadUsage {

    // KÖTÜ: Virtual thread'ler için thread pool kurmak
    private final ExecutorService virtualPool = Executors.newFixedThreadPool(100,
        Thread.ofVirtual().factory()); // Virtual thread'leri pool'lama!

    // KÖTÜ: CPU ağırlıklı görevler için virtual thread kullanmak
    public void performCPUIntensiveWork() {
        ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();

        for (int i = 0; i < 1000; i++) {
            virtualExecutor.submit(() -> {
                // KÖTÜ: Virtual thread'ler CPU ağırlıklı iş için uygun değildir
                double result = 0;
                for (int j = 0; j < 1_000_000; j++) {
                    result += Math.sqrt(j) * Math.sin(j);
                }
                return result;
            });
        }
    }

    // KÖTÜ: Virtual thread'lerle platform thread pattern'leri kullanmak
    public void badResourceManagement() {
        // Executor yerine virtual thread'leri elle açmak
        for (int i = 0; i < 10000; i++) {
            Thread.ofVirtual().start(() -> {
                performIOOperation();
                // KÖTÜ: Düzgün temizlik ve hata yönetimi yok
            });
        }
    }

    // KÖTÜ: Virtual thread'lerle kullanılmaması gereken blocking işlemler
    public void problematicBlocking() {
        ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();

        virtualExecutor.submit(() -> {
            try {
                synchronized (this) { // KÖTÜ: synchronized bloklar virtual thread'leri pin'leyebilir (Java 24 öncesi)
                    Thread.sleep(1000); // Bu, virtual thread'i platform thread'e pin'ler (Java 24 öncesi)
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    // KÖTÜ: ScopedValue yerine ThreadLocal kullanmak
    private static final ThreadLocal<String> USER_CONTEXT = new ThreadLocal<>();

    public void badContextPropagation() {
        USER_CONTEXT.set("user123");

        ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
        virtualExecutor.submit(() -> {
            // KÖTÜ: ThreadLocal değerleri virtual thread'lere düzgün aktarılmaz
            String userId = USER_CONTEXT.get(); // Muhtemelen null
            performTaskForUser(userId);
        });
    }

    private void performIOOperation() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void performTaskForUser(String userId) {
        System.out.println("Processing for user: " + userId);
    }
}
```

### Örnek 9: Thread'ler Arası Paylaşılan Veriyi Scoped Value'larla Yönetme

Başlık: Thread-Safe Context Propagation için Scoped Value Kullan
Açıklama: Dinamik olarak sınırlı bir kapsam içinde görevler arasında immutable veriyi sağlam ve verimli paylaşmak için, özellikle virtual thread'lerle, `ThreadLocal` yerine `ScopedValue` tercih et. Scoped Value'lar Java 25'te stabil oldu (JEP 506). - Scoped value instance'ı oluşturmak için `ScopedValue.newInstance()` kullan. - Otomatik temizlikli scoped binding'ler kurmak için `ScopedValue.where()` kullan. - Karmaşık context'ler için birden fazla scoped value'yu method chaining ile birleştir. - Çağrı zinciri boyunca akması gereken immutable context verisi için scoped value'ları tercih et. - Olası bellek sorunları ve yapısal temizlik eksikliği nedeniyle virtual thread'lerle `ThreadLocal`'den kaçın. - Web uygulamalarında request kapsamlı veri ve distributed tracing için scoped value'ları kullan.

**İyi örnek:**

```java
// İYİ: Thread-safe context taşıma için scoped value'lar
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class ScopedValueExample {

    // Farklı context türleri için scoped value'lar tanımla
    private static final ScopedValue<String> USER_ID = ScopedValue.newInstance();
    private static final ScopedValue<String> REQUEST_ID = ScopedValue.newInstance();
    private static final ScopedValue<Map<String, String>> SECURITY_CONTEXT = ScopedValue.newInstance();

    private final ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();

    // Scoped context ile web isteği işleme
    public String handleWebRequest(String userId, String requestId, Map<String, String> securityContext) {
        return ScopedValue.where(USER_ID, userId)
            .where(REQUEST_ID, requestId)
            .where(SECURITY_CONTEXT, securityContext)
            .call(() -> processRequestWithContext());
    }

    private String processRequestWithContext() {
        String userId = USER_ID.get();
        String requestId = REQUEST_ID.get();

        System.out.println("Processing request " + requestId + " for user " + userId);

        // Context'i koruyarak paralel işle (scoped value'lar virtual thread'lere taşınır)
        return ScopedValue.where(USER_ID, userId)
            .where(REQUEST_ID, requestId)
            .where(SECURITY_CONTEXT, SECURITY_CONTEXT.get())
            .call(() -> {
                try {
                    CompletableFuture<String> businessLogic = CompletableFuture.supplyAsync(this::executeBusinessLogic, virtualExecutor);
                    CompletableFuture<String> auditLog = CompletableFuture.supplyAsync(this::createAuditLog, virtualExecutor);
                    CompletableFuture<String> notification = CompletableFuture.supplyAsync(this::sendNotification, virtualExecutor);

                    return CompletableFuture.allOf(businessLogic, auditLog, notification)
                        .thenApply(v -> combineResults(businessLogic.join(), auditLog.join(), notification.join()))
                        .join();
                } catch (Exception e) {
                    throw new RuntimeException("Request processing failed", e);
                }
            });
    }

    private String executeBusinessLogic() {
        String userId = USER_ID.get();
        String requestId = REQUEST_ID.get();

        // İş mantığını simüle et
        try { Thread.sleep(100); } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return "Business logic completed for user " + userId + " (request: " + requestId + ")";
    }

    private String createAuditLog() {
        String userId = USER_ID.get();
        String requestId = REQUEST_ID.get();
        Map<String, String> securityContext = SECURITY_CONTEXT.get();

        String securityInfo = securityContext.getOrDefault("role", "unknown");
        return "Audit logged for user " + userId + " with role " + securityInfo + " (request: " + requestId + ")";
    }

    private String sendNotification() {
        String userId = USER_ID.get();
        String requestId = REQUEST_ID.get();

        return "Notification sent to user " + userId + " (request: " + requestId + ")";
    }

    // İç içe scoped value kullanımı
    public void processWithNestedScope(String userId, List<String> tasks) {
        ScopedValue.where(USER_ID, userId)
            .run(() -> {
                for (String task : tasks) {
                    ScopedValue.where(REQUEST_ID, "task-" + task)
                        .run(() -> processTask(task));
                }
            });
    }

    private void processTask(String task) {
        String userId = USER_ID.get();
        String requestId = REQUEST_ID.get();

        System.out.println("Processing task " + task + " for user " + userId + " (request: " + requestId + ")");
    }

    private String combineResults(String businessResult, String auditResult, String notificationResult) {
        return String.format("Results: [%s] [%s] [%s]", businessResult, auditResult, notificationResult);
    }

    public void shutdown() {
        virtualExecutor.shutdown();
    }
}
```

**Kötü örnek:**

```java
// KAÇIN: Virtual thread'lerle ThreadLocal
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Map;
import java.util.HashMap;

class BadScopedValueUsage {

    // KÖTÜ: Virtual thread'lerle ThreadLocal kullanmak
    private static final ThreadLocal<String> USER_CONTEXT = new ThreadLocal<>();

    public void processWithThreadLocal(String userId) {
        USER_CONTEXT.set(userId);

        ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();

        virtualExecutor.submit(() -> {
            // KÖTÜ: ThreadLocal değerleri virtual thread'lere düzgün aktarılmaz
            String currentUserId = USER_CONTEXT.get(); // Muhtemelen null
            System.out.println("Processing for user: " + currentUserId);
        });

        // KÖTÜ: ThreadLocal'i temizlememek
        // USER_CONTEXT.remove(); // Eksik temizlik
    }

    // KÖTÜ: Context'i elle taşımak
    public void processWithManualPropagation(String userId, String requestId) {
        ExecutorService executor = Executors.newFixedThreadPool(10);

        // KÖTÜ: Context'i her göreve elle geçirmek
        executor.submit(() -> {
            processTask(userId, requestId, "task1");
        });

        executor.submit(() -> {
            processTask(userId, requestId, "task2");
        });

        // KÖTÜ: Hataya açık ve uzun
    }

    private void processTask(String userId, String requestId, String task) {
        System.out.println("Processing " + task + " for user " + userId + " (request: " + requestId + ")");
    }

    // KÖTÜ: ThreadLocal bellek sızıntısı
    private static final ThreadLocal<Map<String, String>> CONTEXT =
        ThreadLocal.withInitial(HashMap::new);

    public void leakyContextManagement() {
        CONTEXT.get().put("userId", "123");
        CONTEXT.get().put("requestId", "456");

        // Bir şey işle
        processData();

        // KÖTÜ: Temizlemeyi unutmak bellek sızıntısına yol açar
        // CONTEXT.remove(); // Eksik temizlik
    }

    private void processData() {
        System.out.println("Processing data...");
    }
}
```

### Örnek 10: Kooperatif İptal ve Interruption

Başlık: Interruption'ı propagate et, beklemeleri sınırla, güvenle temizle
Açıklama: Görevleri iptal edilebilir ve interruption'a duyarlı tasarla. Blocking çağrılarda timeout kullan, `InterruptedException`'ı yutma ve iptalden sonra kaynakların temizlendiğinden emin ol.

**İyi örnek:**

```java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

class CancellationService {
    private final BlockingQueue<String> queue = new ArrayBlockingQueue<>(100);
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final ReentrantLock lock = new ReentrantLock();

    public Future<?> startWorker() {
        return executor.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    String item = queue.poll(500, TimeUnit.MILLISECONDS); // süreli bekleme
                    if (item != null) process(item);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt(); // interrupt flag'ini geri koy ve döngüden çık
                }
            }
            cleanup();
        });
    }

    public boolean tryUpdateStateSafely() throws InterruptedException {
        if (lock.tryLock(200, TimeUnit.MILLISECONDS)) { // süre sınırlı edinme
            try {
                // state'i güncelle
                return true;
            } finally {
                lock.unlock();
            }
        }
        return false;
    }

    private void process(String s) { /* ... */ }
    private void cleanup() { /* close resources */ }

    public void shutdown() {
        executor.shutdownNow(); // worker'ları interrupt et
    }
}
```

**Kötü örnek:**

```java
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

class BadCancellationService {
    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>(); // sınırsız
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public void startWorker() {
        executor.submit(() -> {
            for (;;) {
                try {
                    String item = queue.take(); // süresiz bloklama
                    process(item);
                } catch (InterruptedException ignored) { // YUTULDU
                    // sonsuza kadar çalışmaya devam eder
                }
            }
        });
    }

    private void process(String s) { /* ... */ }
}
```

### Örnek 11: Aşırı Yük Koruması ve Backpressure

Başlık: Queue'ları sınırla, makul şekilde reddet ve concurrency'yi kısıtla
Açıklama: Bounded queue'lar, uygun rejection policy'ler ve kıt kaynaklar için bulkhead'ler (semaphore) ile sınırsız büyümeyi ve zincirleme hataları önle.

**İyi örnek:**

```java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

class BackpressureExample {
    private final Semaphore dbBulkhead = new Semaphore(20); // eşzamanlı DB çağrılarını sınırla
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
        4, 8, 60, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(200), // sınırlı
        new ThreadPoolExecutor.CallerRunsPolicy() // kontrollü yük atma
    );

    public Future<String> handleRequest(Callable<String> task) {
        return executor.submit(() -> {
            if (!dbBulkhead.tryAcquire(500, TimeUnit.MILLISECONDS)) {
                throw new RejectedExecutionException("Overloaded");
            }
            try {
                return task.call();
            } finally {
                dbBulkhead.release();
            }
        });
    }
}
```

**Kötü örnek:**

```java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class BadBackpressureExample {
    // Sabit pool arkasındaki sınırsız queue sınırsız iş biriktirebilir
    private final ExecutorService pool = Executors.newFixedThreadPool(8); // LinkedBlockingQueue (sınırsız)

    public void flood(Runnable task) {
        for (int i = 0; i < 1_000_000; i++) {
            pool.submit(task); // sınır yok, rejection yok
        }
    }
}
```

### Örnek 12: Blocking İşlemler için ForkJoin ve ManagedBlocker

Başlık: Blocking yaparken common pool ile uyumlu çalış
Açıklama: ForkJoin common pool'u bloklamaktan kaçın. Blocking kaçınılmazsa `ForkJoinPool.ManagedBlocker` veya ayrı bir executor kullan.

**İyi örnek:**

```java
import java.util.concurrent.ForkJoinPool;

class ManagedBlockerExample {
    static String blockingIO() throws InterruptedException {
        Thread.sleep(200); // blocking'i simüle et
        return "ok";
    }

    static String callBlockingSafely() throws InterruptedException {
        ForkJoinPool.managedBlock(new ForkJoinPool.ManagedBlocker() {
            volatile boolean done;
            @Override public boolean block() throws InterruptedException {
                if (!done) {
                    blockingIO();
                    done = true;
                }
                return true;
            }
            @Override public boolean isReleasable() { return done; }
        });
        return "ok";
    }
}
```

**Kötü örnek:**

```java
import java.util.List;

class BadParallelBlocking {
    public List<String> doWork(List<String> inputs) {
        return inputs.parallelStream() // common pool kullanır
            .map(x -> {
                try {
                    Thread.sleep(200); // Common pool worker'ını BLOKLAR
                    return x + "!";
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            })
            .toList();
    }
}
```

### Örnek 13: Virtual Thread'lerde Pinning'den Kaçınma

Başlık: Blocking işlemleri intrinsic lock'ların dışında tut
Açıklama: Virtual thread'lerde blocking çağrıları saran `synchronized`, carrier thread'e pin'lenmeye yol açabilir. Kooperatif lock'ları tercih et veya blocking işlemi kritik bölgenin dışına taşı. (Java 24+ / JEP 491 ile bu sorun büyük ölçüde giderildi; proje notuna bak.)

**İyi örnek:**

```java
import java.util.concurrent.locks.ReentrantLock;

class PinningSafeExample {
    private final ReentrantLock lock = new ReentrantLock();
    private int shared = 0;

    public void doWork() {
        // Kritik bölgeyi küçük ve non-blocking tut
        lock.lock();
        try {
            shared++;
        } finally {
            lock.unlock();
        }
        // Blocking işlemi lock dışında yap (virtual thread'lerle uygun)
        try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
```

**Kötü örnek:**

```java
class PinningBadExample {
    private int shared;
    public synchronized void doWork() {
        shared++;
        try {
            Thread.sleep(100); // virtual thread'i carrier thread'e pin'ler (Java 24 öncesi)
        } catch (InterruptedException e) {
            // yutuldu
        }
    }
}
```


### Örnek 14: Phaser ile Aşamalı Çalıştırma

Başlık: Dinamik katılımcı kaydıyla görevleri aşamalar halinde koordine et
Açıklama: Aşamalar halinde ilerleyen görevleri koordine etmek için Phaser kullan; katılımcıların (party) dinamik kaydına ve kayıttan çıkmasına izin verir.

**İyi örnek:**

```java
import java.util.concurrent.Phaser;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class PhaserExample {
    private final Phaser phaser = new Phaser(1); // main thread

    public void runPhasedTasks(int taskCount) {
        ExecutorService executor = Executors.newFixedThreadPool(taskCount);

        for (int i = 0; i < taskCount; i++) {
            phaser.register(); // dinamik kayıt
            executor.submit(() -> {
                try {
                    // Faz 0 işi
                    Thread.sleep(100);
                    phaser.arriveAndAwaitAdvance(); // hepsini bekle

                    // Faz 1 işi
                    Thread.sleep(200);
                    phaser.arriveAndDeregister(); // bitti
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        phaser.arriveAndAwaitAdvance(); // faz 0'ı ilerlet
        phaser.arriveAndAwaitAdvance(); // faz 1'i ilerlet
        phaser.arriveAndDeregister(); // main bitti

        executor.shutdown();
    }
}
```

**Kötü örnek:**

```java
class BadPhaser {
    public void misusePhaser() {
        Phaser phaser = new Phaser(3);
        // KÖTÜ: arrive/deregister'ı unutmak
        // deadlock'a yol açar
        phaser.awaitAdvance(0); // sonsuza kadar asılı kalır
    }
}
```

### Örnek 15: CyclicBarrier ile Senkronizasyon

Başlık: Thread'lerin ortak bir bariyer noktasına ulaşmasını bekle
Açıklama: Thread'leri bariyer noktalarında senkronize etmek için CyclicBarrier kullan; birden fazla döngüde yeniden kullanılabilir.

**İyi örnek:**

```java
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class BarrierExample {
    public void coordinate(int parties) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(parties, () -> System.out.println("All arrived!"));

        ExecutorService executor = Executors.newFixedThreadPool(parties);
        for (int i = 0; i < parties; i++) {
            executor.submit(() -> {
                try {
                    // İş
                    Thread.sleep(100);
                    barrier.await(); // senkronizasyon noktası 1

                    // Ek iş
                    Thread.sleep(200);
                    barrier.await(); // senkronizasyon noktası 2 (yeniden kullanılabilir)
                } catch (Exception e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        executor.shutdown();
    }
}
```

**Kötü örnek:**

```java
class BadBarrier {
    public void misuse() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(3);
        barrier.await(); // KÖTÜ: yeterli katılımcı yok, asılı kalır
    }
}
```

### Örnek 16: Exchanger ile Veri Değişimi

Başlık: İki thread arasında güvenle veri değiş tokuş et
Açıklama: Tam olarak iki thread arasında noktadan noktaya veri değişimi için Exchanger kullan.

**İyi örnek:**

```java
import java.util.concurrent.Exchanger;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class ExchangerExample {
    public void exchangeData() {
        Exchanger<String> exchanger = new Exchanger<>();
        ExecutorService executor = Executors.newFixedThreadPool(2);

        executor.submit(() -> {
            try {
                String data = "From Thread 1";
                String received = exchanger.exchange(data);
                System.out.println("Thread 1 received: " + received);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        executor.submit(() -> {
            try {
                String data = "From Thread 2";
                String received = exchanger.exchange(data);
                System.out.println("Thread 2 received: " + received);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        executor.shutdown();
    }
}
```

**Kötü örnek:**

```java
class BadExchanger {
    public void misuse() throws InterruptedException {
        Exchanger<String> exchanger = new Exchanger<>();
        exchanger.exchange("data"); // KÖTÜ: partner yok, sonsuza kadar asılı kalır
    }
}
```


### Örnek 17: StructuredTaskScope ile Structured Concurrency

Başlık: İlişkili alt görevleri tek bir iş birimi olarak koordine et
Açıklama: Bir parent görev, birlikte join edilmesi, iptal edilmesi ve gözlemlenmesi gereken ilişkili alt görevler fork ediyorsa Structured Concurrency kullan. `StructuredTaskScope` bir Java 27 preview API'sidir (JEP 533); onu kullanan kodu `--enable-preview` ile derle ve çalıştır (bu proje Java 26 kullanır ve preview açık değildir). Java 27 preview kodunu incelerken bu rehberi uygula: - Kardeş alt görevlerin tek bir yaşam döngüsünü paylaştığı request kapsamlı fan-out için `StructuredTaskScope` tercih et. - Scope'un kapanması ve bitmemiş alt görevlerin iptal edilmesi için try-with-resources kullan. - `Subtask` sonuçlarını okumadan önce scope içinde owner thread'den `join()` çağır. - Java 27 API şeklini bekle: `StructuredTaskScope<T, R, R_X extends Throwable>` ve `Joiner<T, R, R_X>`. - Varsayılan join policy için `StructuredTaskScope.open()`, varsayılan policy'yi isim, timeout veya thread factory ile yapılandırırken `StructuredTaskScope.open(UnaryOperator)` kullan. - Hatanın `ExecutionException` ile yüzeye çıkması gerektiğinde `Joiner.allSuccessfulOrThrow()`, `anySuccessfulOrThrow()` veya `awaitAllSuccessfulOrThrow()` kullan; hataları özel bir exception tipine map'lemek için overload'larını kullan. - Eski preview API şekillerini kullanma: `Joiner.awaitAll()` kaldırıldı, `Joiner.onTimeout()` yerini `timeout()`'a bıraktı. - Structured concurrency'yi genel `ExecutorService`'ten ayır: scope sınırlı bir parent-child ilişkisini modeller, executor ise daha geniş bir görev gönderme mekanizmasıdır.

**İyi örnek:**

```java
import java.util.concurrent.ExecutionException;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Joiner;
import java.util.concurrent.StructuredTaskScope.Subtask;

class StructuredConcurrencyExample {
    Response handle(Request request) throws ExecutionException, InterruptedException {
        try (var scope = StructuredTaskScope.open()) {
            Subtask<Customer> customer = scope.fork(() -> findCustomer(request.customerId()));
            Subtask<OrderHistory> orders = scope.fork(() -> fetchOrderHistory(request.customerId()));

            scope.join(); // alt görevleri join eder ve hatayı ExecutionException olarak yayar

            return new Response(customer.get(), orders.get());
        }
    }

    Quote fastestQuote(Request request) throws ExecutionException, InterruptedException {
        try (var scope = StructuredTaskScope.open(Joiner.<Quote>anySuccessfulOrThrow())) {
            scope.fork(() -> quoteFromPrimary(request));
            scope.fork(() -> quoteFromBackup(request));

            return scope.join(); // ilk başarılı teklifi döner ya da ExecutionException fırlatır
        }
    }

    private Customer findCustomer(String customerId) {
        return new Customer(customerId);
    }

    private OrderHistory fetchOrderHistory(String customerId) {
        return new OrderHistory(customerId);
    }

    private Quote quoteFromPrimary(Request request) {
        return new Quote("primary");
    }

    private Quote quoteFromBackup(Request request) {
        return new Quote("backup");
    }
}

record Request(String customerId) {}
record Customer(String id) {}
record OrderHistory(String customerId) {}
record Quote(String provider) {}
record Response(Customer customer, OrderHistory orders) {}
```

**Kötü örnek:**

```java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

class UnstructuredFanOutExample {
    private final ExecutorService executor = Executors.newCachedThreadPool();

    Response handle(Request request) throws Exception {
        Future<Customer> customer = executor.submit(() -> findCustomer(request.customerId()));
        Future<OrderHistory> orders = executor.submit(() -> fetchOrderHistory(request.customerId()));

        // KÖTÜ: customer.get() fail olursa veya parent görev interrupt edilirse,
        // kardeş görev net bir parent scope olmadan çalışmaya devam edebilir.
        return new Response(customer.get(), orders.get());
    }

    void stalePreviewApiUsage() {
        // KÖTÜ (Java 27 / JEP 533 için):
        // - Kaldırılan Joiner.awaitAll()'u önerme.
        // - Yerine başkası gelen Joiner.onTimeout()'u önerme.
        // - Standart successful-or-throw joiner'lar için eski preview'a özgü FailedException'ı yakalama;
        //   bu joiner'lar artık varsayılan olarak ExecutionException fırlatır.
    }

    private Customer findCustomer(String customerId) {
        return new Customer(customerId);
    }

    private OrderHistory fetchOrderHistory(String customerId) {
        return new OrderHistory(customerId);
    }
}
```


## Çıktı Formatı

- **ANALİZ ET**: Java kodundaki concurrency sorunlarını tespit et ve etkisine (CRITICAL, PERFORMANCE, DEADLOCK_RISK, SCALABILITY, THREAD_SAFETY) ve concurrency alanına (thread safety, senkronizasyon, thread pool'lar, async işlemler, modern concurrency) göre sınıflandır
- **KATEGORİLERE AYIR**: Bulunan concurrency iyileştirmelerini şu başlıklarda topla: Thread Safety Sorunları (race condition vs atomic işlemler, güvensiz collection'lar vs concurrent collection'lar, paylaşılan mutable state vs immutable object'ler), Thread Pool Yönetimi (yanlış boyut vs optimal yapılandırma, kaynak sızıntısı vs doğru yaşam döngüsü yönetimi), Senkronizasyon Sorunları (deadlock riski vs lock-free algoritmalar, aşırı contention vs verimli senkronizasyon), Performans Sorunları (blocking işlemler vs non-blocking alternatifler, memory consistency sorunları vs volatile/final kullanımı) ve Modern Concurrency Eksikleri (platform thread vs virtual thread, yapısız fan-out vs StructuredTaskScope, callback hell vs CompletableFuture kompozisyonu, context propagation için ThreadLocal vs ScopedValue)
- **UYGULA**: Tespit edilen her sorun için en uygun iyileştirmeyi doğrudan uygula: güvensiz collection'ları concurrent alternatiflerle değiştir, atomic sınıflar ve concurrent araçlarla doğru senkronizasyon kur, thread pool'ları uygun boyut ve yaşam döngüsü yönetimiyle yapılandır, blocking işlemleri CompletableFuture ile non-blocking alternatiflere refactor et, race condition'ları immutability ve doğru senkronizasyonla ortadan kaldır ve faydalı olduğu yerde virtual thread ve ScopedValue gibi modern özellikleri benimse (StructuredTaskScope bu projede preview olduğu için kullanılmaz)
- **IMPLEMENT ET**: Kanıtlanmış pattern'lerle kapsamlı concurrency refactoring'i yap: concurrent collection'lar ve atomic sınıflarla thread-safe veri yapıları kur, lock, semaphore ve barrier'larla verimli senkronizasyon uygula, doğru ExecutorService kullanımıyla optimal thread pool yönetimi yapılandır, CompletableFuture kompozisyonuyla asenkron programlama pattern'leri uygula, modern concurrency özelliklerini (virtual thread, structured concurrency, scoped value) entegre et ve concurrent bağlamlarda doğru hata ve kaynak yönetimi uygula
- **REFACTOR ET**: Kodu concurrency iyileştirme yol haritasına göre sistematik refactor et: önce atomic işlemler ve concurrent collection'larla kritik thread safety sorunlarını gider, sonra contention ve deadlock riskini azaltmak için senkronizasyon mekanizmalarını optimize et, thread pool yönetimini ve yaşam döngüsünü doğru yapılandır, blocking işlemleri asenkron alternatiflere refactor et, ölçeklenebilirlik için modern concurrency özelliklerini entegre et ve concurrent kodu doğrulamak için kapsamlı test stratejileri uygula
- **AÇIKLA**: Uygulanan concurrency iyileştirmelerini ve faydalarını açıkla: doğru senkronizasyon ve atomic işlemlerle thread safety iyileştirmeleri, optimize thread pool yönetimi ve non-blocking işlemlerle performans iyileştirmeleri, virtual thread ve StructuredTaskScope gibi modern özelliklerle ölçeklenebilirlik kazanımları, lock-free algoritmalar ve doğru senkronizasyon pattern'leriyle deadlock önleme ve net async kompozisyon ile context propagation için ScopedValue sayesinde bakım kolaylığı
- **DOĞRULA**: Uygulanan tüm concurrency refactoring'inin başarıyla derlendiğini, thread safety garantilerini koruduğunu, race condition ve deadlock risklerini ortadan kaldırdığını, performansı koruduğunu veya iyileştirdiğini ve hedeflenen iyileştirmeleri kapsamlı test ve doğrulamayla sağladığını kontrol et
- **İPTAL/INTERRUPTION**: Blocking çağrıları ve uzun süren görevleri tespit et; interruption'ın propagate edildiğinden/geri konduğundan emin ol, timeout'lar ekle (`orTimeout`, `completeOnTimeout`, süreli `poll/take/tryLock`) ve `Future.cancel(true)` yollarının kaynakları serbest bıraktığını doğrula.
- **BACKPRESSURE/AŞIRI YÜK**: Sınırsız producer'ları ve queue'ları tespit et; bounded queue'lar, rejection policy'ler, semaphore/bulkhead'ler ekle ve streaming'de backpressure uygulamak için `Flow`/Reactive Streams'i tercih et.
- **FORKJOIN/PARALLEL STREAM KULLANIMI**: Common pool'daki blocking işlemleri işaretle, ayrı executor'lara veya `ManagedBlocker`'a taşı, görevlerin CPU ağırlıklı ve yan etkisiz olduğunu doğrula ve `parallelStream()` kullanımını ölçüme bağla.
- **VIRTUAL THREAD'LERDE PINNING**: Blocking I/O'yu saran `synchronized` blokları incele; kooperatif lock'larla değiştir, kritik bölgeleri küçült ve JFR pinning analizi öner (Java 24+ notuna bak).
- **KOORDİNASYON PRIMITIVE'LERİ**: Phaser (aşamalı görevler), CyclicBarrier (yeniden kullanılabilir bariyerler) ve Exchanger (ikili değişim) fırsatlarını tespit et; interruption yönetimi ve kaynak temizliğiyle doğru kullanımı sağla.
- **STRUCTURED CONCURRENCY** (bu projede uygulanmaz: Java 26'da preview, `--enable-preview` kapalı; fan-out için `CompletableFuture` + timeout kullan): Preview açık bir projede ilişkili alt görevler için gelişigüzel `ExecutorService` fan-out'u yerine `StructuredTaskScope`'u tercih et; Java 27 preview kullanımını, `--enable-preview`'u, `ExecutionException` joiner davranışını, `R_X` exception tiplemesini, `open(UnaryOperator)` yapılandırmasını ve eski preview API'lerinin (`awaitAll()`, `onTimeout()`) kaldırıldığını/değiştirildiğini doğrula.


## Güvenlik Önlemleri

- **BLOKLAYICI GÜVENLİK KONTROLÜ**: HERHANGİ bir öneriden önce HER ZAMAN `./mvnw compile` çalıştır
- **KRİTİK DOĞRULAMA**: Tüm testlerin geçtiğinden emin olmak için `./mvnw clean verify` çalıştır
- **ZORUNLU DOĞRULAMA**: Concurrency iyileştirmelerinden sonra mevcut tüm işlevselliğin bozulmadığını teyit et
- **GERİ ALMA GEREKSİNİMİ**: Sorun çıkarsa tüm değişikliklerin kolayca geri alınabildiğinden emin ol
- **ARTIMLI GÜVENLİK**: Concurrency iyileştirmelerini adım adım uygula, her değişiklikten sonra doğrula
- **THREAD SAFETY DOĞRULAMASI**: Thread-safe bileşenlerin eşzamanlı erişimde doğru çalıştığını doğrula
- **DEADLOCK ÖNLEME**: Synchronized kodda olası deadlock senaryolarını kontrol et
- **KAYNAK SIZINTISI KORUMASI**: Thread pool'ların, executor'ların ve diğer concurrent kaynakların doğru temizlendiğinden emin ol
- **MEMORY CONSISTENCY KONTROLÜ**: Doğru senkronizasyon ve bellek görünürlüğü semantiğini doğrula
- **PERFORMANS REGRESYON KORUMASI**: Concurrency değişikliklerinden sonra performans düşüşünü izle
- **INTERRUPTION/İPTAL DİSİPLİNİ**: Interruption'ları asla yutma; propagate et veya interrupt flag'ini geri koy. Blocking işlemlerde timeout olduğundan ve iptal yollarının kaynakları güvenle serbest bıraktığından emin ol.
- **VIRTUAL THREAD PINNING KORUMASI**: Blocking çağrıları saran intrinsic lock'ları denetle; parking ile uyumlu lock implementasyonlarını tercih et. Pinning'i tespit etmek için JFR kullan.
- **AŞIRI YÜK/BACKPRESSURE KORUMASI**: Sınırsız queue'lardan kaçın; sınırlı kapasite, makul rejection policy'ler ve rate/concurrency limitleri uygulayarak zincirleme hataları önle.
- **TIMEOUT/RETRY/IDEMPOTENCY**: Harici çağrıları timeout ile sınırla, sınırlı ve jitter'lı retry'ları sadece idempotent işlemlerde kullan ve çift yan etki oluşmadığını doğrula.
- **KOORDİNASYON GÜVENLİĞİ**: Phaser/Barrier/Exchanger için party sayılarını doğrula, interruption'ları yönet ve eşleşmeyen varışlardan kaynaklanan takılmaları önle.
- **STRUCTURED CONCURRENCY PREVIEW GÜVENLİĞİ**: `StructuredTaskScope` için Java 27 preview farkındalığı ve `--enable-preview` gerekir; `Joiner.awaitAll()` veya `Joiner.onTimeout()` gibi eski preview API'lerini önerme.
