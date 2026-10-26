package com.embedding.pgvector.ingestion.chunking;

import static org.assertj.core.api.Assertions.assertThat;

import com.embedding.pgvector.config.IngestionProperties;
import com.embedding.pgvector.ingestion.parsing.TextSection;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * TextChunker'ın bölümleri parçalara ayırma kurallarını doğrular: boyut sınırları, cümle örtüşmesi,
 * paragraf sınırı ve boşluk düzeni.
 */
class TextChunkerTest {

    private final TextChunker chunker = new TextChunker(new IngestionProperties(96, 1600, 2000, 200, 4, 2, 100));

    private static String sentence(int index) {
        String prefix = "Cumle %02d: ".formatted(index);
        return prefix + "a".repeat(100 - prefix.length() - 1) + ".";
    }

    @Test
    void givenShortSection_whenChunk_thenSingleChunkWithEstimatedTokens() {
        // Arrange
        String body = "x".repeat(205);

        // Act
        List<ChunkDraft> drafts = chunker.chunk(List.of(new TextSection("Hesabım kilitlendi", body)));

        // Assert
        assertThat(drafts).hasSize(1);
        assertThat(drafts.getFirst().chunkNo()).isZero();
        assertThat(drafts.getFirst().content()).isEqualTo(body);
        assertThat(drafts.getFirst().tokenCount()).isEqualTo(57);
    }

    @Test
    void givenSectionWithoutHeading_whenChunk_thenTokensCountContentOnly() {
        // Act
        List<ChunkDraft> drafts = chunker.chunk(List.of(new TextSection(null, "x".repeat(8))));

        // Assert
        assertThat(drafts.getFirst().tokenCount()).isEqualTo(2);
        assertThat(drafts.getFirst().heading()).isNull();
    }

    @Test
    void givenLongSectionOfSentences_whenChunk_thenChunksWithinLimitsAndWithSentenceOverlap() {
        // Arrange
        List<String> sentences = IntStream.range(0, 50).mapToObj(TextChunkerTest::sentence).toList();
        String body = String.join(" ", sentences);

        // Act
        List<ChunkDraft> drafts = chunker.chunk(List.of(new TextSection("Uzun", body)));

        // Assert
        assertThat(drafts.size()).isGreaterThanOrEqualTo(3);
        assertThat(drafts.getFirst().content().length()).isLessThanOrEqualTo(1600);
        for (int index = 0; index < drafts.size(); index++) {
            assertThat(drafts.get(index).content().length()).isBetween(1, 2000);
            assertThat(drafts.get(index).chunkNo()).isEqualTo(index);
            assertThat(drafts.get(index).heading()).isEqualTo("Uzun");
        }
        for (int index = 1; index < drafts.size(); index++) {
            String previous = drafts.get(index - 1).content();
            String current = drafts.get(index).content();
            String previousLastSentence = previous.substring(previous.lastIndexOf("Cumle"));
            assertThat(current).startsWith(previousLastSentence.substring(0, 9));
            int overlapChars = previous.length() - previous.indexOf(current.substring(0, 9));
            assertThat(overlapChars).isLessThanOrEqualTo(200);
        }
        String all = drafts.stream().map(ChunkDraft::content).collect(Collectors.joining("\n"));
        sentences.forEach(sentence -> assertThat(all).contains(sentence));
    }

    @Test
    void givenSentenceLongerThanMax_whenChunk_thenHardSplitsAtWhitespaceWithoutEmptyChunks() {
        // Arrange
        String body = "kelimeler ".repeat(450).strip();

        // Act
        List<ChunkDraft> drafts = chunker.chunk(List.of(new TextSection("Dev", body)));

        // Assert
        assertThat(drafts).hasSizeGreaterThan(1);
        for (ChunkDraft draft : drafts) {
            assertThat(draft.content().length()).isBetween(1, 2000);
            assertThat(draft.content()).isNotBlank();
            for (String word : draft.content().split(" ")) {
                assertThat(word).isEqualTo("kelimeler");
            }
        }
    }

    @Test
    void givenWordLongerThanMaxWithoutWhitespace_whenChunk_thenSplitsAtMaxLength() {
        // Act
        List<ChunkDraft> drafts = chunker.chunk(List.of(new TextSection(null, "z".repeat(4500))));

        // Assert
        assertThat(drafts).allSatisfy(draft -> assertThat(draft.content().length()).isBetween(1, 2000));
        assertThat(drafts.stream().mapToInt(draft -> draft.content().length()).sum()).isGreaterThanOrEqualTo(4500);
    }

    @Test
    void givenTwoSectionsAndBlankSection_whenChunk_thenChunksDoNotCrossSectionsAndBlankIsSkipped() {
        // Arrange
        List<TextSection> sections = List.of(new TextSection("A", "x"), new TextSection("B", "   "),
                new TextSection("C", "y"));

        // Act
        List<ChunkDraft> drafts = chunker.chunk(sections);

        // Assert
        assertThat(drafts).extracting(ChunkDraft::heading).containsExactly("A", "C");
        assertThat(drafts).extracting(ChunkDraft::chunkNo).containsExactly(0, 1);
        assertThat(drafts).extracting(ChunkDraft::content).containsExactly("x", "y");
    }

    @Test
    void givenParagraphsAndIrregularWhitespace_whenChunk_thenWhitespaceNormalizedAndParagraphsKept() {
        // Act
        List<ChunkDraft> drafts = chunker.chunk(List.of(new TextSection(null, "bir   iki\t\tüç\n\n\n  dört  ")));

        // Assert
        assertThat(drafts).hasSize(1);
        assertThat(drafts.getFirst().content()).isEqualTo("bir iki üç\n\ndört");
    }

