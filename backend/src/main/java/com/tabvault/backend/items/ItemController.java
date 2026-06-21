package com.tabvault.backend.items;

import com.tabvault.backend.shared.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for item and category management.
 *
 * All endpoints require a valid JWT access token in the Authorization: Bearer header.
 * The JwtAuthenticationFilter sets the authenticated user's Long userId as the
 * Spring Security principal, which is injected via @AuthenticationPrincipal.
 *
 * Endpoints:
 *   POST   /api/items                             — save a single tab (AC-001, AC-004)
 *   POST   /api/items/batch                       — batch save all tabs (AC-005, AC-065)
 *   POST   /api/items/notes                       — save a plain text note (AC-025)
 *   GET    /api/items                             — list or search items (AC-027)
 *   GET    /api/items/{id}                        — get single item
 *   POST   /api/items/{id}/visit                  — update last_visited_at
 *   PATCH  /api/items/{id}                        — partial update: title, summary, categoryId
 *   PATCH  /api/items/{id}/category               — reassign category (AC-020)
 *   DELETE /api/items/{id}                        — delete item permanently (AC-067)
 *   POST   /api/categories                        — create category (AC-018)
 *   GET    /api/categories                        — list categories
 *   DELETE /api/categories/{id}                   — delete category + reassign items (AC-019)
 */
@Tag(name = "Items", description = "Saved item and category management")
@RestController
@RequestMapping("/api")
public class ItemController {

    private final ItemService itemService;

    public ItemController(ItemService itemService) {
        this.itemService = itemService;
    }

    // -------------------------------------------------------------------------
    // Items
    // -------------------------------------------------------------------------

    /**
     * Saves a single browser tab.
     *
     * AC-001: Creates a new item with URL, title, favicon, created_at.
     * AC-004: Returns HTTP 200 with the existing item if the URL is already saved.
     * AC-002: Returns the item record immediately (LLM analysis is async).
     */
    @Operation(summary = "Save a single browser tab")
    @PostMapping("/items")
    public ResponseEntity<ItemResponse> saveTab(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody SaveItemRequest request) {

        ItemResponse response = itemService.saveTab(userId, request);

        // AC-004: return 200 for both new saves and duplicates
        return ResponseEntity.ok(response);
    }

    /**
     * Saves all open tabs in the current browser window.
     *
     * AC-005: Returns HTTP 202 immediately; items are processed sequentially in the background.
     * AC-065: Returns HTTP 429 if the rate limit is exceeded.
     */
    @Operation(summary = "Batch save all open tabs")
    @PostMapping("/items/batch")
    public ResponseEntity<BatchSaveResponse> batchSave(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody BatchSaveRequest request) {

        itemService.enqueueBatchSave(userId, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new BatchSaveResponse(request.tabs().size()));
    }

