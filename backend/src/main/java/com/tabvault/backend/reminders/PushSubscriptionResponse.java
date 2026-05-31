package com.tabvault.backend.reminders;

import java.time.OffsetDateTime;

/**
 * Response DTO confirming that a push subscription was registered.
 *
 * AC-060: returned after a subscription record is stored successfully.
 */
public record PushSubscriptionResponse(
        Long id,
        Long userId,
        String endpoint,
        OffsetDateTime createdAt
) {
    static PushSubscriptionResponse from(PushSubscription subscription) {
        return new PushSubscriptionResponse(
                subscription.getId(),
                subscription.getUserId(),
                subscription.getEndpoint(),
                subscription.getCreatedAt()
        );
    }
}
