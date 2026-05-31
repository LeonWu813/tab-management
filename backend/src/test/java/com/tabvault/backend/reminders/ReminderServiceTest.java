package com.tabvault.backend.reminders;

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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ReminderService (MOD-005).
 *
 * Coverage:
 * - AC-021: manual reminder creation — item ownership check, future date, optional label
 * - AC-023: update, confirm, and dismiss reminder — ownership check, status transitions
 * - AC-024: dueWithin24Hours badge indicator on ReminderResponse
 * - AC-060: push subscription registration — upsert on duplicate endpoint
 */
@ExtendWith(MockitoExtension.class)
class ReminderServiceTest {

    @Mock
    private SuggestedReminderRepository reminderRepository;

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private PushSubscriptionRepository pushSubscriptionRepository;

    private ReminderService service;

    @BeforeEach
    void setUp() {
        service = new ReminderService(reminderRepository, itemRepository, pushSubscriptionRepository);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Item makeItem(Long id, Long userId, String title) {
        Item item = new Item(userId, ItemType.LINK, "https://example.com/" + id, title, null);
        setId(item, id);
        return item;
    }

    private SuggestedReminder makeReminder(Long id, Long itemId, Long userId,
                                           LocalDate dueDate, ReminderStatus status) {
        SuggestedReminder r = new SuggestedReminder(itemId, userId, dueDate, "Test label",
                UrgencyLevel.MEDIUM);
        r.setStatus(status);
        setId(r, id);
        return r;
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
    // AC-021: createManualReminder
    // -------------------------------------------------------------------------

    @Test
    void createManualReminder_whenItemOwned_createsConfirmedReminder() {
        Item item = makeItem(1L, 10L, "My Article");
        when(itemRepository.findByIdAndUserId(1L, 10L)).thenReturn(Optional.of(item));

        LocalDate futureDate = LocalDate.now().plusDays(7);
        CreateReminderRequest request = new CreateReminderRequest(1L, futureDate, "Read before meeting");

        SuggestedReminder savedReminder = new SuggestedReminder(1L, 10L, futureDate,
                "Read before meeting", UrgencyLevel.MEDIUM);
        savedReminder.setStatus(ReminderStatus.CONFIRMED);
        setId(savedReminder, 100L);

        ArgumentCaptor<SuggestedReminder> captor = ArgumentCaptor.forClass(SuggestedReminder.class);
        when(reminderRepository.save(captor.capture())).thenReturn(savedReminder);

        ReminderResponse response = service.createManualReminder(10L, request);

        // AC-021: reminder created and returned
        assertThat(response).isNotNull();
        assertThat(captor.getValue().getStatus()).isEqualTo(ReminderStatus.CONFIRMED);
        assertThat(captor.getValue().getLabel()).isEqualTo("Read before meeting");
        assertThat(captor.getValue().getDetectedDate()).isEqualTo(futureDate);
        assertThat(captor.getValue().getUserId()).isEqualTo(10L);
        assertThat(captor.getValue().getItemId()).isEqualTo(1L);
    }

    @Test
    void createManualReminder_whenLabelAbsent_defaultsToItemTitle() {
        Item item = makeItem(1L, 10L, "Interesting Article");
        when(itemRepository.findByIdAndUserId(1L, 10L)).thenReturn(Optional.of(item));

        LocalDate futureDate = LocalDate.now().plusDays(3);
        CreateReminderRequest request = new CreateReminderRequest(1L, futureDate, null);

        ArgumentCaptor<SuggestedReminder> captor = ArgumentCaptor.forClass(SuggestedReminder.class);
        when(reminderRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        service.createManualReminder(10L, request);

        // AC-021: label defaults to "Reminder: {item title}" when absent
        assertThat(captor.getValue().getLabel()).isEqualTo("Reminder: Interesting Article");
    }

    @Test
    void createManualReminder_whenItemNotOwned_throwsReminderItemNotFoundException() {
        when(itemRepository.findByIdAndUserId(99L, 10L)).thenReturn(Optional.empty());

        CreateReminderRequest request = new CreateReminderRequest(
                99L, LocalDate.now().plusDays(5), "label");

        // AC-021: item must be owned by the user
        assertThatThrownBy(() -> service.createManualReminder(10L, request))
                .isInstanceOf(ReminderItemNotFoundException.class)
                .hasMessageContaining("99");

        verify(reminderRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // AC-023: updateReminder — dismiss
    // -------------------------------------------------------------------------

    @Test
    void updateReminder_whenDismissedTrue_setsStatusToDismissed() {
        SuggestedReminder reminder = makeReminder(
                50L, 1L, 10L, LocalDate.now().plusDays(5), ReminderStatus.CONFIRMED);
        when(reminderRepository.findByIdAndUserId(50L, 10L)).thenReturn(Optional.of(reminder));
        when(reminderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateReminderRequest request = new UpdateReminderRequest(true, null, null);
        ReminderResponse response = service.updateReminder(10L, 50L, request);

        // AC-023: dismiss sets DISMISSED status
        assertThat(response.status()).isEqualTo("DISMISSED");
    }

    @Test
    void updateReminder_whenDueDateProvided_updatesDueDate() {
        SuggestedReminder reminder = makeReminder(
                50L, 1L, 10L, LocalDate.now().plusDays(5), ReminderStatus.CONFIRMED);
        when(reminderRepository.findByIdAndUserId(50L, 10L)).thenReturn(Optional.of(reminder));
        when(reminderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LocalDate newDate = LocalDate.now().plusDays(14);
        UpdateReminderRequest request = new UpdateReminderRequest(null, newDate, null);
        ReminderResponse response = service.updateReminder(10L, 50L, request);

        // AC-023: due date updated
        assertThat(response.dueDate()).isEqualTo(newDate);
        assertThat(response.status()).isEqualTo("CONFIRMED");
    }

    @Test
    void updateReminder_whenLabelProvided_updatesLabel() {
        SuggestedReminder reminder = makeReminder(
                50L, 1L, 10L, LocalDate.now().plusDays(5), ReminderStatus.CONFIRMED);
        when(reminderRepository.findByIdAndUserId(50L, 10L)).thenReturn(Optional.of(reminder));
        when(reminderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateReminderRequest request = new UpdateReminderRequest(null, null, "New label");
        ReminderResponse response = service.updateReminder(10L, 50L, request);

        // AC-023: label updated
        assertThat(response.label()).isEqualTo("New label");
    }

    @Test
    void updateReminder_whenPendingConfirmation_confirmOnUpdate() {
        SuggestedReminder reminder = makeReminder(
                50L, 1L, 10L, LocalDate.now().plusDays(5), ReminderStatus.PENDING_CONFIRMATION);
        when(reminderRepository.findByIdAndUserId(50L, 10L)).thenReturn(Optional.of(reminder));
        when(reminderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateReminderRequest request = new UpdateReminderRequest(null, null, null);
        ReminderResponse response = service.updateReminder(10L, 50L, request);

        // AC-023: PENDING_CONFIRMATION → CONFIRMED on non-dismiss update
        assertThat(response.status()).isEqualTo("CONFIRMED");
    }

    @Test
    void updateReminder_whenReminderNotOwned_throwsReminderNotFoundException() {
        when(reminderRepository.findByIdAndUserId(99L, 10L)).thenReturn(Optional.empty());

        UpdateReminderRequest request = new UpdateReminderRequest(true, null, null);

        // AC-023: ownership check
        assertThatThrownBy(() -> service.updateReminder(10L, 99L, request))
                .isInstanceOf(ReminderNotFoundException.class)
                .hasMessageContaining("99");
    }

    // -------------------------------------------------------------------------
    // AC-024: dueWithin24Hours badge indicator
    // -------------------------------------------------------------------------

    @Test
    void listReminders_reminderDueToday_hasDueWithin24HoursTrue() {
        LocalDate today = LocalDate.now();
        SuggestedReminder reminder = makeReminder(
                1L, 10L, 20L, today, ReminderStatus.CONFIRMED);
        when(reminderRepository.findByUserIdAndStatusNotOrderByDetectedDateAsc(
                20L, ReminderStatus.DISMISSED)).thenReturn(List.of(reminder));

        List<ReminderResponse> responses = service.listReminders(20L);

        // AC-024: badge indicator when due today
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).dueWithin24Hours()).isTrue();
    }

    @Test
    void listReminders_reminderDueFarFuture_hasDueWithin24HoursFalse() {
        LocalDate farFuture = LocalDate.now().plusDays(30);
        SuggestedReminder reminder = makeReminder(
                1L, 10L, 20L, farFuture, ReminderStatus.CONFIRMED);
        when(reminderRepository.findByUserIdAndStatusNotOrderByDetectedDateAsc(
                20L, ReminderStatus.DISMISSED)).thenReturn(List.of(reminder));

        List<ReminderResponse> responses = service.listReminders(20L);

        // AC-024: no badge when due far in the future
        assertThat(responses.get(0).dueWithin24Hours()).isFalse();
    }

    @Test
    void listReminders_reminderDueTomorrow_hasDueWithin24HoursTrue() {
        // Tomorrow is still within 24 hours window
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        SuggestedReminder reminder = makeReminder(
                1L, 10L, 20L, tomorrow, ReminderStatus.CONFIRMED);
        when(reminderRepository.findByUserIdAndStatusNotOrderByDetectedDateAsc(
                20L, ReminderStatus.DISMISSED)).thenReturn(List.of(reminder));

        List<ReminderResponse> responses = service.listReminders(20L);

        // AC-024: badge shown when due tomorrow (within 24-hour window)
        assertThat(responses.get(0).dueWithin24Hours()).isTrue();
    }

    @Test
    void listReminders_reminderDueInPast_hasDueWithin24HoursFalse() {
        // Past reminders should not show the badge (overdue but not "due within 24h")
        LocalDate yesterday = LocalDate.now().minusDays(1);
        SuggestedReminder reminder = makeReminder(
                1L, 10L, 20L, yesterday, ReminderStatus.CONFIRMED);
        when(reminderRepository.findByUserIdAndStatusNotOrderByDetectedDateAsc(
                20L, ReminderStatus.DISMISSED)).thenReturn(List.of(reminder));

        List<ReminderResponse> responses = service.listReminders(20L);

        // Past due reminders are not "within 24 hours from now"
        assertThat(responses.get(0).dueWithin24Hours()).isFalse();
    }

    // -------------------------------------------------------------------------
    // AC-060: registerPushSubscription
    // -------------------------------------------------------------------------

    @Test
    void registerPushSubscription_newEndpoint_savesRecord() {
        when(pushSubscriptionRepository.findByEndpoint("https://push.example.com/sub/abc"))
                .thenReturn(Optional.empty());

        PushSubscription saved = new PushSubscription(
                10L, "https://push.example.com/sub/abc", "authKey123", "p256dhKey456");
        setId(saved, 1L);
        when(pushSubscriptionRepository.save(any())).thenReturn(saved);

        RegisterPushSubscriptionRequest request = new RegisterPushSubscriptionRequest(
                "https://push.example.com/sub/abc", "authKey123", "p256dhKey456");

        PushSubscriptionResponse response = service.registerPushSubscription(10L, request);

        // AC-060: subscription stored and returned
        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo(10L);
        assertThat(response.endpoint()).isEqualTo("https://push.example.com/sub/abc");
    }

    @Test
    void registerPushSubscription_duplicateEndpoint_updatesExistingRecord() {
        PushSubscription existing = new PushSubscription(
                10L, "https://push.example.com/sub/abc", "oldAuth", "oldP256dh");
        setId(existing, 5L);
        when(pushSubscriptionRepository.findByEndpoint("https://push.example.com/sub/abc"))
                .thenReturn(Optional.of(existing));
        when(pushSubscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RegisterPushSubscriptionRequest request = new RegisterPushSubscriptionRequest(
                "https://push.example.com/sub/abc", "newAuth", "newP256dh");

        PushSubscriptionResponse response = service.registerPushSubscription(10L, request);

        // Upsert: existing record updated, not duplicated
        assertThat(response.id()).isEqualTo(5L);
        verify(pushSubscriptionRepository, never()).delete(any());
    }
}
