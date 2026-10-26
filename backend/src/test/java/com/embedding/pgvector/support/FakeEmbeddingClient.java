package com.embedding.pgvector.support;

import com.embedding.pgvector.embedding.client.EmbeddingBatchResult;
import com.embedding.pgvector.embedding.client.EmbeddingClient;
import com.embedding.pgvector.embedding.client.EmbeddingResult;
import com.embedding.pgvector.exception.ServiceUnavailableException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Testlerde gerçek model yerine aynı metne hep aynı vektörü döndürür; hata ve bekletme durumu kurulabilir.
 */
public class FakeEmbeddingClient implements EmbeddingClient {

    public static final String MODEL = "bge-m3";

    private final Map<String, float[]> registered = new ConcurrentHashMap<>();
    private final List<Integer> batchSizes = new CopyOnWriteArrayList<>();
    private final List<String> singleTexts = new CopyOnWriteArrayList<>();
    private volatile ServiceUnavailableException failure;
    private volatile CountDownLatch gate;
    private volatile CountDownLatch entered = new CountDownLatch(1);
    private volatile int dimensions = 1024;

    public void register(String text, float[] embedding) {
        registered.put(text, embedding);
    }

    public void failWith(ServiceUnavailableException exception) {
        this.failure = exception;
    }

    public void useDimensions(int dimensions) {
        this.dimensions = dimensions;
    }

    public void blockUntilReleased() {
        this.entered = new CountDownLatch(1);
        this.gate = new CountDownLatch(1);
    }

    public boolean awaitBlockedCall(long timeoutSeconds) throws InterruptedException {
        return entered.await(timeoutSeconds, TimeUnit.SECONDS);
    }

    public void release() {
        CountDownLatch current = gate;
        if (current != null) {
            current.countDown();
        }
    }

    public void reset() {
        registered.clear();
        batchSizes.clear();
        singleTexts.clear();
        failure = null;
        dimensions = 1024;
        release();
        gate = null;
    }

    public List<Integer> batchSizes() {
        return List.copyOf(batchSizes);
    }

    public List<String> singleTexts() {
        return List.copyOf(singleTexts);
    }

    @Override
    public EmbeddingResult embed(String text) {
        singleTexts.add(text);
        failIfConfigured();
        return new EmbeddingResult(MODEL, vectorFor(text));
    }

    @Override
    public EmbeddingBatchResult embedAll(List<String> texts) {
        batchSizes.add(texts.size());
        awaitGate();
        failIfConfigured();
        List<float[]> embeddings = new ArrayList<>();
        texts.forEach(text -> embeddings.add(vectorFor(text)));
        return new EmbeddingBatchResult(MODEL, embeddings);
    }

    private void awaitGate() {
        CountDownLatch current = gate;
        if (current == null) {
            return;
        }
        entered.countDown();
        try {
            if (!current.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Blocked embedding call was never released");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private void failIfConfigured() {
        ServiceUnavailableException current = failure;
        if (current != null) {
            throw current;
        }
    }

    private float[] vectorFor(String text) {
        float[] explicit = registered.get(text);
        if (explicit != null) {
            return explicit;
        }
        Random random = new Random(text.hashCode());
        float[] vector = new float[dimensions];
        double norm = 0;
        for (int index = 0; index < dimensions; index++) {
            vector[index] = (float) random.nextGaussian();
            norm += vector[index] * vector[index];
        }
        float scale = (float) (1 / Math.sqrt(norm));
        for (int index = 0; index < dimensions; index++) {
            vector[index] *= scale;
        }
        return vector;
    }
}
