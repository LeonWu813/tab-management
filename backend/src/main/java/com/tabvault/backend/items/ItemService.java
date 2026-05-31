package com.tabvault.backend.items;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Core service for item and category management.
 *
 * Handles:
 * - Single-tab save (link/video) with duplicate detection (AC-001, AC-004)
 * - Batch-save enqueue with async sequential processing (AC-005, AC-006)
 * - Note save (AC-025, AC-026)
 * - Category CRUD — create, delete (with item reassignment), list (AC-018, AC-019)
 * - Category reassignment on an item (AC-020)
 * - Item listing and full-text search (AC-027)
 * - last_visited_at timestamp update on item click-through
 *
 * On item save, a PENDING ContentAnalysisJob record is written to the outbox table
 * so that MOD-003 can process it asynchronously (jobs survive service restarts).
 */
@Service
public class ItemService {

    private static final Logger logger = LoggerFactory.getLogger(ItemService.class);

    private final ItemRepository itemRepository;
    private final CategoryRepository categoryRepository;
    private final ContentAnalysisJobRepository contentAnalysisJobRepository;
    private final BatchRateLimitService batchRateLimitService;

    public ItemService(
            ItemRepository itemRepository,
            CategoryRepository categoryRepository,
            ContentAnalysisJobRepository contentAnalysisJobRepository,
            BatchRateLimitService batchRateLimitService) {
        this.itemRepository = itemRepository;
        this.categoryRepository = categoryRepository;
        this.contentAnalysisJobRepository = contentAnalysisJobRepository;
        this.batchRateLimitService = batchRateLimitService;
    }

    // -------------------------------------------------------------------------
    // Single-tab save
    // -------------------------------------------------------------------------

    /**
     * Saves a single browser tab as a link item.
     *
     * AC-001: Creates a new item with URL, title, faviconUrl, and created_at.
     * AC-004: Returns the existing item if the URL is already saved and not archived.
     * AC-002: The method returns immediately after saving (before LLM analysis).
     *
     * @param userId  the authenticated user's ID
     * @param request save request containing url, title, and optional faviconUrl
     * @return the saved or existing item record
     */
    @Transactional
    public ItemResponse saveTab(Long userId, SaveItemRequest request) {
        // AC-004: duplicate detection — return existing item if URL already saved
        return itemRepository.findByUserIdAndUrlAndArchivedFalse(userId, request.url())
                .map(existingItem -> {
                    logger.info("Duplicate save request for existing item userId={} itemId={}",
                            userId, existingItem.getId());
                    return ItemResponse.from(existingItem);
                })
                .orElseGet(() -> {
                    ItemType itemType = detectItemType(request.url());
                    Item item = new Item(userId, itemType, request.url(), request.title(), request.faviconUrl());
                    Item saved = itemRepository.save(item);

                    // Enqueue analysis job in the outbox table (AC-002: do not wait for analysis)
                    ContentAnalysisJob job = new ContentAnalysisJob(saved.getId());
                    contentAnalysisJobRepository.save(job);

                    logger.info("Item saved userId={} itemId={} type={}", userId, saved.getId(), itemType);
                    return ItemResponse.from(saved);
                });
    }

    // -------------------------------------------------------------------------
    // Batch save
    // -------------------------------------------------------------------------

    /**
     * Enqueues a batch of browser tabs for sequential async processing.
     *
     * AC-005: Returns immediately (HTTP 202 is set in the controller).
     * AC-065: Rate limit is checked before enqueuing.
     *
     * @param userId  the authenticated user's ID
     * @param request batch save request containing a list of tabs
     */
    @Transactional
    public void enqueueBatchSave(Long userId, BatchSaveRequest request) {
        int tabCount = request.tabs().size();

        // AC-065: check rate limit before processing
        batchRateLimitService.checkAndIncrement(userId, tabCount);

        logger.info("Batch save enqueued userId={} tabCount={}", userId, tabCount);

        // AC-005: process each URL sequentially in an async thread
        processBatchAsync(userId, request.tabs());
    }

