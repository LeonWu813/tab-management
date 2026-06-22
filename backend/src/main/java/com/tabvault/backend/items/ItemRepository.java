package com.tabvault.backend.items;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
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

    /**
     * AC-067: Deletes an item by ID scoped to the user, bypassing Hibernate entity lifecycle.
     * Returns the number of rows deleted (0 if not found or not owned by user).
     */
    @Modifying
    @Query("DELETE FROM Item i WHERE i.id = :itemId AND i.userId = :userId")
    int deleteByItemIdAndUserId(@Param("itemId") Long itemId, @Param("userId") Long userId);

    // -------------------------------------------------------------------------
    // MOD-006: Auto-Cleanup queries
    // -------------------------------------------------------------------------

    /**
     * Returns non-pinned, non-archived items for a user whose effective last visit time
     * (last_visited_at if set, otherwise created_at) is older than the given cutoff.
     *
     * Used by the daily auto-cleanup job to identify stale items that need a staleness
     * reminder (AC-033). The COALESCE ensures items that were never visited are compared
     * against their creation date.
     *
     * AC-033: item qualifies if last_visited_at (or created_at if never visited) is older
     *         than the user's staleness threshold; is_pinned=false and is_archived=false.
     *
     * @param userId  the user whose items are checked
     * @param cutoff  the effective visit cutoff — items not visited since this timestamp qualify
     * @return list of stale non-pinned, non-archived items
     */
    @Query(value = """
            SELECT * FROM items
            WHERE user_id = :userId
              AND is_pinned = FALSE
              AND is_archived = FALSE
              AND COALESCE(last_visited_at, created_at) < :cutoff
            """,
            nativeQuery = true)
    List<Item> findStaleItemsForUser(@Param("userId") Long userId,
                                     @Param("cutoff") OffsetDateTime cutoff);

    /**
     * Returns non-pinned, non-archived items for a user that have had their last_visited_at
     * updated since the given cutoff (i.e., visited recently). Used internally to identify
     * items that should have staleness reminders cleared.
     *
     * Not used directly by the job — the job checks via
     * {@code findStaleItemsForUser} — but useful for the clear-on-visit path.
     *
     * @param userId the user whose items are checked
     * @param since  only items visited at or after this timestamp are returned
     * @return list of recently visited non-pinned, non-archived items
     */
    List<Item> findByUserIdAndArchivedFalseAndLastVisitedAtAfter(Long userId, OffsetDateTime since);
}
