package com.energymonitor.notification.adapter.in.messaging;

import com.energymonitor.notification.application.port.in.Activity;
import com.energymonitor.notification.application.port.in.Activity.Delivery;
import com.energymonitor.notification.application.port.out.HomeDirectoryPort;
import com.energymonitor.notification.application.port.out.NotificationRecordPort;
import com.energymonitor.notification.application.usecase.ActivityMessages;
import com.energymonitor.notification.application.usecase.NotifyActivity;
import com.energymonitor.notification.domain.model.NotificationSourceType;
import com.energymonitor.notification.infrastructure.ActivityMessagingConfiguration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Tells the people an account, home or device event concerns, each with the wording meant for
 * them: who joined a home reads that they joined, the other members read that someone did.
 *
 * <p>The home events carry the members, read by the home service before the change, so a member
 * who left or a home that was deleted can still be told about. The device events carry only the
 * home: its members are asked of the home service, and a home that no longer exists, as when it was
 * deleted and its devices unlinked, has nobody left to tell.
 *
 * <p>On the listener thread, so a failure is retried by the broker instead of lost. The message
 * id of the event is the source of the notifications it produced: an event delivered twice finds
 * them and is dropped.
 *
 * <p>Only with {@code messaging.activity.enabled}, like its queue: see
 * {@link ActivityMessagingConfiguration}.
 */
@Component
@ConditionalOnProperty(name = "messaging.activity.enabled", havingValue = "true", matchIfMissing = true)
public class ActivityEventsConsumer {

    static final String FIRST_SIGNED_IN = "account.first-signed-in";
    static final String HOME_CREATED = "home.created";
    static final String MEMBER_JOINED = "home.member-joined";
    static final String MEMBER_LEFT = "home.member-left";
    static final String MEMBER_REMOVED = "home.member-removed";
    static final String THRESHOLDS_UPDATED = "home.thresholds-updated";
    static final String OWNERSHIP_TRANSFERRED = "home.ownership-transferred";
    static final String HOME_DELETED = "home.deleted";
    static final String DEVICE_LINKED = "device.linked";
    static final String DEVICE_UNLINKED = "device.unlinked";

    private static final Logger LOG = LoggerFactory.getLogger(ActivityEventsConsumer.class);

    private final NotifyActivity notifyActivity;
    private final NotificationRecordPort records;
    private final HomeDirectoryPort directory;
    private final ObjectMapper json;

    public ActivityEventsConsumer(NotifyActivity notifyActivity, NotificationRecordPort records,
                                  HomeDirectoryPort directory, ObjectMapper json) {
        this.notifyActivity = notifyActivity;
        this.records = records;
        this.directory = directory;
        this.json = json;
    }

    @RabbitListener(queues = ActivityMessagingConfiguration.ACTIVITY_QUEUE)
    public void on(Message message) {
        String eventId = message.getMessageProperties().getMessageId();
        String type = message.getMessageProperties().getType();
        if (eventId == null || eventId.isBlank()) {
            throw new AmqpRejectAndDontRequeueException(type + " without a message id");
        }
        NotificationSourceType source = sourceOf(type);
        if (records.anyAbout(source, eventId)) {
            LOG.info("{} {} already delivered, dropped", type, eventId);
            return;
        }
        notifyActivity.notify(activity(type, eventId, message.getBody()));
    }

    private static NotificationSourceType sourceOf(String type) {
        if (FIRST_SIGNED_IN.equals(type)) {
            return NotificationSourceType.ACCOUNT;
        }
        if (DEVICE_LINKED.equals(type) || DEVICE_UNLINKED.equals(type)) {
            return NotificationSourceType.DEVICE;
        }
        if (type != null && type.startsWith("home.")) {
            return NotificationSourceType.HOME;
        }
        throw new AmqpRejectAndDontRequeueException("unexpected message type " + type);
    }

