package com.tabvault.backend.contentanalysis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * HTTP client for the Anthropic Claude API using the tool-use pattern.
 *
 * Implements the two-phase interaction:
 *   Phase 1: Send generate_summary + categorize_content tools with page content.
 *            The model may also request extract_deadlines if time-sensitive content is present.
 *   Phase 2: If the model returned tool_use blocks, process results and optionally
 *            handle extract_deadlines invocation.
 *
 * Token budget: page text is truncated to MAX_INPUT_CHARS (12,000 chars = ~3,000 tokens)
 * before inclusion in the prompt, per production.md Shared Conventions.
 *
 * AC-007: Claude API request sent using tool-use pattern within 5 seconds of job pickup.
 * AC-008: Page text truncated to max 3,000 tokens before API request.
 * AC-011: extract_deadlines tool is only invoked when the model requests it.
 */
@Component
public class ClaudeApiClient {

    private static final Logger logger = LoggerFactory.getLogger(ClaudeApiClient.class);

    /**
     * Maximum characters for page text included in the API request.
     * Using characters / 4 = estimated tokens (production.md Shared Conventions).
     * 3,000 tokens × 4 chars/token = 12,000 characters.
     */
    static final int MAX_INPUT_CHARS = 12_000;

    private static final String ANTHROPIC_API_URL = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final int MAX_TOKENS = 1024;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;

    public ClaudeApiClient(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            @Value("${app.content-analysis.anthropic-api-key}") String apiKey,
            @Value("${app.content-analysis.model:claude-sonnet-4-20250514}") String model) {
        this.webClient = webClientBuilder
                .baseUrl(ANTHROPIC_API_URL)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
    }

    /**
     * Calls the Claude API with the tool-use pattern to analyze a saved item.
     *
     * AC-008: pageText is truncated to MAX_INPUT_CHARS before sending.
     * AC-011: extract_deadlines is only called when the model requests it.
     *
     * @param itemTitle the title of the saved item (for context)
     * @param itemUrl   the URL of the saved item (for context)
     * @param pageText  the extracted page text (may be null for items without text)
     * @param existingCategories the user's existing category names (for prompt context)
     * @return structured analysis result from the tool invocations
     */
    public AnalysisResult analyze(String itemTitle, String itemUrl, String pageText,
                                  List<String> existingCategories) {
        String truncatedText = truncateToTokenBudget(pageText);

        // Build the prompt system message
        String systemPrompt = buildSystemPrompt(existingCategories);

        // Build user message with item context
        String userMessage = buildUserMessage(itemTitle, itemUrl, truncatedText);

        // Define the available tools
        List<Map<String, Object>> tools = buildTools();

        // Build request body
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "max_tokens", MAX_TOKENS,
                "system", systemPrompt,
                "tools", tools,
                "messages", List.of(Map.of("role", "user", "content", userMessage))
        );

