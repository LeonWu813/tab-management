package com.tabvault.backend.items;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tabvault.backend.shared.error.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-layer tests using standalone MockMvc — no Spring context, no database.
 * Security principal (Long userId = 1L) is injected manually into the SecurityContext.
 */
@ExtendWith(MockitoExtension.class)
class ItemControllerTest {

    @Mock
    private ItemService itemService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private static final Long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        ItemController controller = new ItemController(itemService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ItemExceptionHandler(), new GlobalExceptionHandler())
                .setCustomArgumentResolvers(
                        new AuthenticationPrincipalArgumentResolver(),
                        new PageableHandlerMethodArgumentResolver())
                .build();

        // Simulate authenticated user: JwtAuthenticationFilter sets Long userId as principal
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(USER_ID, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // -------------------------------------------------------------------------
    // POST /api/items
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("AC-001/AC-002: saveTab returns HTTP 200 with item record for a new URL")
    void saveTab_newUrl_returns200WithItemRecord() throws Exception {
        ItemResponse response = new ItemResponse(
                1L, "LINK", "https://example.com", "Example Page",
                "https://example.com/favicon.ico", null, null, null,
                false, false, null, OffsetDateTime.now());

        when(itemService.saveTab(eq(USER_ID), any(SaveItemRequest.class))).thenReturn(response);

        String body = objectMapper.writeValueAsString(
                Map.of("url", "https://example.com",
                       "title", "Example Page",
                       "faviconUrl", "https://example.com/favicon.ico"));

        mockMvc.perform(post("/api/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://example.com"))
                .andExpect(jsonPath("$.title").value("Example Page"))
                .andExpect(jsonPath("$.itemType").value("LINK"))
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @DisplayName("AC-004: saveTab returns HTTP 200 with existing item when URL is a duplicate")
    void saveTab_duplicateUrl_returns200WithExistingItem() throws Exception {
        ItemResponse existingResponse = new ItemResponse(
                42L, "LINK", "https://example.com", "Example Page",
                null, "A summary", null, null,
                false, false, null, OffsetDateTime.now());

        when(itemService.saveTab(eq(USER_ID), any(SaveItemRequest.class))).thenReturn(existingResponse);

        String body = objectMapper.writeValueAsString(
                Map.of("url", "https://example.com", "title", "Example Page"));

        mockMvc.perform(post("/api/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.summary").value("A summary"));
    }

    @Test
    @DisplayName("saveTab returns HTTP 400 when URL is blank")
    void saveTab_blankUrl_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("url", "", "title", "Example Page"));

        mockMvc.perform(post("/api/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("saveTab returns HTTP 400 when title is blank")
    void saveTab_blankTitle_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("url", "https://example.com", "title", ""));

        mockMvc.perform(post("/api/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    // -------------------------------------------------------------------------
    // POST /api/items/batch
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("AC-005: batchSave returns HTTP 202 immediately with tabsEnqueued count")
    void batchSave_validRequest_returns202WithCount() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("tabs", List.of(
                        Map.of("url", "https://a.com", "title", "A"),
                        Map.of("url", "https://b.com", "title", "B"))));

        mockMvc.perform(post("/api/items/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.tabsEnqueued").value(2));
    }

    @Test
    @DisplayName("AC-065: batchSave returns HTTP 429 when rate limit is exceeded")
    void batchSave_rateLimitExceeded_returns429() throws Exception {
        doThrow(new BatchRateLimitExceededException("Rate limit exceeded"))
                .when(itemService).enqueueBatchSave(eq(USER_ID), any(BatchSaveRequest.class));

        String body = objectMapper.writeValueAsString(
                Map.of("tabs", List.of(
                        Map.of("url", "https://a.com", "title", "A"))));

        mockMvc.perform(post("/api/items/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("BATCH_RATE_LIMIT_EXCEEDED"));
    }

    @Test
    @DisplayName("batchSave returns HTTP 400 when tabs list is empty")
    void batchSave_emptyTabs_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("tabs", List.of()));

        mockMvc.perform(post("/api/items/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    // -------------------------------------------------------------------------
    // POST /api/items/notes
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("AC-025: saveNote returns HTTP 201 with the created note item")
    void saveNote_validBody_returns201WithNoteItem() throws Exception {
        ItemResponse response = new ItemResponse(
                5L, "NOTE", null, null, null, null,
                "My note body", null,
                false, false, null, OffsetDateTime.now());

        when(itemService.saveNote(eq(USER_ID), any(SaveNoteRequest.class))).thenReturn(response);

        String body = objectMapper.writeValueAsString(Map.of("noteBody", "My note body"));

        mockMvc.perform(post("/api/items/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.itemType").value("NOTE"))
                .andExpect(jsonPath("$.noteBody").value("My note body"))
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    @DisplayName("saveNote returns HTTP 400 when noteBody is blank")
    void saveNote_blankBody_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("noteBody", ""));

        mockMvc.perform(post("/api/items/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    // -------------------------------------------------------------------------
    // GET /api/items
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("AC-027: listItems with query param returns search results including notes")
    void listItems_withQuery_returnsSearchResults() throws Exception {
        ItemResponse noteResult = new ItemResponse(
                7L, "NOTE", null, null, null, null,
                "kotlin tutorial tips", null,
                false, false, null, OffsetDateTime.now());
        Page<ItemResponse> page = new PageImpl<>(List.of(noteResult), PageRequest.of(0, 20), 1);

        when(itemService.listItems(eq(USER_ID), eq("kotlin"), any())).thenReturn(page);

        mockMvc.perform(get("/api/items").param("query", "kotlin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].itemType").value("NOTE"))
                .andExpect(jsonPath("$.content[0].noteBody").value("kotlin tutorial tips"));
    }

    @Test
    @DisplayName("listItems without query returns all items")
    void listItems_noQuery_returnsAllItems() throws Exception {
        ItemResponse item = new ItemResponse(
                1L, "LINK", "https://example.com", "Example", null, null, null, null,
                false, false, null, OffsetDateTime.now());
        Page<ItemResponse> page = new PageImpl<>(List.of(item), PageRequest.of(0, 20), 1);

        when(itemService.listItems(eq(USER_ID), eq(null), any())).thenReturn(page);

        mockMvc.perform(get("/api/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].url").value("https://example.com"));
    }

    // -------------------------------------------------------------------------
    // POST /api/categories
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("AC-018: createCategory returns HTTP 201 with created category record")
    void createCategory_validRequest_returns201WithCategory() throws Exception {
        CategoryResponse response = new CategoryResponse(
                3L, "Work", "#ff5733", "briefcase", 0, OffsetDateTime.now());

        when(itemService.createCategory(eq(USER_ID), any(CreateCategoryRequest.class))).thenReturn(response);

        String body = objectMapper.writeValueAsString(
                Map.of("name", "Work", "color", "#ff5733", "icon", "briefcase"));

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.name").value("Work"))
                .andExpect(jsonPath("$.color").value("#ff5733"))
                .andExpect(jsonPath("$.icon").value("briefcase"));
    }

    @Test
    @DisplayName("AC-018: createCategory returns HTTP 400 when color is not a valid hex code")
    void createCategory_invalidColor_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("name", "Work", "color", "red"));

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("AC-018: createCategory returns HTTP 400 when name exceeds 50 characters")
    void createCategory_nameTooLong_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("name", "A".repeat(51), "color", "#ff5733"));

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    // -------------------------------------------------------------------------
    // DELETE /api/categories/{id}
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("AC-019: deleteCategory returns HTTP 204 and does not throw")
    void deleteCategory_existingCategory_returns204() throws Exception {
        doNothing().when(itemService).deleteCategory(USER_ID, 3L);

        mockMvc.perform(delete("/api/categories/3"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("AC-019: deleteCategory returns HTTP 404 when category not found")
    void deleteCategory_notFound_returns404() throws Exception {
        doThrow(new CategoryNotFoundException("Category not found: 999"))
                .when(itemService).deleteCategory(USER_ID, 999L);

        mockMvc.perform(delete("/api/categories/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CATEGORY_NOT_FOUND"));
    }

    // -------------------------------------------------------------------------
    // PATCH /api/items/{id}/category
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("AC-020: reassignCategory returns HTTP 200 with updated item record")
    void reassignCategory_validRequest_returns200WithUpdatedItem() throws Exception {
        ItemResponse response = new ItemResponse(
                10L, "LINK", "https://example.com", "Example", null, null, null, 3L,
                false, false, null, OffsetDateTime.now());

        when(itemService.reassignCategory(eq(USER_ID), eq(10L), eq(3L))).thenReturn(response);

        String body = objectMapper.writeValueAsString(Map.of("targetCategoryId", 3));

        mockMvc.perform(patch("/api/items/10/category")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryId").value(3));
    }

    @Test
    @DisplayName("reassignCategory returns HTTP 404 when item not found")
    void reassignCategory_itemNotFound_returns404() throws Exception {
        when(itemService.reassignCategory(eq(USER_ID), eq(999L), any()))
                .thenThrow(new ItemNotFoundException("Item not found: 999"));

        String body = objectMapper.writeValueAsString(Map.of("targetCategoryId", 3));

        mockMvc.perform(patch("/api/items/999/category")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ITEM_NOT_FOUND"));
    }

    // -------------------------------------------------------------------------
    // POST /api/items/{id}/visit
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("recordVisit returns HTTP 204 on success")
    void recordVisit_validItem_returns204() throws Exception {
        doNothing().when(itemService).recordVisit(USER_ID, 1L);

        mockMvc.perform(post("/api/items/1/visit"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("recordVisit returns HTTP 404 when item not found")
    void recordVisit_itemNotFound_returns404() throws Exception {
        doThrow(new ItemNotFoundException("Item not found: 999"))
                .when(itemService).recordVisit(USER_ID, 999L);

        mockMvc.perform(post("/api/items/999/visit"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ITEM_NOT_FOUND"));
    }
}
