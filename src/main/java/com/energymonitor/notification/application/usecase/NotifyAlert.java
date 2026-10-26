package com.energymonitor.notification.application.usecase;

import com.energymonitor.notification.application.port.out.HomeDirectoryPort;
import com.energymonitor.notification.application.port.out.HomeDirectoryPort.Recipient;
import com.energymonitor.notification.application.port.out.NotificationIdentifierPort;
import com.energymonitor.notification.application.port.out.NotificationPreferencePort;
import com.energymonitor.notification.application.port.out.NotificationRecordPort;
import com.energymonitor.notification.application.port.out.PushSenderPort;
import com.energymonitor.notification.application.port.out.PushSubscriptionPort;
import com.energymonitor.notification.application.port.out.PushDevicePort;
import com.energymonitor.notification.application.port.out.RelayPushSenderPort;
import com.energymonitor.notification.domain.model.NotificationChannel;
import com.energymonitor.notification.domain.model.NotificationPreference;
import com.energymonitor.notification.domain.model.NotificationSourceType;
import com.energymonitor.notification.domain.model.PushDevice;
import com.energymonitor.notification.domain.model.PushSubscription;
import com.energymonitor.notification.infrastructure.mail.EmailSender;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Delivers an alert, or a recommendation, to every member of its home, on the channels each one
 * enabled.
 *
 * <ul>
 *   <li><strong>Mail</strong> to the account address when {@code emailEnabled}.</li>
 *   <li><strong>Push</strong> when {@code pushEnabled}, to every browser the member subscribed and
 *       every app installation registered, each through the transport it can actually be reached
 *       with. One the push service reports as gone is dropped, so the list stops growing.</li>
 * </ul>
 *
 * <p>Every attempt is a row of the delivery log ({@code notification}), one per recipient and
 * channel. A failure on one recipient never stops the others.
 */
@Component
public class NotifyAlert {

    private static final Logger log = LoggerFactory.getLogger(NotifyAlert.class);

    private final HomeDirectoryPort directory;
    private final NotificationPreferencePort preferences;
    private final PushSubscriptionPort subscriptions;
    private final PushDevicePort devices;
    private final PushSenderPort push;
    private final RelayPushSenderPort relayPush;
    private final EmailSender mailer;
    private final NotificationRecordPort records;
    private final NotificationIdentifierPort identifiers;
    private final ObjectMapper objectMapper;
    private final String webUrl;

    public NotifyAlert(HomeDirectoryPort directory, NotificationPreferencePort preferences,
                       PushSubscriptionPort subscriptions, PushDevicePort devices, PushSenderPort push,
                       RelayPushSenderPort relayPush, EmailSender mailer,
                       NotificationRecordPort records, NotificationIdentifierPort identifiers,
                       ObjectMapper objectMapper,
                       @Value("${notification.web-url:http://localhost:5173}") String webUrl) {
        this.directory = directory;
        this.preferences = preferences;
        this.subscriptions = subscriptions;
        this.devices = devices;
        this.push = push;
        this.relayPush = relayPush;
        this.mailer = mailer;
        this.records = records;
        this.identifiers = identifiers;
        this.objectMapper = objectMapper;
        this.webUrl = webUrl.endsWith("/") ? webUrl.substring(0, webUrl.length() - 1) : webUrl;
    }

    public void notify(String idAlert, String homeId, String deviceId, String messageKey) {
        notify(NotificationSourceType.ALERT, idAlert, homeId, deviceId, messageKey);
    }

    /**
     * @param source   what is being delivered: an alert or a recommendation
     * @param idAlert  identifier of that alert or recommendation
     */
    public void notify(NotificationSourceType source, String idAlert, String homeId, String deviceId,
                       String messageKey) {
        List<String> members = directory.memberIds(homeId);
        if (members.isEmpty()) {
            return;
        }
        String homeName = directory.homeName(homeId).orElse(null);
        AlertMessages.Message message = AlertMessages.render(messageKey, homeName,
                directory.deviceName(deviceId).orElse(null));
        Map<String, Recipient> recipients = directory.recipients(members);

        for (String userId : members) {
            NotificationPreference preference =
                    preferences.find(userId).orElseGet(() -> NotificationPreference.defaults(userId));
            Recipient recipient = recipients.get(userId);
            try {
                if (preference.emailEnabled() && recipient != null && recipient.email() != null) {
                    sendMail(source, idAlert, homeId, messageKey, recipient, message);
                }
                // Either transport counts: an installation-only deployment has no VAPID key, and a
                // browser-only one has no relay credential. Requiring both would disable push for
                // whichever setup is actually deployed.
                if (preference.pushEnabled() && (push.isConfigured() || relayPush.isConfigured())) {
                    sendPush(source, idAlert, homeId, userId, messageKey, message);
                }
            } catch (RuntimeException e) {
                log.warn("{} {} could not be delivered to {}: {}", source, idAlert, userId, e.getMessage());
            }
        }
    }

