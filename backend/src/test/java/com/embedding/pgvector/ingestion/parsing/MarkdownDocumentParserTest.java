package com.embedding.pgvector.ingestion.parsing;

import static org.assertj.core.api.Assertions.assertThat;

import com.embedding.pgvector.support.SampleFiles;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * MarkdownDocumentParser'ın başlıklara göre bölüm çıkarmasını ve kod bloğu içindeki satırları başlık
 * saymamasını doğrular.
 */
class MarkdownDocumentParserTest {

    private final MarkdownDocumentParser parser = new MarkdownDocumentParser(new FileTypeDetector());

    @Test
    void givenUtf8BomBeforeHeading_whenParse_thenHeadingHasNoBom() {
        // Act
        List<TextSection> sections = parser.parse(SampleFiles.utf8WithBom("# Başlık\nmetin"));

        // Assert
        assertThat(sections).containsExactly(new TextSection("Başlık", "metin"));
    }

    @Test
    void givenMarkdownWithHeadings_whenParse_thenEachHeadingStartsSection() {
        // Act
        List<TextSection> sections = parser.parse(SampleFiles.utf8("giriş\n# Kurulum\nA\n## Wi-Fi\nB"));

        // Assert
        assertThat(sections).containsExactly(
                new TextSection(null, "giriş"),
                new TextSection("Kurulum", "A"),
                new TextSection("Wi-Fi", "B"));
    }

    @Test
    void givenHashLineInsideFencedCode_whenParse_thenNotTreatedAsHeading() {
        // Act
        List<TextSection> sections = parser.parse(SampleFiles.utf8("# Komutlar\n```\n# yorum\nls\n```"));

        // Assert
        assertThat(sections).hasSize(1);
        assertThat(sections.getFirst().heading()).isEqualTo("Komutlar");
        assertThat(sections.getFirst().body()).contains("# yorum").contains("ls");
    }

    @Test
    void givenHashLineInsideTildeFence_whenParse_thenNotTreatedAsHeading() {
        // Act
        List<TextSection> sections = parser.parse(SampleFiles.utf8("# A\n~~~\n# not heading\n~~~\n# B\nx"));

        // Assert
        assertThat(sections).extracting(TextSection::heading).containsExactly("A", "B");
    }

    @Test
    void givenClosingHashesAndEmptySection_whenParse_thenTrimsHashesAndSkipsEmptySection() {
        // Act
        List<TextSection> sections = parser.parse(SampleFiles.utf8("# Boş bölüm ##\n# Dolu\nmetin"));

        // Assert
        assertThat(sections).containsExactly(new TextSection("Dolu", "metin"));
    }

    @Test
    void givenHashWithoutSpace_whenParse_thenNotAHeading() {
        // Act
        List<TextSection> sections = parser.parse(SampleFiles.utf8("#etiket metin"));

        // Assert
        assertThat(sections).containsExactly(new TextSection(null, "#etiket metin"));
    }

    @Test
    void givenEmptyContent_whenParse_thenNoSections() {
        // Act
        List<TextSection> sections = parser.parse(SampleFiles.utf8("  \n\n"));

        // Assert
        assertThat(sections).isEmpty();
    }

    @Test
    void givenSeveralLinesInsideBacktickFence_whenParse_thenHashLinesAfterFirstFenceLineStayInBody() {
        // Act
        List<TextSection> sections = parser.parse(SampleFiles.utf8("# Komutlar\n```\nls\n# yorum\npwd\n```\nson"));

        // Assert
        assertThat(sections).hasSize(1);
        assertThat(sections.getFirst().body()).isEqualTo("```\nls\n# yorum\npwd\n```\nson");
    }

    @Test
    void givenHeadingAfterClosedFence_whenParse_thenNewSectionStarts() {
        // Act
        List<TextSection> sections = parser.parse(SampleFiles.utf8("# A\n```\nx\n```\n# B\ny"));

        // Assert
        assertThat(sections).extracting(TextSection::heading).containsExactly("A", "B");
    }
}
