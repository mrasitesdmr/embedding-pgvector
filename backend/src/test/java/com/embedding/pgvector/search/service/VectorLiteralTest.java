package com.embedding.pgvector.search.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * VectorLiteral'in sayı dizisini pgvector'ün beklediği köşeli parantezli, virgülle ayrılmış metne
 * çevirdiğini doğrular.
 */
class VectorLiteralTest {

    @Test
    void givenFloats_whenFormat_thenBracketedCommaSeparatedWithoutSpaces() {
        // Act
        String literal = VectorLiteral.format(new float[] {0.1f, -0.2f, 3f});

        // Assert
        assertThat(literal).isEqualTo("[0.1,-0.2,3.0]");
    }

    @Test
    void givenEmptyVector_whenFormat_thenEmptyBrackets() {
        // Assert
        assertThat(VectorLiteral.format(new float[0])).isEqualTo("[]");
    }
}