    private void sendMail(NotificationSourceType source, String idAlert, String homeId, String messageKey,
                          Recipient recipient, AlertMessages.Message message) {
        String link = webUrl + "/notifications";
        String greeting = recipient.name() == null ? "Hola," : "Hola " + recipient.name() + ",";
        String text = """
                %s

                %s

                Revisa tus notificaciones: %s

                Puedes desactivar estos correos en Configuración > Notificaciones.
                """.formatted(greeting, message.body(), link);
        String html = """
                <div style="font-family:Arial,sans-serif;max-width:480px;margin:auto;padding:24px">
                  <p>%s</p>
                  <h2 style="margin:16px 0 8px">%s</h2>
                  <p>%s</p>
                  <p style="margin:24px 0"><a href="%s" style="background:#2563EB;color:#fff;padding:10px 18px;border-radius:6px;text-decoration:none">Ver notificaciones</a></p>
                  <p style="color:#888;font-size:13px">Puedes desactivar estos correos en Configuración &gt; Notificaciones.</p>
                </div>
                """.formatted(EmailSender.escapeHtml(greeting), EmailSender.escapeHtml(message.title()),
                EmailSender.escapeHtml(message.body()), EmailSender.escapeHtml(link));
        mailer.send(new EmailSender.Delivery(identifiers.generate(), recipient.userId(), homeId,
                source, idAlert, messageKey, recipient.email(),
                "Energy Monitor: " + message.title(), text, html));
    }

    private void sendPush(NotificationSourceType source, String idAlert, String homeId, String userId,
                          String messageKey, AlertMessages.Message message) {
        // A transport that is not configured is not attempted at all: asking it would only produce a
        // row of the log saying so, once per member, on every alert.
        List<PushSubscription> browsers =
                push.isConfigured() ? subscriptions.findByUser(userId) : List.of();
        List<PushDevice> installations =
                relayPush.isConfigured() ? devices.findByUser(userId) : List.of();
        if (browsers.isEmpty() && installations.isEmpty()) {
            return;
        }
        String payload = objectMapper.writeValueAsString(Map.of(
                "title", message.title(),
                "body", message.body(),
                "url", "/notifications",
                "tag", idAlert));
        for (PushSubscription browser : browsers) {
            String id = identifiers.generate();
            records.openForSending(id, userId, homeId, source, idAlert, messageKey,
                    NotificationChannel.PUSH);
            PushSenderPort.Result result = push.send(browser, payload);
            switch (result.outcome()) {
                case DELIVERED -> records.recordSent(id, LocalDateTime.now());
                case GONE -> {
                    records.recordFailure(id, "subscription gone: " + result.detail());
                    subscriptions.deleteByEndpoint(browser.endpoint());
                }
                case FAILED -> records.recordFailure(id, result.detail());
            }
        }

        for (PushDevice installation : installations) {
            String id = identifiers.generate();
            records.openForSending(id, userId, homeId, source, idAlert, messageKey,
                    NotificationChannel.PUSH);
            RelayPushSenderPort.Result result =
                    relayPush.send(installation.token(), message.title(), message.body(), "/notifications");
            switch (result.outcome()) {
                case DELIVERED -> records.recordSent(id, LocalDateTime.now());
                case GONE -> {
                    // The relay keeps tokens of uninstalled apps for a while; keeping ours would
                    // mean retrying an installation that no longer exists.
                    records.recordFailure(id, "device token gone: " + result.detail());
                    devices.deleteByToken(installation.token());
                }
                case FAILED -> records.recordFailure(id, result.detail());
            }
        }
    }
}
