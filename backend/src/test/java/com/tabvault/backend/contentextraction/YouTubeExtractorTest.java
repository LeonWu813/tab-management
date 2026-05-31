package com.tabvault.backend.contentextraction;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for YouTubeExtractor.
 *
 * Tests cover video ID extraction from various YouTube URL formats.
 * Network-dependent methods (fetchOEmbedData, fetchTranscript) are tested
 * via ContentExtractionServiceTest using mocks.
 *
 * AC-028: YouTube URL detected by regex pattern match.
 */
class YouTubeExtractorTest {

    // -------------------------------------------------------------------------
    // AC-028: extractVideoId — regex pattern match
    // -------------------------------------------------------------------------

    @Test
    void extractVideoId_standardWatchUrl_returnsVideoId() {
        YouTubeExtractor extractor = buildExtractor();
        assertThat(extractor.extractVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
                .isEqualTo("dQw4w9WgXcQ");
    }

    @Test
    void extractVideoId_watchUrlWithExtraParams_returnsVideoId() {
        YouTubeExtractor extractor = buildExtractor();
        assertThat(extractor.extractVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=30s"))
                .isEqualTo("dQw4w9WgXcQ");
    }

    @Test
    void extractVideoId_youtuBeShortLink_returnsVideoId() {
        YouTubeExtractor extractor = buildExtractor();
        assertThat(extractor.extractVideoId("https://youtu.be/dQw4w9WgXcQ"))
                .isEqualTo("dQw4w9WgXcQ");
    }

    @Test
    void extractVideoId_withoutHttps_returnsVideoId() {
        YouTubeExtractor extractor = buildExtractor();
        assertThat(extractor.extractVideoId("youtube.com/watch?v=abc1234567A"))
                .isEqualTo("abc1234567A");
    }

    @Test
    void extractVideoId_nonYoutubeUrl_returnsNull() {
        YouTubeExtractor extractor = buildExtractor();
        assertThat(extractor.extractVideoId("https://vimeo.com/123456789")).isNull();
        assertThat(extractor.extractVideoId("https://example.com/article")).isNull();
        assertThat(extractor.extractVideoId("https://instagram.com/reel/ABC")).isNull();
    }

    @Test
    void extractVideoId_nullUrl_returnsNull() {
        YouTubeExtractor extractor = buildExtractor();
        assertThat(extractor.extractVideoId(null)).isNull();
    }

    @Test
    void extractVideoId_emptyUrl_returnsNull() {
        YouTubeExtractor extractor = buildExtractor();
        assertThat(extractor.extractVideoId("")).isNull();
    }

    @Test
    void extractVideoId_videoIdExactly11Chars() {
        YouTubeExtractor extractor = buildExtractor();
        // Video IDs are exactly 11 characters — verify correct length captured
        String videoId = extractor.extractVideoId("https://www.youtube.com/watch?v=aaaabbbbccc");
        assertThat(videoId).hasSize(11);
    }

    // -------------------------------------------------------------------------
    // ExtractionResult factory methods — value object correctness
    // -------------------------------------------------------------------------

    @Test
    void forYouTubeWithTranscript_setsCorrectFields() {
        ExtractionResult result = ExtractionResult.forYouTubeWithTranscript(
                "Transcript text", "https://thumbnail.url/img.jpg", "Video Title");

        assertThat(result.pageText()).isEqualTo("Transcript text");
        assertThat(result.thumbnailUrl()).isEqualTo("https://thumbnail.url/img.jpg");
        assertThat(result.platform()).isEqualTo("youtube");
        assertThat(result.summarySkipped()).isFalse();
        assertThat(result.title()).isEqualTo("Video Title");
    }

    @Test
    void forYouTubeWithoutTranscript_setsCorrectFields() {
        ExtractionResult result = ExtractionResult.forYouTubeWithoutTranscript(
                "https://thumbnail.url/img.jpg", "Video Title");

        assertThat(result.pageText()).isNull();
        assertThat(result.thumbnailUrl()).isEqualTo("https://thumbnail.url/img.jpg");
        assertThat(result.platform()).isEqualTo("youtube");
        // AC-058: summarySkipped=true means summary stays null on the item record
        assertThat(result.summarySkipped()).isTrue();
        assertThat(result.title()).isEqualTo("Video Title");
    }

    @Test
    void forVideoMetadataOnly_setsCorrectFields() {
        ExtractionResult result = ExtractionResult.forVideoMetadataOnly(
                "https://img.example.com/reel.jpg", "Instagram Reel Title", "instagram");

        assertThat(result.thumbnailUrl()).isEqualTo("https://img.example.com/reel.jpg");
        assertThat(result.title()).isEqualTo("Instagram Reel Title");
        assertThat(result.platform()).isEqualTo("instagram");
        assertThat(result.pageText()).isNull();
        // AC-031: summarySkipped=true — Claude API not called for these items
        assertThat(result.summarySkipped()).isTrue();
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    /**
     * Builds a YouTubeExtractor with a no-op WebClient builder and empty API key.
     * Only tests that do NOT call network methods should use this helper directly.
     */
    private YouTubeExtractor buildExtractor() {
        // Create extractor with null WebClient builder — only extractVideoId is tested here
        // (network methods are tested via integration/service layer tests with mocks)
        org.springframework.web.reactive.function.client.WebClient.Builder builder =
                org.springframework.web.reactive.function.client.WebClient.builder();
        com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper();
        return new YouTubeExtractor(builder, mapper, "", 1000);
    }
}
