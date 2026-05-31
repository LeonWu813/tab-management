package com.tabvault.backend.reminders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tabvault.backend.shared.error.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-layer tests using standalone MockMvc — no Spring context, no database.
 *
 * The security principal (Long userId = 10L) is injected manually into the SecurityContext,
 * matching how JwtAuthenticationFilter sets the principal in production.
 *
 * Coverage:
 * - AC-021: POST /api/reminders returns HTTP 201 with created reminder
 * - AC-021: POST /api/reminders with missing itemId returns HTTP 400
 * - AC-021: POST /api/reminders with past due date returns HTTP 400
 * - AC-021: POST /api/reminders for non-owned item returns HTTP 404
 * - AC-023: PATCH /api/reminders/{id} dismiss returns HTTP 200 with DISMISSED status
 * - AC-023: PATCH /api/reminders/{id} for non-existent reminder returns HTTP 404
 * - AC-024: GET /api/reminders returns list including dueWithin24Hours flag
 * - AC-060: POST /api/push-subscriptions returns HTTP 201 with subscription record
 * - AC-060: POST /api/push-subscriptions with missing endpoint returns HTTP 400
 * - AC-061: GET /api/push-subscriptions/vapid-public-key returns publicKey
 */
@ExtendWith(MockitoExtension.class)
class ReminderControllerTest {

    @Mock
    private ReminderService reminderService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private VapidConfig vapidConfig;

    private static final Long USER_ID = 10L;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Minimal VapidConfig for tests — just provides a non-blank public key.
        // VapidConfig only validates at PostConstruct; here we construct directly.
        vapidConfig = new VapidConfig("test-vapid-public-key", "test-vapid-private-key",
                "mailto:test@example.com");

        // Build a Jackson converter that serializes dates as ISO strings (not arrays)
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setObjectMapper(objectMapper);

        ReminderController controller = new ReminderController(reminderService, vapidConfig);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ReminderExceptionHandler(), new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setMessageConverters(converter)
                .build();

