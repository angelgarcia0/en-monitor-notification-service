package com.energymonitor.notification.application.usecase;

import com.energymonitor.notification.application.port.out.HomeDirectoryPort;
import com.energymonitor.notification.application.port.out.HomeDirectoryPort.Recipient;
import com.energymonitor.notification.application.port.out.NotificationPreferencePort;
import com.energymonitor.notification.domain.model.NotificationPreference;
import com.energymonitor.notification.domain.model.NotificationSourceType;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Delivers an alert, or a recommendation, to every member of its home, on the channels each one
 * enabled.
 *
 * <ul>
 *   <li><strong>Mail</strong> to the account address when {@code emailEnabled}.</li>
 *   <li><strong>Push</strong> when {@code pushEnabled}, to every browser and app installation of
 *       the member ({@link MemberChannels}).</li>
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
    private final MemberChannels channels;

    public NotifyAlert(HomeDirectoryPort directory, NotificationPreferencePort preferences,
                       MemberChannels channels) {
        this.directory = directory;
        this.preferences = preferences;
        this.channels = channels;
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
                    channels.mail(source, idAlert, homeId, messageKey, recipient, message.title(),
                            message.body());
                }
                if (preference.pushEnabled() && channels.pushAvailable()) {
                    channels.push(source, idAlert, homeId, userId, messageKey, message.title(),
                            message.body(), idAlert);
                }
            } catch (RuntimeException e) {
                log.warn("{} {} could not be delivered to {}: {}", source, idAlert, userId, e.getMessage());
            }
        }
    }
}
