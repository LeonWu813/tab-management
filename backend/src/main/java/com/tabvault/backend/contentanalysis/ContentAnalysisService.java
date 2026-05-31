package com.tabvault.backend.contentanalysis;

import com.tabvault.backend.contentextraction.ContentExtractionService;
import com.tabvault.backend.contentextraction.ExtractionResult;
import com.tabvault.backend.items.Category;
import com.tabvault.backend.items.CategoryRepository;
import com.tabvault.backend.items.ContentAnalysisJob;
import com.tabvault.backend.items.Item;
import com.tabvault.backend.items.ItemRepository;
import com.tabvault.backend.items.JobStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Core service for the content analysis pipeline.
 *
 * Polls the content_analysis_jobs outbox table for PENDING and retryable records,
 * calls the Claude API via the tool-use pattern, and writes analysis results back
 * to the item record. Creates SuggestedReminder records for detected deadlines.
 *
 * AC-007: Claude API request sent using tool-use pattern (generate_summary + categorize_content).
 * AC-008: Page text truncated to max 3,000 tokens before API request.
 * AC-009: URL deduplication cache is checked before every Claude API call.
 * AC-010: summary, suggestedCategory, contentType written to item record on completion.
 * AC-011: extract_deadlines tool only invoked when the model determines time-sensitive content is present.
 * AC-012: SuggestedReminder records created for each deadline from extract_deadlines.
 * AC-013: Reminders start in PENDING_CONFIRMATION status — no push notifications dispatched.
 * AC-056: Failed jobs are retried up to MAX_RETRIES times; then marked permanently FAILED.
 * AC-057: At-least-once delivery by polling for PENDING and retryable records.
 */
