package com.embedding.pgvector.ingestion.parsing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.embedding.pgvector.support.SampleFiles;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * PdfDocumentParser'ın PDF sayfalarını bölümlere çevirmesini, sayfa başlığı üretimini ve bozuk dosya
 * hatasını doğrular.
 */
class PdfDocumentParserTest {

    private final PdfDocumentParser parser = new PdfDocumentParser();

    @Test
    void givenThreePagePdfWithBlankSecondPage_whenParse_thenSectionsForNonBlankPagesWithPageHeadings() {
        // Arrange
        byte[] pdf = SampleFiles.pdf("Kargo yeni adreste kurma\nPON isigi yanip sonebilir.", "",
                "Fabrika ayarlarina donme\nRESET dugmesine bas.");

        // Act
        List<TextSection> sections = parser.parse(pdf);

        // Assert
        assertThat(sections).containsExactly(
                new TextSection("s. 1 · Kargo yeni adreste kurma", "PON isigi yanip sonebilir."),
                new TextSection("s. 3 · Fabrika ayarlarina donme", "RESET dugmesine bas."));
    }

    @Test
    void givenPageWithOnlyOneLine_whenParse_thenBodyIsThatLine() {
        // Act
        List<TextSection> sections = parser.parse(SampleFiles.pdf("Tek satir"));

        // Assert
        assertThat(sections).containsExactly(new TextSection("s. 1 · Tek satir", "Tek satir"));
    }

    @Test
    void givenVeryLongFirstLine_whenParse_thenHeadingCutAtWhitespaceBefore80WithEllipsis() {
        // Arrange
        String firstLine = "kelime ".repeat(14).strip();
        String longLine = firstLine + " " + "ek ".repeat(20).strip();

        // Act
        List<TextSection> sections = parser.parse(SampleFiles.pdf(longLine + "\ngovde"));

        // Assert
        String heading = sections.getFirst().heading();
        assertThat(heading).startsWith("s. 1 · ").endsWith("…");
        assertThat(heading.length()).isLessThanOrEqualTo("s. 1 · ".length() + 81);
        assertThat(heading).doesNotContain("ek ek");
    }

    @Test
    void givenPdfWithOnlyBlankPages_whenParse_thenReturnsNoSections() {
        // Act
        List<TextSection> sections = parser.parse(SampleFiles.pdf("", ""));

        // Assert
        assertThat(sections).isEmpty();
    }

    @Test
    void givenCorruptPdf_whenParse_thenThrowsDocumentUnreadable() {
        // Arrange
        byte[] random = new byte[200];
        new Random(7).nextBytes(random);
        byte[] corrupt = new byte[9 + random.length];
        System.arraycopy("%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII), 0, corrupt, 0, 9);
        System.arraycopy(random, 0, corrupt, 9, random.length);

        // Act
        Throwable thrown = catchThrowable(() -> parser.parse(corrupt));

        // Assert
        assertThat(thrown).isInstanceOf(DocumentUnreadableException.class);
    }

    @Test
    void givenFirstLineOfExactly80Characters_whenParse_thenHeadingKeepsWholeLine() {
        // Arrange
        String line = "abcdefghi ".repeat(7) + "abcdefghij";

        // Act
        List<TextSection> sections = parser.parse(SampleFiles.pdf(line + "\ngovde"));

        // Assert
        assertThat(line).hasSize(80);
        assertThat(sections.getFirst().heading()).isEqualTo("s. 1 · " + line);
    }

    @Test
    void givenFirstLineOf90Characters_whenParse_thenHeadingCutAtLastSpaceBefore80WithEllipsis() {
        // Arrange
        String line = "abcdefghi ".repeat(8) + "abcdefghij";

        // Act
        List<TextSection> sections = parser.parse(SampleFiles.pdf(line + "\ngovde"));

        // Assert
        assertThat(line).hasSize(90);
        assertThat(sections.getFirst().heading()).isEqualTo("s. 1 · " + "abcdefghi ".repeat(7) + "abcdefghi…");
    }

    @Test
    void givenFirstLineWithoutSpaceLongerThan80_whenParse_thenHeadingCutAt80WithEllipsis() {
        // Act
        List<TextSection> sections = parser.parse(SampleFiles.pdf("x".repeat(100) + "\ngovde"));

        // Assert
        assertThat(sections.getFirst().heading()).isEqualTo("s. 1 · " + "x".repeat(80) + "…");
    }

    @Test
    void givenBodyLinesOnPage_whenParse_thenConsecutiveLinesJoinedWithSpace() {
        // Act
        List<TextSection> sections = parser.parse(SampleFiles.pdf("Baslik\nsatir bir\nsatir iki"));

        // Assert
        assertThat(sections.getFirst().body()).isEqualTo("satir bir satir iki");
    }
}
