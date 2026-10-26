package com.embedding.pgvector.ingestion.parsing;

import com.embedding.pgvector.source.domain.entity.SourceKind;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * CSV dosyasındaki her kaydı, başlık satırındaki sütun adlarıyla yazılmış ayrı bir metin bölümüne çevirir;
 * alan ayırıcısını dosyanın ilk satırından kendisi bulur.
 */
@Component
public class CsvDocumentParser implements SourceDocumentParser {

    private static final char QUOTE = '"';
    private static final char[] DELIMITERS = {',', ';', '\t'};

    private final FileTypeDetector fileTypeDetector;

    public CsvDocumentParser(FileTypeDetector fileTypeDetector) {
        this.fileTypeDetector = fileTypeDetector;
    }

    @Override
    public SourceKind kind() {
        return SourceKind.CSV;
    }

    @Override
    public List<TextSection> parse(byte[] content) {
        String text = fileTypeDetector.decodeText(content);
        List<List<String>> records = parseRecords(text, detectDelimiter(text));
        List<TextSection> sections = new ArrayList<>();
        @Nullable List<String> columnNames = null;
        for (List<String> record : records) {
            if (isBlank(record)) {
                continue;
            }
            if (columnNames == null) {
                columnNames = record.stream().map(String::strip).toList();
                continue;
            }
            toSection(columnNames, record, sections);
        }
        return sections;
    }

    private static void toSection(List<String> columnNames, List<String> record, List<TextSection> sections) {
        List<String> lines = new ArrayList<>();
        for (int index = 0; index < record.size(); index++) {
            String value = record.get(index).strip();
            if (value.isEmpty()) {
                continue;
            }
            String columnName = index < columnNames.size() ? columnNames.get(index) : "";
            lines.add(columnName.isEmpty() ? value : columnName + ": " + value);
        }
        if (lines.isEmpty()) {
            return;
        }
        String heading = record.getFirst().strip();
        sections.add(new TextSection(heading.isEmpty() ? null : heading, String.join("\n", lines)));
    }

    private static boolean isBlank(List<String> record) {
        return record.stream().allMatch(String::isBlank);
    }

    static char detectDelimiter(String text) {
        int[] counts = new int[DELIMITERS.length];
        boolean quoted = false;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == QUOTE) {
                quoted = !quoted;
            } else if (!quoted && (character == '\n' || character == '\r')) {
                break;
            } else if (!quoted) {
                for (int delimiter = 0; delimiter < DELIMITERS.length; delimiter++) {
                    if (character == DELIMITERS[delimiter]) {
                        counts[delimiter]++;
                    }
                }
            }
        }
        int best = 0;
        for (int delimiter = 1; delimiter < DELIMITERS.length; delimiter++) {
            if (counts[delimiter] > counts[best]) {
                best = delimiter;
            }
        }
        return DELIMITERS[best];
    }

    private static List<List<String>> parseRecords(String text, char delimiter) {
        List<List<String>> records = new ArrayList<>();
        List<String> record = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        int index = 0;
        while (index < text.length()) {
            char character = text.charAt(index);
            if (quoted) {
                if (character == QUOTE && index + 1 < text.length() && text.charAt(index + 1) == QUOTE) {
                    field.append(QUOTE);
                    index++;
                } else if (character == QUOTE) {
                    quoted = false;
                } else {
                    field.append(character);
                }
            } else if (character == QUOTE) {
                quoted = true;
            } else if (character == delimiter) {
                record.add(field.toString());
                field.setLength(0);
            } else if (character == '\r' || character == '\n') {
                if (character == '\r' && index + 1 < text.length() && text.charAt(index + 1) == '\n') {
                    index++;
                }
                record.add(field.toString());
                field.setLength(0);
                records.add(record);
                record = new ArrayList<>();
            } else {
                field.append(character);
            }
            index++;
        }
        if (field.length() > 0 || !record.isEmpty()) {
            record.add(field.toString());
            records.add(record);
        }
        return records;
    }
}
