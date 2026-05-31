package com.tabvault.backend.reminders;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

/**
 * Stores a Web Push API subscription record for a user's device.
 *
 * Each record corresponds to a single browser PushSubscription object obtained
 * after the user grants push permission. A user may have multiple active subscriptions
 * (one per device or browser).
 *
 * AC-060: stores endpoint URL, auth key, and p256dh key per user device.
 * AC-063: multiple subscriptions per user for multi-device notification delivery.
 */
@Entity
@Table(name = "push_subscriptions")
public class PushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** The push service endpoint URL provided by the browser. */
    @Column(name = "endpoint", nullable = false, columnDefinition = "TEXT", unique = true)
    private String endpoint;

    /** The auth key (URL-safe base64) from the browser PushSubscription keys object. */
    @Column(name = "auth_key", nullable = false, columnDefinition = "TEXT")
    private String authKey;

    /** The p256dh key (URL-safe base64) from the browser PushSubscription keys object. */
    @Column(name = "p256dh_key", nullable = false, columnDefinition = "TEXT")
    private String p256dhKey;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime createdAt;

    protected PushSubscription() {
        // Required by JPA
    }

    /**
     * Creates a new push subscription record for the given user.
     *
     * @param userId   the authenticated user who granted push permission
     * @param endpoint the push service endpoint URL
     * @param authKey  the auth key from the browser PushSubscription
     * @param p256dhKey the p256dh key from the browser PushSubscription
     */
    public PushSubscription(Long userId, String endpoint, String authKey, String p256dhKey) {
        this.userId = userId;
        this.endpoint = endpoint;
        this.authKey = authKey;
        this.p256dhKey = p256dhKey;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getAuthKey() {
        return authKey;
    }

    public String getP256dhKey() {
        return p256dhKey;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
