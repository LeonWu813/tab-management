package com.tabvault.backend.contentextraction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ContentExtractionService.
 *
 * Tests cover URL type detection and correct dispatcher routing.
 *
 * AC-028: YouTube URL detected by regex — routed to YouTubeExtractor.
 * AC-031: Instagram/TikTok URL detected — routed to VideoMetadataExtractor; summarySkipped=true.
 * AC-029: Transcript available → summarySkipped=false.
 * AC-058: Transcript unavailable → summarySkipped=true.
 */
@ExtendWith(MockitoExtension.class)
class ContentExtractionServiceTest {

    @Mock
    private YouTubeExtractor youTubeExtractor;

    @Mock
    private VideoMetadataExtractor videoMetadataExtractor;

    @Mock
    private PdfExtractor pdfExtractor;

    @Mock
    private ArticleExtractor articleExtractor;

    private ContentExtractionService service;

    @BeforeEach
    void setUp() {
        service = new ContentExtractionService(
                youTubeExtractor, videoMetadataExtractor, pdfExtractor, articleExtractor);
    }

    // -------------------------------------------------------------------------
    // AC-028: YouTube URL detection and routing
    // -------------------------------------------------------------------------

    @Test
    void extract_youtubeWatchUrl_routesToYouTubeExtractor() {
        String url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";
        when(youTubeExtractor.extractVideoId(url)).thenReturn("dQw4w9WgXcQ");
        when(youTubeExtractor.fetchOEmbedData(url, "dQw4w9WgXcQ"))
                .thenReturn(new YouTubeExtractor.OEmbedData("Test Title", "https://img.youtube.com/vi/dQw4w9WgXcQ/0.jpg"));
        when(youTubeExtractor.fetchTranscript("dQw4w9WgXcQ")).thenReturn("Transcript text here");

        ExtractionResult result = service.extract(url);

        assertThat(result.platform()).isEqualTo("youtube");
        assertThat(result.thumbnailUrl()).isEqualTo("https://img.youtube.com/vi/dQw4w9WgXcQ/0.jpg");
        assertThat(result.title()).isEqualTo("Test Title");
        assertThat(result.pageText()).isEqualTo("Transcript text here");
        assertThat(result.summarySkipped()).isFalse();

        verify(videoMetadataExtractor, never()).extract(anyString(), anyString());
        verify(articleExtractor, never()).extract(anyString());
        verify(pdfExtractor, never()).extract(anyString());
    }

    @Test
    void extract_youtubeShortUrl_routesToYouTubeExtractor() {
        String url = "https://youtu.be/dQw4w9WgXcQ";
        when(youTubeExtractor.extractVideoId(url)).thenReturn("dQw4w9WgXcQ");
        when(youTubeExtractor.fetchOEmbedData(url, "dQw4w9WgXcQ"))
                .thenReturn(new YouTubeExtractor.OEmbedData("Short Title", "https://img.youtube.com/vi/dQw4w9WgXcQ/0.jpg"));
        when(youTubeExtractor.fetchTranscript("dQw4w9WgXcQ")).thenReturn("Short video transcript");

        ExtractionResult result = service.extract(url);

        assertThat(result.platform()).isEqualTo("youtube");
        assertThat(result.summarySkipped()).isFalse();
    }

    // -------------------------------------------------------------------------
    // AC-029: YouTube transcript available → summarySkipped=false
    // AC-058: YouTube transcript unavailable → summarySkipped=true
    // -------------------------------------------------------------------------

    @Test
    void extract_youtubeWithTranscript_summarySkippedFalse() {
        String url = "https://www.youtube.com/watch?v=abc123def45";
        when(youTubeExtractor.extractVideoId(url)).thenReturn("abc123def45");
        when(youTubeExtractor.fetchOEmbedData(url, "abc123def45"))
                .thenReturn(new YouTubeExtractor.OEmbedData("Video Title", "https://thumbnail.url/img.jpg"));
        when(youTubeExtractor.fetchTranscript("abc123def45")).thenReturn("Full transcript text");

        ExtractionResult result = service.extract(url);

        // AC-029: summarySkipped=false → Claude will be called with transcript text
        assertThat(result.summarySkipped()).isFalse();
        assertThat(result.pageText()).isEqualTo("Full transcript text");
        assertThat(result.thumbnailUrl()).isEqualTo("https://thumbnail.url/img.jpg");
    }

