package com.embedding.pgvector.embedding.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.embedding.pgvector.embedding.client.EmbeddingClient;
import com.embedding.pgvector.embedding.client.EmbeddingResult;
import com.embedding.pgvector.embedding.domain.dto.EmbedTextRequest;
import com.embedding.pgvector.embedding.domain.dto.EmbeddingResponse;
import java.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * EmbeddingService'in embedding sonucunu süreyle döndürdüğünü ve günlüğe metin ile vektör yazmadığını
 * doğrular.
 */
@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class EmbeddingServiceTest {

    @Mock
    private EmbeddingClient embeddingClient;

    @Mock
    private Clock clock;

    private EmbeddingService embeddingService;

    @BeforeEach
    void setUp() {
        embeddingService = new EmbeddingService(embeddingClient, clock);
    }

    @Test
    void givenClientReturnsEmbedding_whenEmbedText_thenReturnsModelDimensionsEmbeddingAndDuration() {
        // Arrange
        given(embeddingClient.embed("izin")).willReturn(
                new EmbeddingResult("bge-m3", new float[] {0.5f, -0.25f, 0.125f, 1.0f}));
        given(clock.millis()).willReturn(1000L, 1085L);

        // Act
        EmbeddingResponse response = embeddingService.embedText(new EmbedTextRequest("izin"));

        // Assert
        assertThat(response.embeddingModel()).isEqualTo("bge-m3");
        assertThat(response.dimensions()).isEqualTo(4);
        assertThat(response.embedding()).containsExactly(0.5f, -0.25f, 0.125f, 1.0f);
        assertThat(response.durationMs()).isEqualTo(85);
    }

    @Test
    void givenTurkishText_whenEmbedText_thenLogsModelDimensionsDurationWithoutTextOrEmbedding(
            CapturedOutput output) {
        // Arrange
        String text = "Parolamı unuttum, online işlemlere giremiyorum";
        given(embeddingClient.embed(text)).willReturn(new EmbeddingResult("bge-m3", new float[] {0.123f, -0.456f}));
        given(clock.millis()).willReturn(1000L, 1085L);

        // Act
        embeddingService.embedText(new EmbedTextRequest(text));

        // Assert
        assertThat(output.getOut()).contains("model=bge-m3", "dimensions=2", "durationMs=85")
                .doesNotContain("Şifremi", "0.123", "-0.456");
    }
}
