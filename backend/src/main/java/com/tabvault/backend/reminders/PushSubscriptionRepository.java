package com.tabvault.backend.reminders;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * JPA repository for PushSubscription entities.
 *
 * AC-060: supports storing subscription records per user device.
 * AC-062: supports deleting stale subscriptions by endpoint when HTTP 410 Gone is received.
 * AC-063: supports fetching all subscriptions for a user for multi-device delivery.
 */
public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    /**
     * Returns all push subscriptions for a user (all registered devices).
     *
     * AC-063: used by notification dispatch to deliver to every registered device.
     */
    List<PushSubscription> findByUserId(Long userId);

    /**
     * Finds a subscription by its endpoint URL.
     * Used to upsert on duplicate-endpoint registration.
     */
    Optional<PushSubscription> findByEndpoint(String endpoint);

    /**
     * Deletes the push subscription for a given endpoint URL.
     *
     * AC-062: called when the Web Push service returns HTTP 410 Gone for that endpoint.
     */
    void deleteByEndpoint(String endpoint);
}
