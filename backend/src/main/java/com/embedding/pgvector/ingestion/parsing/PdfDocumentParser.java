package com.embedding.pgvector.ingestion.parsing;

import com.embedding.pgvector.source.domain.entity.SourceKind;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/**
 * PDF dosyasının her sayfasını, sayfa numarası ve ilk satırından oluşan başlıkla ayrı bir metin bölümüne çevirir.
 */
@Component
public class PdfDocumentParser implements SourceDocumentParser {

    private static final int MAX_HEADING_LINE_LENGTH = 80;
    private static final String ELLIPSIS = "…";
    private static final String LINE_BREAK = "\n";
    private static final String PARAGRAPH_BREAK = "\n\n";

    @Override
    public SourceKind kind() {
        return SourceKind.PDF;
    }

    @Override
    public List<TextSection> parse(byte[] content) {
        try (PDDocument document = Loader.loadPDF(content)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setParagraphEnd(LINE_BREAK);
            List<TextSection> sections = new ArrayList<>();
            for (int pageNo = 1; pageNo <= document.getNumberOfPages(); pageNo++) {
                stripper.setStartPage(pageNo);
                stripper.setEndPage(pageNo);
                pageSection(pageNo, stripper.getText(document), sections);
            }
            return sections;
        } catch (IOException | RuntimeException exception) {
            throw new DocumentUnreadableException("PDF document cannot be read", exception);
        }
    }

    private static void pageSection(int pageNo, String pageText, List<TextSection> sections) {
        List<String> lines = pageText.lines().map(String::strip).toList();
        int firstLineIndex = 0;
        while (firstLineIndex < lines.size() && lines.get(firstLineIndex).isEmpty()) {
            firstLineIndex++;
        }
        if (firstLineIndex == lines.size()) {
            return;
        }
        String firstLine = lines.get(firstLineIndex);
        String body = joinParagraphs(lines.subList(firstLineIndex + 1, lines.size()));
        String heading = "s. " + pageNo + " · " + shorten(firstLine);
        sections.add(new TextSection(heading, body.isEmpty() ? firstLine : body));
    }

    private static String joinParagraphs(List<String> lines) {
        List<String> paragraphs = new ArrayList<>();
        StringBuilder paragraph = new StringBuilder();
        for (String line : lines) {
            if (line.isEmpty()) {
                addParagraph(paragraph, paragraphs);
                continue;
            }
            if (!paragraph.isEmpty()) {
                paragraph.append(' ');
            }
            paragraph.append(line);
        }
        addParagraph(paragraph, paragraphs);
        return String.join(PARAGRAPH_BREAK, paragraphs);
    }

    private static void addParagraph(StringBuilder paragraph, List<String> paragraphs) {
        if (!paragraph.isEmpty()) {
            paragraphs.add(paragraph.toString());
            paragraph.setLength(0);
        }
    }

    private static String shorten(String line) {
        if (line.length() <= MAX_HEADING_LINE_LENGTH) {
            return line;
        }
        int cut = line.lastIndexOf(' ', MAX_HEADING_LINE_LENGTH);
        if (cut <= 0) {
            cut = MAX_HEADING_LINE_LENGTH;
        }
        return line.substring(0, cut).strip() + ELLIPSIS;
    }
}
