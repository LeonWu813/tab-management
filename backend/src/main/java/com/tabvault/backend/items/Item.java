package com.tabvault.backend.items;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/**
 * Persistent saved-item record.
 *
 * An item can be a saved link (LINK/VIDEO) or a plain text note (NOTE).
 * - Link/video items: url, title, favicon_url are required; note_body is NULL.
 * - Note items: note_body contains the plain text body; url/favicon_url are NULL.
 *
 * summary and category_id are initially NULL and populated asynchronously by the
 * content analysis pipeline (MOD-003) after the item is saved.
 *
 * search_vector is a tsvector column maintained by a PostgreSQL trigger that
 * combines title, summary, and note_body using the english text search config.
 *
 * is_archived is used to mark soft-deleted or auto-archived items.
 * Duplicate detection checks url + user_id + is_archived=false.
 */
@Entity
@Table(name = "items")
public class Item {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "category_id")
    private Long categoryId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "item_type", nullable = false, columnDefinition = "item_type")
    private ItemType itemType;

    @Column(name = "url", columnDefinition = "TEXT")
    private String url;

    @Column(name = "title", length = 1000)
    private String title;

    @Column(name = "favicon_url", columnDefinition = "TEXT")
    private String faviconUrl;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    // Populated by MOD-003 (content analysis pipeline) after Claude API call (AC-010)
    @Column(name = "suggested_category", length = 100)
    private String suggestedCategory;

    // Populated by MOD-003 (content analysis pipeline) after Claude API call (AC-010)
    @Column(name = "content_type", length = 50)
    private String contentType;

    @Column(name = "note_body", columnDefinition = "TEXT")
    private String noteBody;

    // Populated by MOD-004 (content extraction) before the LLM analysis is called.
    // Contains extracted readable text from article HTML, PDF, or YouTube transcript.
    @Column(name = "page_text", columnDefinition = "TEXT")
    private String pageText;

    // Populated by MOD-004 for video items (YouTube oEmbed URL or og:image thumbnail).
    @Column(name = "thumbnail_url", columnDefinition = "TEXT")
    private String thumbnailUrl;

    // Populated by MOD-004 for video items: "youtube", "instagram", or "tiktok".
    // NULL for article and PDF items.
    @Column(name = "platform", length = 50)
    private String platform;

    @Column(name = "is_pinned", nullable = false)
    private boolean pinned;

    @Column(name = "is_archived", nullable = false)
    private boolean archived;

    @Column(name = "last_visited_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime lastVisitedAt;

    // Maintained by a PostgreSQL trigger — never set directly by Hibernate.
    // insertable=false, updatable=false prevents Hibernate from trying to write this column.
    @Column(name = "search_vector", insertable = false, updatable = false, columnDefinition = "tsvector")
    private String searchVector;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime updatedAt;

    protected Item() {
        // Required by JPA
    }

    /**
     * Creates a link or video item.
     */
    public Item(Long userId, ItemType itemType, String url, String title, String faviconUrl) {
        this.userId = userId;
        this.itemType = itemType;
        this.url = url;
        this.title = title;
        this.faviconUrl = faviconUrl;
        this.pinned = false;
        this.archived = false;
    }

    /**
     * Creates a plain text note item.
     */
    public Item(Long userId, String noteBody) {
        this.userId = userId;
        this.itemType = ItemType.NOTE;
        this.noteBody = noteBody;
        this.pinned = false;
        this.archived = false;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public ItemType getItemType() {
        return itemType;
    }

    public String getUrl() {
        return url;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getFaviconUrl() {
        return faviconUrl;
    }

    public void setFaviconUrl(String faviconUrl) {
        this.faviconUrl = faviconUrl;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getSuggestedCategory() {
        return suggestedCategory;
    }

    public void setSuggestedCategory(String suggestedCategory) {
        this.suggestedCategory = suggestedCategory;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getNoteBody() {
        return noteBody;
    }

    public void setNoteBody(String noteBody) {
        this.noteBody = noteBody;
    }

    public String getPageText() {
        return pageText;
    }

    public void setPageText(String pageText) {
        this.pageText = pageText;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public void setThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    public boolean isArchived() {
        return archived;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
    }

    public OffsetDateTime getLastVisitedAt() {
        return lastVisitedAt;
    }

    public void setLastVisitedAt(OffsetDateTime lastVisitedAt) {
        this.lastVisitedAt = lastVisitedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