    @Test
    void givenSeveralParagraphsExceedingTarget_whenChunk_thenSplitsAtParagraphBoundary() {
        // Arrange
        List<String> paragraphs = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            paragraphs.add(("P" + index + " ").repeat(1).concat("m".repeat(700)));
        }

        // Act
        List<ChunkDraft> drafts = chunker.chunk(List.of(new TextSection("H", String.join("\n\n", paragraphs))));

        // Assert
        assertThat(drafts).hasSizeGreaterThan(1);
        assertThat(drafts.getFirst().content()).startsWith("P0 ").contains("P1 ");
        assertThat(drafts.getFirst().content().length()).isLessThanOrEqualTo(1600);
    }

    @Test
    void givenNoSections_whenChunk_thenNoDrafts() {
        // Assert
        assertThat(chunker.chunk(List.of())).isEmpty();
    }

    @Test
    void givenHeadingAndContent_whenEmbeddingInput_thenHeadingBlankLineContent() {
        // Assert
        assertThat(new ChunkDraft(0, "Baş", "içerik", 3).embeddingInput()).isEqualTo("Baş\n\niçerik");
        assertThat(new ChunkDraft(0, null, "içerik", 2).embeddingInput()).isEqualTo("içerik");
    }

    @Test
    void givenTwoParagraphsFillingTargetExactly_whenChunk_thenOneChunk() {
        // Arrange
        String body = "a".repeat(800) + "\n\n" + "b".repeat(798);

        // Act
        List<ChunkDraft> drafts = chunker.chunk(List.of(new TextSection(null, body)));

        // Assert
        assertThat(drafts).hasSize(1);
        assertThat(drafts.getFirst().content()).hasSize(1600);
    }

    @Test
    void givenTwoParagraphsOneCharOverTarget_whenChunk_thenTwoChunks() {
        // Arrange
        String body = "a".repeat(800) + "\n\n" + "b".repeat(799);

        // Act
        List<ChunkDraft> drafts = chunker.chunk(List.of(new TextSection(null, body)));

        // Assert
        assertThat(drafts).hasSize(2);
        assertThat(drafts.get(0).content()).isEqualTo("a".repeat(800));
        assertThat(drafts.get(1).content()).isEqualTo("b".repeat(799));
    }

    @Test
    void givenSingleSentenceBetweenTargetAndMax_whenChunk_thenKeptWholeInOneChunk() {
        // Act
        List<ChunkDraft> atMax = chunker.chunk(List.of(new TextSection(null, "w".repeat(2000))));
        List<ChunkDraft> overMax = chunker.chunk(List.of(new TextSection(null, "word ".repeat(401).strip())));

        // Assert
        assertThat(atMax).hasSize(1);
        assertThat(atMax.getFirst().content()).hasSize(2000);
        assertThat(overMax).hasSize(2);
        assertThat(overMax).allSatisfy(draft -> assertThat(draft.content().length()).isBetween(1, 2000));
    }

    @Test
    void givenSentencesOf99Characters_whenChunk_thenTwoSentencesOverlapNextChunk() {
        // Arrange
        List<String> sentences = IntStream.range(0, 50).mapToObj(index -> {
            String prefix = "Cumle %02d: ".formatted(index);
            return prefix + "a".repeat(99 - prefix.length() - 1) + ".";
        }).toList();

        // Act
        List<ChunkDraft> drafts = chunker.chunk(List.of(new TextSection(null, String.join(" ", sentences))));

        // Assert
        String first = drafts.get(0).content();
        String second = drafts.get(1).content();
        String[] firstSentences = first.split(" (?=Cumle)");
        String penultimate = firstSentences[firstSentences.length - 2];
        String last = firstSentences[firstSentences.length - 1];
        assertThat(second).startsWith(penultimate + " " + last);
        assertThat(second).doesNotStartWith(firstSentences[firstSentences.length - 3]);
    }

    @Test
    void givenSentencesOf100Characters_whenChunk_thenOnlyLastSentenceOverlapsNextChunk() {
        // Arrange
        List<String> sentences = IntStream.range(0, 50).mapToObj(TextChunkerTest::sentence).toList();

        // Act
        List<ChunkDraft> drafts = chunker.chunk(List.of(new TextSection(null, String.join(" ", sentences))));

        // Assert
        String[] firstSentences = drafts.get(0).content().split(" (?=Cumle)");
        String last = firstSentences[firstSentences.length - 1];
        String penultimate = firstSentences[firstSentences.length - 2];
        assertThat(drafts.get(1).content()).startsWith(last);
        assertThat(drafts.get(1).content()).doesNotStartWith(penultimate);
    }

    @Test
    void givenLongSentenceWithSpaces_whenChunk_thenHardSplitHappensAtLastSpaceNotMidWord() {
        // Arrange
        String body = "abcdefghi ".repeat(300).strip();

        // Act
        List<ChunkDraft> drafts = chunker.chunk(List.of(new TextSection(null, body)));

        // Assert
        assertThat(drafts).allSatisfy(draft -> {
            assertThat(draft.content()).hasSizeLessThanOrEqualTo(2000);
            assertThat(draft.content()).startsWith("abcdefghi").endsWith("abcdefghi");
        });
        assertThat(drafts.get(0).content().length()).isGreaterThan(1990);
    }

    @Test
    void givenLineBreakInsideParagraph_whenChunk_thenLineBreakPreserved() {
        // Act
        List<ChunkDraft> drafts = chunker.chunk(List.of(new TextSection(null, "Kod: A\nAçıklama: B")));

        // Assert
        assertThat(drafts.getFirst().content()).isEqualTo("Kod: A\nAçıklama: B");
    }
}
