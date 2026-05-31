package com.tabvault.backend.contentanalysis;

import com.tabvault.backend.items.Category;
import com.tabvault.backend.items.CategoryRepository;
import com.tabvault.backend.items.ContentAnalysisJob;
import com.tabvault.backend.items.Item;
import com.tabvault.backend.items.ItemRepository;
import com.tabvault.backend.items.ItemType;
import com.tabvault.backend.items.JobStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ContentAnalysisService.
 *
 * Tests cover:
 * - AC-009: Cache hit skips Claude API call
 * - AC-010: Analysis results written to item record
 * - AC-012: Reminder records created for detected deadlines
 * - AC-013: Reminders created with PENDING_CONFIRMATION status
 * - AC-056: Retry logic — retry_count incremented, FAILED after MAX_RETRIES
 * - AC-057: processPendingJobs polls and processes PENDING records
 */
@ExtendWith(MockitoExtension.class)
class ContentAnalysisServiceTest {

    @Mock
    private ContentAnalysisJobPollingRepository jobRepository;

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private SuggestedReminderRepository reminderRepository;

    @Mock
    private ClaudeApiClient claudeApiClient;

    @Mock
    private AnalysisCacheService analysisCacheService;

    private ContentAnalysisService service;

    @BeforeEach
    void setUp() {
        service = new ContentAnalysisService(
                jobRepository,
                itemRepository,
                categoryRepository,
                reminderRepository,
                claudeApiClient,
                analysisCacheService
        );
    }

    // -------------------------------------------------------------------------
    // Helper factories
    // -------------------------------------------------------------------------

    private Item makeLinkItem(Long id, Long userId, String url, String title) {
        Item item = new Item(userId, ItemType.LINK, url, title, null);
        // Set id via reflection (no public setter — ID is DB-assigned)
        setId(item, id);
        return item;
    }

    private ContentAnalysisJob makeJob(Long id, Long itemId, int retryCount, JobStatus status) {
        ContentAnalysisJob job = new ContentAnalysisJob(itemId);
        setId(job, id);
        job.setRetryCount(retryCount);
        job.setStatus(status);
        return job;
    }

    private void setId(Object target, Long idValue) {
        try {
            Field idField = target.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(target, idValue);
        } catch (Exception exception) {
            throw new RuntimeException("Failed to set id via reflection", exception);
        }
    }

    // -------------------------------------------------------------------------
    // AC-057: processPendingJobs polls and processes pending jobs
    // -------------------------------------------------------------------------

    @Test
    void processPendingJobs_whenNoPendingJobs_doesNothing() {
        when(jobRepository.findPendingAndRetryableJobs(ContentAnalysisService.MAX_RETRIES))
                .thenReturn(List.of());

        service.processPendingJobs();

        verify(itemRepository, never()).findById(anyLong());
    }