    @Test
    void extract_youtubeWithoutTranscript_summarySkippedTrue() {
        String url = "https://www.youtube.com/watch?v=abc123def45";
        when(youTubeExtractor.extractVideoId(url)).thenReturn("abc123def45");
        when(youTubeExtractor.fetchOEmbedData(url, "abc123def45"))
                .thenReturn(new YouTubeExtractor.OEmbedData("Video Title", "https://thumbnail.url/img.jpg"));
        when(youTubeExtractor.fetchTranscript("abc123def45")).thenReturn(null);

        ExtractionResult result = service.extract(url);

        // AC-058: transcript unavailable → summarySkipped=true, summary=null
        assertThat(result.summarySkipped()).isTrue();
        assertThat(result.pageText()).isNull();
        assertThat(result.thumbnailUrl()).isEqualTo("https://thumbnail.url/img.jpg");
        assertThat(result.platform()).isEqualTo("youtube");
    }

    @Test
    void extract_youtubeEmptyTranscript_summarySkippedTrue() {
        String url = "https://www.youtube.com/watch?v=abc123def45";
        when(youTubeExtractor.extractVideoId(url)).thenReturn("abc123def45");
        when(youTubeExtractor.fetchOEmbedData(url, "abc123def45"))
                .thenReturn(new YouTubeExtractor.OEmbedData("Video Title", null));
        when(youTubeExtractor.fetchTranscript("abc123def45")).thenReturn("  "); // blank

        ExtractionResult result = service.extract(url);

        // Blank transcript treated as unavailable
        assertThat(result.summarySkipped()).isTrue();
        assertThat(result.pageText()).isNull();
    }

    // -------------------------------------------------------------------------
    // AC-031: Instagram URL detection → VideoMetadataExtractor, summarySkipped=true
    // -------------------------------------------------------------------------

    @Test
    void extract_instagramReelUrl_routesToVideoMetadataExtractor() {
        String url = "https://www.instagram.com/reel/ABC123def/";
        when(youTubeExtractor.extractVideoId(url)).thenReturn(null);
        ExtractionResult mockResult = ExtractionResult.forVideoMetadataOnly(
                "https://img.example.com/reel.jpg", "Instagram Reel Title", "instagram");
        when(videoMetadataExtractor.extract(url, "instagram")).thenReturn(mockResult);

        ExtractionResult result = service.extract(url);

        // AC-031: summarySkipped=true, Claude NOT called
        assertThat(result.summarySkipped()).isTrue();
        assertThat(result.platform()).isEqualTo("instagram");
        assertThat(result.thumbnailUrl()).isEqualTo("https://img.example.com/reel.jpg");
        assertThat(result.pageText()).isNull();

        verify(articleExtractor, never()).extract(anyString());
        verify(pdfExtractor, never()).extract(anyString());
    }

    @Test
    void extract_tiktokUrl_routesToVideoMetadataExtractor() {
        String url = "https://www.tiktok.com/@user/video/1234567890123456789";
        when(youTubeExtractor.extractVideoId(url)).thenReturn(null);
        ExtractionResult mockResult = ExtractionResult.forVideoMetadataOnly(
                "https://img.example.com/tiktok.jpg", "TikTok Video Title", "tiktok");
        when(videoMetadataExtractor.extract(url, "tiktok")).thenReturn(mockResult);

        ExtractionResult result = service.extract(url);

        // AC-031: summarySkipped=true for TikTok too
        assertThat(result.summarySkipped()).isTrue();
        assertThat(result.platform()).isEqualTo("tiktok");

        verify(articleExtractor, never()).extract(anyString());
    }

    @Test
    void extract_vmTiktokUrl_routesToVideoMetadataExtractor() {
        String url = "https://vm.tiktok.com/ZMxxxxxxxx/";
        when(youTubeExtractor.extractVideoId(url)).thenReturn(null);
        ExtractionResult mockResult = ExtractionResult.forVideoMetadataOnly(null, null, "tiktok");
        when(videoMetadataExtractor.extract(url, "tiktok")).thenReturn(mockResult);

        ExtractionResult result = service.extract(url);

        assertThat(result.summarySkipped()).isTrue();
        assertThat(result.platform()).isEqualTo("tiktok");
    }

    // -------------------------------------------------------------------------
    // PDF URL detection and routing
    // -------------------------------------------------------------------------

    @Test
    void extract_pdfUrl_routesToPdfExtractor() {
        String url = "https://example.com/document.pdf";
        when(youTubeExtractor.extractVideoId(url)).thenReturn(null);
        when(pdfExtractor.extract(url)).thenReturn("PDF text content from the document");

        ExtractionResult result = service.extract(url);

        assertThat(result.pageText()).isEqualTo("PDF text content from the document");
        assertThat(result.summarySkipped()).isFalse();
        assertThat(result.platform()).isNull();

        verify(articleExtractor, never()).extract(anyString());
        verify(videoMetadataExtractor, never()).extract(anyString(), anyString());
    }

