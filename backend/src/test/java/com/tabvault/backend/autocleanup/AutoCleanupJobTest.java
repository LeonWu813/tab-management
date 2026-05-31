package com.tabvault.backend.autocleanup;

import com.tabvault.backend.auth.User;
import com.tabvault.backend.auth.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AutoCleanupJob}.
 *
 * Coverage:
 * - Job invokes AutoCleanupService.runDailyCleanup with all user IDs
 * - Job handles no registered users (empty list)
 * - Job wraps fatal errors as JobExecutionException
 */
@ExtendWith(MockitoExtension.class)
class AutoCleanupJobTest {

    @Mock
    private AutoCleanupService autoCleanupService;

    @Mock
    private UserRepository userRepository;

    private AutoCleanupJob job;

    @BeforeEach
    void setUp() {
        job = new AutoCleanupJob(autoCleanupService, userRepository);
    }

    private User makeUser(Long id) {
        User user = new User("user" + id + "@example.com", "password", "User " + id);
        try {
            Field idField = user.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(user, id);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set user id", e);
        }
        return user;
    }

    @Test
    void execute_withTwoUsers_callsCleanupForBothUserIds() throws JobExecutionException {
        User user1 = makeUser(1L);
        User user2 = makeUser(2L);
        when(userRepository.findAll()).thenReturn(List.of(user1, user2));

        job.execute(mock(JobExecutionContext.class));

        // Both user IDs should be passed to the cleanup service
        verify(autoCleanupService).runDailyCleanup(List.of(1L, 2L));
    }

    @Test
    void execute_withNoUsers_callsCleanupWithEmptyList() throws JobExecutionException {
        when(userRepository.findAll()).thenReturn(List.of());

        job.execute(mock(JobExecutionContext.class));

        verify(autoCleanupService).runDailyCleanup(List.of());
    }

    @Test
    void execute_whenCleanupServiceThrows_wrapsInJobExecutionException() {
        when(userRepository.findAll()).thenReturn(List.of(makeUser(1L)));
        doThrow(new RuntimeException("DB error")).when(autoCleanupService).runDailyCleanup(anyList());

        assertThatThrownBy(() -> job.execute(mock(JobExecutionContext.class)))
                .isInstanceOf(JobExecutionException.class)
                .hasMessageContaining("Daily auto-cleanup job failed");
    }
}
