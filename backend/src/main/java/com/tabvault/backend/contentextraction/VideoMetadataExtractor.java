package com.tabvault.backend.contentextraction;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Extracts Open Graph metadata from non-YouTube video platform URLs.
 *
 * Targets Instagram Reels and TikTok URLs that match the configured platform patterns.
 * Fetches the page HTML with Jsoup and reads the og:title and og:image tags.
 * No LLM summarization is performed for these items — metadata is stored as-is.
 *
 * AC-031: URL, og:title, og:image thumbnail, and platform name stored without calling Claude API.
 * AC-032: Dashboard displays "No summary available — open to watch" label for these items.
 */
@Component
public class VideoMetadataExtractor {

    private static final Logger logger = LoggerFactory.getLogger(VideoMetadataExtractor.class);

    private final int fetchTimeoutMs;

    public VideoMetadataExtractor(
            @Value("${app.content-extraction.fetch-timeout-ms:8000}") int fetchTimeoutMs) {
        this.fetchTimeoutMs = fetchTimeoutMs;
    }

    /**
     * Fetches the page at the given URL and extracts Open Graph metadata.
     *
     * Reads og:title for the page title and og:image for the thumbnail URL.
     * Falls back to the HTML {@code <title>} element when og:title is absent.
     *
     * AC-031: stores URL, og:title, og:image, and platform name on the item record.
     *
     * @param url      the URL of the video platform page
     * @param platform the platform identifier ("instagram" or "tiktok")
     * @return ExtractionResult with thumbnailUrl, title, and platform — summarySkipped=true
     */
    public ExtractionResult extract(String url, String platform) {
        String ogTitle = null;
        String ogImage = null;

        try {
            logger.debug("Fetching Open Graph metadata url={} platform={}", url, platform);
            Document document = Jsoup.connect(url)
                    .timeout(fetchTimeoutMs)
                    .userAgent("Mozilla/5.0 (compatible; TabVault/1.0; +https://tabvault.app)")
                    .get();

            // Read og:title
            Element ogTitleElement = document.selectFirst("meta[property=og:title]");
            if (ogTitleElement != null) {
                ogTitle = ogTitleElement.attr("content");
            }
            if (ogTitle == null || ogTitle.isBlank()) {
                // Fallback to HTML <title> element
                ogTitle = document.title();
            }

            // Read og:image
            Element ogImageElement = document.selectFirst("meta[property=og:image]");
            if (ogImageElement != null) {
                ogImage = ogImageElement.attr("content");
            }

            logger.info("Open Graph metadata extracted url={} platform={} hasImage={}",
                    url, platform, ogImage != null && !ogImage.isBlank());

        } catch (Exception exception) {
            // Non-fatal: metadata fetch failed; store whatever we have (may be all null)
            logger.warn("Open Graph metadata fetch failed url={} platform={} error={}",
                    url, platform, exception.getMessage());
        }

        return ExtractionResult.forVideoMetadataOnly(ogImage, ogTitle, platform);
    }
}
