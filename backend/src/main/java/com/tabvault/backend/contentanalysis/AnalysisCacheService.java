package com.tabvault.backend.contentanalysis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Redis-backed URL deduplication cache for content analysis results.
 *
 * Before calling the Claude API, the pipeline checks this cache for the URL.
 * If a cached result is found, it is returned immediately without an API call.
 * After a successful API call, the result is written to the cache.
 *
 * AC-009: The system shall skip the Claude API call and return the cached analysis
 * result when the saved URL matches an entry in the URL deduplication cache.
 *
 * Fail-open: if Redis is unavailable, cache misses are logged and the pipeline
 * proceeds with the API call rather than failing the job.
 */
@Service
public class AnalysisCacheService {

    private static final Logger logger = LoggerFactory.getLogger(AnalysisCacheService.class);

    /**
     * Redis key prefix for cached analysis results.
     * Full key: "analysis:url:<url>"
     */
    private static final String CACHE_KEY_PREFIX = "analysis:url:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final long cacheTtlHours;

    public AnalysisCacheService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${app.content-analysis.cache-ttl-hours:168}") long cacheTtlHours) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.cacheTtlHours = cacheTtlHours;
    }

    /**
     * Looks up a cached analysis result for the given URL.
     *
     * @param url the saved item URL
     * @return an Optional containing the cached result, or empty on cache miss or Redis failure
     */
    public Optional<AnalysisResult> get(String url) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        String key = buildKey(url);
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) {
                return Optional.empty();
            }
            AnalysisResult result = objectMapper.readValue(json, AnalysisResult.class);
            logger.debug("Cache hit for URL analysis url={}", url);
            return Optional.of(result);
        } catch (Exception exception) {
            // Fail-open: Redis unavailability or deserialization error is non-fatal
            logger.warn("Failed to read analysis cache url={} error={}", url, exception.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Writes an analysis result to the cache for the given URL.
     *
     * @param url    the saved item URL
     * @param result the analysis result to cache
     */
    public void put(String url, AnalysisResult result) {
        if (url == null || url.isBlank() || result == null) {
            return;
        }
        String key = buildKey(url);
        try {
            String json = objectMapper.writeValueAsString(result);
            redisTemplate.opsForValue().set(key, json, Duration.ofHours(cacheTtlHours));
            logger.debug("Cached analysis result url={} ttlHours={}", url, cacheTtlHours);
        } catch (JsonProcessingException exception) {
            logger.warn("Failed to serialize analysis result for cache url={} error={}",
                    url, exception.getMessage());
        } catch (Exception exception) {
            // Fail-open: Redis write failure is non-fatal
            logger.warn("Failed to write analysis cache url={} error={}", url, exception.getMessage());
        }
    }

    private String buildKey(String url) {
        return CACHE_KEY_PREFIX + url;
    }
}
