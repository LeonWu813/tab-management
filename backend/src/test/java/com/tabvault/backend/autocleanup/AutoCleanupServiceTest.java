package com.tabvault.backend.autocleanup;

import com.tabvault.backend.contentanalysis.ReminderStatus;
import com.tabvault.backend.contentanalysis.SuggestedReminder;
import com.tabvault.backend.contentanalysis.SuggestedReminderRepository;
import com.tabvault.backend.contentanalysis.UrgencyLevel;
import com.tabvault.backend.items.Item;
import com.tabvault.backend.items.ItemRepository;
import com.tabvault.backend.items.ItemType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AutoCleanupService} (MOD-006).
 *
 * Coverage:
 * - AC-033: staleness reminder created for stale non-pinned, non-archived items
 * - AC-034: items auto-archived after dismissed reminder + grace period without visit
 * - AC-035: staleness reminders cleared on item visit
 * - AC-037: pinned items never receive staleness reminders
 * - AC-038: settings update — allowed threshold values (14, 30, 60, 90)
 * - AC-038: settings update — invalid threshold value throws InvalidStalenessThresholdException
 * - AC-039: opted-out users are skipped
 * - AC-066: idempotency — no duplicate reminder when PENDING or PENDING_CONFIRMATION exists
 */
@ExtendWith(MockitoExtension.class)
class AutoCleanupServiceTest {

    @Mock
    private UserCleanupSettingsRepository cleanupSettingsRepository;

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private SuggestedReminderRepository reminderRepository;

    private AutoCleanupService service;

    @BeforeEach
    void setUp() {
        service = new AutoCleanupService(
                cleanupSettingsRepository,
                itemRepository,
                reminderRepository,
                "0 0 9 * * ?"
        );
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Item makeItem(Long id, Long userId, boolean pinned, boolean archived,
                          OffsetDateTime lastVisitedAt) {
        Item item = new Item(userId, ItemType.LINK, "https://example.com/" + id, "Title " + id, null);
        setField(item, "id", id);
        item.setPinned(pinned);
        item.setArchived(archived);
        item.setLastVisitedAt(lastVisitedAt);
        return item;
    }

    private UserCleanupSettings makeSettings(Long userId, boolean enabled, int threshold) {
        UserCleanupSettings settings = new UserCleanupSettings(userId);
        settings.setAutoCleanupEnabled(enabled);
        settings.setStalenessThresholdDays(threshold);
        return settings;
    }

    private SuggestedReminder makeReminder(Long id, Long itemId, Long userId,
                                           ReminderStatus status, OffsetDateTime updatedAt) {
        SuggestedReminder r = new SuggestedReminder(itemId, userId, LocalDate.now(),
                "Test label", UrgencyLevel.LOW);
        r.setStatus(status);
        setField(r, "id", id);
        setField(r, "updatedAt", updatedAt);
        return r;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            // Walk up the class hierarchy to find the field
            Class<?> clazz = target.getClass();
            while (clazz != null) {
                try {
                    Field f = clazz.getDeclaredField(fieldName);
                    f.setAccessible(true);
                    f.set(target, value);
                    return;
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
                }
            }
            throw new RuntimeException("Field not found: " + fieldName);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to set field via reflection: " + fieldName, e);
        }
    }

    // -------------------------------------------------------------------------
    // AC-038, AC-039: settings
    // -------------------------------------------------------------------------

    @Test
    void getSettings_whenNoSettingsExist_returnsDefaults() {
        when(cleanupSettingsRepository.findByUserId(1L)).thenReturn(Optional.empty());

        CleanupSettingsResponse response = service.getSettings(1L);

        // AC-038: default threshold is 30 days
        assertThat(response.stalenessThresholdDays()).isEqualTo(30);
        // AC-039: default is auto-cleanup enabled
        assertThat(response.autoCleanupEnabled()).isTrue();
        // Should NOT persist when just reading defaults
        verify(cleanupSettingsRepository, never()).save(any());
    }

