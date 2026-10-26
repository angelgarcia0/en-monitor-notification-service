package com.energymonitor.notification.infrastructure.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.energymonitor.notification.application.port.out.NotificationRecordPort;
import com.energymonitor.notification.infrastructure.MailDeliveryProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The startup sweep that closes records a previous run left behind.
 *
 * <p>Two properties are worth pinning. It closes only rows older than the threshold, so a delivery
 * still in flight is not declared failed underneath the thread sending it; and it closes them with
 * the bulk statement rather than one call per row, which is what makes it safe against a large
 * backlog.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Abandoned delivery sweep")
class AbandonedDeliverySweeperTest {

    private static final Instant NOW = Instant.parse("2026-01-02T03:04:05Z");
    private static final Duration THRESHOLD = Duration.ofMinutes(30);

    @Mock
    private NotificationRecordPort notifications;

    private AbandonedDeliverySweeper sweeper(Clock clock) {
        MailDeliveryProperties properties = new MailDeliveryProperties(THRESHOLD,
                Duration.ofMinutes(5));
        return new AbandonedDeliverySweeper(notifications, properties, clock);
    }

    @Test
    @DisplayName("the cutoff is the threshold before now, not now itself")
    void cutoffIsTheThresholdBeforeNow() {
        when(notifications.failAbandoned(any(), anyString())).thenReturn(3);

        sweeper(Clock.fixed(NOW, ZoneOffset.UTC)).sweep();

        ArgumentCaptor<java.time.LocalDateTime> cutoff =
                ArgumentCaptor.forClass(java.time.LocalDateTime.class);
        verify(notifications).failAbandoned(cutoff.capture(), anyString());
        assertThat(cutoff.getValue())
                .isEqualTo(java.time.LocalDateTime.ofInstant(NOW.minus(THRESHOLD), ZoneOffset.UTC));
    }

    @Test
    @DisplayName("rows are closed with a reason that says why, not with an empty message")
    void rowsAreClosedWithAReason() {
        when(notifications.failAbandoned(any(), anyString())).thenReturn(0);

        sweeper(Clock.fixed(NOW, ZoneOffset.UTC)).sweep();

        ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);
        verify(notifications).failAbandoned(any(), reason.capture());
        assertThat(reason.getValue()).isEqualTo(AbandonedDeliverySweeper.ABANDONED_REASON);
    }

    @Test
    @DisplayName("nothing is announced when there was nothing to close")
    void silenceWhenThereIsNothingToClose() {
        when(notifications.failAbandoned(any(), anyString())).thenReturn(0);

        int closed = sweeper(Clock.fixed(NOW, ZoneOffset.UTC)).sweep();

        assertThat(closed).isZero();
    }

    @Test
    @DisplayName("the sweep reports how many rows it closed")
    void reportsWhatItClosed() {
        when(notifications.failAbandoned(any(), anyString())).thenReturn(7);

        assertThat(sweeper(Clock.fixed(NOW, ZoneOffset.UTC)).sweep()).isEqualTo(7);
    }
}