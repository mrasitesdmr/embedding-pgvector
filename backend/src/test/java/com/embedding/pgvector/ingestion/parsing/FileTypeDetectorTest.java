package com.embedding.pgvector.ingestion.parsing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.embedding.pgvector.exception.UnsupportedMediaTypeException;
import com.embedding.pgvector.source.domain.entity.SourceKind;
import com.embedding.pgvector.support.SampleFiles;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * FileTypeDetector'ın dosya türünü uzantı ve içerikten bulmasını, desteklenmeyen tür ve kodlamaları
 * reddetmesini doğrular.
 */
class FileTypeDetectorTest {

    private final FileTypeDetector detector = new FileTypeDetector();

    @ParameterizedTest
    @CsvSource({"kilavuz.PDF,PDF", "notlar.md,MARKDOWN", "notlar.TXT,MARKDOWN", "a.markdown,MARKDOWN",
            "kodlar.csv,CSV", "C:\\yol\\kodlar.CSV,CSV", "klasor/notlar.md,MARKDOWN"})
    void givenSupportedExtensionAndMatchingContent_whenDetect_thenReturnsSourceKind(String fileName,
            SourceKind expected) {
        // Arrange
        byte[] content = expected == SourceKind.PDF
                ? "%PDF-1.7\nbody".getBytes(StandardCharsets.US_ASCII)
                : SampleFiles.utf8("merhaba");

        // Act
        SourceKind kind = detector.detect(fileName, content);

        // Assert
        assertThat(kind).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"rapor.docx,docx", "README,none", "bitis.,none", "arsiv.tar.gz,gz"})
    void givenUnsupportedOrMissingExtension_whenDetect_thenThrowsUnsupportedFileTypeNamingExtensionOnly(
            String fileName, String extension) {
        // Act
        Throwable thrown = catchThrowable(() -> detector.detect(fileName, SampleFiles.utf8("gizli-icerik")));

        // Assert
        assertThat(thrown).isInstanceOf(UnsupportedMediaTypeException.class)
                .extracting("errorCode").isEqualTo("UNSUPPORTED_FILE_TYPE");
        assertThat(thrown.getMessage()).contains(extension).doesNotContain("gizli-icerik");
    }

    @Test
    void givenNullFileName_whenDetect_thenThrowsUnsupportedFileType() {
        // Act
        Throwable thrown = catchThrowable(() -> detector.detect(null, SampleFiles.utf8("x")));

        // Assert
        assertThat(thrown).isInstanceOf(UnsupportedMediaTypeException.class)
                .extracting("errorCode").isEqualTo("UNSUPPORTED_FILE_TYPE");
        assertThat(thrown.getMessage()).contains("none");
    }

    @Test
    void givenHiddenFileNameWithOnlyExtension_whenDetect_thenUsesExtension() {
        // Act
        SourceKind kind = detector.detect(".csv", SampleFiles.utf8("a,b"));

        // Assert
        assertThat(kind).isEqualTo(SourceKind.CSV);
    }

    @Test
    void givenContentOfOnlyBom_whenDecodeText_thenEmptyString() {
        // Act
        String text = detector.decodeText(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});

        // Assert
        assertThat(text).isEmpty();
    }

    @Test
    void givenPdfExtensionWithoutPdfHeader_whenDetect_thenThrowsUnsupportedFileType() {
        // Act
        Throwable thrown = catchThrowable(() -> detector.detect("fake.pdf", SampleFiles.utf8("merhaba")));

        // Assert
        assertThat(thrown).isInstanceOf(UnsupportedMediaTypeException.class)
                .extracting("errorCode").isEqualTo("UNSUPPORTED_FILE_TYPE");
    }

    @Test
    void givenPdfSignatureWithinFirst1024Bytes_whenDetect_thenAcceptedButBeyondIsRejected() {
        // Arrange
        byte[] within = new byte[1100];
        Arrays.fill(within, (byte) ' ');
        System.arraycopy("%PDF-".getBytes(StandardCharsets.US_ASCII), 0, within, 1019, 5);
        byte[] beyond = new byte[1100];
        Arrays.fill(beyond, (byte) ' ');
        System.arraycopy("%PDF-".getBytes(StandardCharsets.US_ASCII), 0, beyond, 1020, 5);

        // Act
        SourceKind accepted = detector.detect("a.pdf", within);
        Throwable rejected = catchThrowable(() -> detector.detect("a.pdf", beyond));

        // Assert
        assertThat(accepted).isEqualTo(SourceKind.PDF);
        assertThat(rejected).isInstanceOf(UnsupportedMediaTypeException.class);
    }

    @Test
    void givenWindows1254Text_whenDetect_thenThrowsUnsupportedFileEncoding() {
        // Arrange
        byte[] content = SampleFiles.windows1254("şifre");

        // Act
        Throwable thrown = catchThrowable(() -> detector.detect("notlar.txt", content));

        // Assert
        assertThat(content).contains((byte) 0xFE);
        assertThat(thrown).isInstanceOf(UnsupportedMediaTypeException.class)
                .extracting("errorCode").isEqualTo("UNSUPPORTED_FILE_ENCODING");
    }

    @Test
    void givenTextWithNulByte_whenDetect_thenThrowsUnsupportedFileType() {
        // Act
        Throwable thrown = catchThrowable(() -> detector.detect("kodlar.csv", SampleFiles.utf8("a\u0000b")));

        // Assert
        assertThat(thrown).isInstanceOf(UnsupportedMediaTypeException.class)
                .extracting("errorCode").isEqualTo("UNSUPPORTED_FILE_TYPE");
    }

    @Test
    void givenUtf8Bom_whenDecodeText_thenBomIsDropped() {
        // Act
        String text = detector.decodeText(SampleFiles.utf8WithBom("Şifre"));

        // Assert
        assertThat(text).isEqualTo("Şifre");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "a"})
    void givenShortContentWithoutBom_whenDecodeText_thenReturnsContent(String content) {
        // Act
        String text = detector.decodeText(SampleFiles.utf8(content));

        // Assert
        assertThat(text).isEqualTo(content);
    }
}
