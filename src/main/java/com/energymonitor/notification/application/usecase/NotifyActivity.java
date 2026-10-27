package com.energymonitor.notification.application.usecase;

import com.energymonitor.notification.application.port.in.Activity;
import com.energymonitor.notification.application.port.out.ActivityNotificationPort;
import com.energymonitor.notification.application.port.out.HomeDirectoryPort;
import com.energymonitor.notification.application.port.out.HomeDirectoryPort.Recipient;
import com.energymonitor.notification.application.port.out.NotificationIdentifierPort;
import com.energymonitor.notification.application.port.out.NotificationPreferencePort;
import com.energymonitor.notification.domain.model.Notification;
import com.energymonitor.notification.domain.model.NotificationPreference;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Tells the people an activity concerns, on every channel they chose.
 *
 * <ul>
 *   <li><strong>In the apps</strong>, always: one in-app notification per recipient, with the
 *       names its text is built from, listed with the alerts and recommendations of the home.</li>
 *   <li><strong>Mail</strong> to the account address when the recipient has {@code emailEnabled},
 *       the same switch as the alerts.</li>
 *   <li><strong>Push</strong> to the recipient's browsers and phones when they have
 *       {@code pushEnabled}.</li>
 * </ul>
 *
 * <p>A failure on one recipient never stops the others. While the security service does not
 * answer, nobody's name or address is known: the in-app notifications are still written, without
 * the names, push still goes out, and the mail is skipped.
 */
@Component
public class NotifyActivity {

    private static final Logger log = LoggerFactory.getLogger(NotifyActivity.class);

    private final HomeDirectoryPort directory;
    private final NotificationPreferencePort preferences;
    private final ActivityNotificationPort inApp;
    private final MemberChannels channels;
    private final NotificationIdentifierPort identifiers;
    private final Clock clock;

    public NotifyActivity(HomeDirectoryPort directory, NotificationPreferencePort preferences,
                          ActivityNotificationPort inApp, MemberChannels channels,
                          NotificationIdentifierPort identifiers, Clock clock) {
        this.directory = directory;
        this.preferences = preferences;
        this.inApp = inApp;
        this.channels = channels;
        this.identifiers = identifiers;
        this.clock = clock;
    }

    /**
     * @param activity what happened and who is told
     */
    public void notify(Activity activity) {
        if (activity.deliveries().isEmpty()) {
            return;
        }
        Map<String, Recipient> people = directory.recipients(peopleOf(activity));
        Map<String, String> params = params(activity, people);

        for (Activity.Delivery delivery : activity.deliveries()) {
            try {
                deliver(activity, delivery, params, people.get(delivery.userId()));
            } catch (RuntimeException e) {
                log.warn("{} {} could not be delivered to {}: {}", activity.source(),
                        activity.eventId(), delivery.userId(), e.getMessage());
            }
        }
    }

    private void deliver(Activity activity, Activity.Delivery delivery, Map<String, String> params,
                         Recipient recipient) {
        Notification notification = Notification.pendingInApp(identifiers.generate(),
                delivery.userId(), activity.homeId(), activity.source(), activity.eventId(),
                delivery.messageKey(), params);
        notification.markAsSent(LocalDateTime.now(clock));
        inApp.save(notification);

        NotificationPreference preference = preferences.find(delivery.userId())
                .orElseGet(() -> NotificationPreference.defaults(delivery.userId()));
        AlertMessages.Message message = ActivityMessages.render(delivery.messageKey(), params);
        if (preference.emailEnabled() && recipient != null && recipient.email() != null) {
            channels.mail(activity.source(), activity.eventId(), activity.homeId(),
                    delivery.messageKey(), recipient, message.title(), message.body());
        }
        if (preference.pushEnabled() && channels.pushAvailable()) {
            channels.push(activity.source(), activity.eventId(), activity.homeId(),
                    delivery.userId(), delivery.messageKey(), message.title(), message.body(),
                    activity.eventId());
        }
    }

    /** Every account whose name or address the messages need, asked for in one request. */
    private static List<String> peopleOf(Activity activity) {
        Set<String> people = new LinkedHashSet<>();
        activity.deliveries().forEach(delivery -> people.add(delivery.userId()));
        Optional.ofNullable(activity.actorId()).ifPresent(people::add);
        Optional.ofNullable(activity.subjectId()).ifPresent(people::add);
        return List.copyOf(people);
    }

    private Map<String, String> params(Activity activity, Map<String, Recipient> people) {
        Map<String, String> params = new HashMap<>();
        String homeName = activity.homeName() != null || activity.homeId() == null
                ? activity.homeName()
                : directory.homeName(activity.homeId()).orElse(null);
        putIfPresent(params, ActivityMessages.HOME_NAME, homeName);
        putIfPresent(params, ActivityMessages.ACTOR_NAME, nameOf(people, activity.actorId()));
        putIfPresent(params, ActivityMessages.SUBJECT_NAME, nameOf(people, activity.subjectId()));
        putIfPresent(params, ActivityMessages.DEVICE_NAME, activity.deviceName());
        return params;
    }

    private static String nameOf(Map<String, Recipient> people, String userId) {
        if (userId == null) {
            return null;
        }
        Recipient person = people.get(userId);
        return person == null ? null : person.name();
    }

    private static void putIfPresent(Map<String, String> params, String key, String value) {
        if (value != null && !value.isBlank()) {
            params.put(key, value);
        }
    }
}
