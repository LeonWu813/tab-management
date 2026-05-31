package com.tabvault.backend.contentextraction;

/**
 * Value object returned by the content extraction pipeline.
 *
 * Carries all extracted metadata for a single URL:
 * - pageText: extracted readable text for LLM input (article text, YouTube transcript, PDF text).
 *             Null when extraction is not applicable (non-YouTube video platforms).
 * - thumbnailUrl: video thumbnail URL (YouTube oEmbed or og:image). Null for articles and PDFs.
 * - platform: platform identifier string ("youtube", "instagram", "tiktok"). Null for non-video.
 * - summarySkipped: true when the LLM summarization call should be skipped entirely.
 *                   Set for non-YouTube video items (AC-031, AC-032) and YouTube items
 *                   where transcript is unavailable (AC-058, AC-059).
 * - title: extracted or resolved title; may override the client-supplied title when the
 *          extraction source provides a more accurate one (e.g., YouTube oEmbed API title).
 *          Null means "keep the original title from the item record."
 */
public record ExtractionResult(
        String pageText,
        String thumbnailUrl,
        String platform,
        boolean summarySkipped,
        String title
) {

    /**
     * Creates a result for an article page with extracted readable text.
     *
     * @param pageText readable body text extracted from the HTML
     */
    public static ExtractionResult forArticle(String pageText) {
        return new ExtractionResult(pageText, null, null, false, null);
    }

    /**
     * Creates a result for a PDF link with extracted text.
     *
     * @param pageText text extracted from the PDF
     */
    public static ExtractionResult forPdf(String pageText) {
        return new ExtractionResult(pageText, null, null, false, null);
    }

    /**
     * Creates a result for a YouTube video where transcript is available.
     *
     * AC-029: transcript passed to MOD-003 for summarization (same 3,000-token limit).
     * AC-030: title, thumbnailUrl, platform="youtube" stored on the item record.
     *
     * @param transcriptText transcript text (will be truncated by MOD-003 pipeline)
     * @param thumbnailUrl   YouTube oEmbed thumbnail URL
     * @param title          video title from oEmbed (may be null if oEmbed unavailable)
     */
    public static ExtractionResult forYouTubeWithTranscript(
            String transcriptText, String thumbnailUrl, String title) {
        return new ExtractionResult(transcriptText, thumbnailUrl, "youtube", false, title);
    }

    /**
     * Creates a result for a YouTube video where transcript is unavailable.
     *
     * AC-058: title and thumbnailUrl stored; summary field set to null.
     * AC-059: dashboard label "Transcript unavailable — open to watch".
     *
     * @param thumbnailUrl YouTube oEmbed thumbnail URL (may be null if oEmbed also unavailable)
     * @param title        video title from oEmbed (may be null)
     */
    public static ExtractionResult forYouTubeWithoutTranscript(String thumbnailUrl, String title) {
        return new ExtractionResult(null, thumbnailUrl, "youtube", true, title);
    }

    /**
     * Creates a result for a non-YouTube video platform (Instagram, TikTok).
     *
     * AC-031: URL, og:title, og:image thumbnail, platform name stored without calling Claude.
     * AC-032: dashboard label "No summary available — open to watch".
     *
     * @param thumbnailUrl value of the og:image tag
     * @param title        value of og:title (may override the browser-supplied page title)
     * @param platform     platform identifier ("instagram" or "tiktok")
     */
    public static ExtractionResult forVideoMetadataOnly(
            String thumbnailUrl, String title, String platform) {
        return new ExtractionResult(null, thumbnailUrl, platform, true, title);
    }
}
