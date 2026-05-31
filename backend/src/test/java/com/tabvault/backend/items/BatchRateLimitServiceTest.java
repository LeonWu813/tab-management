package com.tabvault.backend.items;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for BatchRateLimitService — Redis operations are mocked.
 */
@ExtendWith(MockitoExtension.class)
class BatchRateLimitServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private BatchRateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        rateLimitService = new BatchRateLimitService(redisTemplate);
        // Inject @Value fields via ReflectionTestUtils
        ReflectionTestUtils.setField(rateLimitService, "maxUrls", 100);
        ReflectionTestUtils.setField(rateLimitService, "windowMinutes", 60);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("AC-065: allows request when current count plus new URLs is within limit")
    void checkAndIncrement_withinLimit_doesNotThrow() {
        Long userId = 1L;
        // current count is 50, adding 30 = 80, which is within limit of 100
        when(valueOperations.increment("batch_rate_limit:1", 0)).thenReturn(50L);
        when(valueOperations.increment("batch_rate_limit:1", 30)).thenReturn(80L);

        assertThatCode(() -> rateLimitService.checkAndIncrement(userId, 30))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("AC-065: throws BatchRateLimitExceededException when limit would be exceeded")
    void checkAndIncrement_exceedsLimit_throwsException() {
        Long userId = 1L;
        // current count is 90, adding 15 = 105, which exceeds limit of 100
        when(valueOperations.increment("batch_rate_limit:1", 0)).thenReturn(90L);

        assertThatThrownBy(() -> rateLimitService.checkAndIncrement(userId, 15))
                .isInstanceOf(BatchRateLimitExceededException.class)
                .hasMessageContaining("100");
    }

    @Test
    @DisplayName("AC-065: throws when exactly at limit (100 + 1 = 101 exceeds 100)")
    void checkAndIncrement_atExactLimit_throwsException() {
        Long userId = 1L;
        when(valueOperations.increment("batch_rate_limit:1", 0)).thenReturn(100L);

        assertThatThrownBy(() -> rateLimitService.checkAndIncrement(userId, 1))
                .isInstanceOf(BatchRateLimitExceededException.class);
    }

    @Test
    @DisplayName("sets TTL on first increment when key is newly created")
    void checkAndIncrement_firstRequest_setsTtl() {
        Long userId = 1L;
        // increment(key, 0) returns 0 (key doesn't exist), increment(key, 5) returns 5 (first use)
        when(valueOperations.increment("batch_rate_limit:1", 0)).thenReturn(0L);
        when(valueOperations.increment("batch_rate_limit:1", 5)).thenReturn(5L);

        rateLimitService.checkAndIncrement(userId, 5);

        verify(redisTemplate).expire("batch_rate_limit:1", Duration.ofMinutes(60));
    }

    @Test
    @DisplayName("does not reset TTL when key already exists (not first increment)")
    void checkAndIncrement_existingKey_doesNotResetTtl() {
        Long userId = 1L;
        // current count is 20, adding 5 = 25 (within limit). newCount = 25, not equal to urlCount=5
        when(valueOperations.increment("batch_rate_limit:1", 0)).thenReturn(20L);
        when(valueOperations.increment("batch_rate_limit:1", 5)).thenReturn(25L);

        rateLimitService.checkAndIncrement(userId, 5);

        verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("allows request when Redis throws an exception (fail-open behavior)")
    void checkAndIncrement_redisUnavailable_allowsRequest() {
        Long userId = 1L;
        when(valueOperations.increment(anyString(), anyLong()))
                .thenThrow(new RuntimeException("Redis connection refused"));

        // Should not throw — fail-open
        assertThatCode(() -> rateLimitService.checkAndIncrement(userId, 10))
                .doesNotThrowAnyException();
    }
}
