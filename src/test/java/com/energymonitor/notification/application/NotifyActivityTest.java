package com.energymonitor.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.energymonitor.notification.application.port.in.Activity;
import com.energymonitor.notification.application.port.in.Activity.Delivery;
import com.energymonitor.notification.application.port.out.ActivityNotificationPort;
import com.energymonitor.notification.application.port.out.HomeDirectoryPort;
import com.energymonitor.notification.application.port.out.HomeDirectoryPort.Recipient;
import com.energymonitor.notification.application.port.out.NotificationPreferencePort;
import com.energymonitor.notification.application.usecase.ActivityMessages;
import com.energymonitor.notification.application.usecase.MemberChannels;
import com.energymonitor.notification.application.usecase.NotifyActivity;
import com.energymonitor.notification.domain.model.Notification;
import com.energymonitor.notification.domain.model.NotificationChannel;
import com.energymonitor.notification.domain.model.NotificationPreference;
import com.energymonitor.notification.domain.model.NotificationSourceType;
import com.energymonitor.notification.domain.model.NotificationStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Activity notifications")
class NotifyActivityTest {

    private static final String OWNER = "use0000001";
    private static final String JOINER = "use0000002";
    private static final Instant NOW = Instant.parse("2026-10-27T15:00:00Z");

    private final HomeDirectoryPort directory = mock(HomeDirectoryPort.class);
    private final NotificationPreferencePort preferences = mock(NotificationPreferencePort.class);
    private final List<Notification> inApp = new ArrayList<>();
    private final ActivityNotificationPort inAppPort = inApp::add;
    private final MemberChannels channels = mock(MemberChannels.class);
    private int ids;
    private final NotifyActivity notifyActivity = new NotifyActivity(directory, preferences,
            inAppPort, channels, () -> String.format("not%07d", ++ids), Clock.fixed(NOW, ZoneOffset.UTC));

    /** Ana joined the home of Luis: she reads that she joined, he reads that she did. */
    private static Activity joined(String homeName) {
        return new Activity(NotificationSourceType.HOME, "msg0000001", "hom0000001", homeName,
                JOINER, null, null, List.of(
                        new Delivery(JOINER, ActivityMessages.HOME_JOINED),
                        new Delivery(OWNER, ActivityMessages.MEMBER_JOINED)));
    }

    @BeforeEach
    void bothReachable() {
        given(directory.recipients(anyList())).willReturn(Map.of(
                OWNER, new Recipient(OWNER, "Luis", "luis@example.com"),
                JOINER, new Recipient(JOINER, "Ana", "ana@example.com")));
        given(preferences.find(anyString())).willReturn(Optional.empty());
        given(channels.pushAvailable()).willReturn(true);
    }

    @Test
    @DisplayName("each recipient gets an in-app notification with its wording and the names")
    void writesOneInAppNotificationPerRecipient() {
        notifyActivity.notify(joined("Casa"));

        assertThat(inApp).extracting(Notification::userId, Notification::messageKey)
                .containsExactly(
                        tuple(JOINER, ActivityMessages.HOME_JOINED),
                        tuple(OWNER, ActivityMessages.MEMBER_JOINED));
        Notification first = inApp.getFirst();
        assertThat(first.channel()).isEqualTo(NotificationChannel.IN_APP);
        assertThat(first.sourceType()).isEqualTo(NotificationSourceType.HOME);
        assertThat(first.sourceId()).isEqualTo("msg0000001");
        assertThat(first.homeId()).isEqualTo("hom0000001");
        assertThat(first.deliveryStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(first.sentAt()).isEqualTo(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        assertThat(first.messageParams()).containsExactlyInAnyOrderEntriesOf(Map.of(
                ActivityMessages.HOME_NAME, "Casa", ActivityMessages.ACTOR_NAME, "Ana"));
    }

    @Test
    @DisplayName("mail and push follow the switches each recipient already has")
    void followsThePreferences() {
        given(preferences.find(OWNER)).willReturn(Optional.of(new NotificationPreference(OWNER, false, true)));
        given(preferences.find(JOINER)).willReturn(Optional.of(new NotificationPreference(JOINER, true, false)));

        notifyActivity.notify(joined("Casa"));

        verify(channels).mail(eq(NotificationSourceType.HOME), eq("msg0000001"), eq("hom0000001"),
                eq(ActivityMessages.HOME_JOINED), eq(new Recipient(JOINER, "Ana", "ana@example.com")),
                eq("Te uniste a un hogar"), eq("Ahora eres miembro de «Casa»."));
        verify(channels, never()).mail(any(), any(), any(), eq(ActivityMessages.MEMBER_JOINED), any(),
                any(), any());
        verify(channels).push(NotificationSourceType.HOME, "msg0000001", "hom0000001", OWNER,
                ActivityMessages.MEMBER_JOINED, "Nuevo miembro", "Ana se unió a «Casa».", "msg0000001");
        verify(channels, never()).push(any(), any(), any(), eq(JOINER), any(), any(), any(), any());
    }

    @Test
    @DisplayName("no push is attempted when no push transport is configured")
    void noPushWithoutATransport() {
        given(channels.pushAvailable()).willReturn(false);

        notifyActivity.notify(joined("Casa"));

        verify(channels, never()).push(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("without the security service: in-app without names, push sent, mail skipped")
    void securityServiceDown() {
        given(directory.recipients(anyList())).willReturn(Map.of());

        notifyActivity.notify(joined("Casa"));

        assertThat(inApp).hasSize(2);
        assertThat(inApp.getFirst().messageParams())
                .containsExactlyEntriesOf(Map.of(ActivityMessages.HOME_NAME, "Casa"));
        verify(channels, never()).mail(any(), any(), any(), any(), any(), any(), any());
        verify(channels).push(any(), any(), any(), eq(OWNER), any(), any(), any(), any());
    }

    @Test
    @DisplayName("the home service is asked for the name only when the event did not carry it")
    void asksForTheHomeNameOnlyWhenMissing() {
        given(directory.homeName("hom0000001")).willReturn(Optional.of("Finca"));

        notifyActivity.notify(joined("Casa"));
        verify(directory, never()).homeName(anyString());

        notifyActivity.notify(joined(null));
        assertThat(inApp.getLast().messageParams()).containsEntry(ActivityMessages.HOME_NAME, "Finca");
    }

    @Test
    @DisplayName("a failure for one recipient does not stop the next one")
    void oneFailureDoesNotStopTheOthers() {
        willThrow(new IllegalStateException("smtp down")).given(channels).mail(any(), any(), any(),
                eq(ActivityMessages.HOME_JOINED), any(), any(), any());

        notifyActivity.notify(joined("Casa"));

        assertThat(inApp).extracting(Notification::userId).containsExactly(JOINER, OWNER);
        verify(channels).mail(any(), any(), any(), eq(ActivityMessages.MEMBER_JOINED), any(), any(),
                any());
    }

    @Test
    @DisplayName("nobody to tell, nothing asked")
    void nobodyToTell() {
        notifyActivity.notify(new Activity(NotificationSourceType.DEVICE, "msg0000009",
                "hom0000001", null, null, null, "Nevera", List.of()));

        verifyNoInteractions(directory, channels);
        assertThat(inApp).isEmpty();
    }
}