    private Activity activity(String type, String eventId, byte[] body) {
        return switch (type) {
            case FIRST_SIGNED_IN -> {
                var event = json.readValue(body, FirstSignedInMessage.class);
                yield new Activity(NotificationSourceType.ACCOUNT, eventId, null, null, event.userId(),
                        null, null, List.of(new Delivery(event.userId(), ActivityMessages.WELCOME)));
            }
            case HOME_CREATED -> {
                var event = json.readValue(body, HomeCreatedMessage.class);
                yield home(eventId, event.homeId(), event.homeName(), event.ownerId(), null,
                        List.of(new Delivery(event.ownerId(), ActivityMessages.HOME_CREATED)));
            }
            case MEMBER_JOINED -> {
                var event = json.readValue(body, MemberMessage.class);
                yield home(eventId, event.homeId(), event.homeName(), event.userId(), null,
                        to(event.userId(), ActivityMessages.HOME_JOINED,
                                others(event.memberIds(), event.userId()), ActivityMessages.MEMBER_JOINED));
            }
            case MEMBER_LEFT -> {
                var event = json.readValue(body, MemberMessage.class);
                yield home(eventId, event.homeId(), event.homeName(), event.userId(), null,
                        everyone(others(event.memberIds(), event.userId()), ActivityMessages.MEMBER_LEFT));
            }
            case MEMBER_REMOVED -> {
                var event = json.readValue(body, MemberRemovedMessage.class);
                yield home(eventId, event.homeId(), event.homeName(), event.removedBy(), event.userId(),
                        to(event.userId(), ActivityMessages.REMOVED_FROM_HOME,
                                others(event.memberIds(), event.userId(), event.removedBy()),
                                ActivityMessages.MEMBER_REMOVED));
            }
            case THRESHOLDS_UPDATED -> {
                var event = json.readValue(body, ThresholdsUpdatedMessage.class);
                yield home(eventId, event.homeId(), event.homeName(), event.updatedBy(), null,
                        everyone(others(event.memberIds(), event.updatedBy()),
                                ActivityMessages.THRESHOLDS_UPDATED));
            }
            case OWNERSHIP_TRANSFERRED -> {
                var event = json.readValue(body, OwnershipTransferredMessage.class);
                yield home(eventId, event.homeId(), event.homeName(), event.previousOwnerId(),
                        event.newOwnerId(), ownershipDeliveries(event));
            }
            case HOME_DELETED -> {
                var event = json.readValue(body, HomeDeletedMessage.class);
                List<String> members = event.memberIds() == null ? List.of() : event.memberIds();
                List<Delivery> deliveries = new ArrayList<>();
                if (members.contains(event.deletedBy())) {
                    deliveries.add(new Delivery(event.deletedBy(), ActivityMessages.HOME_DELETED_BY_YOU));
                }
                deliveries.addAll(everyone(others(members, event.deletedBy()), ActivityMessages.HOME_DELETED));
                yield home(eventId, event.homeId(), event.homeName(), event.deletedBy(), null, deliveries);
            }
            case DEVICE_LINKED -> {
                var event = json.readValue(body, DeviceLinkedMessage.class);
                yield device(eventId, event.homeId(), event.linkedBy(), event.deviceName(),
                        ActivityMessages.DEVICE_LINKED);
            }
            case DEVICE_UNLINKED -> {
                var event = json.readValue(body, DeviceUnlinkedMessage.class);
                yield device(eventId, event.homeId(), null,
                        directory.deviceName(event.deviceId()).orElse(null),
                        ActivityMessages.DEVICE_UNLINKED);
            }
            default -> throw new AmqpRejectAndDontRequeueException("unexpected message type " + type);
        };
    }

    /**
     * The new owner is told they own it, the previous owner that they handed it over (unless their
     * account was deleted, which is how a member inherits a home), and the others who owns it now.
     */
    private static List<Delivery> ownershipDeliveries(OwnershipTransferredMessage event) {
        List<String> members = event.memberIds() == null ? List.of() : event.memberIds();
        List<Delivery> deliveries = new ArrayList<>();
        deliveries.add(new Delivery(event.newOwnerId(), ActivityMessages.OWNERSHIP_RECEIVED));
        if (members.contains(event.previousOwnerId())) {
            deliveries.add(new Delivery(event.previousOwnerId(), ActivityMessages.OWNERSHIP_GIVEN));
        }
        deliveries.addAll(everyone(others(members, event.newOwnerId(), event.previousOwnerId()),
                ActivityMessages.OWNER_CHANGED));
        return deliveries;
    }