    /**
     * Saves a plain text note.
     *
     * AC-025: Creates a NOTE item with the provided noteBody.
     * AC-026: noteBody stored as-is.
     */
    @Operation(summary = "Save a plain text note")
    @PostMapping("/items/notes")
    public ResponseEntity<ItemResponse> saveNote(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody SaveNoteRequest request) {

        ItemResponse response = itemService.saveNote(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Lists or searches items for the authenticated user.
     *
     * AC-027: Full-text search matches note_body for note items.
     *
     * @param query    optional search query (PostgreSQL full-text search)
     * @param page     zero-based page number (default 0)
     * @param pageSize number of items per page (default 20, max 100)
     */
    @Operation(summary = "List or full-text search saved items")
    @GetMapping("/items")
    public ResponseEntity<Page<ItemResponse>> listItems(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {

        int clampedPageSize = Math.min(pageSize, 100);
        Pageable pageable = PageRequest.of(page, clampedPageSize);
        Page<ItemResponse> items = itemService.listItems(userId, query, pageable);
        return ResponseEntity.ok(items);
    }

    /**
     * Gets a single item by ID, scoped to the authenticated user.
     */
    @Operation(summary = "Get a single saved item by ID")
    @GetMapping("/items/{id}")
    public ResponseEntity<ItemResponse> getItem(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id) {

        ItemResponse response = itemService.getItem(userId, id);
        return ResponseEntity.ok(response);
    }

    /**
     * Records a visit to the item — updates last_visited_at to now.
     */
    @Operation(summary = "Record a click-through visit to the saved item URL")
    @PostMapping("/items/{id}/visit")
    public ResponseEntity<Void> recordVisit(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id) {

        itemService.recordVisit(userId, id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Partially updates an item's title, summary, and/or categoryId.
     *
     * Only fields present (non-null) in the request body are applied.
     * At least one field must be provided — returns HTTP 400 if the body contains no
     * non-null fields (i.e. all three keys are absent or all three are null).
     *
     * HTTP 200 — updated item record
     * HTTP 400 — request body is empty / all fields are null
     * HTTP 404 — item not found or belongs to a different user
     *
     * Used by MOD-008 PWA Dashboard for inline edit of title, summary, and category.
     */
    @Operation(summary = "Partially update an item's title, summary, and/or category")
    @PatchMapping("/items/{id}")
    public ResponseEntity<?> updateItem(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @Valid @RequestBody UpdateItemRequest request) {

        // True partial update: at least one field must be present (non-null).
        // categoryId null is a valid value ("move to uncategorized"), so we cannot
        // use a simple null check on all three fields. The rule is: at least one of
        // title, summary, or categoryId must be present in the JSON body — but because
        // Java records cannot distinguish "key absent" from "key present with null value",
        // we enforce that the combined deserialized result must not have all three fields
        // null, which covers the empty-body case and the all-null case.
        if (request.title() == null && request.summary() == null && request.categoryId() == null) {
            return ResponseEntity.badRequest()
                    .body(ApiErrorResponse.of(
                            "VALIDATION_ERROR",
                            "At least one field (title, summary, categoryId) must be provided"));
        }

        ItemResponse response = itemService.updateItem(userId, id, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Reassigns an item to a different category.
     *
     * AC-020: Updates category_id and returns the updated item record.
     * Pass null targetCategoryId to move the item to "uncategorized".
     */
    @Operation(summary = "Reassign an item to a different category")
    @PatchMapping("/items/{id}/category")
    public ResponseEntity<ItemResponse> reassignCategory(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @RequestBody ReassignCategoryRequest request) {

        ItemResponse response = itemService.reassignCategory(userId, id, request.targetCategoryId());
        return ResponseEntity.ok(response);
    }

    // -------------------------------------------------------------------------
    // Categories
    // -------------------------------------------------------------------------

    /**
     * Creates a new category.
     *
     * AC-018: name 1–50 chars, hex color code (#rrggbb), optional icon.
     */
    @Operation(summary = "Create a new category")
    @PostMapping("/categories")
    public ResponseEntity<CategoryResponse> createCategory(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CreateCategoryRequest request) {

        CategoryResponse response = itemService.createCategory(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Lists all categories for the authenticated user.
     */
    @Operation(summary = "List all categories")
    @GetMapping("/categories")
    public ResponseEntity<List<CategoryResponse>> listCategories(
            @AuthenticationPrincipal Long userId) {

        List<CategoryResponse> categories = itemService.listCategories(userId);
        return ResponseEntity.ok(categories);
    }

    /**
     * Permanently deletes an item.
     *
     * AC-067: Removes the item from the database.
     */
    @Operation(summary = "Delete an item permanently")
    @DeleteMapping("/items/{id}")
    public ResponseEntity<Void> deleteItem(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id) {

        itemService.deleteItem(userId, id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Deletes a category and reassigns all its items to uncategorized.
     *
     * AC-019: Items are not deleted; they become uncategorized.
     */
    @Operation(summary = "Delete a category and reassign its items to uncategorized")
    @DeleteMapping("/categories/{id}")
    public ResponseEntity<Void> deleteCategory(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id) {

        itemService.deleteCategory(userId, id);
        return ResponseEntity.noContent().build();
    }
}
