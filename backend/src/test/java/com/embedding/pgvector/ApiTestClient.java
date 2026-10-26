package com.embedding.pgvector;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/**
 * Entegrasyon testlerinde çalışan uygulamaya GET, JSON POST ve çok parçalı POST istekleri atan küçük HTTP
 * istemcisidir.
 */
public final class ApiTestClient {

    public record Response(int status, String body, String contentType) {
    }

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final String baseUrl;

    public ApiTestClient(int port) {
        this.baseUrl = "http://127.0.0.1:" + port;
    }

    public Response get(String path) {
        return send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build());
    }

    public Response postJson(String path, String json) {
        return send(HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)).build());
    }

    public record Part(String name, String fileName, String contentType, byte[] content) {

        public static Part field(String name, String value) {
            return new Part(name, null, null, value.getBytes(StandardCharsets.UTF_8));
        }

        public static Part file(String name, String fileName, byte[] content) {
            return new Part(name, fileName, "application/octet-stream", content);
        }
    }

    public Response postMultipart(String path, Part... parts) {
        String boundary = "----test" + System.nanoTime();
        java.io.ByteArrayOutputStream body = new java.io.ByteArrayOutputStream();
        for (Part part : parts) {
            StringBuilder header = new StringBuilder("--").append(boundary).append("\r\n")
                    .append("Content-Disposition: form-data; name=\"").append(part.name()).append('"');
            if (part.fileName() != null) {
                header.append("; filename=\"").append(part.fileName()).append('"');
            }
            header.append("\r\n");
            if (part.contentType() != null) {
                header.append("Content-Type: ").append(part.contentType()).append("\r\n");
            }
            header.append("\r\n");
            body.writeBytes(header.toString().getBytes(StandardCharsets.UTF_8));
            body.writeBytes(part.content());
            body.writeBytes("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        body.writeBytes(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return send(HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build());
    }

    private Response send(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new Response(response.statusCode(), response.body(),
                    response.headers().firstValue("Content-Type").orElse(""));
        } catch (IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
