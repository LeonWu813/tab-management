package com.tabvault.backend.autocleanup;

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

import java.time.OffsetDateTime;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-layer tests for {@link AutoCleanupSettingsController} using standalone MockMvc.
 *
 * No Spring context, no database. The security principal (Long userId = 10L) is injected
 * manually into the SecurityContext, matching how JwtAuthenticationFilter works.
 *
 * Coverage:
 * - AC-038: GET /api/cleanup-settings returns current settings including threshold
 * - AC-038: PUT /api/cleanup-settings with valid threshold returns 200
 * - AC-038: PUT /api/cleanup-settings with invalid threshold returns 400 INVALID_STALENESS_THRESHOLD
 * - AC-039: PUT /api/cleanup-settings with autoCleanupEnabled=false returns 200 with disabled setting
 */
@ExtendWith(MockitoExtension.class)
class AutoCleanupControllerTest {

    @Mock
    private AutoCleanupService autoCleanupService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private static final Long USER_ID = 10L;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setObjectMapper(objectMapper);

        AutoCleanupSettingsController controller = new AutoCleanupSettingsController(autoCleanupService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new AutoCleanupExceptionHandler(), new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setMessageConverters(converter)
                .build();

        // Simulate authenticated user: JwtAuthenticationFilter sets Long userId as principal
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(USER_ID, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private CleanupSettingsResponse makeSettingsResponse(int threshold, boolean enabled) {
        return new CleanupSettingsResponse(USER_ID, threshold, enabled, OffsetDateTime.now());
    }

    // -------------------------------------------------------------------------
    // AC-038, AC-039: GET /api/cleanup-settings
    // -------------------------------------------------------------------------

    @Test
    void getSettings_returnsCurrentSettings() throws Exception {
        CleanupSettingsResponse response = makeSettingsResponse(30, true);
        when(autoCleanupService.getSettings(USER_ID)).thenReturn(response);

        // AC-038: returns threshold; AC-039: returns opt-out state
        mockMvc.perform(get("/api/cleanup-settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(USER_ID))
                .andExpect(jsonPath("$.stalenessThresholdDays").value(30))
                .andExpect(jsonPath("$.autoCleanupEnabled").value(true));
    }

    @Test
    void getSettings_whenOptedOut_returnsDisabledFlag() throws Exception {
        CleanupSettingsResponse response = makeSettingsResponse(30, false);
        when(autoCleanupService.getSettings(USER_ID)).thenReturn(response);

        // AC-039: opt-out flag returned correctly
        mockMvc.perform(get("/api/cleanup-settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autoCleanupEnabled").value(false));
    }

    // -------------------------------------------------------------------------
    // AC-038: PUT /api/cleanup-settings — valid threshold
    // -------------------------------------------------------------------------

    @Test
    void updateSettings_withValidThreshold_returns200() throws Exception {
        CleanupSettingsResponse response = makeSettingsResponse(14, true);
        when(autoCleanupService.updateSettings(eq(USER_ID), any())).thenReturn(response);

        String requestBody = """
                { "stalenessThresholdDays": 14 }
                """;

        // AC-038: valid threshold 14 accepted, returns updated settings
        mockMvc.perform(put("/api/cleanup-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stalenessThresholdDays").value(14));
    }

    @Test
    void updateSettings_withInvalidThreshold_returns400() throws Exception {
        when(autoCleanupService.updateSettings(eq(USER_ID), any()))
                .thenThrow(new InvalidStalenessThresholdException(15));

        String requestBody = """
                { "stalenessThresholdDays": 15 }
                """;

        // AC-038: invalid threshold returns 400 with INVALID_STALENESS_THRESHOLD code
        mockMvc.perform(put("/api/cleanup-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_STALENESS_THRESHOLD"))
                .andExpect(jsonPath("$.error.field").value("stalenessThresholdDays"));
    }

    // -------------------------------------------------------------------------
    // AC-039: PUT /api/cleanup-settings — opt-out toggle
    // -------------------------------------------------------------------------

    @Test
    void updateSettings_withAutoCleanupDisabled_returns200() throws Exception {
        CleanupSettingsResponse response = makeSettingsResponse(30, false);
        when(autoCleanupService.updateSettings(eq(USER_ID), any())).thenReturn(response);

        String requestBody = """
                { "autoCleanupEnabled": false }
                """;

        // AC-039: opt-out toggle returns updated settings with enabled=false
        mockMvc.perform(put("/api/cleanup-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autoCleanupEnabled").value(false));
    }

    @Test
    void updateSettings_withBothFieldsProvided_returns200() throws Exception {
        CleanupSettingsResponse response = makeSettingsResponse(60, false);
        when(autoCleanupService.updateSettings(eq(USER_ID), any())).thenReturn(response);

        String requestBody = """
                { "stalenessThresholdDays": 60, "autoCleanupEnabled": false }
                """;

        mockMvc.perform(put("/api/cleanup-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stalenessThresholdDays").value(60))
                .andExpect(jsonPath("$.autoCleanupEnabled").value(false));
    }
}
