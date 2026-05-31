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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ReminderScheduler (MOD-005).
 *
 * Coverage:
 * - AC-022: dispatches push notification when reminder is due today
 * - AC-022: sends item title and reminder label in the notification
 * - AC-022: continues dispatching other reminders if one fails
 * - AC-022: no dispatch when no reminders are due
 */
@ExtendWith(MockitoExtension.class)
class ReminderSchedulerTest {

    @Mock
    private SuggestedReminderRepository reminderRepository;

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private PushNotificationService pushNotificationService;

    private ReminderScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ReminderScheduler(reminderRepository, itemRepository, pushNotificationService);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private SuggestedReminder makeConfirmedReminder(Long id, Long itemId, Long userId, LocalDate dueDate,
                                                    String label) {
        SuggestedReminder r = new SuggestedReminder(itemId, userId, dueDate, label, UrgencyLevel.HIGH);
        r.setStatus(ReminderStatus.CONFIRMED);
        setId(r, id);
        return r;
    }

    private Item makeItem(Long id, Long userId, String title) {
        Item item = new Item(userId, ItemType.LINK, "https://example.com/" + id, title, null);
        setId(item, id);
        return item;
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
    // AC-022: dispatchDueReminders
    // -------------------------------------------------------------------------

    @Test
    void dispatchDueReminders_whenNoDueReminders_doesNotSendAnyNotifications() {
        LocalDate today = LocalDate.now();
        when(reminderRepository.findConfirmedRemindersDueOn(today)).thenReturn(List.of());

        scheduler.dispatchDueReminders();

        // AC-022: no push notifications sent when no reminders are due
        verify(pushNotificationService, never())
                .sendReminderNotification(anyLong(), anyString(), anyString());
    }

    @Test
    void dispatchDueReminders_whenRemindersAreDue_sendsNotificationForEachReminder() {
        LocalDate today = LocalDate.now();
        Item item1 = makeItem(1L, 10L, "Article About Deadlines");
        Item item2 = makeItem(2L, 11L, "Conference Registration");

        SuggestedReminder reminder1 = makeConfirmedReminder(
                100L, 1L, 10L, today, "Application deadline");
        SuggestedReminder reminder2 = makeConfirmedReminder(
                101L, 2L, 11L, today, "Early bird deadline");

        when(reminderRepository.findConfirmedRemindersDueOn(today))
                .thenReturn(List.of(reminder1, reminder2));
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item1));
        when(itemRepository.findById(2L)).thenReturn(Optional.of(item2));

        scheduler.dispatchDueReminders();

        // AC-022: one notification per due reminder
        verify(pushNotificationService, times(2))
                .sendReminderNotification(anyLong(), anyString(), anyString());
    }

    @Test
    void dispatchDueReminders_sendsItemTitleAndLabelInNotification() {
        LocalDate today = LocalDate.now();
        Item item = makeItem(1L, 10L, "Harvard Application");

        SuggestedReminder reminder = makeConfirmedReminder(
                100L, 1L, 10L, today, "Submit application by today");

        when(reminderRepository.findConfirmedRemindersDueOn(today))
                .thenReturn(List.of(reminder));
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));

        scheduler.dispatchDueReminders();

        // AC-022: item title and reminder label included in notification
        verify(pushNotificationService).sendReminderNotification(
                eq(10L),
                eq("Harvard Application"),
                eq("Submit application by today"));
    }

    @Test
    void dispatchDueReminders_whenItemNotFound_usesGenericTitleAndContinues() {
        LocalDate today = LocalDate.now();
        SuggestedReminder reminder = makeConfirmedReminder(
                100L, 999L, 10L, today, "Reminder label");

        when(reminderRepository.findConfirmedRemindersDueOn(today)).thenReturn(List.of(reminder));
        when(itemRepository.findById(999L)).thenReturn(Optional.empty());

        scheduler.dispatchDueReminders();

        // Dispatch continues with generic "Saved Item" title when item no longer exists
        verify(pushNotificationService).sendReminderNotification(
                eq(10L), eq("Saved Item"), eq("Reminder label"));
    }

    @Test
    void dispatchDueReminders_whenOneReminderFails_continuesDispatchingOthers() {
        LocalDate today = LocalDate.now();
        Item item1 = makeItem(1L, 10L, "Item 1");
        Item item2 = makeItem(2L, 11L, "Item 2");

        SuggestedReminder reminder1 = makeConfirmedReminder(100L, 1L, 10L, today, "Label 1");
        SuggestedReminder reminder2 = makeConfirmedReminder(101L, 2L, 11L, today, "Label 2");

        when(reminderRepository.findConfirmedRemindersDueOn(today))
                .thenReturn(List.of(reminder1, reminder2));
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item1));
        when(itemRepository.findById(2L)).thenReturn(Optional.of(item2));

        // First reminder dispatch throws — should not block the second
        org.mockito.Mockito.doThrow(new RuntimeException("Push service unavailable"))
                .when(pushNotificationService).sendReminderNotification(
                        eq(10L), anyString(), anyString());

        scheduler.dispatchDueReminders();

        // AC-022: second reminder still dispatched despite first failure
        verify(pushNotificationService).sendReminderNotification(
                eq(11L), eq("Item 2"), eq("Label 2"));
    }
}
