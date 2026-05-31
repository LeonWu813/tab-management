package com.tabvault.backend.items;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * JPA repository for ContentAnalysisJob entities.
 *
 * This module only writes PENDING jobs. MOD-003 reads and processes them.
 */
public interface ContentAnalysisJobRepository extends JpaRepository<ContentAnalysisJob, Long> {
}
