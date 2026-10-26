package com.embedding.pgvector.embedding.domain.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

/**
 * EmbedTextRequest'in metnin başındaki ve sonundaki boşlukları kırptığını, iç boşlukları ve boş değeri
 * koruduğunu doğrular.
 */
class EmbedTextRequestTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "'  izin ışığı kırmızı yanıyor  '|izin ışığı kırmızı yanıyor",
            "'   izin'|izin",
            "'izin   '|izin"})
    void givenTextWithSurroundingWhitespace_whenCreateRequest_thenStripsLeadingAndTrailingWhitespace(
            String input, String expected) {
        // Act
        EmbedTextRequest request = new EmbedTextRequest(input);

        // Assert
        assertThat(request.text()).isEqualTo(expected);
    }

    @Test
    void givenTextWithTabsAndNewlines_whenCreateRequest_thenStripsThem() {
        // Act
        EmbedTextRequest request = new EmbedTextRequest("\t\n izin \n");

        // Assert
        assertThat(request.text()).isEqualTo("izin");
    }

    @Test
    void givenTextWithInnerDoubleSpace_whenCreateRequest_thenKeepsInnerWhitespace() {
        // Act
        EmbedTextRequest request = new EmbedTextRequest(" izin  ışığı ");

        // Assert
        assertThat(request.text()).isEqualTo("izin  ışığı");
    }

    @Test
    void givenNullText_whenCreateRequest_thenTextStaysNull() {
        // Act
        EmbedTextRequest request = new EmbedTextRequest(null);

        // Assert
        assertThat(request.text()).isNull();
    }
}