@Service
public class ContentAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(ContentAnalysisService.class);

    /**
     * Maximum number of retry attempts for a failed analysis job.
     * AC-056: After 3 failures, job status is set to FAILED permanently.
     */
    static final int MAX_RETRIES = 3;

    private final ContentAnalysisJobPollingRepository jobRepository;
    private final ItemRepository itemRepository;
    private final CategoryRepository categoryRepository;
    private final SuggestedReminderRepository reminderRepository;
    private final ClaudeApiClient claudeApiClient;
    private final AnalysisCacheService analysisCacheService;
    private final ContentExtractionService contentExtractionService;

    public ContentAnalysisService(
            ContentAnalysisJobPollingRepository jobRepository,
            ItemRepository itemRepository,
            CategoryRepository categoryRepository,
            SuggestedReminderRepository reminderRepository,
            ClaudeApiClient claudeApiClient,
            AnalysisCacheService analysisCacheService,
            ContentExtractionService contentExtractionService) {
        this.jobRepository = jobRepository;
        this.itemRepository = itemRepository;
        this.categoryRepository = categoryRepository;
        this.reminderRepository = reminderRepository;
        this.claudeApiClient = claudeApiClient;
        this.analysisCacheService = analysisCacheService;
        this.contentExtractionService = contentExtractionService;
    }

    // -------------------------------------------------------------------------
    // Scheduler: poll for pending jobs
    // -------------------------------------------------------------------------

    /**
     * Polls the content_analysis_jobs table for pending and retryable jobs.
     *
     * fixedDelay of 5 seconds means each poll starts 5 seconds after the previous
     * poll completes, satisfying the "within 5 seconds" requirement (AC-007).
     *
     * AC-057: At-least-once delivery — jobs are polled until completed or permanently failed.
     */
    @Scheduled(fixedDelayString = "${app.content-analysis.poll-interval-ms:5000}")
    public void processPendingJobs() {
        List<ContentAnalysisJob> jobs = jobRepository.findPendingAndRetryableJobs(MAX_RETRIES);
        if (jobs.isEmpty()) {
            return;
        }
        logger.info("Processing content analysis jobs count={}", jobs.size());

        for (ContentAnalysisJob job : jobs) {
            try {
                processJob(job);
            } catch (Exception exception) {
                // Safety catch — processJob has its own try/catch; this handles unexpected errors
                // (e.g., DB connection failure during status write).
                logger.error("Unexpected error processing job jobId={} itemId={}",
                        job.getId(), job.getItemId(), exception);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Job processing
    // -------------------------------------------------------------------------

    /**
     * Processes a single content analysis job.
     *
     * Step 1: Mark job as PROCESSING (prevents duplicate processing in concurrent cycles).
     * Step 2: Load the item record.
     * Step 3: Check URL deduplication cache (AC-009).
     * Step 4: Call Claude API if cache miss (AC-007, AC-008).
     * Step 5: Write analysis results to item record (AC-010).
     * Step 6: Create reminder records for detected deadlines (AC-012).
     * Step 7: Mark job as COMPLETED (AC-057: job not complete until item is updated).
     *
     * On failure: increment retry_count, set status back to FAILED for retry eligibility.
     * After MAX_RETRIES failures: mark job as permanently FAILED (AC-056).
     */
    @Transactional
    public void processJob(ContentAnalysisJob job) {
        Long jobId = job.getId();
        Long itemId = job.getItemId();

        logger.info("Processing analysis job jobId={} itemId={} retryCount={}",
                jobId, itemId, job.getRetryCount());

        // Mark as PROCESSING so concurrent poll cycles skip this job
        job.setStatus(JobStatus.PROCESSING);
        job.setLastAttemptedAt(OffsetDateTime.now());
        jobRepository.save(job);

        try {
            // Load item record
            Optional<Item> itemOptional = itemRepository.findById(itemId);
            if (itemOptional.isEmpty()) {
                // Item was deleted after the job was queued — mark permanently failed
                logger.warn("Item not found for job — marking FAILED permanently jobId={} itemId={}",
                        jobId, itemId);
                job.setStatus(JobStatus.FAILED);
                jobRepository.save(job);
                return;
            }

            Item item = itemOptional.get();
            String url = item.getUrl();
            String title = item.getTitle();
            Long userId = item.getUserId();

            // ---------------------------------------------------------------
            // MOD-004: Content Extraction
            // Extract text or metadata from the URL before calling Claude.
            // For NOTE items (url=null): skip extraction; use note body text.
            // For non-YouTube video items: extraction stores metadata and skips
            // the Claude API call entirely (AC-031, AC-058).
            // ---------------------------------------------------------------
            ExtractionResult extraction = null;
            if (url != null && !url.isBlank()) {
                extraction = contentExtractionService.extract(url);

                // Store extracted metadata on the item record (thumbnail, platform, page text)
                if (extraction.thumbnailUrl() != null) {
                    item.setThumbnailUrl(extraction.thumbnailUrl());
                }
                if (extraction.platform() != null) {
                    item.setPlatform(extraction.platform());
                }
                if (extraction.pageText() != null && !extraction.pageText().isBlank()) {
                    item.setPageText(extraction.pageText());
                }
                // Title override: when extraction provides a more accurate title (e.g., YouTube oEmbed)
                if (extraction.title() != null && !extraction.title().isBlank()) {
                    item.setTitle(extraction.title());
                }
                // Persist extraction metadata to the item record immediately
                itemRepository.save(item);
                logger.info("Extraction metadata stored jobId={} itemId={} platform={} hasThumbnail={}",
                        jobId, itemId, extraction.platform(), extraction.thumbnailUrl() != null);
            }

            // AC-031, AC-058: Skip Claude API for non-YouTube video items and YouTube
            // items without a transcript. summarySkipped=true means summary stays null.
            if (extraction != null && extraction.summarySkipped()) {
                logger.info("Skipping Claude API — summarySkipped=true jobId={} itemId={} platform={}",
                        jobId, itemId, extraction.platform());
                job.setStatus(JobStatus.COMPLETED);
                jobRepository.save(job);
                return;
            }

            // Load user's existing categories for prompt context (MOD-001 dependency)
            List<String> existingCategories = loadUserCategoryNames(userId);

            // AC-009: Check URL deduplication cache before calling Claude API
            AnalysisResult result;
            Optional<AnalysisResult> cached = (url != null && !url.isBlank())
                    ? analysisCacheService.get(url)
                    : Optional.empty();

            if (cached.isPresent()) {
                result = cached.get();
                logger.info("Cache hit for URL analysis jobId={} itemId={} url={}", jobId, itemId, url);
            } else {
                // Cache miss — call Claude API (AC-007, AC-008)
                // Determine the text to analyze:
                //   - For URL-based items: use pageText from MOD-004 extraction
                //   - For NOTE items: use noteBody
                //   - Fallback: null (Claude analyzes based on title + URL only)
                String textToAnalyze;
                if (extraction != null && extraction.pageText() != null && !extraction.pageText().isBlank()) {
                    textToAnalyze = extraction.pageText();
                } else if (item.getPageText() != null && !item.getPageText().isBlank()) {
                    textToAnalyze = item.getPageText();
                } else {
                    textToAnalyze = item.getNoteBody();
                }
                result = claudeApiClient.analyze(title, url, textToAnalyze, existingCategories);

                // Write result to cache (only for URL-based items — cache key is the URL)
                if (url != null && !url.isBlank()) {
                    analysisCacheService.put(url, result);
                }
                logger.info("Analysis complete from Claude API jobId={} itemId={}", jobId, itemId);
            }

            // AC-010: Write summary, suggestedCategory, and contentType to item record
            item.setSummary(result.summary());
            item.setSuggestedCategory(result.suggestedCategory());
            item.setContentType(result.contentType());
            itemRepository.save(item);

            // AC-012: Create SuggestedReminder records for detected deadlines
            // AC-011: deadlines list is only non-empty when extract_deadlines was invoked by the model
            if (result.deadlines() != null && !result.deadlines().isEmpty()) {
                createSuggestedReminders(item, result.deadlines());
            }

            // AC-057: Mark COMPLETED — job is not complete until item record has been updated
            job.setStatus(JobStatus.COMPLETED);
            jobRepository.save(job);

            logger.info("Analysis job completed jobId={} itemId={} category={} deadlineCount={}",
                    jobId, itemId, result.suggestedCategory(),
                    result.deadlines() != null ? result.deadlines().size() : 0);

        } catch (Exception exception) {
            handleJobFailure(job, exception);
        }
    }

    // -------------------------------------------------------------------------
    // Reminder creation
    // -------------------------------------------------------------------------

    /**
     * Creates a SuggestedReminder record for each detected deadline.
     *
     * AC-012: Each deadline returns a reminder with detected date, label, urgency level.
     * AC-013: Reminder status starts as PENDING_CONFIRMATION; no push notifications dispatched.
     */
    private void createSuggestedReminders(Item item, List<AnalysisResult.DetectedDeadline> deadlines) {
        for (AnalysisResult.DetectedDeadline deadline : deadlines) {
            try {
                SuggestedReminder reminder = new SuggestedReminder(
                        item.getId(),
                        item.getUserId(),
                        deadline.date(),
                        deadline.label(),
                        deadline.urgency()
                );
                reminderRepository.save(reminder);
                logger.info("Suggested reminder created itemId={} userId={} date={} label={} urgency={}",
                        item.getId(), item.getUserId(), deadline.date(),
                        deadline.label(), deadline.urgency());
            } catch (Exception exception) {
                // Non-fatal: failure to create one reminder must not fail the entire job
                logger.error("Failed to create suggested reminder itemId={} date={} error={}",
                        item.getId(), deadline.date(), exception.getMessage());
            }
        }
    }

    // -------------------------------------------------------------------------
    // Retry / failure handling
    // -------------------------------------------------------------------------

    /**
     * Handles a job processing failure: increments retry_count and updates status.
     *
     * AC-056: retry_count and last_attempted_at are updated on each attempt.
     *         After MAX_RETRIES failures, job status is set to FAILED permanently.
     */
    private void handleJobFailure(ContentAnalysisJob job, Exception exception) {
        int newRetryCount = job.getRetryCount() + 1;
        job.setRetryCount(newRetryCount);
        job.setLastAttemptedAt(OffsetDateTime.now());
        job.setStatus(JobStatus.FAILED);

        if (newRetryCount >= MAX_RETRIES) {
            // Permanently failed — item will not receive a summary
            logger.error("Analysis job permanently failed after {} retries jobId={} itemId={}",
                    MAX_RETRIES, job.getId(), job.getItemId(), exception);
        } else {
            // Still retryable — FAILED status means it will be picked up by the next poll cycle
            logger.warn("Analysis job failed retryCount={}/{} jobId={} itemId={} error={}",
                    newRetryCount, MAX_RETRIES, job.getId(), job.getItemId(), exception.getMessage());
        }

        jobRepository.save(job);
    }

    // -------------------------------------------------------------------------
    // Helper: load user category names for prompt context
    // -------------------------------------------------------------------------

    /**
     * Loads the user's category names to provide context to the Claude prompt.
     *
     * Provides MOD-001 dependency: existing category list helps the model suggest
     * a category name consistent with the user's existing taxonomy.
     */
    private List<String> loadUserCategoryNames(Long userId) {
        try {
            return categoryRepository.findByUserIdOrderBySortOrderAscCreatedAtAsc(userId)
                    .stream()
                    .map(Category::getName)
                    .collect(Collectors.toList());
        } catch (Exception exception) {
            logger.warn("Failed to load user categories for prompt context userId={} error={}",
                    userId, exception.getMessage());
            return List.of();
        }
    }
}