    private static Activity home(String eventId, String homeId, String homeName, String actorId,
                                 String subjectId, List<Delivery> deliveries) {
        requireHome(homeId);
        return new Activity(NotificationSourceType.HOME, eventId, homeId, homeName, actorId,
                subjectId, null, deliveries);
    }

    private Activity device(String eventId, String homeId, String actorId, String deviceName,
                            String messageKey) {
        if (homeId == null || homeId.isBlank()) {
            // A device linked to no home concerns nobody.
            return new Activity(NotificationSourceType.DEVICE, eventId, null, null, null, null,
                    deviceName, List.of());
        }
        return new Activity(NotificationSourceType.DEVICE, eventId, homeId, null, actorId, null,
                deviceName, everyone(directory.memberIds(homeId), messageKey));
    }

    private static void requireHome(String homeId) {
        if (homeId == null || homeId.isBlank()) {
            throw new AmqpRejectAndDontRequeueException("home event without a home");
        }
    }

    /** One user with one wording, and everyone else with another. */
    private static List<Delivery> to(String userId, String messageKey, List<String> others,
                                     String othersKey) {
        List<Delivery> deliveries = new ArrayList<>();
        deliveries.add(new Delivery(userId, messageKey));
        deliveries.addAll(everyone(others, othersKey));
        return deliveries;
    }

    private static List<Delivery> everyone(List<String> userIds, String messageKey) {
        return userIds.stream().map(userId -> new Delivery(userId, messageKey)).toList();
    }

    /** The members, without the given users and without repeats. */
    private static List<String> others(List<String> memberIds, String... excluded) {
        List<String> left = List.of(excluded).stream().filter(Objects::nonNull).toList();
        return (memberIds == null ? List.<String>of() : memberIds).stream()
                .filter(Objects::nonNull)
                .filter(member -> !left.contains(member))
                .distinct()
                .toList();
    }

    /** Body of {@code account.first-signed-in}. */
    record FirstSignedInMessage(String userId, Instant occurredAt) {
    }

    /** Body of {@code home.created}. */
    record HomeCreatedMessage(String homeId, String homeName, String ownerId, Instant occurredAt) {
    }

    /** Body of {@code home.member-joined} and {@code home.member-left}. */
    record MemberMessage(String homeId, String homeName, String userId, List<String> memberIds,
                         Instant occurredAt) {
    }

    /** Body of {@code home.member-removed}. */
    record MemberRemovedMessage(String homeId, String homeName, String userId, String removedBy,
                                List<String> memberIds, Instant occurredAt) {
    }

    /** Body of {@code home.thresholds-updated}. */
    record ThresholdsUpdatedMessage(String homeId, String homeName, String updatedBy,
                                    List<String> memberIds, Instant occurredAt) {
    }

    /** Body of {@code home.ownership-transferred}. */
    record OwnershipTransferredMessage(String homeId, String homeName, String previousOwnerId,
                                       String newOwnerId, List<String> memberIds,
                                       Instant occurredAt) {
    }

    /** Body of {@code home.deleted}; a message from before the members were added has none. */
    record HomeDeletedMessage(String homeId, String deletedBy, Instant deletedAt, String homeName,
                              List<String> memberIds) {
    }

    /** Body of {@code device.linked}. */
    record DeviceLinkedMessage(String deviceId, String homeId, String deviceName, String linkedBy,
                               Instant linkedAt) {
    }

    /** Body of {@code device.unlinked}. */
    record DeviceUnlinkedMessage(String deviceId, String homeId, Instant unlinkedAt) {
    }
}
