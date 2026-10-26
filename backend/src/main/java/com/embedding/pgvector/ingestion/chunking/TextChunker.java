package com.embedding.pgvector.ingestion.chunking;

import com.embedding.pgvector.config.IngestionProperties;
import com.embedding.pgvector.ingestion.parsing.TextSection;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Belge bölümlerini paragraf ve cümle sınırlarından bölerek hedef boyutta, birbiriyle örtüşen parçalara ayırır.
 */
@Component
public class TextChunker {

    private static final Pattern INLINE_WHITESPACE = Pattern.compile("[\\h\\x0B\\f]+");
    private static final Pattern SENTENCE_BOUNDARY = Pattern.compile("(?<=[.!?…])\\s+");
    private static final String PARAGRAPH_SEPARATOR = "\n\n";
    private static final String LINE_SEPARATOR = "\n";
    private static final String WORD_SEPARATOR = " ";

    private final IngestionProperties properties;

    public TextChunker(IngestionProperties properties) {
        this.properties = properties;
    }

    public List<ChunkDraft> chunk(List<TextSection> sections) {
        List<ChunkDraft> drafts = new ArrayList<>();
        for (TextSection section : sections) {
            List<Unit> units = units(paragraphs(section.body()));
            for (String content : assemble(units)) {
                drafts.add(draft(drafts.size(), section.heading(), content));
            }
        }
        return drafts;
    }

    private ChunkDraft draft(int chunkNo, @Nullable String heading, String content) {
        int inputLength = ChunkDraft.embeddingInput(heading, content).length();
        int tokenCount = (inputLength + properties.charsPerToken() - 1) / properties.charsPerToken();
        return new ChunkDraft(chunkNo, heading, content, tokenCount);
    }

    private static List<String> paragraphs(String body) {
        List<String> paragraphs = new ArrayList<>();
        StringBuilder paragraph = new StringBuilder();
        for (String line : body.lines().toList()) {
            String normalized = INLINE_WHITESPACE.matcher(line).replaceAll(" ").strip();
            if (normalized.isEmpty()) {
                flushParagraph(paragraph, paragraphs);
                continue;
            }
            if (!paragraph.isEmpty()) {
                paragraph.append(LINE_SEPARATOR);
            }
            paragraph.append(normalized);
        }
        flushParagraph(paragraph, paragraphs);
        return paragraphs;
    }

    private static void flushParagraph(StringBuilder paragraph, List<String> paragraphs) {
        if (!paragraph.isEmpty()) {
            paragraphs.add(paragraph.toString());
            paragraph.setLength(0);
        }
    }

    private List<Unit> units(List<String> paragraphs) {
        List<Unit> units = new ArrayList<>();
        for (String paragraph : paragraphs) {
            String paragraphSeparator = units.isEmpty() ? "" : PARAGRAPH_SEPARATOR;
            if (paragraph.length() <= properties.targetChunkChars()) {
                units.add(new Unit(paragraph, paragraphSeparator));
                continue;
            }
            String separator = paragraphSeparator;
            Matcher boundary = SENTENCE_BOUNDARY.matcher(paragraph);
            int start = 0;
            while (boundary.find()) {
                addSentence(paragraph.substring(start, boundary.start()), separator, units);
                separator = whitespaceSeparator(boundary.group());
                start = boundary.end();
            }
            addSentence(paragraph.substring(start), separator, units);
        }
        return units;
    }

    private void addSentence(String sentence, String separator, List<Unit> units) {
        String remaining = sentence.strip();
        String nextSeparator = separator;
        while (remaining.length() > properties.maxChunkChars()) {
            int cut = hardSplitIndex(remaining);
            units.add(new Unit(remaining.substring(0, cut).strip(), nextSeparator));
            nextSeparator = whitespaceSeparator(remaining.substring(cut, Math.min(cut + 1, remaining.length())));
            remaining = remaining.substring(cut).strip();
        }
        if (!remaining.isEmpty()) {
            units.add(new Unit(remaining, nextSeparator));
        }
    }

    private int hardSplitIndex(String text) {
        int max = properties.maxChunkChars();
        for (int index = max; index > 0; index--) {
            if (Character.isWhitespace(text.charAt(index))) {
                return index;
            }
        }
        return Character.isLowSurrogate(text.charAt(max)) ? max - 1 : max;
    }

    private static String whitespaceSeparator(String whitespace) {
        return whitespace.indexOf('\n') >= 0 ? LINE_SEPARATOR : WORD_SEPARATOR;
    }

    private List<String> assemble(List<Unit> units) {
        List<String> contents = new ArrayList<>();
        List<Unit> current = new ArrayList<>();
        for (Unit unit : units) {
            if (current.isEmpty() || length(current) + unit.separator().length() + unit.text().length()
                    <= properties.targetChunkChars()) {
                current.add(unit);
                continue;
            }
            contents.add(join(current));
            List<Unit> next = new ArrayList<>(overlap(current));
            while (!next.isEmpty() && length(next) + unit.separator().length() + unit.text().length()
                    > properties.maxChunkChars()) {
                next.removeFirst();
            }
            next.add(unit);
            current = next;
        }
        if (!current.isEmpty()) {
            contents.add(join(current));
        }
        return contents;
    }

    private List<Unit> overlap(List<Unit> previous) {
        List<Unit> overlap = new ArrayList<>();
        int total = 0;
        for (int index = previous.size() - 1; index > 0; index--) {
            Unit unit = previous.get(index);
            int added = unit.text().length() + (overlap.isEmpty() ? 0 : overlap.getFirst().separator().length());
            if (total + added > properties.overlapChars()) {
                break;
            }
            overlap.addFirst(unit);
            total += added;
        }
        return overlap;
    }

    private static int length(List<Unit> units) {
        int length = 0;
        for (int index = 0; index < units.size(); index++) {
            Unit unit = units.get(index);
            length += unit.text().length() + (index == 0 ? 0 : unit.separator().length());
        }
        return length;
    }

    private static String join(List<Unit> units) {
        StringBuilder content = new StringBuilder();
        for (Unit unit : units) {
            if (!content.isEmpty()) {
                content.append(unit.separator().isEmpty() ? WORD_SEPARATOR : unit.separator());
            }
            content.append(unit.text());
        }
        return content.toString();
    }

    private record Unit(String text, String separator) {
    }
}