    /**
     * Processes a list of tabs sequentially in the background.
     *
     * AC-006: Items with failures are saved with URL and title only (summary/category blank).
     * The @Async annotation runs this in Spring's task executor thread pool.
     */
    @Async
    public void processBatchAsync(Long userId, List<SaveItemRequest> tabs) {
        for (SaveItemRequest tab : tabs) {
            try {
                processSingleTabInBatch(userId, tab);
            } catch (Exception exception) {
                // AC-006: failure for one item must not prevent the rest from being saved
                logger.error("Failed to process batch tab userId={} url={}", userId, tab.url(), exception);
            }
        }
        logger.info("Batch processing complete userId={} tabCount={}", userId, tabs.size());
    }

    @Transactional
    public void processSingleTabInBatch(Long userId, SaveItemRequest tab) {
        // Skip duplicates silently in batch mode
        boolean alreadySaved = itemRepository.findByUserIdAndUrlAndArchivedFalse(userId, tab.url()).isPresent();
        if (alreadySaved) {
            logger.debug("Skipping duplicate URL in batch userId={} url={}", userId, tab.url());
            return;
        }

        ItemType itemType = detectItemType(tab.url());
        Item item = new Item(userId, itemType, tab.url(), tab.title(), tab.faviconUrl());
        Item saved = itemRepository.save(item);

        // Enqueue analysis job
        ContentAnalysisJob job = new ContentAnalysisJob(saved.getId());
        contentAnalysisJobRepository.save(job);

        logger.info("Batch item saved userId={} itemId={}", userId, saved.getId());
    }

    // -------------------------------------------------------------------------
    // Note save
    // -------------------------------------------------------------------------

    /**
     * Saves a plain text note item.
     *
     * AC-025: Creates a NOTE item with the user-provided body text.
     * AC-026: Stores the note body as-is — no modification, sanitization, or interpretation.
     *
     * @param userId  the authenticated user's ID
     * @param request note body text
     * @return the saved note item record
     */
    @Transactional
    public ItemResponse saveNote(Long userId, SaveNoteRequest request) {
        Item item = new Item(userId, request.noteBody());
        Item saved = itemRepository.save(item);
        logger.info("Note saved userId={} itemId={}", userId, saved.getId());
        return ItemResponse.from(saved);
    }

    // -------------------------------------------------------------------------
    // Item listing and search
    // -------------------------------------------------------------------------

    /**
     * Returns a paginated list of non-archived items for the user.
     * If query is provided, performs a PostgreSQL full-text search.
     *
     * AC-027: Note items appear in search results when the query matches note_body.
     *
     * @param userId   the authenticated user's ID
     * @param query    optional full-text search query (null or blank = list all)
     * @param pageable pagination and sort
     * @return page of item response DTOs
     */
    @Transactional(readOnly = true)
    public Page<ItemResponse> listItems(Long userId, String query, Pageable pageable) {
        if (query != null && !query.isBlank()) {
            return itemRepository.searchByFullText(userId, query, pageable)
                    .map(ItemResponse::from);
        }
        return itemRepository.findByUserIdAndArchivedFalseOrderByCreatedAtDesc(userId, pageable)
                .map(ItemResponse::from);
    }

    /**
     * Returns a single item by ID, scoped to the authenticated user.
     */
    @Transactional(readOnly = true)
    public ItemResponse getItem(Long userId, Long itemId) {
        Item item = itemRepository.findByIdAndUserId(itemId, userId)
                .orElseThrow(() -> new ItemNotFoundException("Item not found: " + itemId));
        return ItemResponse.from(item);
    }

