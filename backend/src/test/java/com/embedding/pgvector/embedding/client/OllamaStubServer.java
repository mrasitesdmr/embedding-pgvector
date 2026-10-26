package com.embedding.pgvector.embedding.client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Testlerde gerçek Ollama yerine hazır yanıt veren, gelen istekleri kaydeden ve yanıtı bekletebilen yerel
 * HTTP sunucusudur.
 */
public final class OllamaStubServer implements AutoCloseable {

    public record RecordedRequest(String method, String path, String body) {
    }

    private final HttpServer server;
    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
    private final CountDownLatch release = new CountDownLatch(1);
    private volatile int status = 200;
    private volatile String responseBody = "{}";
    private volatile boolean hang;

    public OllamaStubServer() {
        this(0);
    }

    public OllamaStubServer(int port) {
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        server.createContext("/", this::handle);
        server.start();
    }

    public static int freePort() {
        try (ServerSocket socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + port();
    }

    public void respondWith(int status, String body) {
        this.status = status;
        this.responseBody = body;
    }

    public void hangUntilClosed() {
        this.hang = true;
    }

    public void reset() {
        requests.clear();
        status = 200;
        responseBody = "{}";
    }

    public List<RecordedRequest> requests() {
        return List.copyOf(requests);
    }

    public int requestCount() {
        return requests.size();
    }

    private void handle(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        requests.add(new RecordedRequest(exchange.getRequestMethod(), exchange.getRequestURI().getPath(), body));
        if (hang) {
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
        byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        try {
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        } catch (IOException ignored) {
            // client already gave up (timeout test)
        } finally {
            exchange.close();
        }
    }

    @Override
    public void close() {
        release.countDown();
        server.stop(0);
    }
}
