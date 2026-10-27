package com.energymonitor.notification.adapter.in.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.energymonitor.notification.application.port.in.Activity;
import com.energymonitor.notification.application.port.in.Activity.Delivery;
import com.energymonitor.notification.application.port.out.HomeDirectoryPort;
import com.energymonitor.notification.application.port.out.NotificationRecordPort;
import com.energymonitor.notification.application.usecase.ActivityMessages;
import com.energymonitor.notification.application.usecase.NotifyActivity;
import com.energymonitor.notification.domain.model.NotificationSourceType;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("Activity events consumer")
class ActivityEventsConsumerTest {

    private static final String OWNER = "use0000001";
    private static final String ANA = "use0000002";
    private static final String LUIS = "use0000003";

    private final NotifyActivity notifyActivity = mock(NotifyActivity.class);
    private final NotificationRecordPort records = mock(NotificationRecordPort.class);
    private final HomeDirectoryPort directory = mock(HomeDirectoryPort.class);
    private final ActivityEventsConsumer consumer = new ActivityEventsConsumer(notifyActivity, records,
            directory, JsonMapper.builder().build());

    private Activity delivered(String type, String body) {
        consumer.on(message("msg0000001", type, body));
        ArgumentCaptor<Activity> activity = ArgumentCaptor.forClass(Activity.class);
        verify(notifyActivity).notify(activity.capture());
        return activity.getValue();
    }

    @Test
    @DisplayName("the first sign-in welcomes the account")
    void welcome() {
        Activity activity = delivered("account.first-signed-in",
                "{\"userId\":\"use0000002\",\"occurredAt\":\"2026-10-27T09:00:00Z\"}");

        assertThat(activity.source()).isEqualTo(NotificationSourceType.ACCOUNT);
        assertThat(activity.eventId()).isEqualTo("msg0000001");
        assertThat(activity.homeId()).isNull();
        assertThat(activity.deliveries()).containsExactly(new Delivery(ANA, ActivityMessages.WELCOME));
    }

    @Test
    @DisplayName("a new home is told to its owner")
    void homeCreated() {
        Activity activity = delivered("home.created", "{\"homeId\":\"hom0000001\",\"homeName\":\"Casa\","
                + "\"ownerId\":\"use0000001\",\"occurredAt\":\"2026-10-27T09:00:00Z\"}");

        assertThat(activity.source()).isEqualTo(NotificationSourceType.HOME);
        assertThat(activity.homeName()).isEqualTo("Casa");
        assertThat(activity.deliveries()).containsExactly(new Delivery(OWNER, ActivityMessages.HOME_CREATED));
    }

    @Test
    @DisplayName("who joined reads that they joined, the others that someone did")
    void memberJoined() {
        Activity activity = delivered("home.member-joined", "{\"homeId\":\"hom0000001\",\"homeName\":\"Casa\","
                + "\"userId\":\"use0000002\",\"memberIds\":[\"use0000001\",\"use0000002\",\"use0000003\"]}");

        assertThat(activity.actorId()).isEqualTo(ANA);
        assertThat(activity.deliveries()).extracting(Delivery::userId, Delivery::messageKey).containsExactly(
                tuple(ANA, ActivityMessages.HOME_JOINED),
                tuple(OWNER, ActivityMessages.MEMBER_JOINED),
                tuple(LUIS, ActivityMessages.MEMBER_JOINED));
    }

    @Test
    @DisplayName("a member who left is told to the members who stay")
    void memberLeft() {
        Activity activity = delivered("home.member-left", "{\"homeId\":\"hom0000001\",\"homeName\":\"Casa\","
                + "\"userId\":\"use0000002\",\"memberIds\":[\"use0000001\",\"use0000003\"]}");

        assertThat(activity.deliveries()).extracting(Delivery::userId)
                .containsExactly(OWNER, LUIS);
        assertThat(activity.deliveries()).extracting(Delivery::messageKey)
                .containsOnly(ActivityMessages.MEMBER_LEFT);
    }

    @Test
    @DisplayName("the removed member is told, and the others except the owner who removed them")
    void memberRemoved() {
        Activity activity = delivered("home.member-removed", "{\"homeId\":\"hom0000001\",\"homeName\":\"Casa\","
                + "\"userId\":\"use0000002\",\"removedBy\":\"use0000001\","
                + "\"memberIds\":[\"use0000001\",\"use0000003\"]}");

        assertThat(activity.actorId()).isEqualTo(OWNER);
        assertThat(activity.subjectId()).isEqualTo(ANA);
        assertThat(activity.deliveries()).extracting(Delivery::userId, Delivery::messageKey).containsExactly(
                tuple(ANA, ActivityMessages.REMOVED_FROM_HOME),
                tuple(LUIS, ActivityMessages.MEMBER_REMOVED));
    }

    @Test
    @DisplayName("new thresholds are told to every member but the one who changed them")
    void thresholdsUpdated() {
        Activity activity = delivered("home.thresholds-updated", "{\"homeId\":\"hom0000001\","
                + "\"homeName\":\"Casa\",\"updatedBy\":\"use0000001\","
                + "\"memberIds\":[\"use0000001\",\"use0000002\",\"use0000003\"]}");

        assertThat(activity.deliveries()).extracting(Delivery::userId).containsExactly(ANA, LUIS);
        assertThat(activity.deliveries()).extracting(Delivery::messageKey)
                .containsOnly(ActivityMessages.THRESHOLDS_UPDATED);
    }

