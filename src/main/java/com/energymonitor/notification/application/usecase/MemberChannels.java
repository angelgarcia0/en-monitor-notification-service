package com.energymonitor.notification.application.usecase;

import com.energymonitor.notification.application.port.out.HomeDirectoryPort.Recipient;
import com.energymonitor.notification.application.port.out.NotificationIdentifierPort;
import com.energymonitor.notification.application.port.out.NotificationRecordPort;
import com.energymonitor.notification.application.port.out.PushDevicePort;
import com.energymonitor.notification.application.port.out.PushSenderPort;
import com.energymonitor.notification.application.port.out.PushSubscriptionPort;
import com.energymonitor.notification.application.port.out.RelayPushSenderPort;
import com.energymonitor.notification.domain.model.NotificationChannel;
import com.energymonitor.notification.domain.model.NotificationSourceType;
import com.energymonitor.notification.domain.model.PushDevice;
import com.energymonitor.notification.domain.model.PushSubscription;
import com.energymonitor.notification.infrastructure.mail.EmailSender;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * The mail and push a member is reached on, shared by the alerts, the recommendations and the
 * activity notifications.
 *
 * <ul>
 *   <li><strong>Mail</strong> to the account address, with a link to the notifications of the web
 *       app.</li>
 *   <li><strong>Push</strong> to every browser the member subscribed and every app installation
 *       registered, each through the transport it can actually be reached with. One the push
 *       service reports as gone is dropped, so the list stops growing.</li>
 * </ul>
 *
 * <p>Every attempt is a row of the delivery log ({@code notification}), one per recipient and
 * channel. Whether a member wants a channel at all is the caller's call.
 */
@Component
public class MemberChannels {

    private final PushSubscriptionPort subscriptions;
    private final PushDevicePort devices;
    private final PushSenderPort push;
    private final RelayPushSenderPort relayPush;
    private final EmailSender mailer;
    private final NotificationRecordPort records;
    private final NotificationIdentifierPort identifiers;
    private final ObjectMapper objectMapper;
    private final String webUrl;

    public MemberChannels(PushSubscriptionPort subscriptions, PushDevicePort devices, PushSenderPort push,
                          RelayPushSenderPort relayPush, EmailSender mailer,
                          NotificationRecordPort records, NotificationIdentifierPort identifiers,
                          ObjectMapper objectMapper,
                          @Value("${notification.web-url:http://localhost:5173}") String webUrl) {
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

    /**
     * Whether push can go out at all.
     *
     * <p>Either transport counts: an installation-only deployment has no VAPID key, and a
     * browser-only one has no relay credential. Requiring both would disable push for whichever
     * setup is actually deployed.
     */
    public boolean pushAvailable() {
        return push.isConfigured() || relayPush.isConfigured();
    }

    /**
     * Mails a message to a member.
     *
     * @param source     what the message is about
     * @param sourceId   the row it refers to
     * @param homeId     the home it concerns, optional
     * @param messageKey stable name of the wording
     * @param recipient  the member, with a name and an address
     * @param title      subject line and heading
     * @param body       one or two sentences, plain text
     */
    public void mail(NotificationSourceType source, String sourceId, String homeId, String messageKey,
                     Recipient recipient, String title, String body) {
        String link = webUrl + "/notifications";
        String greeting = recipient.name() == null ? "Hola," : "Hola " + recipient.name() + ",";
        String text = """
                %s

                %s

                Revisa tus notificaciones: %s

                Puedes desactivar estos correos en Configuración > Notificaciones.
                """.formatted(greeting, body, link);
        String html = """
                <div style="font-family:Arial,sans-serif;max-width:480px;margin:auto;padding:24px">
                  <p>%s</p>
                  <h2 style="margin:16px 0 8px">%s</h2>
                  <p>%s</p>
                  <p style="margin:24px 0"><a href="%s" style="background:#2563EB;color:#fff;padding:10px 18px;border-radius:6px;text-decoration:none">Ver notificaciones</a></p>
                  <p style="color:#888;font-size:13px">Puedes desactivar estos correos en Configuración &gt; Notificaciones.</p>
                </div>
                """.formatted(EmailSender.escapeHtml(greeting), EmailSender.escapeHtml(title),
                EmailSender.escapeHtml(body), EmailSender.escapeHtml(link));
        mailer.send(new EmailSender.Delivery(identifiers.generate(), recipient.userId(), homeId,
                source, sourceId, messageKey, recipient.email(),
                "Energy Monitor: " + title, text, html));
    }

    /**
     * Pushes a message to every browser and installation of a member.
     *
     * @param tag the same for every copy of one message, so a device shows it once
     */
    public void push(NotificationSourceType source, String sourceId, String homeId, String userId,
                     String messageKey, String title, String body, String tag) {
        // A transport that is not configured is not attempted at all: asking it would only produce a
        // row of the log saying so, once per member, on every message.
        List<PushSubscription> browsers =
                push.isConfigured() ? subscriptions.findByUser(userId) : List.of();
        List<PushDevice> installations =
                relayPush.isConfigured() ? devices.findByUser(userId) : List.of();
        if (browsers.isEmpty() && installations.isEmpty()) {
            return;
        }
        String payload = objectMapper.writeValueAsString(Map.of(
                "title", title,
                "body", body,
                "url", "/notifications",
                "tag", tag));
        for (PushSubscription browser : browsers) {
            String id = identifiers.generate();
            records.openForSending(id, userId, homeId, source, sourceId, messageKey,
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
            records.openForSending(id, userId, homeId, source, sourceId, messageKey,
                    NotificationChannel.PUSH);
            RelayPushSenderPort.Result result =
                    relayPush.send(installation.token(), title, body, "/notifications");
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
