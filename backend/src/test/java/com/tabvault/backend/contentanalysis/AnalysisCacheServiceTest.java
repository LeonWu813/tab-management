package com.tabvault.backend.contentanalysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for AnalysisCacheService.
 *
 * Tests cover:
 * - AC-009: Cache hit returns stored result without API call
 * - Fail-open behavior when Redis is unavailable
 * - Cache write after successful API call
 */
@ExtendWith(MockitoExtension.class)
class AnalysisCacheServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AnalysisCacheService cacheService;

    @BeforeEach
    void setUp() {
        // Use lenient() because some tests do not trigger Redis interactions
        // (e.g., null/blank URL and null result early-return paths).
        org.mockito.Mockito.lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        cacheService = new AnalysisCacheService(redisTemplate, objectMapper, 168L);
    }

    // -------------------------------------------------------------------------
    // Cache get
    // -------------------------------------------------------------------------

    @Test
    void get_whenCacheHit_returnsAnalysisResult() throws Exception {
        AnalysisResult storedResult = new AnalysisResult("Cached summary", "Tech", "article", List.of());
        String json = objectMapper.writeValueAsString(storedResult);
        when(valueOperations.get("analysis:url:https://example.com")).thenReturn(json);

        Optional<AnalysisResult> result = cacheService.get("https://example.com");

        assertThat(result).isPresent();
        assertThat(result.get().summary()).isEqualTo("Cached summary");
        assertThat(result.get().suggestedCategory()).isEqualTo("Tech");
    }

    @Test
    void get_whenCacheMiss_returnsEmpty() {
        when(valueOperations.get(anyString())).thenReturn(null);

        Optional<AnalysisResult> result = cacheService.get("https://example.com/missing");

        assertThat(result).isEmpty();
    }

    @Test
    void get_whenUrlIsNull_returnsEmpty() {
        Optional<AnalysisResult> result = cacheService.get(null);

        assertThat(result).isEmpty();
    }

    @Test
    void get_whenUrlIsBlank_returnsEmpty() {
        Optional<AnalysisResult> result = cacheService.get("  ");

        assertThat(result).isEmpty();
    }

    @Test
    void get_whenRedisThrows_returnsEmptyFailOpen() {
        when(valueOperations.get(anyString())).thenThrow(new RuntimeException("Redis unavailable"));

        // Fail-open: should return empty, not throw
        Optional<AnalysisResult> result = cacheService.get("https://example.com");

        assertThat(result).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Cache put
    // -------------------------------------------------------------------------

    @Test
    void put_writesResultToRedisWithTtl() throws Exception {
        AnalysisResult result = new AnalysisResult("Summary", "Work", "article", List.of());

        cacheService.put("https://example.com", result);

        verify(valueOperations).set(eq("analysis:url:https://example.com"), anyString(), any());
    }

    @Test
    void put_whenRedisThrows_doesNotPropagate() {
        AnalysisResult result = new AnalysisResult("Summary", "Tech", "article", List.of());
        doThrow(new RuntimeException("Redis write failure"))
                .when(valueOperations).set(anyString(), anyString(), any());

        // Fail-open: should not throw
        cacheService.put("https://example.com", result);
    }

    @Test
    void put_whenUrlIsNull_doesNothing() {
        AnalysisResult result = new AnalysisResult("Summary", "Tech", "article", List.of());

        // Should not throw and should not interact with Redis
        cacheService.put(null, result);

        verify(valueOperations, org.mockito.Mockito.never()).set(anyString(), anyString(), any());
    }

    @Test
    void put_whenResultIsNull_doesNothing() {
        cacheService.put("https://example.com", null);

        verify(valueOperations, org.mockito.Mockito.never()).set(anyString(), anyString(), any());
    }
}
