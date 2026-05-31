package com.tabvault.backend.contentextraction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts content from YouTube video URLs.
 *
 * Retrieves:
 * 1. Video title and thumbnail via the YouTube oEmbed endpoint (no API key required).
 * 2. Video transcript via the YouTube Data API v3 captions endpoint.
 *
 * AC-028: YouTube URL detected by regex pattern match; transcript retrieved via YouTube Data API v3.
 * AC-029: Transcript passed to MOD-003 for summarization (3,000-token truncation applied by MOD-003).
 * AC-030: title, thumbnailUrl, platform="youtube", and LLM summary stored on the item record.
 * AC-058: When transcript is unavailable, title and thumbnailUrl stored; summary set to null.
 * AC-059: Dashboard displays "Transcript unavailable — open to watch" when summary is null.
 */
@Component
public class YouTubeExtractor {

    private static final Logger logger = LoggerFactory.getLogger(YouTubeExtractor.class);

    /**
     * Regex patterns matching YouTube watch and short-link URLs.
     * AC-028: URL detection via regex pattern match.
     */
    public static final Pattern YOUTUBE_URL_PATTERN =
            Pattern.compile("(?:https?://)?(?:www\\.)?(?:youtube\\.com/watch\\?.*v=|youtu\\.be/)([a-zA-Z0-9_-]{11})");

    /**
     * YouTube oEmbed endpoint — returns title and thumbnail without an API key.
     */
    private static final String OEMBED_URL =
            "https://www.youtube.com/oembed?url=%s&format=json";

    /**
     * YouTube Data API v3 captions list endpoint.
     * Requires a valid API key.
     */
    private static final String CAPTIONS_LIST_URL =
            "https://www.googleapis.com/youtube/v3/captions?part=snippet&videoId=%s&key=%s";

    /**
     * YouTube timedtext endpoint for fetching auto-generated captions without OAuth.
     * Used as a fallback when the Data API captions.download requires OAuth.
     */
    private static final String TIMEDTEXT_URL =
            "https://www.youtube.com/api/timedtext?lang=%s&v=%s&fmt=json3";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String youtubeApiKey;
    private final int fetchTimeoutMs;

    public YouTubeExtractor(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            @Value("${app.content-extraction.youtube-api-key:}") String youtubeApiKey,
            @Value("${app.content-extraction.fetch-timeout-ms:8000}") int fetchTimeoutMs) {
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
        this.youtubeApiKey = youtubeApiKey;
        this.fetchTimeoutMs = fetchTimeoutMs;
    }

