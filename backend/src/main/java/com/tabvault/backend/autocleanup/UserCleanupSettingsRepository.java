package com.tabvault.backend.autocleanup;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * JPA repository for {@link UserCleanupSettings} entities.
 *
 * One row per user. The service uses {@code findByUserId} to retrieve settings and
 * creates a default record on first access when none exists.
 */
public interface UserCleanupSettingsRepository extends JpaRepository<UserCleanupSettings, Long> {

    /**
     * Returns the cleanup settings record for the specified user, if it exists.
     *
     * @param userId the user's ID
     * @return the settings record, or empty if the user has never configured cleanup settings
     */
    Optional<UserCleanupSettings> findByUserId(Long userId);
}
