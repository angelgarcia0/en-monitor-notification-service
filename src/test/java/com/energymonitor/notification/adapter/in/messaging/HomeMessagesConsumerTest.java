package com.energymonitor.notification.adapter.in.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.energymonitor.notification.adapter.out.home.HomeServiceUnavailableException;
import com.energymonitor.notification.application.port.out.NotificationRecordPort;
import com.energymonitor.notification.application.usecase.NotifyAlert;
import com.energymonitor.notification.domain.model.NotificationSourceType;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("Alerts and recommendations consumer")
class HomeMessagesConsumerTest {

    private static final String ALERT = "{\"idAlert\":\"alr0000001\",\"homeId\":\"hom0000001\","
            + "\"deviceId\":\"dev0000001\",\"type\":\"THRESHOLD\",\"messageKey\":\"alert.threshold.high\","
            + "\"dateTime\":\"2026-10-20T15:00:00Z\"}";
    private static final String RECOMMENDATION = "{\"idRecommendation\":\"rec0000001\","
            + "\"homeId\":\"hom0000001\",\"deviceId\":null,\"type\":\"PEAK_HOURS\","
            + "\"messageKey\":\"recommendation.peakHours\",\"dateTime\":\"2026-10-20T15:00:00Z\"}";

    private final NotifyAlert notifyAlert = mock(NotifyAlert.class);
    private final NotificationRecordPort records = mock(NotificationRecordPort.class);
    private final HomeMessagesConsumer consumer =
            new HomeMessagesConsumer(notifyAlert, records, JsonMapper.builder().build());

    @Test
    @DisplayName("delivers an alert to the members of its home")
    void deliversAlert() {
        consumer.onAlert(message("alert.raised", ALERT));

        verify(notifyAlert).notify(NotificationSourceType.ALERT, "alr0000001", "hom0000001",
                "dev0000001", "alert.threshold.high");
    }

    @Test
    @DisplayName("delivers a recommendation about the whole home")
    void deliversRecommendation() {
        consumer.onRecommendation(message("recommendation.created", RECOMMENDATION));

        verify(notifyAlert).notify(NotificationSourceType.RECOMMENDATION, "rec0000001",
                "hom0000001", null, "recommendation.peakHours");
    }

    @Test
    @DisplayName("drops an alert the delivery log already holds")
    void dropsDuplicate() {
        given(records.anyAbout(NotificationSourceType.ALERT, "alr0000001")).willReturn(true);

        consumer.onAlert(message("alert.raised", ALERT));

        verifyNoInteractions(notifyAlert);
    }

    @Test
    @DisplayName("a home service that does not answer reaches the broker, which retries")
    void homeServiceDownIsRetried() {
        willThrow(new HomeServiceUnavailableException("down", null)).given(notifyAlert)
                .notify(NotificationSourceType.ALERT, "alr0000001", "hom0000001", "dev0000001",
                        "alert.threshold.high");

        assertThatThrownBy(() -> consumer.onAlert(message("alert.raised", ALERT)))
                .isInstanceOf(HomeServiceUnavailableException.class);
    }

    @Test
    @DisplayName("refuses a message of another type, without retrying it")
    void refusesUnexpectedType() {
        assertThatThrownBy(() -> consumer.onAlert(message("alert.deleted", ALERT)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);

        verifyNoInteractions(notifyAlert);
    }

    @Test
    @DisplayName("refuses an alert without its home, without retrying it")
    void refusesIncompleteAlert() {
        assertThatThrownBy(() -> consumer.onAlert(message("alert.raised",
                "{\"idAlert\":\"alr0000001\",\"messageKey\":\"alert.threshold.high\"}")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);

        verifyNoInteractions(notifyAlert);
    }

    private static Message message(String type, String body) {
        MessageProperties properties = new MessageProperties();
        properties.setType(type);
        return new Message(body.getBytes(StandardCharsets.UTF_8), properties);
    }
}