    @Test
    @DisplayName("the new owner, the previous one and the others each read their own wording")
    void ownershipTransferred() {
        Activity activity = delivered("home.ownership-transferred", "{\"homeId\":\"hom0000001\","
                + "\"homeName\":\"Casa\",\"previousOwnerId\":\"use0000001\",\"newOwnerId\":\"use0000002\","
                + "\"memberIds\":[\"use0000001\",\"use0000002\",\"use0000003\"]}");

        assertThat(activity.deliveries()).extracting(Delivery::userId, Delivery::messageKey).containsExactly(
                tuple(ANA, ActivityMessages.OWNERSHIP_RECEIVED),
                tuple(OWNER, ActivityMessages.OWNERSHIP_GIVEN),
                tuple(LUIS, ActivityMessages.OWNER_CHANGED));
    }

    @Test
    @DisplayName("a home inherited because the owner's account was deleted tells no previous owner")
    void inheritedHome() {
        Activity activity = delivered("home.ownership-transferred", "{\"homeId\":\"hom0000001\","
                + "\"homeName\":\"Casa\",\"previousOwnerId\":\"use0000001\",\"newOwnerId\":\"use0000002\","
                + "\"memberIds\":[\"use0000002\",\"use0000003\"]}");

        assertThat(activity.deliveries()).extracting(Delivery::userId).containsExactly(ANA, LUIS);
    }

    @Test
    @DisplayName("a deleted home is told to the members it had, the one who deleted it with their wording")
    void homeDeleted() {
        Activity activity = delivered("home.deleted", "{\"homeId\":\"hom0000001\",\"deletedBy\":\"use0000001\","
                + "\"deletedAt\":\"2026-10-27T09:00:00Z\",\"homeName\":\"Casa\","
                + "\"memberIds\":[\"use0000001\",\"use0000002\"]}");

        assertThat(activity.homeName()).isEqualTo("Casa");
        assertThat(activity.deliveries()).extracting(Delivery::userId, Delivery::messageKey).containsExactly(
                tuple(OWNER, ActivityMessages.HOME_DELETED_BY_YOU),
                tuple(ANA, ActivityMessages.HOME_DELETED));
    }

    @Test
    @DisplayName("a home.deleted from before the members were added tells nobody")
    void oldHomeDeleted() {
        Activity activity = delivered("home.deleted", "{\"homeId\":\"hom0000001\",\"deletedBy\":\"use0000001\","
                + "\"deletedAt\":\"2026-10-27T09:00:00Z\"}");

        assertThat(activity.deliveries()).isEmpty();
    }

    @Test
    @DisplayName("a linked device is told to the members of its home, with who linked it")
    void deviceLinked() {
        given(directory.memberIds("hom0000001")).willReturn(List.of(OWNER, ANA));

        Activity activity = delivered("device.linked", "{\"deviceId\":\"dev0000001\",\"homeId\":\"hom0000001\","
                + "\"deviceName\":\"Nevera\",\"linkedBy\":\"use0000001\",\"linkedAt\":\"2026-10-27T09:00:00Z\"}");

        assertThat(activity.source()).isEqualTo(NotificationSourceType.DEVICE);
        assertThat(activity.actorId()).isEqualTo(OWNER);
        assertThat(activity.deviceName()).isEqualTo("Nevera");
        assertThat(activity.deliveries()).extracting(Delivery::userId).containsExactly(OWNER, ANA);
        assertThat(activity.deliveries()).extracting(Delivery::messageKey)
                .containsOnly(ActivityMessages.DEVICE_LINKED);
    }

    @Test
    @DisplayName("an unlinked device is named by the device service and told to the members")
    void deviceUnlinked() {
        given(directory.memberIds("hom0000001")).willReturn(List.of(OWNER));
        given(directory.deviceName("dev0000001")).willReturn(Optional.of("Nevera"));

        Activity activity = delivered("device.unlinked", "{\"deviceId\":\"dev0000001\",\"homeId\":\"hom0000001\","
                + "\"unlinkedAt\":\"2026-10-27T09:00:00Z\"}");

        assertThat(activity.deviceName()).isEqualTo("Nevera");
        assertThat(activity.deliveries()).containsExactly(new Delivery(OWNER, ActivityMessages.DEVICE_UNLINKED));
    }

    @Test
    @DisplayName("an event already delivered is dropped")
    void dropsDuplicate() {
        given(records.anyAbout(NotificationSourceType.HOME, "msg0000001")).willReturn(true);

        consumer.on(message("msg0000001", "home.created", "{\"homeId\":\"hom0000001\"}"));

        verifyNoInteractions(notifyActivity);
    }

    @Test
    @DisplayName("refuses, without retrying, a message without an id, of another type or without a home")
    void refusesWhatCannotBeDelivered() {
        assertThatThrownBy(() -> consumer.on(message(null, "home.created", "{}")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThatThrownBy(() -> consumer.on(message("msg0000002", "account.deleted", "{}")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThatThrownBy(() -> consumer.on(message("msg0000003", "home.created", "{\"ownerId\":\"x\"}")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);

        verifyNoInteractions(notifyActivity);
    }

    private static Message message(String id, String type, String body) {
        MessageProperties properties = new MessageProperties();
        properties.setMessageId(id);
        properties.setType(type);
        return new Message(body.getBytes(StandardCharsets.UTF_8), properties);
    }
}
