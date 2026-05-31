package com.tabvault.backend.items;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * JPA repository for Item entities.
 */
public interface ItemRepository extends JpaRepository<Item, Long> {

    /**
     * Finds an existing non-archived item by URL for a user (duplicate detection).
     */
    Optional<Item> findByUserIdAndUrlAndArchivedFalse(Long userId, String url);

    Optional<Item> findByIdAndUserId(Long id, Long userId);

    /**
     * Full-text search over items for a user, using the search_vector column.
     * Returns non-archived items ordered by relevance then created_at descending.
     */
    @Query(value = """
            SELECT * FROM items
            WHERE user_id = :userId
              AND is_archived = false
              AND search_vector @@ plainto_tsquery('english', :query)
            ORDER BY ts_rank(search_vector, plainto_tsquery('english', :query)) DESC,
                     created_at DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM items
            WHERE user_id = :userId
              AND is_archived = false
              AND search_vector @@ plainto_tsquery('english', :query)
            """,
            nativeQuery = true)
    Page<Item> searchByFullText(@Param("userId") Long userId,
                                @Param("query") String query,
                                Pageable pageable);

    /**
     * Lists all non-archived items for a user, newest first.
     */
    Page<Item> findByUserIdAndArchivedFalseOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /**
     * Lists items filtered by category for a user.
     */
    Page<Item> findByUserIdAndCategoryIdAndArchivedFalseOrderByCreatedAtDesc(
            Long userId, Long categoryId, Pageable pageable);

    /**
     * Lists items with NULL category (uncategorized) for a user.
     */
    Page<Item> findByUserIdAndCategoryIdIsNullAndArchivedFalseOrderByCreatedAtDesc(
            Long userId, Pageable pageable);

    /**
     * Reassigns all items in a deleted category to uncategorized (NULL).
     * Called before deleting the category (the DB ON DELETE SET NULL also handles it,
     * but this explicit update gives us a clean record before the delete).
     */
    @Modifying
    @Query("UPDATE Item i SET i.categoryId = NULL WHERE i.userId = :userId AND i.categoryId = :categoryId")
    void reassignItemsToUncategorized(@Param("userId") Long userId, @Param("categoryId") Long categoryId);
}