        try {
            String requestJson = objectMapper.writeValueAsString(requestBody);
            logger.debug("Sending Claude API request itemUrl={} textLength={}",
                    itemUrl, truncatedText != null ? truncatedText.length() : 0);

            String responseJson = webClient.post()
                    .uri("")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", ANTHROPIC_VERSION)
                    .bodyValue(requestJson)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return parseToolUseResponse(responseJson, itemTitle, itemUrl);

        } catch (WebClientResponseException exception) {
            throw new ClaudeApiException("Claude API HTTP error status=" + exception.getStatusCode()
                    + " body=" + exception.getResponseBodyAsString(), exception);
        } catch (JsonProcessingException exception) {
            throw new ClaudeApiException("Failed to serialize Claude API request", exception);
        }
    }

    /**
     * Truncates page text to MAX_INPUT_CHARS characters.
     * Uses characters / 4 = estimated tokens per production.md Shared Conventions.
     *
     * AC-008: max 3,000 tokens = 12,000 characters.
     */
    String truncateToTokenBudget(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        if (text.length() <= MAX_INPUT_CHARS) {
            return text;
        }
        logger.warn("Page text truncated to token budget originalLength={} truncatedLength={}",
                text.length(), MAX_INPUT_CHARS);
        return text.substring(0, MAX_INPUT_CHARS);
    }

    /**
     * Builds the system prompt, including the user's existing categories for context.
     */
    private String buildSystemPrompt(List<String> existingCategories) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a content analysis assistant for TabVault, a tab management application. ");
        sb.append("Your task is to analyze saved web page content and produce structured metadata.\n\n");
        sb.append("You must call the generate_summary tool and the categorize_content tool for every request. ");
        sb.append("You should call the extract_deadlines tool ONLY if the content contains specific dates, ");
        sb.append("deadlines, event registrations, application deadlines, or other time-sensitive information. ");
        sb.append("Do not call extract_deadlines for pages with no date-related content.\n\n");

        if (existingCategories != null && !existingCategories.isEmpty()) {
            sb.append("The user's existing categories are: ");
            sb.append(String.join(", ", existingCategories));
            sb.append(". Prefer suggesting one of these if it fits well, or suggest a new category name if none fit.");
        } else {
            sb.append("The user has no existing categories. Suggest an appropriate category name.");
        }

        return sb.toString();
    }

    /**
     * Builds the user message containing item metadata and (optionally truncated) page text.
     */
    private String buildUserMessage(String title, String url, String pageText) {
        StringBuilder sb = new StringBuilder();
        sb.append("Please analyze the following saved web page:\n\n");
        sb.append("Title: ").append(title != null ? title : "(no title)").append("\n");
        sb.append("URL: ").append(url != null ? url : "(no url)").append("\n\n");

        if (pageText != null && !pageText.isBlank()) {
            sb.append("Page content:\n").append(pageText);
        } else {
            sb.append("(No page content available — analyze based on title and URL only)");
        }

        return sb.toString();
    }

    /**
     * Defines the three tools available for the model to use.
     *
     * Tools:
     * - generate_summary: produces a concise summary of the page content
     * - categorize_content: suggests a category name and content type
     * - extract_deadlines: (conditional) returns a list of detected deadlines
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> buildTools() {
        Map<String, Object> summaryTool = Map.of(
                "name", "generate_summary",
                "description", "Generate a concise 1-3 sentence summary of the saved page content. "
                        + "Focus on the key information the user will want to recall.",
                "input_schema", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "summary", Map.of(
                                        "type", "string",
                                        "description", "A concise 1-3 sentence summary of the page content"
                                )
                        ),
                        "required", List.of("summary")
                )
        );

        Map<String, Object> categorizeTool = Map.of(
                "name", "categorize_content",
                "description", "Suggest a category name and identify the content type for the saved page.",
                "input_schema", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "suggested_category", Map.of(
                                        "type", "string",
                                        "description", "A short category name (e.g., 'Technology', 'Research', 'Finance')"
                                ),
                                "content_type", Map.of(
                                        "type", "string",
                                        "description", "The type of content: one of 'article', 'video', 'documentation', "
                                                + "'tool', 'social', 'shopping', 'news', 'reference', or 'other'"
                                )
                        ),
                        "required", List.of("suggested_category", "content_type")
                )
        );

        Map<String, Object> deadlinesTool = Map.of(
                "name", "extract_deadlines",
                "description", "Extract time-sensitive dates and deadlines from the page content. "
                        + "Only call this tool if the content contains specific dates, deadlines, "
                        + "event registrations, application deadlines, or other time-sensitive information. "
                        + "Do NOT call this for pages with no date content.",
                "input_schema", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "deadlines", Map.of(
                                        "type", "array",
                                        "items", Map.of(
                                                "type", "object",
                                                "properties", Map.of(
                                                        "date", Map.of(
                                                                "type", "string",
                                                                "description", "The deadline date in ISO 8601 format (YYYY-MM-DD)"
                                                        ),
                                                        "label", Map.of(
                                                                "type", "string",
                                                                "description", "A short description of the deadline (e.g., 'Application deadline', 'Registration closes')"
                                                        ),
                                                        "urgency", Map.of(
                                                                "type", "string",
                                                                "enum", List.of("low", "medium", "high"),
                                                                "description", "Urgency level based on how soon the deadline is and how critical it is"
                                                        )
                                                ),
                                                "required", List.of("date", "label", "urgency")
                                        ),
                                        "description", "List of detected deadlines"
                                )
                        ),
                        "required", List.of("deadlines")
                )
        );

        return List.of(summaryTool, categorizeTool, deadlinesTool);
    }

    /**
     * Parses the tool_use response from the Claude API and extracts structured results.
     *
     * Processes tool_use blocks for:
     * - generate_summary → summary field
     * - categorize_content → suggestedCategory, contentType fields
     * - extract_deadlines → deadlines list (AC-011: only present when model invoked this tool)
     */
    private AnalysisResult parseToolUseResponse(String responseJson, String itemTitle, String itemUrl) {
        if (responseJson == null) {
            throw new ClaudeApiException("Empty response from Claude API");
        }

        try {
            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode content = root.path("content");

            String summary = null;
            String suggestedCategory = null;
            String contentType = null;
            List<AnalysisResult.DetectedDeadline> deadlines = new ArrayList<>();

            for (JsonNode block : content) {
                String blockType = block.path("type").asText();
                if (!"tool_use".equals(blockType)) {
                    continue;
                }

                String toolName = block.path("name").asText();
                JsonNode input = block.path("input");

                switch (toolName) {
                    case "generate_summary" -> {
                        summary = input.path("summary").asText(null);
                        logger.debug("Tool generate_summary parsed itemUrl={}", itemUrl);
                    }
                    case "categorize_content" -> {
                        suggestedCategory = input.path("suggested_category").asText(null);
                        contentType = input.path("content_type").asText(null);
                        logger.debug("Tool categorize_content parsed itemUrl={} category={} type={}",
                                itemUrl, suggestedCategory, contentType);
                    }
                    case "extract_deadlines" -> {
                        // AC-011: only processed when the model chose to invoke this tool
                        JsonNode deadlineArray = input.path("deadlines");
                        for (JsonNode deadline : deadlineArray) {
                            try {
                                LocalDate date = LocalDate.parse(deadline.path("date").asText());
                                String label = deadline.path("label").asText("Deadline");
                                UrgencyLevel urgency = parseUrgency(deadline.path("urgency").asText("medium"));
                                deadlines.add(new AnalysisResult.DetectedDeadline(date, label, urgency));
                            } catch (Exception parseException) {
                                logger.warn("Failed to parse deadline entry itemUrl={} error={}",
                                        itemUrl, parseException.getMessage());
                            }
                        }
                        logger.debug("Tool extract_deadlines parsed itemUrl={} deadlineCount={}",
                                itemUrl, deadlines.size());
                    }
                    default -> logger.debug("Unrecognized tool in response toolName={} itemUrl={}", toolName, itemUrl);
                }
            }

            // Fallback if model did not invoke a required tool
            if (summary == null) {
                summary = itemTitle != null ? "Saved page: " + itemTitle : "No summary available";
                logger.warn("generate_summary tool not invoked by model itemUrl={}", itemUrl);
            }
            if (suggestedCategory == null) {
                suggestedCategory = "General";
                logger.warn("categorize_content tool not invoked by model itemUrl={}", itemUrl);
            }
            if (contentType == null) {
                contentType = "other";
            }

            return new AnalysisResult(summary, suggestedCategory, contentType, deadlines);

        } catch (JsonProcessingException exception) {
            throw new ClaudeApiException("Failed to parse Claude API response", exception);
        }
    }

    /**
     * Parses an urgency string from the API response to a UrgencyLevel enum.
     * Defaults to MEDIUM for unrecognized values.
     */
    private UrgencyLevel parseUrgency(String urgencyStr) {
        return switch (urgencyStr.toLowerCase()) {
            case "low" -> UrgencyLevel.LOW;
            case "high" -> UrgencyLevel.HIGH;
            default -> UrgencyLevel.MEDIUM;
        };
    }

    // -------------------------------------------------------------------------
    // Internal DTOs for JSON deserialization (not exposed outside this class)
    // -------------------------------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ApiResponse(
            @JsonProperty("id") String id,
            @JsonProperty("type") String type,
            @JsonProperty("content") List<ContentBlock> content,
            @JsonProperty("stop_reason") String stopReason
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ContentBlock(
            @JsonProperty("type") String type,
            @JsonProperty("name") String name,
            @JsonProperty("input") Map<String, Object> input
    ) {
    }
}
