package com.energymonitor.notification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

import com.energymonitor.notification.application.port.out.HomeDirectoryPort;
import com.energymonitor.notification.application.port.out.NotificationPreferencePort;
import com.energymonitor.notification.application.port.out.NotificationRecordPort;
import com.energymonitor.notification.application.port.out.PushDevicePort;
import com.energymonitor.notification.application.port.out.PushSenderPort;
import com.energymonitor.notification.application.port.out.PushSubscriptionPort;
import com.energymonitor.notification.application.port.out.RelayPushSenderPort;
import com.energymonitor.notification.application.usecase.MemberChannels;
import com.energymonitor.notification.application.usecase.NotifyAlert;
import com.energymonitor.notification.domain.model.NotificationPreference;
import com.energymonitor.notification.domain.model.NotificationSourceType;
import com.energymonitor.notification.domain.model.PushDevice;
import com.energymonitor.notification.domain.model.PushDevicePlatform;
import com.energymonitor.notification.domain.model.PushSubscription;
import com.energymonitor.notification.infrastructure.mail.EmailSender;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

class NotifyAlertTest {

    private final Map<String, NotificationPreference> prefs = new HashMap<>();
    private final List<PushSubscription> subscriptions = new ArrayList<>();
    private final List<PushDevice> devices = new ArrayList<>();
    private final List<String> pushedTo = new ArrayList<>();
    private final List<String> pushedToDevices = new ArrayList<>();
    private PushSenderPort.Outcome pushOutcome = PushSenderPort.Outcome.DELIVERED;
    private RelayPushSenderPort.Outcome relayOutcome = RelayPushSenderPort.Outcome.DELIVERED;
    private boolean webPushConfigured = true;
    private boolean relayPushConfigured = false;
    private final EmailSender mailer = mock(EmailSender.class);
    private int ids;

    private final NotifyAlert notifyAlert = new NotifyAlert(
            new HomeDirectoryPort() {
                @Override
                public List<String> memberIds(String homeId) {
                    return List.of("usr0000001", "usr0000002");
                }

                @Override
                public Map<String, Recipient> recipients(List<String> userIds) {
                    return Map.of("usr0000001", new Recipient("usr0000001", "Ana", "ana@example.com"),
                            "usr0000002", new Recipient("usr0000002", "Luis", "luis@example.com"));
                }

                @Override
                public Optional<String> homeName(String homeId) {
                    return Optional.of("Casa");
                }

                @Override
                public Optional<String> deviceName(String deviceId) {
                    return Optional.of("Nevera");
                }
            },
            new NotificationPreferencePort() {
                @Override
                public Optional<NotificationPreference> find(String userId) {
                    return Optional.ofNullable(prefs.get(userId));
                }

                @Override
                public void save(NotificationPreference preference) {
                    prefs.put(preference.userId(), preference);
                }

                @Override
                public void forget(String userId) {
                    prefs.remove(userId);
                }
            },
            new MemberChannels(
            new PushSubscriptionPort() {
                @Override
                public List<PushSubscription> findByUser(String userId) {
                    return subscriptions.stream().filter(s -> s.userId().equals(userId)).toList();
                }

                @Override
                public Optional<PushSubscription> findByEndpoint(String endpoint) {
                    return subscriptions.stream().filter(s -> s.endpoint().equals(endpoint)).findFirst();
                }

                @Override
                public void save(PushSubscription subscription) {
                    subscriptions.add(subscription);
                }

                @Override
                public void deleteByEndpoint(String endpoint) {
                    subscriptions.removeIf(s -> s.endpoint().equals(endpoint));
                }
            },
            new PushDevicePort() {
                @Override
                public List<PushDevice> findByUser(String userId) {
                    return devices.stream().filter(d -> d.userId().equals(userId)).toList();
                }

                @Override
                public Optional<PushDevice> findByToken(String token) {
                    return devices.stream().filter(d -> d.token().equals(token)).findFirst();
                }

                @Override
                public void save(PushDevice device) {
                    devices.add(device);
                }

                @Override
                public void deleteByToken(String token) {
                    devices.removeIf(d -> d.token().equals(token));
                }

                @Override
                public void deleteByUser(String userId) {
                    devices.removeIf(d -> d.userId().equals(userId));
                }
            },
            new PushSenderPort() {
                @Override
                public boolean isConfigured() {
                    return webPushConfigured;
                }

                @Override
                public String publicKey() {
                    return "key";
                }

                @Override
                public Result send(PushSubscription subscription, String jsonPayload) {
                    pushedTo.add(subscription.userId());
                    return new Result(pushOutcome, null);
                }
            },
            new RelayPushSenderPort() {
                @Override
                public boolean isConfigured() {
                    return relayPushConfigured;
                }

                @Override
                public Result send(String token, String title, String body, String path) {
                    pushedToDevices.add(token);
                    return new Result(relayOutcome, null);
                }
            },
            mailer,
            mock(NotificationRecordPort.class),
            () -> String.format("not%07d", ++ids),
            JsonMapper.builder().build(),
            "http://localhost:5173"));

