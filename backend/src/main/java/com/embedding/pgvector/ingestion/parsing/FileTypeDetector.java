package com.embedding.pgvector.ingestion.parsing;

import com.embedding.pgvector.exception.UnsupportedMediaTypeException;
import com.embedding.pgvector.source.domain.entity.SourceKind;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Yüklenen dosyanın kaynak türünü uzantısından ve içeriğinden belirler ve metin dosyalarını UTF-8 olarak çözer;
 * uygun olmayan dosyayı 415 hatasıyla reddeder.
 */
@Component
public class FileTypeDetector {

    private static final Map<String, SourceKind> KINDS_BY_EXTENSION = Map.of(
            "pdf", SourceKind.PDF,
            "txt", SourceKind.MARKDOWN,
            "md", SourceKind.MARKDOWN,
            "markdown", SourceKind.MARKDOWN,
            "csv", SourceKind.CSV);
    private static final byte[] PDF_SIGNATURE = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final int PDF_SIGNATURE_SEARCH_BYTES = 1024;
    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final String NO_EXTENSION = "none";

    public SourceKind detect(@Nullable String fileName, byte[] content) {
        String extension = extension(fileName);
        SourceKind kind = KINDS_BY_EXTENSION.get(extension);
        if (kind == null) {
            throw unsupportedType(extension);
        }
        if (kind == SourceKind.PDF) {
            if (!containsPdfSignature(content)) {
                throw unsupportedType(extension);
            }
            return kind;
        }
        if (containsNulByte(content)) {
            throw unsupportedType(extension);
        }
        decodeText(content);
        return kind;
    }

    public String decodeText(byte[] content) {
        int offset = startsWithBom(content) ? UTF8_BOM.length : 0;
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return decoder.decode(ByteBuffer.wrap(content, offset, content.length - offset)).toString();
        } catch (CharacterCodingException exception) {
            throw new UnsupportedMediaTypeException("UNSUPPORTED_FILE_ENCODING", "File is not valid UTF-8");
        }
    }

    private static String extension(@Nullable String fileName) {
        if (fileName == null) {
            return NO_EXTENSION;
        }
        String baseName = fileName.substring(Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\')) + 1);
        int dot = baseName.lastIndexOf('.');
        if (dot < 0 || dot == baseName.length() - 1) {
            return NO_EXTENSION;
        }
        return baseName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static boolean containsPdfSignature(byte[] content) {
        int searchEnd = Math.min(content.length, PDF_SIGNATURE_SEARCH_BYTES) - PDF_SIGNATURE.length;
        for (int start = 0; start <= searchEnd; start++) {
            if (matchesAt(content, start, PDF_SIGNATURE)) {
                return true;
            }
        }
        return false;
    }

    private static boolean startsWithBom(byte[] content) {
        return content.length >= UTF8_BOM.length && matchesAt(content, 0, UTF8_BOM);
    }

    private static boolean matchesAt(byte[] content, int start, byte[] expected) {
        for (int index = 0; index < expected.length; index++) {
            if (content[start + index] != expected[index]) {
                return false;
            }
        }
        return true;
    }

    private static boolean containsNulByte(byte[] content) {
        for (byte value : content) {
            if (value == 0) {
                return true;
            }
        }
        return false;
    }

    private static UnsupportedMediaTypeException unsupportedType(String extension) {
        return new UnsupportedMediaTypeException("UNSUPPORTED_FILE_TYPE", "Unsupported file type: " + extension);
    }
}