        // Simulate authenticated user: JwtAuthenticationFilter sets Long userId as principal
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(USER_ID, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private ReminderResponse makeReminderResponse(Long id, String status, boolean dueWithin24Hours) {
        return new ReminderResponse(
                id, 1L, USER_ID,
                LocalDate.now().plusDays(1), "Test label",
                "MEDIUM", status,
                dueWithin24Hours,
                OffsetDateTime.now(), OffsetDateTime.now()
        );
    }

    // -------------------------------------------------------------------------
    // AC-021: POST /api/reminders
    // -------------------------------------------------------------------------

    @Test
    void createReminder_withValidRequest_returns201() throws Exception {
        ReminderResponse response = makeReminderResponse(1L, "CONFIRMED", true);
        when(reminderService.createManualReminder(eq(USER_ID), any())).thenReturn(response);

        String requestBody = """
                {
                    "itemId": 1,
                    "dueDate": "%s",
                    "label": "Read before the meeting"
                }
                """.formatted(LocalDate.now().plusDays(7));

        // AC-021: manual reminder created; returns HTTP 201 with the reminder record
        mockMvc.perform(post("/api/reminders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void createReminder_withMissingItemId_returns400() throws Exception {
        String requestBody = """
                {
                    "dueDate": "%s",
                    "label": "Some label"
                }
                """.formatted(LocalDate.now().plusDays(5));

        // AC-021: itemId is required
        mockMvc.perform(post("/api/reminders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createReminder_withPastDueDate_returns400() throws Exception {
        String requestBody = """
                {
                    "itemId": 1,
                    "dueDate": "%s",
                    "label": "Label"
                }
                """.formatted(LocalDate.now().minusDays(1));

        // AC-021: dueDate must be in the future
        mockMvc.perform(post("/api/reminders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createReminder_whenItemNotFound_returns404() throws Exception {
        when(reminderService.createManualReminder(eq(USER_ID), any()))
                .thenThrow(new ReminderItemNotFoundException(999L));

        String requestBody = """
                {
                    "itemId": 999,
                    "dueDate": "%s"
                }
                """.formatted(LocalDate.now().plusDays(5));

        // AC-021: item must be owned by the user
        mockMvc.perform(post("/api/reminders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ITEM_NOT_FOUND"));
    }

    // -------------------------------------------------------------------------
    // AC-024: GET /api/reminders
    // -------------------------------------------------------------------------

    @Test
    void listReminders_returnsActiveRemindersWithBadgeIndicator() throws Exception {
        ReminderResponse dueToday = makeReminderResponse(1L, "CONFIRMED", true);
        ReminderResponse dueLater = makeReminderResponse(2L, "CONFIRMED", false);
        when(reminderService.listReminders(USER_ID)).thenReturn(List.of(dueToday, dueLater));

        // AC-024: list includes dueWithin24Hours flag for badge indicator on item card
        mockMvc.perform(get("/api/reminders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].dueWithin24Hours").value(true))
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].dueWithin24Hours").value(false));
    }

    @Test
    void listRemindersForItem_returnsRemindersForItem() throws Exception {
        ReminderResponse reminder = makeReminderResponse(1L, "CONFIRMED", false);
        when(reminderService.listRemindersForItem(USER_ID, 1L)).thenReturn(List.of(reminder));

        // AC-024: item-specific reminder list for badge indicator
        mockMvc.perform(get("/api/reminders/item/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    // -------------------------------------------------------------------------
    // AC-023: PATCH /api/reminders/{id}
    // -------------------------------------------------------------------------

    @Test
    void updateReminder_dismiss_returns200WithDismissedStatus() throws Exception {
        ReminderResponse dismissed = makeReminderResponse(1L, "DISMISSED", false);
        when(reminderService.updateReminder(eq(USER_ID), eq(1L), any())).thenReturn(dismissed);

        String requestBody = """
                { "dismissed": true }
                """;

        // AC-023: dismiss returns updated record with DISMISSED status
        mockMvc.perform(patch("/api/reminders/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISMISSED"));
    }

    @Test
    void updateReminder_updateDueDate_returns200WithUpdatedDate() throws Exception {
        LocalDate newDate = LocalDate.now().plusDays(14);
        ReminderResponse updated = new ReminderResponse(
                1L, 1L, USER_ID, newDate, "Test label", "MEDIUM", "CONFIRMED",
                false, OffsetDateTime.now(), OffsetDateTime.now()
        );
        when(reminderService.updateReminder(eq(USER_ID), eq(1L), any())).thenReturn(updated);

        String requestBody = """
                { "dueDate": "%s" }
                """.formatted(newDate);

        // AC-023: update returns record with new due date
        mockMvc.perform(patch("/api/reminders/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dueDate").value(newDate.toString()));
    }

    @Test
    void updateReminder_whenNotFound_returns404() throws Exception {
        when(reminderService.updateReminder(eq(USER_ID), eq(99L), any()))
                .thenThrow(new ReminderNotFoundException(99L));

        String requestBody = """
                { "dismissed": true }
                """;

        // AC-023: ownership check — not found returns 404
        mockMvc.perform(patch("/api/reminders/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("REMINDER_NOT_FOUND"));
    }

    // -------------------------------------------------------------------------
    // AC-060: POST /api/push-subscriptions
    // -------------------------------------------------------------------------

    @Test
    void registerPushSubscription_withValidRequest_returns201() throws Exception {
        PushSubscriptionResponse response = new PushSubscriptionResponse(
                1L, USER_ID, "https://push.example.com/sub/abc", OffsetDateTime.now());
        when(reminderService.registerPushSubscription(eq(USER_ID), any())).thenReturn(response);

        String requestBody = """
                {
                    "endpoint": "https://push.example.com/sub/abc",
                    "auth": "authKey123",
                    "p256dh": "p256dhKey456"
                }
                """;

        // AC-060: subscription record stored and returned
        mockMvc.perform(post("/api/push-subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.userId").value(USER_ID))
                .andExpect(jsonPath("$.endpoint").value("https://push.example.com/sub/abc"));
    }

    @Test
    void registerPushSubscription_withMissingEndpoint_returns400() throws Exception {
        String requestBody = """
                {
                    "auth": "authKey123",
                    "p256dh": "p256dhKey456"
                }
                """;

        // AC-060: endpoint is required
        mockMvc.perform(post("/api/push-subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    // -------------------------------------------------------------------------
    // AC-061: GET /api/push-subscriptions/vapid-public-key
    // -------------------------------------------------------------------------

    @Test
    void getVapidPublicKey_returns200WithPublicKey() throws Exception {
        // AC-061: VAPID public key returned for client to use in pushManager.subscribe()
        mockMvc.perform(get("/api/push-subscriptions/vapid-public-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicKey").value("test-vapid-public-key"));
    }
}