    private static PushSubscription browser(String userId, String endpoint) {
        return new PushSubscription("sub" + endpoint.length(), userId, endpoint, "p".repeat(87), "a".repeat(22));
    }

    private static PushDevice installation(String userId, String token) {
        return new PushDevice("dev" + token.length(), userId, token, PushDevicePlatform.ANDROID);
    }

    @Test
    void everyMemberGetsMailByDefault() {
        notifyAlert.notify("ale0000001", "hom0000001", "dev0000001", "alert.device.linked");

        ArgumentCaptor<EmailSender.Delivery> mail = ArgumentCaptor.forClass(EmailSender.Delivery.class);
        verify(mailer, org.mockito.Mockito.times(2)).send(mail.capture());
        assertEquals(List.of("ana@example.com", "luis@example.com"),
                mail.getAllValues().stream().map(EmailSender.Delivery::recipient).sorted().toList());
        assertTrue(mail.getValue().text().contains("«Nevera»"));
    }

    @Test
    void preferencesAreRespectedPerMember() {
        prefs.put("usr0000001", new NotificationPreference("usr0000001", false, true));
        prefs.put("usr0000002", new NotificationPreference("usr0000002", false, false));
        subscriptions.add(browser("usr0000001", "https://push.example/a"));
        subscriptions.add(browser("usr0000002", "https://push.example/b"));

        notifyAlert.notify("ale0000001", "hom0000001", "dev0000001", "alert.connectivity.offline");

        verify(mailer, never()).send(any(EmailSender.Delivery.class));
        assertEquals(List.of("usr0000001"), pushedTo);
    }

    @Test
    void aSubscriptionThePushServiceForgotIsDeleted() {
        prefs.put("usr0000001", new NotificationPreference("usr0000001", false, true));
        subscriptions.add(browser("usr0000001", "https://push.example/a"));
        pushOutcome = PushSenderPort.Outcome.GONE;

        notifyAlert.notify("ale0000001", "hom0000001", "dev0000001", "alert.threshold.high");

        assertTrue(subscriptions.isEmpty());
    }

    @Test
    void installationsAreReachedWithTheRelayAlone() {
        // No VAPID key: a browser-only deployment has none, and an app-only one never will.
        // Requiring web push to be configured would silently disable push for every phone.
        webPushConfigured = false;
        relayPushConfigured = true;
        prefs.put("usr0000001", new NotificationPreference("usr0000001", false, true));
        devices.add(installation("usr0000001", "ExponentPushToken[one]"));

        notifyAlert.notify("ale0000001", "hom0000001", "dev0000001", "alert.threshold.high");

        assertEquals(List.of("ExponentPushToken[one]"), pushedToDevices);
        assertTrue(pushedTo.isEmpty());
    }

    @Test
    void nothingIsSentToInstallationsWhileNoRelayCredentialIsConfigured() {
        relayPushConfigured = false;
        prefs.put("usr0000001", new NotificationPreference("usr0000001", false, true));
        devices.add(installation("usr0000001", "ExponentPushToken[one]"));

        notifyAlert.notify("ale0000001", "hom0000001", "dev0000001", "alert.threshold.high");

        assertTrue(pushedToDevices.isEmpty());
    }

    @Test
    void aTokenTheRelayForgotIsDeleted() {
        relayPushConfigured = true;
        prefs.put("usr0000001", new NotificationPreference("usr0000001", false, true));
        devices.add(installation("usr0000001", "ExponentPushToken[one]"));
        relayOutcome = RelayPushSenderPort.Outcome.GONE;

        notifyAlert.notify("ale0000001", "hom0000001", "dev0000001", "alert.threshold.high");

        assertTrue(devices.isEmpty());
    }

    @Test
    void bothTransportsDeliverToTheirOwnList() {
        relayPushConfigured = true;
        prefs.put("usr0000001", new NotificationPreference("usr0000001", false, true));
        subscriptions.add(browser("usr0000001", "https://push.example/a"));
        devices.add(installation("usr0000001", "ExponentPushToken[one]"));

        notifyAlert.notify("ale0000001", "hom0000001", "dev0000001", "alert.threshold.high");

        assertEquals(List.of("usr0000001"), pushedTo);
        assertEquals(List.of("ExponentPushToken[one]"), pushedToDevices);
    }

    @Test
    void recommendationsAreDeliveredAsTheirOwnSource() {
        notifyAlert.notify(NotificationSourceType.RECOMMENDATION, "rec0000001", "hom0000001", "dev0000001",
                "recommendation.standby");

        ArgumentCaptor<EmailSender.Delivery> mail = ArgumentCaptor.forClass(EmailSender.Delivery.class);
        verify(mailer, org.mockito.Mockito.times(2)).send(mail.capture());
        assertEquals(NotificationSourceType.RECOMMENDATION, mail.getValue().sourceType());
        assertEquals("rec0000001", mail.getValue().sourceId());
        assertTrue(mail.getValue().subject().contains("consumo en reposo"));
        assertTrue(mail.getValue().text().contains("«Nevera»"));
    }
}
