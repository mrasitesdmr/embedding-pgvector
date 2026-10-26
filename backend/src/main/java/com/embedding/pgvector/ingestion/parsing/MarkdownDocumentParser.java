package com.embedding.pgvector.ingestion.parsing;

import com.embedding.pgvector.source.domain.entity.SourceKind;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Markdown ya da düz metin dosyasını başlıklarına göre metin bölümlerine ayırır; kod blokları içindeki satırları
 * başlık saymaz.
 */
@Component
public class MarkdownDocumentParser implements SourceDocumentParser {

    private static final Pattern HEADING = Pattern.compile("^ {0,3}#{1,6}\\s+(.+?)\\s*#*\\s*$");
    private static final String BACKTICK_FENCE = "```";
    private static final String TILDE_FENCE = "~~~";

    private final FileTypeDetector fileTypeDetector;

    public MarkdownDocumentParser(FileTypeDetector fileTypeDetector) {
        this.fileTypeDetector = fileTypeDetector;
    }

    @Override
    public SourceKind kind() {
        return SourceKind.MARKDOWN;
    }

    @Override
    public List<TextSection> parse(byte[] content) {
        List<TextSection> sections = new ArrayList<>();
        @Nullable String heading = null;
        StringBuilder body = new StringBuilder();
        @Nullable String openFence = null;
        for (String line : fileTypeDetector.decodeText(content).lines().toList()) {
            String stripped = line.strip();
            if (openFence == null) {
                Matcher matcher = HEADING.matcher(line);
                if (matcher.matches()) {
                    addSection(heading, body, sections);
                    heading = matcher.group(1);
                    body.setLength(0);
                    continue;
                }
                openFence = openingFence(stripped);
            } else if (stripped.startsWith(openFence)) {
                openFence = null;
            }
            body.append(line).append('\n');
        }
        addSection(heading, body, sections);
        return sections;
    }

    private static @Nullable String openingFence(String strippedLine) {
        if (strippedLine.startsWith(BACKTICK_FENCE)) {
            return BACKTICK_FENCE;
        }
        if (strippedLine.startsWith(TILDE_FENCE)) {
            return TILDE_FENCE;
        }
        return null;
    }

    private static void addSection(@Nullable String heading, StringBuilder body, List<TextSection> sections) {
        String text = body.toString().strip();
        if (!text.isEmpty()) {
            sections.add(new TextSection(heading, text));
        }
    }
}
