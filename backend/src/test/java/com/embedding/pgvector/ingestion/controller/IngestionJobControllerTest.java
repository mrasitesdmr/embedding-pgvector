package com.embedding.pgvector.ingestion.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.embedding.pgvector.exception.NotFoundException;
import com.embedding.pgvector.ingestion.domain.dto.IngestionJobResponse;
import com.embedding.pgvector.ingestion.domain.entity.IngestionErrorCode;
import com.embedding.pgvector.ingestion.domain.entity.IngestionStage;
import com.embedding.pgvector.ingestion.service.IngestionService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * IngestionJobController'ın içe alma işi getirme ucunu, sözleşme alanlarını ve 404 hata yanıtını sahte
 * servisle doğrular.
 */
@WebMvcTest(IngestionJobController.class)
class IngestionJobControllerTest {

    @Autowired
    private MockMvcTester mockMvcTester;

    @MockitoBean
    private IngestionService ingestionService;

    @Test
    void givenUnknownJob_whenGetIngestionJob_thenReturns404IngestionJobNotFound() {
        // Arrange
        given(ingestionService.getIngestionJob(42L, 99L))
                .willThrow(new NotFoundException("INGESTION_JOB_NOT_FOUND", "x"));

        // Act
        MvcTestResult result = mockMvcTester.get().uri("/api/workspaces/42/ingestion-jobs/99").exchange();

        // Assert
        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).bodyJson().extractingPath("$.errorCode").isEqualTo("INGESTION_JOB_NOT_FOUND");
    }

    @Test
    void givenFailedJob_whenGetIngestionJob_thenReturnsEveryContractField() {
        // Arrange
        given(ingestionService.getIngestionJob(42L, 7L)).willReturn(new IngestionJobResponse(7L, 42L, 302L, "3.2",
                "v3.2.pdf", IngestionStage.FAILED, 0.1, 0, 12, IngestionErrorCode.EMBEDDING_UNAVAILABLE,
                Instant.parse("2026-10-25T10:00:00Z"), Instant.parse("2026-10-25T10:00:01Z"),
                Instant.parse("2026-10-25T10:00:02Z"), "/api/workspaces/42/ingestion-jobs/7"));

        // Act
        MvcTestResult result = mockMvcTester.get().uri("/api/workspaces/42/ingestion-jobs/7").exchange();

        // Assert
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.stage").isEqualTo("FAILED");
        assertThat(result).bodyJson().extractingPath("$.errorCode").isEqualTo("EMBEDDING_UNAVAILABLE");
        assertThat(result).bodyJson().extractingPath("$.progress").isEqualTo(0.1);
        assertThat(result).bodyJson().extractingPath("$.totalChunks").isEqualTo(12);
        assertThat(result).bodyJson().extractingPath("$.sourceId").isEqualTo(302);
        assertThat(result).bodyJson().extractingPath("$.fileName").isEqualTo("v3.2.pdf");
        assertThat(result).bodyJson().extractingPath("$.finishedAt").isEqualTo("2026-10-25T10:00:02Z");
        assertThat(result).bodyJson().extractingPath("$.statusUrl").isEqualTo("/api/workspaces/42/ingestion-jobs/7");
    }
}