    /**
     * Updates the last_visited_at timestamp on an item to the current time.
     * Called when the user clicks through to the saved URL.
     *
     * @param userId the authenticated user's ID
     * @param itemId the ID of the item being visited
     */
    @Transactional
    public void recordVisit(Long userId, Long itemId) {
        Item item = itemRepository.findByIdAndUserId(itemId, userId)
                .orElseThrow(() -> new ItemNotFoundException("Item not found: " + itemId));
        item.setLastVisitedAt(OffsetDateTime.now());
        itemRepository.save(item);
        logger.debug("Visit recorded userId={} itemId={}", userId, itemId);
    }

    // -------------------------------------------------------------------------
    // Category management
    // -------------------------------------------------------------------------

    /**
     * Creates a new category for the authenticated user.
     *
     * AC-018: name 1–50 chars, hex color code, optional icon.
     *
     * @param userId  the authenticated user's ID
     * @param request category create request
     * @return the created category record
     */
    @Transactional
    public CategoryResponse createCategory(Long userId, CreateCategoryRequest request) {
        Category category = new Category(userId, request.name(), request.color(), request.icon());
        Category saved = categoryRepository.save(category);
        logger.info("Category created userId={} categoryId={}", userId, saved.getId());
        return CategoryResponse.from(saved);
    }

    /**
     * Lists all categories for the authenticated user ordered by sortOrder then createdAt.
     */
    @Transactional(readOnly = true)
    public List<CategoryResponse> listCategories(Long userId) {
        return categoryRepository.findByUserIdOrderBySortOrderAscCreatedAtAsc(userId)
                .stream()
                .map(CategoryResponse::from)
                .toList();
    }

    /**
     * Deletes a category and reassigns all its items to uncategorized (NULL).
     *
     * AC-019: Items in the deleted category are not deleted; they become uncategorized.
     *
     * @param userId     the authenticated user's ID
     * @param categoryId the ID of the category to delete
     * @throws CategoryNotFoundException if the category does not belong to the user
     */
    @Transactional
    public void deleteCategory(Long userId, Long categoryId) {
        Category category = categoryRepository.findByIdAndUserId(categoryId, userId)
                .orElseThrow(() -> new CategoryNotFoundException("Category not found: " + categoryId));

        // Explicitly reassign items before delete (DB ON DELETE SET NULL also handles this,
        // but the explicit update ensures JPA-managed entities are not stale in the session).
        itemRepository.reassignItemsToUncategorized(userId, categoryId);

        categoryRepository.delete(category);
        logger.info("Category deleted userId={} categoryId={}", userId, categoryId);
    }

    /**
     * Reassigns an item to a different category (or to uncategorized if targetCategoryId is null).
     *
     * AC-020: Updates the item's category_id and returns the updated item record.
     *
     * @param userId           the authenticated user's ID
     * @param itemId           the item to reassign
     * @param targetCategoryId the new category ID, or null for uncategorized
     * @return the updated item record
     */
    @Transactional
    public ItemResponse reassignCategory(Long userId, Long itemId, Long targetCategoryId) {
        Item item = itemRepository.findByIdAndUserId(itemId, userId)
                .orElseThrow(() -> new ItemNotFoundException("Item not found: " + itemId));

        if (targetCategoryId != null) {
            boolean categoryExists = categoryRepository.existsByIdAndUserId(targetCategoryId, userId);
            if (!categoryExists) {
                throw new CategoryNotFoundException("Category not found: " + targetCategoryId);
            }
        }

        item.setCategoryId(targetCategoryId);
        Item saved = itemRepository.save(item);
        logger.info("Item category reassigned userId={} itemId={} newCategoryId={}",
                userId, itemId, targetCategoryId);
        return ItemResponse.from(saved);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Detects whether a URL is a video (YouTube/Vimeo) or a generic link.
     */
    private ItemType detectItemType(String url) {
        if (url == null) {
            return ItemType.LINK;
        }
        String lowercaseUrl = url.toLowerCase();
        if (lowercaseUrl.contains("youtube.com/watch") || lowercaseUrl.contains("youtu.be/")
                || lowercaseUrl.contains("vimeo.com/")) {
            return ItemType.VIDEO;
        }
        return ItemType.LINK;
    }
}
