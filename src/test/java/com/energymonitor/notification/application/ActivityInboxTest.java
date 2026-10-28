package com.energymonitor.notification.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.energymonitor.notification.application.port.out.ActivityNotificationPort;
import com.energymonitor.notification.application.usecase.ActivityInbox;
import com.energymonitor.notification.domain.model.Notification;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Activity inbox")
class ActivityInboxTest {

    private static final Instant NOW = Instant.parse("2026-10-27T15:00:00Z");
    private static final LocalDateTime NOW_LOCAL = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);

    private final ActivityNotificationPort notifications = mock(ActivityNotificationPort.class);
    private final ActivityInbox inbox =
            new ActivityInbox(notifications, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("an empty home filter means every home")
    void blankHomeIsEveryHome() {
        inbox.list("use0000001", " ");
        inbox.markAllRead("use0000001", "");

        verify(notifications).listForUser("use0000001", null, ActivityInbox.LIST_LIMIT);
        verify(notifications).markAllRead("use0000001", null, NOW_LOCAL);
    }

    @Test
    @DisplayName("reads and deletes only the caller's own notification")
    void scopedToTheCaller() {
        given(notifications.findForUser("use0000001", "not0000001"))
                .willReturn(Optional.of(mock(Notification.class)));
        given(notifications.findForUser("use0000002", "not0000001")).willReturn(Optional.empty());

        inbox.markRead("use0000001", "not0000001");
        inbox.delete("use0000001", "not0000001");
        verify(notifications).markRead("not0000001", NOW_LOCAL);
        verify(notifications).delete("not0000001", NOW_LOCAL);

        assertThatThrownBy(() -> inbox.markRead("use0000002", "not0000001"))
                .isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> inbox.delete("use0000002", "not0000001"))
                .isInstanceOf(NoSuchElementException.class);
        verify(notifications, times(1)).markRead(any(), any());
        verify(notifications, times(1)).delete(any(), any());
    }
}
