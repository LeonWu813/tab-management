package com.tabvault.backend.contentanalysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ClaudeApiClient.
 *
 * Tests truncateToTokenBudget directly (package-private for testing).
 * HTTP call tests use a mocked WebClient.
 *
 * AC-008: Page text truncated to max 3,000 tokens (12,000 chars).
 */
@ExtendWith(MockitoExtension.class)
class ClaudeApiClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private WebClient.Builder webClientBuilder;

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    // -------------------------------------------------------------------------
    // AC-008: Token budget truncation
    // -------------------------------------------------------------------------

    /**
     * Helper to create a ClaudeApiClient with a mocked WebClient.
     */
    @SuppressWarnings("unchecked")
    private ClaudeApiClient createClient() {
        when(webClientBuilder.baseUrl(anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.defaultHeader(anyString(), anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);
        return new ClaudeApiClient(webClientBuilder, objectMapper,
                "test-api-key", "claude-test-model");
    }

    @Test
    void truncateToTokenBudget_whenTextBelowLimit_returnsOriginal() {
        ClaudeApiClient client = createClient();
        String shortText = "a".repeat(100);

        String result = client.truncateToTokenBudget(shortText);

        assertThat(result).isEqualTo(shortText);
        assertThat(result.length()).isEqualTo(100);
    }

    @Test
    void truncateToTokenBudget_whenTextExceedsLimit_truncatesExactly() {
        ClaudeApiClient client = createClient();
        // Build a string exceeding MAX_INPUT_CHARS (12,000 chars)
        String longText = "x".repeat(ClaudeApiClient.MAX_INPUT_CHARS + 500);

        String result = client.truncateToTokenBudget(longText);

        // AC-008: must not exceed 12,000 characters = ~3,000 tokens
        assertThat(result.length()).isEqualTo(ClaudeApiClient.MAX_INPUT_CHARS);
    }

    @Test
    void truncateToTokenBudget_whenTextAtExactLimit_returnsUnchanged() {
        ClaudeApiClient client = createClient();
        String exactText = "y".repeat(ClaudeApiClient.MAX_INPUT_CHARS);

        String result = client.truncateToTokenBudget(exactText);

        assertThat(result.length()).isEqualTo(ClaudeApiClient.MAX_INPUT_CHARS);
        assertThat(result).isEqualTo(exactText);
    }

    @Test
    void truncateToTokenBudget_whenTextIsNull_returnsEmpty() {
        ClaudeApiClient client = createClient();

        String result = client.truncateToTokenBudget(null);

        assertThat(result).isEmpty();
    }

    @Test
    void truncateToTokenBudget_whenTextIsBlank_returnsEmpty() {
        ClaudeApiClient client = createClient();

        String result = client.truncateToTokenBudget("   ");

        assertThat(result).isEmpty();
    }

    @Test
    void maxInputChars_equals12000_representingApproximately3000Tokens() {
        // Verify the constant matches the token budget spec:
        // production.md: characters / 4 = estimated tokens, max 3,000 tokens
        assertThat(ClaudeApiClient.MAX_INPUT_CHARS).isEqualTo(12_000);
    }

    // -------------------------------------------------------------------------
    // HTTP call tests using mocked WebClient
    // -------------------------------------------------------------------------

    @Test
    void analyze_whenApiReturnsToolUseBlocks_parsesResultCorrectly() {
        ClaudeApiClient client = createClient();

        // Simulate Claude API response with generate_summary + categorize_content tool blocks
        String apiResponse = """
                {
                  "id": "msg_test",
                  "type": "message",
                  "stop_reason": "tool_use",
                  "content": [
                    {
                      "type": "tool_use",
                      "id": "tu_1",
                      "name": "generate_summary",
                      "input": {
                        "summary": "This article covers the basics of machine learning."
                      }
                    },
                    {
                      "type": "tool_use",
                      "id": "tu_2",
                      "name": "categorize_content",
                      "input": {
                        "suggested_category": "Technology",
                        "content_type": "article"
                      }
                    }
                  ]
                }
                """;

        setupMockedWebClientResponse(apiResponse);

        AnalysisResult result = client.analyze("ML Basics", "https://example.com/ml",
                "Machine learning content...", List.of());

        assertThat(result.summary()).isEqualTo("This article covers the basics of machine learning.");
        assertThat(result.suggestedCategory()).isEqualTo("Technology");
        assertThat(result.contentType()).isEqualTo("article");
        assertThat(result.deadlines()).isEmpty();
    }

    @Test
    void analyze_whenApiReturnsExtractDeadlinesTool_parsesDeadlines() {
        ClaudeApiClient client = createClient();

        String apiResponse = """
                {
                  "id": "msg_test",
                  "type": "message",
                  "stop_reason": "tool_use",
                  "content": [
                    {
                      "type": "tool_use",
                      "id": "tu_1",
                      "name": "generate_summary",
                      "input": {
                        "summary": "Application deadline approaching for graduate program."
                      }
                    },
                    {
                      "type": "tool_use",
                      "id": "tu_2",
                      "name": "categorize_content",
                      "input": {
                        "suggested_category": "Education",
                        "content_type": "article"
                      }
                    },
                    {
                      "type": "tool_use",
                      "id": "tu_3",
                      "name": "extract_deadlines",
                      "input": {
                        "deadlines": [
                          {
                            "date": "2026-12-01",
                            "label": "Graduate program application deadline",
                            "urgency": "high"
                          }
                        ]
                      }
                    }
                  ]
                }
                """;

        setupMockedWebClientResponse(apiResponse);

        AnalysisResult result = client.analyze("Grad School Application",
                "https://university.edu/apply", "Apply by December 1st...", List.of());

        // AC-011: deadlines only present when model invoked extract_deadlines
        assertThat(result.deadlines()).hasSize(1);
        AnalysisResult.DetectedDeadline deadline = result.deadlines().get(0);
        assertThat(deadline.date().toString()).isEqualTo("2026-12-01");
        assertThat(deadline.label()).isEqualTo("Graduate program application deadline");
        assertThat(deadline.urgency()).isEqualTo(UrgencyLevel.HIGH);
    }

    @Test
    void analyze_whenNoExtractDeadlinesTool_returnsEmptyDeadlines() {
        ClaudeApiClient client = createClient();

        // Response without extract_deadlines tool invocation
        String apiResponse = """
                {
                  "id": "msg_test",
                  "type": "message",
                  "stop_reason": "tool_use",
                  "content": [
                    {
                      "type": "tool_use",
                      "id": "tu_1",
                      "name": "generate_summary",
                      "input": { "summary": "A regular article about cooking." }
                    },
                    {
                      "type": "tool_use",
                      "id": "tu_2",
                      "name": "categorize_content",
                      "input": {
                        "suggested_category": "Food",
                        "content_type": "article"
                      }
                    }
                  ]
                }
                """;

        setupMockedWebClientResponse(apiResponse);

        AnalysisResult result = client.analyze("Cooking Tips",
                "https://example.com/cooking", "Cooking content...", List.of());

        // AC-011: no deadlines when extract_deadlines was not invoked
        assertThat(result.deadlines()).isEmpty();
    }

    @Test
    void analyze_whenApiReturnsNullResponse_throwsClaudeApiException() {
        ClaudeApiClient client = createClient();

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        // Use doReturn to bypass WebClient's complex generic chain
        doReturn(requestBodySpec).when(requestBodySpec).header(anyString(), anyString());
        doReturn(requestBodySpec).when(requestBodySpec).bodyValue(any());
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.empty()); // null from Mono.empty()

        assertThatThrownBy(() ->
                client.analyze("Title", "https://example.com", "content", List.of()))
                .isInstanceOf(ClaudeApiException.class);
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private void setupMockedWebClientResponse(String responseBody) {
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        // Use doReturn to bypass WebClient's complex generic chain in the header() return type
        doReturn(requestBodySpec).when(requestBodySpec).header(anyString(), anyString());
        doReturn(requestBodySpec).when(requestBodySpec).bodyValue(any());
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(responseBody));
    }
}
