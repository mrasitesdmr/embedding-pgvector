package com.embedding.pgvector.embedding.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * Vektöre çevrilecek metnin istek gövdesini taşır; metnin başındaki ve sonundaki boşlukları doğrulamadan önce kırpar.
 */
public record EmbedTextRequest(
        @Schema(description = "Text to embed. Leading and trailing whitespace is stripped before validation and "
                + "embedding; whitespace inside the text is kept. After stripping it must not be empty and must be "
                + "at most 2000 characters (UTF-16 code units, same as JavaScript String.length).",
                example = "Yıllık izin kaç gün?",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = TEXT_BLANK_MESSAGE)
        @Size(max = TEXT_MAX_LENGTH, message = TEXT_TOO_LONG_MESSAGE)
        @Nullable String text) {

    public static final int TEXT_MAX_LENGTH = 2000;
    public static final String TEXT_BLANK_MESSAGE = "must not be blank";
    public static final String TEXT_TOO_LONG_MESSAGE = "size must be at most 2000 characters";

    public EmbedTextRequest {
        text = text == null ? null : text.strip();
    }
}