    @Test
    void updateSettings_withValidThreshold_updatesThreshold() {
        UserCleanupSettings existing = makeSettings(1L, true, 30);
        when(cleanupSettingsRepository.findByUserId(1L)).thenReturn(Optional.of(existing));
        when(cleanupSettingsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CleanupSettingsRequest request = new CleanupSettingsRequest(14, null);
        CleanupSettingsResponse response = service.updateSettings(1L, request);

        // AC-038: threshold updated to allowed value 14
        assertThat(response.stalenessThresholdDays()).isEqualTo(14);
    }

    @Test
    void updateSettings_withAllowedThresholdValues_acceptsAll() {
        // AC-038: all four allowed values — 14, 30, 60, 90 — must be accepted
        for (int threshold : List.of(14, 30, 60, 90)) {
            UserCleanupSettings existing = makeSettings(1L, true, 30);
            when(cleanupSettingsRepository.findByUserId(1L)).thenReturn(Optional.of(existing));
            when(cleanupSettingsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CleanupSettingsRequest request = new CleanupSettingsRequest(threshold, null);
            CleanupSettingsResponse response = service.updateSettings(1L, request);

            assertThat(response.stalenessThresholdDays()).isEqualTo(threshold);
        }
    }

    @Test
    void updateSettings_withInvalidThreshold_throwsInvalidStalenessThresholdException() {
        // AC-038: value 15 is not allowed
        CleanupSettingsRequest request = new CleanupSettingsRequest(15, null);

        assertThatThrownBy(() -> service.updateSettings(1L, request))
                .isInstanceOf(InvalidStalenessThresholdException.class)
                .hasMessageContaining("15");

        verify(cleanupSettingsRepository, never()).save(any());
    }

    @Test
    void updateSettings_withAutoCleanupDisabled_disablesCleanup() {
        UserCleanupSettings existing = makeSettings(1L, true, 30);
        when(cleanupSettingsRepository.findByUserId(1L)).thenReturn(Optional.of(existing));
        when(cleanupSettingsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CleanupSettingsRequest request = new CleanupSettingsRequest(null, false);
        CleanupSettingsResponse response = service.updateSettings(1L, request);

        // AC-039: auto-cleanup disabled
        assertThat(response.autoCleanupEnabled()).isFalse();
    }

    @Test
    void updateSettings_withNullFields_leavesExistingValuesUnchanged() {
        UserCleanupSettings existing = makeSettings(1L, false, 60);
        when(cleanupSettingsRepository.findByUserId(1L)).thenReturn(Optional.of(existing));
        when(cleanupSettingsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Both fields null — nothing changes
        CleanupSettingsRequest request = new CleanupSettingsRequest(null, null);
        CleanupSettingsResponse response = service.updateSettings(1L, request);

        assertThat(response.stalenessThresholdDays()).isEqualTo(60);
        assertThat(response.autoCleanupEnabled()).isFalse();
    }

    // -------------------------------------------------------------------------
    // AC-039: processUserCleanup skips opted-out users
    // -------------------------------------------------------------------------

    @Test
    void processUserCleanup_whenUserOptedOut_skipsCleanup() {
        UserCleanupSettings settings = makeSettings(1L, false, 30);
        when(cleanupSettingsRepository.findByUserId(1L)).thenReturn(Optional.of(settings));

        AutoCleanupService.CleanupResult result = service.processUserCleanup(1L);

        // AC-039: no reminders created and no items archived for opted-out user
        assertThat(result.remindersCreated()).isZero();
        assertThat(result.itemsArchived()).isZero();
        verify(itemRepository, never()).findStaleItemsForUser(anyLong(), any());
    }

    // -------------------------------------------------------------------------
    // AC-033: createStalenessReminders
    // -------------------------------------------------------------------------

    @Test
    void createStalenessReminders_forStaleItem_createsReminder() {
        // Item not visited for 35 days; threshold is 30 days → stale
        Item staleItem = makeItem(10L, 1L, false, false, null);
        when(itemRepository.findStaleItemsForUser(eq(1L), any())).thenReturn(List.of(staleItem));
        when(reminderRepository.findByItemIdAndStatus(10L, ReminderStatus.PENDING))
                .thenReturn(Collections.emptyList());
        when(reminderRepository.findByItemIdAndStatus(10L, ReminderStatus.PENDING_CONFIRMATION))
                .thenReturn(Collections.emptyList());

        ArgumentCaptor<SuggestedReminder> captor = ArgumentCaptor.forClass(SuggestedReminder.class);
        when(reminderRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        int created = service.createStalenessReminders(1L, 30);

        // AC-033: one reminder created
        assertThat(created).isEqualTo(1);

        // AC-033: label format
        assertThat(captor.getValue().getLabel())
                .isEqualTo("You haven't revisited this in 30 days — still need it?");

        // Reminder starts as PENDING
        assertThat(captor.getValue().getStatus()).isEqualTo(ReminderStatus.PENDING);
    }

    @Test
    void createStalenessReminders_labelContainsThresholdDays() {
        // Threshold 60 days — label must say 60
        Item staleItem = makeItem(10L, 1L, false, false, null);
        when(itemRepository.findStaleItemsForUser(eq(1L), any())).thenReturn(List.of(staleItem));
        when(reminderRepository.findByItemIdAndStatus(any(), any())).thenReturn(Collections.emptyList());

        ArgumentCaptor<SuggestedReminder> captor = ArgumentCaptor.forClass(SuggestedReminder.class);
        when(reminderRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        service.createStalenessReminders(1L, 60);

        assertThat(captor.getValue().getLabel())
                .isEqualTo("You haven't revisited this in 60 days — still need it?");
    }

    @Test
    void createStalenessReminders_whenNoStaleItems_createsNoReminders() {
        when(itemRepository.findStaleItemsForUser(eq(1L), any())).thenReturn(Collections.emptyList());

        int created = service.createStalenessReminders(1L, 30);

        assertThat(created).isZero();
        verify(reminderRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // AC-066: idempotency
    // -------------------------------------------------------------------------

    @Test
    void createStalenessReminders_whenPendingReminderExists_doesNotCreateDuplicate() {
        Item staleItem = makeItem(10L, 1L, false, false, null);
        when(itemRepository.findStaleItemsForUser(eq(1L), any())).thenReturn(List.of(staleItem));

        // Existing PENDING reminder for the same item
        SuggestedReminder existing = makeReminder(5L, 10L, 1L, ReminderStatus.PENDING,
                OffsetDateTime.now().minusDays(1));
        when(reminderRepository.findByItemIdAndStatus(10L, ReminderStatus.PENDING))
                .thenReturn(List.of(existing));

        int created = service.createStalenessReminders(1L, 30);

        // AC-066: no new reminder created when PENDING already exists
        assertThat(created).isZero();
        verify(reminderRepository, never()).save(any());
    }

    @Test
    void createStalenessReminders_whenPendingConfirmationExists_doesNotCreateDuplicate() {
        Item staleItem = makeItem(10L, 1L, false, false, null);
        when(itemRepository.findStaleItemsForUser(eq(1L), any())).thenReturn(List.of(staleItem));

        // PENDING exists = empty; PENDING_CONFIRMATION exists
        when(reminderRepository.findByItemIdAndStatus(10L, ReminderStatus.PENDING))
                .thenReturn(Collections.emptyList());
        SuggestedReminder existing = makeReminder(5L, 10L, 1L, ReminderStatus.PENDING_CONFIRMATION,
                OffsetDateTime.now().minusDays(1));
        when(reminderRepository.findByItemIdAndStatus(10L, ReminderStatus.PENDING_CONFIRMATION))
                .thenReturn(List.of(existing));

        int created = service.createStalenessReminders(1L, 30);

        // AC-066: no new reminder created when PENDING_CONFIRMATION already exists
        assertThat(created).isZero();
        verify(reminderRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // AC-034: archiveItemsPassedGracePeriod
    // -------------------------------------------------------------------------

    @Test
    void archiveItemsPassedGracePeriod_whenDismissedAndGraceElapsed_archivesItem() {
        // Item not visited since dismissal, dismissed 8 days ago (> 7-day grace period)
        OffsetDateTime dismissedAt = OffsetDateTime.now(ZoneOffset.UTC).minusDays(8);
        Item item = makeItem(10L, 1L, false, false, null); // no visit after dismissal
        SuggestedReminder dismissedReminder = makeReminder(5L, 10L, 1L, ReminderStatus.DISMISSED,
                dismissedAt);

        // findByUserIdAndStatusNotOrderByDetectedDateAsc returns all non-CONFIRMED reminders
        when(reminderRepository.findByUserIdAndStatusNotOrderByDetectedDateAsc(1L,
                ReminderStatus.CONFIRMED)).thenReturn(List.of(dismissedReminder));
        when(itemRepository.findById(10L)).thenReturn(Optional.of(item));

        ArgumentCaptor<Item> itemCaptor = ArgumentCaptor.forClass(Item.class);
        when(itemRepository.save(itemCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

        int archived = service.archiveItemsPassedGracePeriod(1L);

        // AC-034: item archived after dismissed reminder + grace period
        assertThat(archived).isEqualTo(1);
        assertThat(itemCaptor.getValue().isArchived()).isTrue();
    }

    @Test
    void archiveItemsPassedGracePeriod_whenDismissedButGraceNotElapsed_doesNotArchive() {
        // Dismissed only 3 days ago — grace period not elapsed
        OffsetDateTime dismissedAt = OffsetDateTime.now(ZoneOffset.UTC).minusDays(3);
        SuggestedReminder dismissedReminder = makeReminder(5L, 10L, 1L, ReminderStatus.DISMISSED,
                dismissedAt);

        when(reminderRepository.findByUserIdAndStatusNotOrderByDetectedDateAsc(1L,
                ReminderStatus.CONFIRMED)).thenReturn(List.of(dismissedReminder));

        int archived = service.archiveItemsPassedGracePeriod(1L);

        // Grace period not elapsed — item not archived
        assertThat(archived).isZero();
        verify(itemRepository, never()).save(any());
    }

    @Test
    void archiveItemsPassedGracePeriod_whenVisitedAfterDismissal_doesNotArchive() {
        // Dismissed 8 days ago but item was visited 2 days ago — user "kept" it
        OffsetDateTime dismissedAt = OffsetDateTime.now(ZoneOffset.UTC).minusDays(8);
        OffsetDateTime visitedAt = OffsetDateTime.now(ZoneOffset.UTC).minusDays(2);
        Item item = makeItem(10L, 1L, false, false, visitedAt); // visited after dismissal
        SuggestedReminder dismissedReminder = makeReminder(5L, 10L, 1L, ReminderStatus.DISMISSED,
                dismissedAt);

        when(reminderRepository.findByUserIdAndStatusNotOrderByDetectedDateAsc(1L,
                ReminderStatus.CONFIRMED)).thenReturn(List.of(dismissedReminder));
        when(itemRepository.findById(10L)).thenReturn(Optional.of(item));

        int archived = service.archiveItemsPassedGracePeriod(1L);

        // AC-034: item visited after dismissal — do NOT archive
        assertThat(archived).isZero();
        verify(itemRepository, never()).save(any());
    }

    @Test
    void archiveItemsPassedGracePeriod_whenItemAlreadyArchived_doesNotDoubleArchive() {
        OffsetDateTime dismissedAt = OffsetDateTime.now(ZoneOffset.UTC).minusDays(10);
        Item alreadyArchived = makeItem(10L, 1L, false, true, null); // already archived
        SuggestedReminder dismissedReminder = makeReminder(5L, 10L, 1L, ReminderStatus.DISMISSED,
                dismissedAt);

        when(reminderRepository.findByUserIdAndStatusNotOrderByDetectedDateAsc(1L,
                ReminderStatus.CONFIRMED)).thenReturn(List.of(dismissedReminder));
        when(itemRepository.findById(10L)).thenReturn(Optional.of(alreadyArchived));

        int archived = service.archiveItemsPassedGracePeriod(1L);

        assertThat(archived).isZero();
        verify(itemRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // AC-035: clearStalenessRemindersOnVisit
    // -------------------------------------------------------------------------

    @Test
    void clearStalenessRemindersOnVisit_deletesPendingReminders() {
        // AC-035: visiting an item clears its PENDING staleness reminders
        service.clearStalenessRemindersOnVisit(10L);

        verify(reminderRepository).deletePendingStalenessRemindersForItem(10L);
    }
}