    @Test
    void extract_pdfUrlWithQueryString_routesToPdfExtractor() {
        String url = "https://example.com/research.pdf?token=abc123";
        when(youTubeExtractor.extractVideoId(url)).thenReturn(null);
        when(pdfExtractor.extract(url)).thenReturn("PDF text");

        ExtractionResult result = service.extract(url);

        assertThat(result.summarySkipped()).isFalse();
        assertThat(result.pageText()).isEqualTo("PDF text");
        verify(pdfExtractor).extract(url);
    }

    // -------------------------------------------------------------------------
    // Article URL (default path)
    // -------------------------------------------------------------------------

    @Test
    void extract_regularArticleUrl_routesToArticleExtractor() {
        String url = "https://example.com/article/interesting-topic";
        when(youTubeExtractor.extractVideoId(url)).thenReturn(null);
        when(articleExtractor.extract(url)).thenReturn("Interesting article text");

        ExtractionResult result = service.extract(url);

        assertThat(result.pageText()).isEqualTo("Interesting article text");
        assertThat(result.summarySkipped()).isFalse();
        assertThat(result.platform()).isNull();
        assertThat(result.thumbnailUrl()).isNull();

        verify(pdfExtractor, never()).extract(anyString());
        verify(videoMetadataExtractor, never()).extract(anyString(), anyString());
    }

    // -------------------------------------------------------------------------
    // Null / blank URL edge cases
    // -------------------------------------------------------------------------

    @Test
    void extract_nullUrl_returnsEmptyArticleResult() {
        ExtractionResult result = service.extract(null);

        assertThat(result.summarySkipped()).isFalse();
        assertThat(result.pageText()).isEmpty();
        assertThat(result.platform()).isNull();

        verify(youTubeExtractor, never()).extractVideoId(any());
        verify(articleExtractor, never()).extract(anyString());
    }

    @Test
    void extract_blankUrl_returnsEmptyArticleResult() {
        ExtractionResult result = service.extract("   ");

        assertThat(result.summarySkipped()).isFalse();
        assertThat(result.pageText()).isEmpty();
    }

    // -------------------------------------------------------------------------
    // URL pattern detection unit tests (via service method indirection)
    // -------------------------------------------------------------------------

    @Test
    void instagramUrlPattern_matchesReelPaths() {
        assertThat(ContentExtractionService.INSTAGRAM_URL_PATTERN
                .matcher("https://www.instagram.com/reel/ABC123def/").find()).isTrue();
        assertThat(ContentExtractionService.INSTAGRAM_URL_PATTERN
                .matcher("https://instagram.com/reels/XYZ789/").find()).isTrue();
        assertThat(ContentExtractionService.INSTAGRAM_URL_PATTERN
                .matcher("https://www.instagram.com/p/ABC123/").find()).isTrue();
    }

    @Test
    void instagramUrlPattern_doesNotMatchNonInstagram() {
        assertThat(ContentExtractionService.INSTAGRAM_URL_PATTERN
                .matcher("https://www.youtube.com/watch?v=abc").find()).isFalse();
        assertThat(ContentExtractionService.INSTAGRAM_URL_PATTERN
                .matcher("https://example.com").find()).isFalse();
    }

    @Test
    void tiktokUrlPattern_matchesTikTokDomains() {
        assertThat(ContentExtractionService.TIKTOK_URL_PATTERN
                .matcher("https://www.tiktok.com/@user/video/123").find()).isTrue();
        assertThat(ContentExtractionService.TIKTOK_URL_PATTERN
                .matcher("https://vm.tiktok.com/ZMxxxx/").find()).isTrue();
        assertThat(ContentExtractionService.TIKTOK_URL_PATTERN
                .matcher("https://tiktok.com/t/ABC/").find()).isTrue();
    }

    @Test
    void pdfUrlPattern_matchesPdfExtension() {
        assertThat(ContentExtractionService.PDF_URL_PATTERN
                .matcher("https://example.com/doc.pdf").matches()).isTrue();
        assertThat(ContentExtractionService.PDF_URL_PATTERN
                .matcher("https://example.com/doc.PDF").matches()).isTrue();
        assertThat(ContentExtractionService.PDF_URL_PATTERN
                .matcher("https://example.com/doc.pdf?v=1").matches()).isTrue();
        assertThat(ContentExtractionService.PDF_URL_PATTERN
                .matcher("https://example.com/article.html").matches()).isFalse();
    }
}
