package com.tabvault.backend.contentextraction;

import net.dankito.readability4j.Article;
import net.dankito.readability4j.Readability4J;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Extracts readable article text from an HTML page URL using Jsoup + Readability4J.
 *
 * Jsoup fetches and parses the raw HTML. Readability4J applies Mozilla's readability
 * algorithm to extract the main article body text, stripping navigation, ads, and
 * boilerplate content.
 *
 * AC-028 (article path): extracted text is returned to the content analysis pipeline
 * as pageText input for MOD-003 LLM summarization.
 */
@Component
public class ArticleExtractor {

    private static final Logger logger = LoggerFactory.getLogger(ArticleExtractor.class);

    /**
     * Connection timeout in milliseconds for fetching the article URL.
     * Configurable via {@code app.content-extraction.fetch-timeout-ms}.
     */
    private final int fetchTimeoutMs;

    public ArticleExtractor(
            @Value("${app.content-extraction.fetch-timeout-ms:8000}") int fetchTimeoutMs) {
        this.fetchTimeoutMs = fetchTimeoutMs;
    }

    /**
     * Fetches the HTML at the given URL and extracts its readable article text.
     *
     * Uses Jsoup to fetch and parse the HTML, then passes the raw HTML string to
     * Readability4J for content extraction. Falls back to the full Jsoup-parsed body
     * text if Readability4J does not extract any content.
     *
     * @param url the URL of the article page to fetch and extract
     * @return extracted readable text, or an empty string if extraction fails
     */
    public String extract(String url) {
        try {
            logger.debug("Fetching article HTML url={}", url);
            Document document = Jsoup.connect(url)
                    .timeout(fetchTimeoutMs)
                    .userAgent("Mozilla/5.0 (compatible; TabVault/1.0; +https://tabvault.app)")
                    .get();

            String rawHtml = document.outerHtml();

            // Apply Mozilla Readability algorithm to isolate the main article content
            Readability4J readability = new Readability4J(url, rawHtml);
            Article article = readability.parse();

            String extracted = article.getTextContent();
            if (extracted != null && !extracted.isBlank()) {
                logger.info("Article text extracted via Readability4J url={} chars={}",
                        url, extracted.length());
                return extracted.trim();
            }

            // Fallback: use Jsoup body text (less clean but better than nothing)
            String fallback = document.body().text();
            logger.warn("Readability4J returned empty content — using Jsoup body text fallback url={} chars={}",
                    url, fallback.length());
            return fallback;

        } catch (Exception exception) {
            logger.error("Article extraction failed url={} error={}", url, exception.getMessage());
            return "";
        }
    }
}
