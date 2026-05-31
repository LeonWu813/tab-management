package com.tabvault.backend.reminders;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * Reads and validates VAPID keys from environment variables.
 *
 * AC-061: The system shall read VAPID public and private keys from the
 * VAPID_PUBLIC_KEY and VAPID_PRIVATE_KEY environment variables.
 * The system shall fail to start if either variable is absent or empty.
 *
 * VAPID_SUBJECT is also required per RFC 8292 (included in push request headers
 * as the contact URI — mailto: or https: URI identifying the operator).
 */
@Configuration
public class VapidConfig {

    private final String publicKey;
    private final String privateKey;
    private final String subject;

    public VapidConfig(
            @Value("${app.reminders.vapid.public-key}") String publicKey,
            @Value("${app.reminders.vapid.private-key}") String privateKey,
            @Value("${app.reminders.vapid.subject}") String subject) {
        this.publicKey = publicKey;
        this.privateKey = privateKey;
        this.subject = subject;
    }

    /**
     * Fails fast at startup if any VAPID key is absent or blank.
     *
     * AC-061: the system shall fail to start if VAPID_PUBLIC_KEY or VAPID_PRIVATE_KEY
     * is absent or empty.
     */
    @PostConstruct
    public void validate() {
        if (publicKey == null || publicKey.isBlank()) {
            throw new IllegalStateException(
                    "VAPID public key is required (app.reminders.vapid.public-key / VAPID_PUBLIC_KEY) " +
                    "but is absent or empty. The application cannot start without a VAPID key pair.");
        }
        if (privateKey == null || privateKey.isBlank()) {
            throw new IllegalStateException(
                    "VAPID private key is required (app.reminders.vapid.private-key / VAPID_PRIVATE_KEY) " +
                    "but is absent or empty. The application cannot start without a VAPID key pair.");
        }
        if (subject == null || subject.isBlank()) {
            throw new IllegalStateException(
                    "VAPID subject is required (app.reminders.vapid.subject / VAPID_SUBJECT) " +
                    "but is absent or empty. VAPID subject must be a mailto: or https: URI.");
        }
    }

    /** Returns the VAPID public key (URL-safe base64, no padding). */
    public String getPublicKey() {
        return publicKey;
    }

    /** Returns the VAPID private key (URL-safe base64, no padding). */
    public String getPrivateKey() {
        return privateKey;
    }

    /**
     * Returns the VAPID subject (mailto: or https: URI identifying the push service operator).
     * Included in Authorization headers per RFC 8292.
     */
    public String getSubject() {
        return subject;
    }
}
