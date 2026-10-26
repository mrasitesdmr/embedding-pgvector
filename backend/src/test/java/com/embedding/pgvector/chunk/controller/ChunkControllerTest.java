package com.embedding.pgvector.chunk.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.embedding.pgvector.chunk.domain.dto.ChunkResponse;
import com.embedding.pgvector.chunk.service.ChunkService;
import com.embedding.pgvector.exception.NotFoundException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * ChunkController'ın parça listeleme ve tek parça getirme uçlarını, sınır doğrulamasını ve hata yanıtlarını
 * sahte servisle doğrular.
 */
@WebMvcTest(ChunkController.class)
class ChunkControllerTest {

    private static final String URL = "/api/workspaces/42/sources/302/chunks";

    @Autowired
    private MockMvcTester mockMvcTester;

    @MockitoBean
    private ChunkService chunkService;

    @Test
    void givenChunks_whenListChunks_thenReturnsChunkFieldsWithoutEmbeddingVector() {
        // Arrange
        ChunkResponse chunk = new ChunkResponse(9L, 2, "s. 31 · Kargo yeni adreste kurma", "içerik", 12, "bge-m3@1024");
        given(chunkService.listChunks(42L, 302L, 1, 2)).willReturn(new PageImpl<>(List.of(chunk),
                PageRequest.of(1, 2), 5));

        // Act
        MvcTestResult result = mockMvcTester.get().uri(URL + "?page=1&size=2").exchange();

        // Assert
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.content[0].chunkId").isEqualTo(9);
        assertThat(result).bodyJson().extractingPath("$.content[0].chunkNo").isEqualTo(2);
        assertThat(result).bodyJson().extractingPath("$.content[0].embeddingModel").isEqualTo("bge-m3@1024");
        assertThat(result).bodyJson().extractingPath("$.content[0].tokenCount").isEqualTo(12);
        assertThat(result).bodyJson().doesNotHavePath("$.content[0].embedding");
        assertThat(result).bodyJson().extractingPath("$.totalElements").isEqualTo(5);
        assertThat(result).bodyJson().extractingPath("$.totalPages").isEqualTo(3);
        assertThat(result).bodyJson().extractingPath("$.page").isEqualTo(1);
    }

    @Test
    void givenSizeAbove100_whenListChunks_thenReturns400() {
        // Act
        MvcTestResult result = mockMvcTester.get().uri(URL + "?size=101").exchange();

        // Assert
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.violations[0].field").isEqualTo("size");
    }

    @Test
    void givenUnknownSource_whenListChunks_thenReturns404SourceNotFound() {
        // Arrange
        given(chunkService.listChunks(42L, 302L, 0, 20)).willThrow(new NotFoundException("SOURCE_NOT_FOUND", "x"));

        // Act
        MvcTestResult result = mockMvcTester.get().uri(URL).exchange();

        // Assert
        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).bodyJson().extractingPath("$.errorCode").isEqualTo("SOURCE_NOT_FOUND");
    }

    @Test
    void givenMissingChunk_whenGetChunk_thenReturns404ProblemDetail() {
        // Arrange
        given(chunkService.getChunk(42L, 302L, 9L)).willThrow(new NotFoundException("CHUNK_NOT_FOUND", "x"));

        // Act
        MvcTestResult result = mockMvcTester.get().uri(URL + "/9").exchange();

        // Assert
        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).hasContentTypeCompatibleWith("application/problem+json");
        assertThat(result).bodyJson().extractingPath("$.errorCode").isEqualTo("CHUNK_NOT_FOUND");
    }

    @Test
    void givenChunk_whenGetChunk_thenReturnsContractFields() {
        // Arrange
        given(chunkService.getChunk(42L, 302L, 9L)).willReturn(
                new ChunkResponse(9L, 2, "Başlık", "içerik", 12, "bge-m3@1024"));

        // Act
        MvcTestResult result = mockMvcTester.get().uri(URL + "/9").exchange();

        // Assert
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.chunkId").isEqualTo(9);
        assertThat(result).bodyJson().extractingPath("$.chunkNo").isEqualTo(2);
        assertThat(result).bodyJson().extractingPath("$.heading").isEqualTo("Başlık");
        assertThat(result).bodyJson().extractingPath("$.tokenCount").isEqualTo(12);
        assertThat(result).bodyJson().doesNotHavePath("$.embedding");
    }
}
