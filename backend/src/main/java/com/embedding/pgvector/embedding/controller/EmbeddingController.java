package com.embedding.pgvector.embedding.controller;

import com.embedding.pgvector.embedding.domain.dto.EmbedTextRequest;
import com.embedding.pgvector.embedding.domain.dto.EmbeddingResponse;
import com.embedding.pgvector.embedding.service.EmbeddingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bir metni yerel embedding modeliyle vektöre çeviren REST ucunu sunar.
 */
@RestController
@RequestMapping("/api/embeddings")
@Tag(name = "Embeddings",
        description = "Turns a text into an embedding vector with the local embedding model (Ollama)")
public class EmbeddingController {

    private static final String PROBLEM_JSON = "application/problem+json";

    private final EmbeddingService embeddingService;

    public EmbeddingController(EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    @Operation(operationId = "embedText", summary = "Embed a single text with the configured local embedding model")
    @ApiResponse(responseCode = "200", description = "Text embedded")
    @ApiResponse(responseCode = "400",
            description = "VALIDATION_FAILED: text is missing, blank or longer than 2000 characters; "
                    + "BAD_REQUEST: body is missing or not valid JSON",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR: unexpected server error",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "503",
            description = "EMBEDDING_UNAVAILABLE: Ollama is not reachable; "
                    + "EMBEDDING_MODEL_NOT_FOUND: the configured model is not pulled",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping
    public EmbeddingResponse embedText(@Valid @RequestBody EmbedTextRequest request) {
        return embeddingService.embedText(request);
    }
}
