package com.embedding.pgvector.embedding.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.embedding.pgvector.embedding.domain.dto.EmbedTextRequest;
import com.embedding.pgvector.embedding.domain.dto.EmbeddingResponse;
import com.embedding.pgvector.embedding.service.EmbeddingService;
import com.embedding.pgvector.exception.ServiceUnavailableException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * EmbeddingController'ın metin doğrulamasını, boşlukların kırpılmasını ve 503 hata yanıtlarını sahte
 * servisle doğrular.
 */
@WebMvcTest(EmbeddingController.class)
class EmbeddingControllerTest {

    private static final String URL = "/api/embeddings";

    @Autowired
    private MockMvcTester mockMvcTester;

    @MockitoBean
    private EmbeddingService embeddingService;

    private MvcTestResult post(String body) {
        return mockMvcTester.post().uri(URL).contentType(MediaType.APPLICATION_JSON).content(body).exchange();
    }

    private void assertValidationFailed(MvcTestResult result, String message) {
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.errorCode").isEqualTo("VALIDATION_FAILED");
        assertThat(result).bodyJson().extractingPath("$.type")
                .isEqualTo("https://embedding-pgvector.example/errors/validation-failed");
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Validation Failed");
        assertThat(result).bodyJson().extractingPath("$.violations[0].field").isEqualTo("text");
        assertThat(result).bodyJson().extractingPath("$.violations[0].message").isEqualTo(message);
        then(embeddingService).should(never()).embedText(any());
    }

    @Test
    void givenEmptyText_whenPostEmbeddings_thenReturns400ValidationFailedAndServiceNotCalled() {
        // Act
        MvcTestResult result = post("{\"text\":\"\"}");

        // Assert
        assertValidationFailed(result, "must not be blank");
    }

    @Test
    void givenWhitespaceOnlyText_whenPostEmbeddings_thenReturns400ValidationFailedAndServiceNotCalled() {
        // Act
        MvcTestResult result = post("{\"text\":\"   \"}");

        // Assert
        assertValidationFailed(result, "must not be blank");
    }

    @Test
    void givenMissingText_whenPostEmbeddings_thenReturns400ValidationFailed() {
        // Act
        MvcTestResult missing = post("{}");
        MvcTestResult nullText = post("{\"text\":null}");

        // Assert
        assertValidationFailed(missing, "must not be blank");
        assertValidationFailed(nullText, "must not be blank");
    }

    @Test
    void givenTextOf2001Characters_whenPostEmbeddings_thenReturns400ValidationFailed() {
        // Act
        MvcTestResult result = post("{\"text\":\"" + "a".repeat(2001) + "\"}");

        // Assert
        assertValidationFailed(result, "size must be at most 2000 characters");
    }

    @Test
    void givenTextOf2000Characters_whenPostEmbeddings_thenReturns200() {
        // Arrange
        given(embeddingService.embedText(any())).willReturn(new EmbeddingResponse("bge-m3", 1, new float[] {1f}, 5));

        // Act
        MvcTestResult result = post("{\"text\":\"" + "ş".repeat(2000) + "\"}");

        // Assert
        assertThat(result).hasStatusOk();
    }

    @Test
    void givenTextOf2000CharactersWithSurroundingSpaces_whenPostEmbeddings_thenServiceReceivesStrippedText() {
        // Arrange
        given(embeddingService.embedText(any())).willReturn(new EmbeddingResponse("bge-m3", 1, new float[] {1f}, 5));

        // Act
        MvcTestResult result = post("{\"text\":\"  " + "a".repeat(2000) + "  \"}");

        // Assert
        assertThat(result).hasStatusOk();
        ArgumentCaptor<EmbedTextRequest> captor = ArgumentCaptor.forClass(EmbedTextRequest.class);
        then(embeddingService).should().embedText(captor.capture());
        assertThat(captor.getValue().text()).hasSize(2000);
    }

    @Test
    void givenMalformedJson_whenPostEmbeddings_thenReturns400BadRequest() {
        // Act
        MvcTestResult result = post("{\"text\":");

        // Assert
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.errorCode").isEqualTo("BAD_REQUEST");
        assertThat(result).bodyJson().extractingPath("$.type")
                .isEqualTo("https://embedding-pgvector.example/errors/bad-request");
    }

    @Test
    void givenServiceReturnsEmbedding_whenPostEmbeddings_thenReturns200WithEmbeddingResponseJson() {
        // Arrange
        given(embeddingService.embedText(any())).willReturn(
                new EmbeddingResponse("bge-m3", 3, new float[] {0.5f, -0.25f, 0.125f}, 85));

        // Act
        MvcTestResult result = post("{\"text\":\"izin\"}");

        // Assert
        assertThat(result).hasStatusOk().hasContentType(MediaType.APPLICATION_JSON);
        assertThat(result).bodyJson().isLenientlyEqualTo("""
                {"embeddingModel":"bge-m3","dimensions":3,"embedding":[0.5,-0.25,0.125],"durationMs":85}
                """);
    }

    @Test
    void givenEmbeddingUnavailable_whenPostEmbeddings_thenReturns503EmbeddingUnavailable() {
        // Arrange
        given(embeddingService.embedText(any())).willThrow(
                new ServiceUnavailableException("EMBEDDING_UNAVAILABLE", "Embedding service is not reachable"));

        // Act
        MvcTestResult result = post("{\"text\":\"izin\"}");

        // Assert
        assertThat(result).hasStatus(HttpStatus.SERVICE_UNAVAILABLE)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.errorCode").isEqualTo("EMBEDDING_UNAVAILABLE");
        assertThat(result).bodyJson().extractingPath("$.type")
                .isEqualTo("https://embedding-pgvector.example/errors/embedding-unavailable");
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo(503);
    }

    @Test
    void givenEmbeddingModelNotFound_whenPostEmbeddings_thenReturns503EmbeddingModelNotFound() {
        // Arrange
        given(embeddingService.embedText(any())).willThrow(
                new ServiceUnavailableException("EMBEDDING_MODEL_NOT_FOUND", "Embedding model is not available: x"));

        // Act
        MvcTestResult result = post("{\"text\":\"izin\"}");

        // Assert
        assertThat(result).hasStatus(HttpStatus.SERVICE_UNAVAILABLE)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.errorCode").isEqualTo("EMBEDDING_MODEL_NOT_FOUND");
        assertThat(result).bodyJson().extractingPath("$.type")
                .isEqualTo("https://embedding-pgvector.example/errors/embedding-model-not-found");
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo(503);
    }

    @Test
    void givenUnexpectedException_whenPostEmbeddings_thenReturns500InternalErrorWithoutInternalMessage() {
        // Arrange
        given(embeddingService.embedText(any())).willThrow(new IllegalStateException("secret internal"));

        // Act
        MvcTestResult result = post("{\"text\":\"izin\"}");

        // Assert
        assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.errorCode").isEqualTo("INTERNAL_ERROR");
        assertThat(result).bodyJson().extractingPath("$.type")
                .isEqualTo("https://embedding-pgvector.example/errors/internal-error");
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Internal Server Error");
        assertThat(result).bodyJson().extractingPath("$.instance").isEqualTo("/api/embeddings");
        assertThat(result).bodyText().doesNotContain("secret internal");
    }
}
