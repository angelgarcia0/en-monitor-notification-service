package com.energymonitor.notification.application.port.in;

import com.energymonitor.notification.domain.model.NotificationSourceType;
import java.util.List;
import java.util.Objects;

/**
 * Something that happened to an account, a home or a device, and who is told about it, each with
 * the wording meant for them: who joined a home reads "you joined", the other members read that
 * someone joined.
 *
 * <p>The people are identifiers; their names come from the security service when the message is
 * delivered. The names of the home and the device travel here when the event that announced them
 * carried them, because a deleted home can no longer be asked for its name.
 *
 * @param source     {@code ACCOUNT}, {@code HOME} or {@code DEVICE}
 * @param eventId    the message that announced it, the same for every recipient; a message
 *                   delivered twice by the broker is recognised by it
 * @param homeId     the home it concerns; null for the account
 * @param homeName   the name of that home, null to ask the home service for it
 * @param actorId    who did it, may be null
 * @param subjectId  the member it is about, when it is not the actor, may be null
 * @param deviceName the name of the device, may be null
 * @param deliveries who is told, and with which wording
 */
public record Activity(NotificationSourceType source, String eventId, String homeId,
                       String homeName, String actorId, String subjectId, String deviceName,
                       List<Delivery> deliveries) {

    public Activity {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(eventId, "eventId");
        deliveries = List.copyOf(deliveries);
    }

    /**
     * @param userId     the account told
     * @param messageKey the wording it reads, one of the keys of {@code ActivityMessages}
     */
    public record Delivery(String userId, String messageKey) {

        public Delivery {
            Objects.requireNonNull(userId, "userId");
            Objects.requireNonNull(messageKey, "messageKey");
        }
    }
}
