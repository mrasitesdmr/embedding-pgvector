package com.embedding.pgvector.ingestion.parsing;

import static org.assertj.core.api.Assertions.assertThat;

import com.embedding.pgvector.support.SampleFiles;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * CsvDocumentParser'ın CSV satırlarını bölümlere çevirmesini, ayraç tespitini ve tırnak ile boş satır
 * kurallarını doğrular.
 */
class CsvDocumentParserTest {

    private final CsvDocumentParser parser = new CsvDocumentParser(new FileTypeDetector());

    @Test
    void givenHeaderAndQuotedRow_whenParse_thenRowBecomesSectionWithColumnNames() {
        // Arrange
        String csv = "Kod,Açıklama,Çözüm\nIZN-99,PPPoE kimlik doğrulama hatası,\"İzin arayüzünde, PPPoE bilgisini gir\"";

        // Act
        List<TextSection> sections = parser.parse(SampleFiles.utf8(csv));

        // Assert
        assertThat(sections).containsExactly(new TextSection("IZN-99",
                "Kod: IZN-99\nAçıklama: PPPoE kimlik doğrulama hatası\nÇözüm: İzin arayüzünde, PPPoE bilgisini gir"));
    }

    @Test
    void givenSemicolonDelimitedCsv_whenParse_thenDetectsDelimiter() {
        // Act
        List<TextSection> sections = parser.parse(SampleFiles.utf8("Kod;Açıklama\nTS-104;Fiber sinyali zayıf"));

        // Assert
        assertThat(sections).containsExactly(new TextSection("TS-104", "Kod: TS-104\nAçıklama: Fiber sinyali zayıf"));
    }

    @Test
    void givenTabDelimitedCsv_whenParse_thenDetectsDelimiter() {
        // Act
        List<TextSection> sections = parser.parse(SampleFiles.utf8("Kod\tAçıklama\nTS-1\tYavaş, ama çalışıyor"));

        // Assert
        assertThat(sections).containsExactly(new TextSection("TS-1", "Kod: TS-1\nAçıklama: Yavaş, ama çalışıyor"));
    }

    @Test
    void givenEscapedQuotesMultilineFieldAndBlankLines_whenParse_thenParsesFieldsAndSkipsBlanks() {
        // Arrange
        String csv = "Kod,Not,Çözüm\n\nA1,\"a \"\"b\"\" c\",\"satır1\nsatır2\"\n\nB2,,boş-not-yok\n";

        // Act
        List<TextSection> sections = parser.parse(SampleFiles.utf8(csv));

        // Assert
        assertThat(sections).containsExactly(
                new TextSection("A1", "Kod: A1\nNot: a \"b\" c\nÇözüm: satır1\nsatır2"),
                new TextSection("B2", "Kod: B2\nÇözüm: boş-not-yok"));
    }

    @Test
    void givenRowWithMoreValuesThanColumns_whenParse_thenExtraValuesAppendedWithoutName() {
        // Act
        List<TextSection> sections = parser.parse(SampleFiles.utf8("Kod,Not\nX,y,fazla"));

        // Assert
        assertThat(sections).containsExactly(new TextSection("X", "Kod: X\nNot: y\nfazla"));
    }

    @Test
    void givenEmptyFirstValue_whenParse_thenHeadingIsNull() {
        // Act
        List<TextSection> sections = parser.parse(SampleFiles.utf8("Kod,Not\n,sadece not"));

        // Assert
        assertThat(sections).containsExactly(new TextSection(null, "Not: sadece not"));
    }

    @Test
    void givenCsvWithOnlyHeader_whenParse_thenReturnsNoSections() {
        // Act
        List<TextSection> sections = parser.parse(SampleFiles.utf8("Kod,Açıklama\n"));

        // Assert
        assertThat(sections).isEmpty();
    }

    @Test
    void givenRowOfOnlySeparators_whenParse_thenRowIsSkipped() {
        // Act
        List<TextSection> sections = parser.parse(SampleFiles.utf8("Kod,Not\n,\nA1,x"));

        // Assert
        assertThat(sections).containsExactly(new TextSection("A1", "Kod: A1\nNot: x"));
    }

    @Test
    void givenWindowsLineEndings_whenParse_thenRowsSplitWithoutCarriageReturns() {
        // Act
        List<TextSection> sections = parser.parse(SampleFiles.utf8("Kod,Not\r\nA1,x\r\nB2,y\r\n"));

        // Assert
        assertThat(sections).containsExactly(new TextSection("A1", "Kod: A1\nNot: x"),
                new TextSection("B2", "Kod: B2\nNot: y"));
    }

    @Test
    void givenDelimiterInsideQuotedHeaderName_whenParse_thenQuotedSeparatorsAreNotCounted() {
        // Act
        List<TextSection> sections = parser.parse(SampleFiles.utf8("\"A;B;C\",Not\nv1,v2"));

        // Assert
        assertThat(sections).containsExactly(new TextSection("v1", "A;B;C: v1\nNot: v2"));
    }

    @Test
    void givenEqualCountOfCommaAndSemicolonInHeader_whenParse_thenCommaWins() {
        // Act
        List<TextSection> sections = parser.parse(SampleFiles.utf8("a,b;c\nx,y;z"));

        // Assert
        assertThat(sections).containsExactly(new TextSection("x", "a: x\nb;c: y;z"));
    }

    @Test
    void givenMoreSemicolonsThanCommas_whenParse_thenSemicolonWins() {
        // Act
        List<TextSection> sections = parser.parse(SampleFiles.utf8("a;b;c,d\nx;y;z,w"));

        // Assert
        assertThat(sections).containsExactly(new TextSection("x", "a: x\nb: y\nc,d: z,w"));
    }

    @Test
    void givenRowWithoutTrailingNewline_whenParse_thenLastRowIsKept() {
        // Act
        List<TextSection> sections = parser.parse(SampleFiles.utf8("Kod,Not\nA1,x"));

        // Assert
        assertThat(sections).hasSize(1);
    }
}
