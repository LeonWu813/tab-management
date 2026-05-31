package com.tabvault.backend.contentanalysis;

import com.tabvault.backend.items.ContentAnalysisJob;
import com.tabvault.backend.items.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Read/write repository for ContentAnalysisJob used by the content analysis pipeline.
 *
 * This repository is separate from MOD-002's ContentAnalysisJobRepository (write-only)
 * and adds the polling query needed by MOD-003 to read and process pending jobs.
 *
 * AC-057: At-least-once delivery is guaranteed by polling for PENDING and retryable records.
 */
public interface ContentAnalysisJobPollingRepository extends JpaRepository<ContentAnalysisJob, Long> {

    /**
     * Returns jobs eligible for processing:
     *   - status = PENDING (never attempted), OR
     *   - status = FAILED AND retry_count < maxRetries (eligible for retry)
     *
     * Ordered by created_at ascending so oldest jobs are processed first.
     *
     * AC-056: Retry jobs are included when retry_count is below the max retry threshold.
     */
    @Query("SELECT j FROM ContentAnalysisJob j WHERE j.status = 'PENDING' " +
           "OR (j.status = 'FAILED' AND j.retryCount < :maxRetries) " +
           "ORDER BY j.createdAt ASC")
    List<ContentAnalysisJob> findPendingAndRetryableJobs(@Param("maxRetries") int maxRetries);

    /**
     * Returns all jobs for a given item (used in tests to verify job state).
     */
    List<ContentAnalysisJob> findByItemId(Long itemId);

    /**
     * Returns jobs by status (used in tests).
     */
    List<ContentAnalysisJob> findByStatus(JobStatus status);
}
