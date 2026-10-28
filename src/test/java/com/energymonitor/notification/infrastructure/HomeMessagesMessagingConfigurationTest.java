package com.energymonitor.notification.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * The queues of the alerts and recommendations: when they are declared, what each one is bound
 * to, and where a message goes after failing every retry. The exchanges and routing keys are the
 * contract with energy-monitor-backend, which publishes them, so they are pinned here.
 */
@DisplayName("Alerts and recommendations queues")
class HomeMessagesMessagingConfigurationTest {

    private static final String DLX = "notification-service.dlx";

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(HomeMessagesMessagingConfiguration.class)
            .withPropertyValues(
                    "messaging.alert-exchange=energy-monitor.alert",
                    "messaging.recommendation-exchange=energy-monitor.recommendation",
                    "messaging.dead-letter-exchange=" + DLX);

    @Test
    @DisplayName("not declared while switched off (the backend still delivers its alerts)")
    void offByDefault() {
        context.run(started -> assertThat(started).doesNotHaveBean(Declarables.class));
    }

    @Test
    @DisplayName("hears alert.raised and recommendation.created when switched on")
    void declaredWhenEnabled() {
        context.withPropertyValues("messaging.home-messages.enabled=true").run(started -> {
            Declarables declarables = started.getBean(Declarables.class);

            assertThat(declarables.getDeclarablesByType(Binding.class))
                    .extracting(Binding::getExchange, Binding::getDestination, Binding::getRoutingKey)
                    .contains(
                            tuple("energy-monitor.alert",
                                    HomeMessagesMessagingConfiguration.ALERT_RAISED_QUEUE, "alert.raised"),
                            tuple("energy-monitor.recommendation",
                                    HomeMessagesMessagingConfiguration.RECOMMENDATION_CREATED_QUEUE,
                                    "recommendation.created"),
                            tuple(DLX, HomeMessagesMessagingConfiguration.ALERT_RAISED_QUEUE + ".dlq",
                                    HomeMessagesMessagingConfiguration.ALERT_RAISED_QUEUE),
                            tuple(DLX, HomeMessagesMessagingConfiguration.RECOMMENDATION_CREATED_QUEUE + ".dlq",
                                    HomeMessagesMessagingConfiguration.RECOMMENDATION_CREATED_QUEUE));
            assertThat(declarables.getDeclarablesByType(Queue.class))
                    .filteredOn(queue -> !queue.getName().endsWith(".dlq"))
                    .allSatisfy(queue -> {
                        assertThat(queue.isDurable()).isTrue();
                        assertThat(queue.getArguments())
                                .containsEntry("x-dead-letter-exchange", DLX)
                                .containsEntry("x-dead-letter-routing-key", queue.getName());
                    });
        });
    }
}