    @Test
    void processPendingJobs_processesEachPendingJob() {
        Item item1 = makeLinkItem(1L, 10L, "https://example.com/a", "Article A");
        Item item2 = makeLinkItem(2L, 10L, "https://example.com/b", "Article B");
        ContentAnalysisJob job1 = makeJob(100L, 1L, 0, JobStatus.PENDING);
        ContentAnalysisJob job2 = makeJob(101L, 2L, 0, JobStatus.PENDING);

        when(jobRepository.findPendingAndRetryableJobs(anyInt())).thenReturn(List.of(job1, job2));
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item1));
        when(itemRepository.findById(2L)).thenReturn(Optional.of(item2));
        when(categoryRepository.findByUserIdOrderBySortOrderAscCreatedAtAsc(10L)).thenReturn(List.of());
        when(analysisCacheService.get(anyString())).thenReturn(Optional.empty());

        AnalysisResult result = new AnalysisResult("Summary", "Tech", "article", List.of());
        when(claudeApiClient.analyze(anyString(), anyString(), any(), any())).thenReturn(result);
        when(jobRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.processPendingJobs();

        verify(claudeApiClient, times(2)).analyze(anyString(), anyString(), any(), any());
    }

    // -------------------------------------------------------------------------
    // AC-009: Cache hit skips Claude API call
    // -------------------------------------------------------------------------

    @Test
    void processJob_whenCacheHit_skipsCloudeApiCall() {
        Item item = makeLinkItem(1L, 10L, "https://example.com/article", "Test Article");
        ContentAnalysisJob job = makeJob(100L, 1L, 0, JobStatus.PENDING);

        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(categoryRepository.findByUserIdOrderBySortOrderAscCreatedAtAsc(10L)).thenReturn(List.of());

        AnalysisResult cached = new AnalysisResult("Cached summary", "Research", "article", List.of());
        when(analysisCacheService.get("https://example.com/article")).thenReturn(Optional.of(cached));
        when(jobRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.processJob(job);

        // AC-009: Claude API must not be called when cache hit
        verify(claudeApiClient, never()).analyze(anyString(), anyString(), any(), any());
        // Cache result must not be re-written (cache hit, not a new result)
        verify(analysisCacheService, never()).put(anyString(), any());
    }

    @Test
    void processJob_whenCacheMiss_callsClaudeApiAndWritesToCache() {
        Item item = makeLinkItem(1L, 10L, "https://example.com/article", "Test Article");
        ContentAnalysisJob job = makeJob(100L, 1L, 0, JobStatus.PENDING);

        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(categoryRepository.findByUserIdOrderBySortOrderAscCreatedAtAsc(10L)).thenReturn(List.of());
        when(analysisCacheService.get("https://example.com/article")).thenReturn(Optional.empty());

        AnalysisResult result = new AnalysisResult("Summary text", "Technology", "article", List.of());
        when(claudeApiClient.analyze(anyString(), anyString(), any(), any())).thenReturn(result);
        when(jobRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.processJob(job);

        verify(claudeApiClient, times(1)).analyze(anyString(), anyString(), any(), any());
        verify(analysisCacheService, times(1)).put("https://example.com/article", result);
    }

    // -------------------------------------------------------------------------
    // AC-010: Analysis results written to item record
    // -------------------------------------------------------------------------

    @Test
    void processJob_writesAnalysisSummaryToItemRecord() {
        Item item = makeLinkItem(1L, 10L, "https://example.com", "Title");
        ContentAnalysisJob job = makeJob(100L, 1L, 0, JobStatus.PENDING);

        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(categoryRepository.findByUserIdOrderBySortOrderAscCreatedAtAsc(10L)).thenReturn(List.of());
        when(analysisCacheService.get(anyString())).thenReturn(Optional.empty());

        AnalysisResult result = new AnalysisResult("This is the summary", "Work", "article", List.of());
        when(claudeApiClient.analyze(anyString(), anyString(), any(), any())).thenReturn(result);
        when(jobRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ArgumentCaptor<Item> itemCaptor = ArgumentCaptor.forClass(Item.class);
        when(itemRepository.save(itemCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        service.processJob(job);

        // AC-010: summary must be written to the item record
        assertThat(itemCaptor.getValue().getSummary()).isEqualTo("This is the summary");
    }

    @Test
    void processJob_setsJobStatusToCompleted() {
        Item item = makeLinkItem(1L, 10L, "https://example.com", "Title");
        ContentAnalysisJob job = makeJob(100L, 1L, 0, JobStatus.PENDING);

        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(categoryRepository.findByUserIdOrderBySortOrderAscCreatedAtAsc(10L)).thenReturn(List.of());
        when(analysisCacheService.get(anyString())).thenReturn(Optional.empty());

        AnalysisResult result = new AnalysisResult("Summary", "Tech", "article", List.of());
        when(claudeApiClient.analyze(anyString(), anyString(), any(), any())).thenReturn(result);
        when(jobRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.processJob(job);

        // AC-057: job must be COMPLETED after item record is updated
        assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
    }

    // -------------------------------------------------------------------------
    // AC-012: Reminder records created for detected deadlines
    // AC-013: Reminders start in PENDING_CONFIRMATION status
    // -------------------------------------------------------------------------

    @Test
    void processJob_createsReminderRecordsForDetectedDeadlines() {
        Item item = makeLinkItem(1L, 10L, "https://example.com/event", "Event Page");
        ContentAnalysisJob job = makeJob(100L, 1L, 0, JobStatus.PENDING);

        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(categoryRepository.findByUserIdOrderBySortOrderAscCreatedAtAsc(10L)).thenReturn(List.of());
        when(analysisCacheService.get(anyString())).thenReturn(Optional.empty());

        List<AnalysisResult.DetectedDeadline> deadlines = List.of(
                new AnalysisResult.DetectedDeadline(
                        LocalDate.of(2026, 8, 15), "Application deadline", UrgencyLevel.HIGH),
                new AnalysisResult.DetectedDeadline(
                        LocalDate.of(2026, 9, 1), "Registration closes", UrgencyLevel.MEDIUM)
        );
        AnalysisResult result = new AnalysisResult("Event summary", "Events", "article", deadlines);
        when(claudeApiClient.analyze(anyString(), anyString(), any(), any())).thenReturn(result);
        when(jobRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ArgumentCaptor<SuggestedReminder> reminderCaptor =
                ArgumentCaptor.forClass(SuggestedReminder.class);
        when(reminderRepository.save(reminderCaptor.capture()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.processJob(job);

        // AC-012: two reminders created, one per deadline
        verify(reminderRepository, times(2)).save(any(SuggestedReminder.class));

        List<SuggestedReminder> savedReminders = reminderCaptor.getAllValues();
        assertThat(savedReminders).hasSize(2);

        SuggestedReminder first = savedReminders.get(0);
        assertThat(first.getDetectedDate()).isEqualTo(LocalDate.of(2026, 8, 15));
        assertThat(first.getLabel()).isEqualTo("Application deadline");
        assertThat(first.getUrgency()).isEqualTo(UrgencyLevel.HIGH);
        // AC-013: PENDING_CONFIRMATION status set by constructor
        assertThat(first.getStatus()).isEqualTo(ReminderStatus.PENDING_CONFIRMATION);

        SuggestedReminder second = savedReminders.get(1);
        assertThat(second.getDetectedDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(second.getUrgency()).isEqualTo(UrgencyLevel.MEDIUM);
        assertThat(second.getStatus()).isEqualTo(ReminderStatus.PENDING_CONFIRMATION);
    }

    @Test
    void processJob_whenNoDeadlines_createsNoReminders() {
        Item item = makeLinkItem(1L, 10L, "https://example.com/article", "Regular Article");
        ContentAnalysisJob job = makeJob(100L, 1L, 0, JobStatus.PENDING);

        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(categoryRepository.findByUserIdOrderBySortOrderAscCreatedAtAsc(10L)).thenReturn(List.of());
        when(analysisCacheService.get(anyString())).thenReturn(Optional.empty());

        AnalysisResult result = new AnalysisResult("Summary", "Tech", "article", List.of());
        when(claudeApiClient.analyze(anyString(), anyString(), any(), any())).thenReturn(result);
        when(jobRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.processJob(job);

        // AC-011: extract_deadlines not called = empty list = no reminders
        verify(reminderRepository, never()).save(any(SuggestedReminder.class));
    }

    // -------------------------------------------------------------------------
    // AC-056: Retry logic
    // -------------------------------------------------------------------------

    @Test
    void processJob_onApiFailure_incrementsRetryCountAndSetsFailedStatus() {
        Item item = makeLinkItem(1L, 10L, "https://example.com", "Title");
        ContentAnalysisJob job = makeJob(100L, 1L, 0, JobStatus.PENDING);

        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(categoryRepository.findByUserIdOrderBySortOrderAscCreatedAtAsc(10L)).thenReturn(List.of());
        when(analysisCacheService.get(anyString())).thenReturn(Optional.empty());
        when(claudeApiClient.analyze(anyString(), anyString(), any(), any()))
                .thenThrow(new ClaudeApiException("API error"));
        when(jobRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.processJob(job);

        // AC-056: retry_count incremented to 1
        assertThat(job.getRetryCount()).isEqualTo(1);
        // Status FAILED so next poll cycle picks it up
        assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
        // last_attempted_at updated
        assertThat(job.getLastAttemptedAt()).isNotNull();
    }

    @Test
    void processJob_afterMaxRetries_jobRemainsFailedPermanently() {
        Item item = makeLinkItem(1L, 10L, "https://example.com", "Title");
        // Job already at MAX_RETRIES - 1 (one more failure will hit the limit)
        ContentAnalysisJob job = makeJob(100L, 1L, ContentAnalysisService.MAX_RETRIES - 1, JobStatus.FAILED);

        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(categoryRepository.findByUserIdOrderBySortOrderAscCreatedAtAsc(10L)).thenReturn(List.of());
        when(analysisCacheService.get(anyString())).thenReturn(Optional.empty());
        when(claudeApiClient.analyze(anyString(), anyString(), any(), any()))
                .thenThrow(new ClaudeApiException("API error"));
        when(jobRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.processJob(job);

        // AC-056: after MAX_RETRIES failures, job is permanently FAILED
        assertThat(job.getRetryCount()).isEqualTo(ContentAnalysisService.MAX_RETRIES);
        assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
    }

    @Test
    void processJob_whenItemNotFound_marksJobFailedPermanently() {
        ContentAnalysisJob job = makeJob(100L, 999L, 0, JobStatus.PENDING);

        when(itemRepository.findById(999L)).thenReturn(Optional.empty());
        when(jobRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.processJob(job);

        // Item deleted after job queued — job marked FAILED permanently, no retry
        assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
        verify(claudeApiClient, never()).analyze(anyString(), anyString(), any(), any());
    }

    // -------------------------------------------------------------------------
    // Category context provided to Claude prompt (MOD-001 dependency)
    // -------------------------------------------------------------------------

    @Test
    void processJob_passesExistingCategoriesToClaudeApiForContext() {
        Item item = makeLinkItem(1L, 10L, "https://example.com", "Title");
        ContentAnalysisJob job = makeJob(100L, 1L, 0, JobStatus.PENDING);

        Category cat1 = new Category(10L, "Work", "#ff5733", null);
        Category cat2 = new Category(10L, "Research", "#3357ff", null);

        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(categoryRepository.findByUserIdOrderBySortOrderAscCreatedAtAsc(10L))
                .thenReturn(List.of(cat1, cat2));
        when(analysisCacheService.get(anyString())).thenReturn(Optional.empty());

        AnalysisResult result = new AnalysisResult("Summary", "Work", "article", List.of());
        ArgumentCaptor<List> categoriesCaptor = ArgumentCaptor.forClass(List.class);
        when(claudeApiClient.analyze(anyString(), anyString(), any(), categoriesCaptor.capture()))
                .thenReturn(result);
        when(jobRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.processJob(job);

        @SuppressWarnings("unchecked")
        List<String> passedCategories = (List<String>) categoriesCaptor.getValue();
        assertThat(passedCategories).containsExactly("Work", "Research");
    }
}
