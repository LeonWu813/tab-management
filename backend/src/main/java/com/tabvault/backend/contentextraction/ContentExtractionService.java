package com.tabvault.backend.contentextraction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * Orchestrates content extraction for saved items.
 *
 * Detects the URL type (YouTube, Instagram, TikTok, PDF, article) and dispatches to
 * the appropriate extractor. Returns an ExtractionResult that the content analysis
 * pipeline (MOD-003) uses to decide whether to call the Claude API and what text to
 * send as input.
 *
 * URL type detection order (first match wins):
 *   1. YouTube URL pattern → YouTubeExtractor
 *   2. Instagram URL pattern → VideoMetadataExtractor (platform="instagram")
 *   3. TikTok URL pattern → VideoMetadataExtractor (platform="tiktok")
 *   4. PDF URL pattern (ends with .pdf or content-type check) → PdfExtractor
 *   5. All other URLs → ArticleExtractor
 *
 * AC-028: YouTube URL detected by regex; transcript retrieved via YouTube Data API v3.
 * AC-029: Transcript passed to Claude (3,000-token truncation applied by MOD-003 pipeline).
 * AC-030: title, thumbnailUrl, platform="youtube" stored on item record.
 * AC-031: Instagram/TikTok: og:title, og:image, platform name stored; Claude NOT called.
 * AC-032: "No summary available — open to watch" label on non-YouTube video items.
 * AC-058: YouTube transcript unavailable → title + thumbnailUrl stored; summary=null.
 * AC-059: "Transcript unavailable — open to watch" label on YouTube items with null summary.
 */
@Service
public class ContentExtractionService {

    private static final Logger logger = LoggerFactory.getLogger(ContentExtractionService.class);

    /**
     * Pattern matching Instagram Reel and video URLs.
     * Matches instagram.com/reel/, instagram.com/reels/, instagram.com/p/ paths.
     */
    static final Pattern INSTAGRAM_URL_PATTERN =
            Pattern.compile("(?:https?://)?(?:www\\.)?instagram\\.com/(?:reel|reels|p)/", Pattern.CASE_INSENSITIVE);

    /**
     * Pattern matching TikTok video URLs.
     * Matches tiktok.com/@user/video/, tiktok.com/v/, vm.tiktok.com/ paths.
     */
    static final Pattern TIKTOK_URL_PATTERN =
            Pattern.compile("(?:https?://)?(?:www\\.|vm\\.)?tiktok\\.com/", Pattern.CASE_INSENSITIVE);

    /**
     * Pattern matching URLs that directly serve PDF files.
     * Matches URLs ending with .pdf (with optional query string).
     */
    static final Pattern PDF_URL_PATTERN =
            Pattern.compile("^https?://.*\\.pdf(\\?.*)?$", Pattern.CASE_INSENSITIVE);

    private final YouTubeExtractor youTubeExtractor;
    private final VideoMetadataExtractor videoMetadataExtractor;
    private final PdfExtractor pdfExtractor;
    private final ArticleExtractor articleExtractor;

    public ContentExtractionService(
            YouTubeExtractor youTubeExtractor,
            VideoMetadataExtractor videoMetadataExtractor,
            PdfExtractor pdfExtractor,
            ArticleExtractor articleExtractor) {
        this.youTubeExtractor = youTubeExtractor;
        this.videoMetadataExtractor = videoMetadataExtractor;
        this.pdfExtractor = pdfExtractor;
        this.articleExtractor = articleExtractor;
    }

    /**
     * Extracts content from the given URL.
     *
     * Detects URL type and dispatches to the appropriate extractor.
     * The returned ExtractionResult carries:
     * - pageText: text to pass to the LLM (null if Claude should not be called)
     * - thumbnailUrl: video thumbnail (null for non-video)
     * - platform: "youtube" | "instagram" | "tiktok" | null
     * - summarySkipped: true when Claude should not be called for this item
     * - title: resolved title override (null means keep existing item title)
     *
     * @param url the URL of the saved item
     * @return ExtractionResult with extracted data
     */
    public ExtractionResult extract(String url) {
        if (url == null || url.isBlank()) {
            logger.debug("URL is null or blank — returning empty extraction result");
            return ExtractionResult.forArticle("");
        }

        // 1. YouTube URL
        String youtubeVideoId = youTubeExtractor.extractVideoId(url);
        if (youtubeVideoId != null) {
            return extractYouTube(url, youtubeVideoId);
        }

        // 2. Instagram URL
        if (INSTAGRAM_URL_PATTERN.matcher(url).find()) {
            logger.info("Instagram URL detected url={}", url);
            return videoMetadataExtractor.extract(url, "instagram");
        }

        // 3. TikTok URL
        if (TIKTOK_URL_PATTERN.matcher(url).find()) {
            logger.info("TikTok URL detected url={}", url);
            return videoMetadataExtractor.extract(url, "tiktok");
        }

        // 4. PDF URL
        if (PDF_URL_PATTERN.matcher(url).matches()) {
            logger.info("PDF URL detected url={}", url);
            String pdfText = pdfExtractor.extract(url);
            return ExtractionResult.forPdf(pdfText);
        }

        // 5. Article (default)
        logger.info("Article URL detected url={}", url);
        String articleText = articleExtractor.extract(url);
        return ExtractionResult.forArticle(articleText);
    }

    // -------------------------------------------------------------------------
    // YouTube extraction
    // -------------------------------------------------------------------------

    /**
     * Extracts content from a YouTube video URL.
     *
     * Step 1: Fetch title and thumbnail via oEmbed (no API key required).
     * Step 2: Attempt to retrieve the video transcript.
     * Step 3: Return appropriate ExtractionResult based on transcript availability.
     *
     * AC-028: YouTube URL detected; transcript retrieved via YouTube Data API v3.
     * AC-058: When transcript unavailable, returns forYouTubeWithoutTranscript so summary=null.
     */
    private ExtractionResult extractYouTube(String url, String videoId) {
        logger.info("YouTube URL detected url={} videoId={}", url, videoId);

        // Fetch title and thumbnail via oEmbed
        YouTubeExtractor.OEmbedData oembedData = youTubeExtractor.fetchOEmbedData(url, videoId);

        // Attempt transcript retrieval
        String transcript = youTubeExtractor.fetchTranscript(videoId);

        if (transcript != null && !transcript.isBlank()) {
            // AC-029: transcript available — pass to Claude for summarization
            // AC-030: store title, thumbnailUrl, platform="youtube" on item record
            logger.info("YouTube transcript available videoId={} chars={}", videoId, transcript.length());
            return ExtractionResult.forYouTubeWithTranscript(
                    transcript, oembedData.thumbnailUrl(), oembedData.title());
        } else {
            // AC-058: transcript unavailable — store title + thumbnail, summary=null
            logger.info("YouTube transcript unavailable videoId={}", videoId);
            return ExtractionResult.forYouTubeWithoutTranscript(
                    oembedData.thumbnailUrl(), oembedData.title());
        }
    }
}