    /**
     * Extracts the 11-character YouTube video ID from a YouTube URL.
     *
     * AC-028: detects YouTube URL by regex pattern match.
     *
     * @param url the YouTube video URL
     * @return the video ID, or null if the URL does not match
     */
    public String extractVideoId(String url) {
        if (url == null) {
            return null;
        }
        Matcher matcher = YOUTUBE_URL_PATTERN.matcher(url);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * Fetches the video title and thumbnail URL from the YouTube oEmbed endpoint.
     *
     * The oEmbed endpoint does not require an API key.
     *
     * AC-030: title and thumbnailUrl stored on the item record.
     * AC-058: thumbnailUrl still populated even when transcript is unavailable.
     *
     * @param url     the YouTube video URL
     * @param videoId the extracted video ID
     * @return OEmbedData with title and thumbnailUrl; fields may be null on failure
     */
    public OEmbedData fetchOEmbedData(String url, String videoId) {
        try {
            String oembedUrl = String.format(OEMBED_URL, java.net.URLEncoder.encode(url, "UTF-8"));
            logger.debug("Fetching YouTube oEmbed data videoId={}", videoId);

            String responseJson = webClient.get()
                    .uri(URI.create(oembedUrl))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (responseJson != null) {
                JsonNode root = objectMapper.readTree(responseJson);
                String title = root.path("title").asText(null);
                String thumbnailUrl = root.path("thumbnail_url").asText(null);
                logger.info("YouTube oEmbed data fetched videoId={} hasTitle={} hasThumbnail={}",
                        videoId, title != null, thumbnailUrl != null);
                return new OEmbedData(title, thumbnailUrl);
            }
        } catch (Exception exception) {
            logger.warn("YouTube oEmbed fetch failed videoId={} error={}", videoId, exception.getMessage());
        }
        return new OEmbedData(null, null);
    }

    /**
     * Retrieves the transcript text for a YouTube video.
     *
     * Strategy:
     * 1. If a YouTube Data API v3 key is configured: list available captions via the
     *    Data API, then attempt to download the English auto-generated caption track
     *    using the timedtext endpoint.
     * 2. If no API key is configured or the Data API call fails: fall back to directly
     *    probing the timedtext endpoint for "en" and "en-US" language codes.
     *
     * AC-028: transcript retrieved via YouTube Data API v3.
     * AC-029: returned transcript text will be truncated to 3,000 tokens by MOD-003.
     * AC-058: returns null when transcript is unavailable.
     *
     * @param videoId the 11-character YouTube video ID
     * @return transcript text, or null if no transcript is available
     */
    public String fetchTranscript(String videoId) {
        // Try via YouTube Data API v3 if API key is configured
        if (youtubeApiKey != null && !youtubeApiKey.isBlank()) {
            try {
                String transcript = fetchTranscriptViaDataApi(videoId);
                if (transcript != null && !transcript.isBlank()) {
                    return transcript;
                }
            } catch (Exception exception) {
                logger.warn("YouTube Data API transcript fetch failed videoId={} error={}",
                        videoId, exception.getMessage());
            }
        }

        // Fallback: probe timedtext endpoint directly for English captions
        return fetchTranscriptViaTimedtext(videoId);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Lists captions using the YouTube Data API v3 and attempts to download a
     * transcript via the timedtext endpoint.
     *
     * AC-028: YouTube Data API v3 used for caption availability check.
     */
    private String fetchTranscriptViaDataApi(String videoId) {
        logger.debug("Listing YouTube captions via Data API videoId={}", videoId);
        String captionsListUrl = String.format(CAPTIONS_LIST_URL, videoId, youtubeApiKey);

        String captionsJson = webClient.get()
                .uri(captionsListUrl)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        if (captionsJson == null) {
            return null;
        }

        // Parse the captions list to find a suitable English caption track
        try {
            JsonNode root = objectMapper.readTree(captionsJson);
            JsonNode items = root.path("items");

            // Prefer auto-generated English caption track (trackKind=ASR)
            // then fall back to manual English track
            String selectedLang = null;
            for (JsonNode item : items) {
                String trackKind = item.path("snippet").path("trackKind").asText("");
                String language = item.path("snippet").path("language").asText("");
                if (language.startsWith("en")) {
                    if ("asr".equalsIgnoreCase(trackKind)) {
                        selectedLang = language;
                        break; // Prefer ASR (auto-generated)
                    } else if (selectedLang == null) {
                        selectedLang = language; // Keep first English track as fallback
                    }
                }
            }

            if (selectedLang != null) {
                logger.debug("Fetching transcript via timedtext lang={} videoId={}", selectedLang, videoId);
                return fetchTimedtextContent(videoId, selectedLang);
            }
        } catch (Exception exception) {
            logger.warn("Failed to parse YouTube captions list videoId={} error={}",
                    videoId, exception.getMessage());
        }
        return null;
    }

    /**
     * Directly probes the YouTube timedtext endpoint for English captions without
     * using the Data API. Tries "en" and "en-US" language codes.
     */
    private String fetchTranscriptViaTimedtext(String videoId) {
        for (String lang : new String[]{"en", "en-US"}) {
            try {
                String transcript = fetchTimedtextContent(videoId, lang);
                if (transcript != null && !transcript.isBlank()) {
                    logger.info("Transcript fetched via timedtext videoId={} lang={}", videoId, lang);
                    return transcript;
                }
            } catch (Exception exception) {
                logger.debug("Timedtext not available videoId={} lang={} error={}",
                        videoId, lang, exception.getMessage());
            }
        }
        logger.info("No transcript available for videoId={}", videoId);
        return null;
    }

    /**
     * Fetches and parses the JSON3-format timedtext content for a given video and language.
     *
     * The JSON3 format contains an "events" array; each event with a "segs" array
     * has text segments. This method extracts the plain text from all segments.
     *
     * @param videoId the YouTube video ID
     * @param lang    the BCP-47 language code (e.g., "en", "en-US")
     * @return concatenated transcript text, or null if the endpoint returns no content
     */
    private String fetchTimedtextContent(String videoId, String lang) {
        try {
            String timedtextUrl = String.format(TIMEDTEXT_URL, lang, videoId);
            URL url = URI.create(timedtextUrl).toURL();
            URLConnection connection = url.openConnection();
            connection.setConnectTimeout(fetchTimeoutMs);
            connection.setReadTimeout(fetchTimeoutMs);
            connection.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (compatible; TabVault/1.0; +https://tabvault.app)");

            String responseBody;
            try (java.io.InputStream inputStream = connection.getInputStream()) {
                responseBody = new String(inputStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }

            if (responseBody == null || responseBody.isBlank()) {
                return null;
            }

            // Parse JSON3 format transcript
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode events = root.path("events");
            if (events.isMissingNode() || !events.isArray()) {
                return null;
            }

            StringBuilder transcriptBuilder = new StringBuilder();
            for (JsonNode event : events) {
                JsonNode segs = event.path("segs");
                if (segs.isArray()) {
                    for (JsonNode seg : segs) {
                        String text = seg.path("utf8").asText("");
                        if (!text.isBlank()) {
                            transcriptBuilder.append(text);
                        }
                    }
                    transcriptBuilder.append(" ");
                }
            }

            String transcript = transcriptBuilder.toString().trim();
            if (transcript.isBlank()) {
                return null;
            }
            logger.debug("Timedtext content parsed videoId={} lang={} chars={}", videoId, lang, transcript.length());
            return transcript;

        } catch (Exception exception) {
            logger.debug("Timedtext content fetch failed videoId={} lang={} error={}",
                    videoId, lang, exception.getMessage());
            return null;
        }
    }

    /**
     * Value object carrying YouTube oEmbed title and thumbnail URL.
     */
    public record OEmbedData(String title, String thumbnailUrl) {
    }
}
