package com.tabvault.backend.items;

import com.tabvault.backend.autocleanup.AutoCleanupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ItemService — all dependencies are mocked.
 * Tests verify business logic, not implementation internals.
 */
@ExtendWith(MockitoExtension.class)
class ItemServiceTest {

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ContentAnalysisJobRepository contentAnalysisJobRepository;

    @Mock
    private BatchRateLimitService batchRateLimitService;

    @Mock
    private AutoCleanupService autoCleanupService;

    private ItemService itemService;

    @BeforeEach
    void setUp() {
        itemService = new ItemService(
                itemRepository,
                categoryRepository,
                contentAnalysisJobRepository,
                batchRateLimitService,
                autoCleanupService);
    }

    // -------------------------------------------------------------------------
    // saveTab
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("AC-001: saveTab creates new item and returns it when URL is not already saved")
    void saveTab_newUrl_createsItemAndReturnsIt() {
        Long userId = 1L;
        SaveItemRequest request = new SaveItemRequest(
                "https://example.com", "Example Page", "https://example.com/favicon.ico");

        when(itemRepository.findByUserIdAndUrlAndArchivedFalse(userId, request.url()))
                .thenReturn(Optional.empty());
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> {
            Item item = invocation.getArgument(0);
            // simulate ID assignment
            return item;
        });
        when(contentAnalysisJobRepository.save(any(ContentAnalysisJob.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ItemResponse response = itemService.saveTab(userId, request);

        assertThat(response.url()).isEqualTo("https://example.com");
        assertThat(response.title()).isEqualTo("Example Page");
        assertThat(response.faviconUrl()).isEqualTo("https://example.com/favicon.ico");
        assertThat(response.itemType()).isEqualTo("LINK");
        verify(itemRepository).save(any(Item.class));
        verify(contentAnalysisJobRepository).save(any(ContentAnalysisJob.class));
    }

    @Test
    @DisplayName("AC-004: saveTab returns existing item when URL is already saved by the same user")
    void saveTab_duplicateUrl_returnsExistingItem() {
        Long userId = 1L;
        SaveItemRequest request = new SaveItemRequest(
                "https://example.com", "Example Page", null);
        Item existingItem = new Item(userId, ItemType.LINK, "https://example.com", "Example Page", null);

        when(itemRepository.findByUserIdAndUrlAndArchivedFalse(userId, request.url()))
                .thenReturn(Optional.of(existingItem));

        ItemResponse response = itemService.saveTab(userId, request);

        assertThat(response.url()).isEqualTo("https://example.com");
        verify(itemRepository, never()).save(any(Item.class));
        verify(contentAnalysisJobRepository, never()).save(any(ContentAnalysisJob.class));
    }

    @Test
    @DisplayName("saveTab detects YouTube URL as VIDEO type")
    void saveTab_youtubeUrl_detectsVideoType() {
        Long userId = 1L;
        SaveItemRequest request = new SaveItemRequest(
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ", "Rick Astley", null);

        when(itemRepository.findByUserIdAndUrlAndArchivedFalse(eq(userId), anyString()))
                .thenReturn(Optional.empty());
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(contentAnalysisJobRepository.save(any(ContentAnalysisJob.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ItemResponse response = itemService.saveTab(userId, request);

        assertThat(response.itemType()).isEqualTo("VIDEO");
    }

    // -------------------------------------------------------------------------
    // enqueueBatchSave
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("AC-065: enqueueBatchSave throws BatchRateLimitExceededException when rate limit exceeded")
    void enqueueBatchSave_rateLimitExceeded_throwsException() {
        Long userId = 1L;
        List<SaveItemRequest> tabs = List.of(
                new SaveItemRequest("https://a.com", "A", null));
        BatchSaveRequest request = new BatchSaveRequest(tabs);

        org.mockito.Mockito.doThrow(new BatchRateLimitExceededException("Rate limit exceeded"))
                .when(batchRateLimitService).checkAndIncrement(userId, 1);

        assertThatThrownBy(() -> itemService.enqueueBatchSave(userId, request))
                .isInstanceOf(BatchRateLimitExceededException.class)
                .hasMessageContaining("Rate limit exceeded");
    }

    @Test
    @DisplayName("AC-005: enqueueBatchSave does not throw when within rate limit")
    void enqueueBatchSave_withinLimit_doesNotThrow() {
        Long userId = 1L;
        List<SaveItemRequest> tabs = List.of(
                new SaveItemRequest("https://a.com", "A", null));
        BatchSaveRequest request = new BatchSaveRequest(tabs);

        // batchRateLimitService.checkAndIncrement does nothing (within limit)
        // enqueueBatchSave should complete without throwing
        itemService.enqueueBatchSave(userId, request);

        verify(batchRateLimitService).checkAndIncrement(userId, 1);
    }

    // -------------------------------------------------------------------------
    // saveNote
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("AC-025: saveNote creates a NOTE item with the provided body text")
    void saveNote_validBody_createsNoteItem() {
        Long userId = 1L;
        SaveNoteRequest request = new SaveNoteRequest("My note body");

        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ItemResponse response = itemService.saveNote(userId, request);

        assertThat(response.itemType()).isEqualTo("NOTE");
        assertThat(response.noteBody()).isEqualTo("My note body");
        assertThat(response.url()).isNull();
    }

    @Test
    @DisplayName("AC-026: saveNote stores note body exactly as provided — no modification")
    void saveNote_specialCharacters_storedAsIs() {
        Long userId = 1L;
        String rawBody = "<script>alert('xss')</script> & 'hello' \"world\"";
        SaveNoteRequest request = new SaveNoteRequest(rawBody);

        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ItemResponse response = itemService.saveNote(userId, request);

        assertThat(response.noteBody()).isEqualTo(rawBody);
    }

    // -------------------------------------------------------------------------
    // listItems / search
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("AC-027: listItems with query delegates to full-text search")
    void listItems_withQuery_delegatesToFullTextSearch() {
        Long userId = 1L;
        String query = "kotlin testing";
        PageRequest pageable = PageRequest.of(0, 20);
        Item noteItem = new Item(userId, "Kotlin testing tips");
        Page<Item> resultPage = new PageImpl<>(List.of(noteItem));

        when(itemRepository.searchByFullText(userId, query, pageable)).thenReturn(resultPage);

        Page<ItemResponse> result = itemService.listItems(userId, query, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).noteBody()).isEqualTo("Kotlin testing tips");
        verify(itemRepository).searchByFullText(userId, query, pageable);
    }

    @Test
    @DisplayName("listItems without query returns paginated list of all non-archived items")
    void listItems_noQuery_returnsAllItems() {
        Long userId = 1L;
        PageRequest pageable = PageRequest.of(0, 20);
        Item item = new Item(userId, ItemType.LINK, "https://example.com", "Example", null);
        Page<Item> resultPage = new PageImpl<>(List.of(item));

        when(itemRepository.findByUserIdAndArchivedFalseOrderByCreatedAtDesc(userId, pageable))
                .thenReturn(resultPage);

        Page<ItemResponse> result = itemService.listItems(userId, null, pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(itemRepository).findByUserIdAndArchivedFalseOrderByCreatedAtDesc(userId, pageable);
    }

    // -------------------------------------------------------------------------
    // getItem
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getItem throws ItemNotFoundException when item does not belong to user")
    void getItem_notOwned_throwsItemNotFound() {
        Long userId = 1L;
        Long itemId = 99L;

        when(itemRepository.findByIdAndUserId(itemId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> itemService.getItem(userId, itemId))
                .isInstanceOf(ItemNotFoundException.class);
    }

    // -------------------------------------------------------------------------
    // updateItem
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("updateItem applies title when title is non-null and leaves other fields unchanged")
    void updateItem_titleOnly_updatesTitleOnly() {
        Long userId = 1L;
        Long itemId = 10L;
        Item item = new Item(userId, ItemType.LINK, "https://example.com", "Original Title", null);
        item.setSummary("Original summary");
        item.setCategoryId(3L);
        UpdateItemRequest request = new UpdateItemRequest("New Title", null, null);

        when(itemRepository.findByIdAndUserId(itemId, userId)).thenReturn(Optional.of(item));
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ItemResponse response = itemService.updateItem(userId, itemId, request);

        assertThat(response.title()).isEqualTo("New Title");
        assertThat(response.summary()).isEqualTo("Original summary");
        assertThat(response.categoryId()).isEqualTo(3L);
        verify(categoryRepository, never()).existsByIdAndUserId(anyLong(), anyLong());
    }

    @Test
    @DisplayName("updateItem applies summary when summary is non-null")
    void updateItem_summaryOnly_updatesSummaryOnly() {
        Long userId = 1L;
        Long itemId = 10L;
        Item item = new Item(userId, ItemType.LINK, "https://example.com", "Title", null);
        UpdateItemRequest request = new UpdateItemRequest(null, "New summary", null);

        when(itemRepository.findByIdAndUserId(itemId, userId)).thenReturn(Optional.of(item));
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ItemResponse response = itemService.updateItem(userId, itemId, request);

        assertThat(response.summary()).isEqualTo("New summary");
        assertThat(response.title()).isEqualTo("Title");
    }

    @Test
    @DisplayName("updateItem applies categoryId when categoryId is non-null and category belongs to user")
    void updateItem_categoryIdOnly_updatesCategoryId() {
        Long userId = 1L;
        Long itemId = 10L;
        Long newCategoryId = 5L;
        Item item = new Item(userId, ItemType.LINK, "https://example.com", "Title", null);
        UpdateItemRequest request = new UpdateItemRequest(null, null, newCategoryId);

        when(itemRepository.findByIdAndUserId(itemId, userId)).thenReturn(Optional.of(item));
        when(categoryRepository.existsByIdAndUserId(newCategoryId, userId)).thenReturn(true);
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ItemResponse response = itemService.updateItem(userId, itemId, request);

        assertThat(response.categoryId()).isEqualTo(newCategoryId);
    }

    @Test
    @DisplayName("updateItem applies all three fields when all are non-null")
    void updateItem_allFields_updatesAllFields() {
        Long userId = 1L;
        Long itemId = 10L;
        Long newCategoryId = 5L;
        Item item = new Item(userId, ItemType.LINK, "https://example.com", "Old Title", null);
        UpdateItemRequest request = new UpdateItemRequest("New Title", "New summary", newCategoryId);

        when(itemRepository.findByIdAndUserId(itemId, userId)).thenReturn(Optional.of(item));
        when(categoryRepository.existsByIdAndUserId(newCategoryId, userId)).thenReturn(true);
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ItemResponse response = itemService.updateItem(userId, itemId, request);

        assertThat(response.title()).isEqualTo("New Title");
        assertThat(response.summary()).isEqualTo("New summary");
        assertThat(response.categoryId()).isEqualTo(newCategoryId);
    }

    @Test
    @DisplayName("updateItem throws ItemNotFoundException when item does not belong to user")
    void updateItem_itemNotOwned_throwsItemNotFound() {
        Long userId = 1L;
        Long itemId = 99L;
        UpdateItemRequest request = new UpdateItemRequest("New Title", null, null);

        when(itemRepository.findByIdAndUserId(itemId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> itemService.updateItem(userId, itemId, request))
                .isInstanceOf(ItemNotFoundException.class);
    }

    @Test
    @DisplayName("updateItem throws CategoryNotFoundException when categoryId does not belong to user")
    void updateItem_invalidCategoryId_throwsCategoryNotFound() {
        Long userId = 1L;
        Long itemId = 10L;
        Long badCategoryId = 999L;
        Item item = new Item(userId, ItemType.LINK, "https://example.com", "Title", null);
        UpdateItemRequest request = new UpdateItemRequest(null, null, badCategoryId);

        when(itemRepository.findByIdAndUserId(itemId, userId)).thenReturn(Optional.of(item));
        when(categoryRepository.existsByIdAndUserId(badCategoryId, userId)).thenReturn(false);

        assertThatThrownBy(() -> itemService.updateItem(userId, itemId, request))
                .isInstanceOf(CategoryNotFoundException.class);
    }

    @Test
    @DisplayName("updateItem with null categoryId in request does not touch the categoryId field")
    void updateItem_nullCategoryId_doesNotChangeCategoryId() {
        Long userId = 1L;
        Long itemId = 10L;
        Item item = new Item(userId, ItemType.LINK, "https://example.com", "Title", null);
        item.setCategoryId(7L);
        UpdateItemRequest request = new UpdateItemRequest("New Title", null, null);

        when(itemRepository.findByIdAndUserId(itemId, userId)).thenReturn(Optional.of(item));
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ItemResponse response = itemService.updateItem(userId, itemId, request);

        // categoryId was not in the intent (null), so item keeps its original value
        assertThat(response.categoryId()).isEqualTo(7L);
        verify(categoryRepository, never()).existsByIdAndUserId(anyLong(), anyLong());
    }

    // -------------------------------------------------------------------------
    // createCategory
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("AC-018: createCategory creates category with name, color, and optional icon")
    void createCategory_validRequest_returnsCategoryRecord() {
        Long userId = 1L;
        CreateCategoryRequest request = new CreateCategoryRequest("Work", "#ff5733", "briefcase");

        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CategoryResponse response = itemService.createCategory(userId, request);

        assertThat(response.name()).isEqualTo("Work");
        assertThat(response.color()).isEqualTo("#ff5733");
        assertThat(response.icon()).isEqualTo("briefcase");
    }

    @Test
    @DisplayName("AC-018: createCategory creates category without icon when icon is null")
    void createCategory_noIcon_categoryHasNullIcon() {
        Long userId = 1L;
        CreateCategoryRequest request = new CreateCategoryRequest("Research", "#3498db", null);

        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CategoryResponse response = itemService.createCategory(userId, request);

        assertThat(response.icon()).isNull();
    }

    // -------------------------------------------------------------------------
    // deleteCategory
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("AC-019: deleteCategory reassigns items to uncategorized before deleting category")
    void deleteCategory_existingCategory_reassignsItemsAndDeletes() {
        Long userId = 1L;
        Long categoryId = 5L;
        Category category = new Category(userId, "Old Category", "#aabbcc", null);

        when(categoryRepository.findByIdAndUserId(categoryId, userId))
                .thenReturn(Optional.of(category));

        itemService.deleteCategory(userId, categoryId);

        verify(itemRepository).reassignItemsToUncategorized(userId, categoryId);
        verify(categoryRepository).delete(category);
    }

    @Test
    @DisplayName("deleteCategory throws CategoryNotFoundException when category does not belong to user")
    void deleteCategory_notOwned_throwsCategoryNotFound() {
        Long userId = 1L;
        Long categoryId = 99L;

        when(categoryRepository.findByIdAndUserId(categoryId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> itemService.deleteCategory(userId, categoryId))
                .isInstanceOf(CategoryNotFoundException.class);
    }

    // -------------------------------------------------------------------------
    // reassignCategory
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("AC-020: reassignCategory updates item categoryId and returns updated record")
    void reassignCategory_validRequest_updatesItem() {
        Long userId = 1L;
        Long itemId = 10L;
        Long newCategoryId = 3L;
        Item item = new Item(userId, ItemType.LINK, "https://example.com", "Example", null);
        ReassignCategoryRequest request = new ReassignCategoryRequest(newCategoryId);

        when(itemRepository.findByIdAndUserId(itemId, userId)).thenReturn(Optional.of(item));
        when(categoryRepository.existsByIdAndUserId(newCategoryId, userId)).thenReturn(true);
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ItemResponse response = itemService.reassignCategory(userId, itemId, request.targetCategoryId());

        assertThat(response.categoryId()).isEqualTo(newCategoryId);
    }

    @Test
    @DisplayName("reassignCategory with null targetCategoryId moves item to uncategorized")
    void reassignCategory_nullTarget_movesToUncategorized() {
        Long userId = 1L;
        Long itemId = 10L;
        Item item = new Item(userId, ItemType.LINK, "https://example.com", "Example", null);
        item.setCategoryId(5L);

        when(itemRepository.findByIdAndUserId(itemId, userId)).thenReturn(Optional.of(item));
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ItemResponse response = itemService.reassignCategory(userId, itemId, null);

        assertThat(response.categoryId()).isNull();
        verify(categoryRepository, never()).existsByIdAndUserId(anyLong(), anyLong());
    }

    @Test
    @DisplayName("reassignCategory throws CategoryNotFoundException when target category does not belong to user")
    void reassignCategory_invalidCategory_throwsCategoryNotFound() {
        Long userId = 1L;
        Long itemId = 10L;
        Long badCategoryId = 999L;
        Item item = new Item(userId, ItemType.LINK, "https://example.com", "Example", null);

        when(itemRepository.findByIdAndUserId(itemId, userId)).thenReturn(Optional.of(item));
        when(categoryRepository.existsByIdAndUserId(badCategoryId, userId)).thenReturn(false);

        assertThatThrownBy(() -> itemService.reassignCategory(userId, itemId, badCategoryId))
                .isInstanceOf(CategoryNotFoundException.class);
    }
}
