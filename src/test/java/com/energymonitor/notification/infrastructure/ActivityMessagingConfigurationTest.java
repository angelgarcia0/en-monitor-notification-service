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
 * The activity queue: declared by default, what it is bound to, and where a message goes after
 * failing every retry. The exchanges and routing keys are the contract with the security, home and
 * device services, which publish them, so they are pinned here.
 */
@DisplayName("Activity queue")
class ActivityMessagingConfigurationTest {

    private static final String DLX = "notification-service.dlx";
    private static final String QUEUE = ActivityMessagingConfiguration.ACTIVITY_QUEUE;

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(ActivityMessagingConfiguration.class)
            .withPropertyValues(
                    "messaging.security-exchange=energy-monitor.security",
                    "messaging.home-exchange=energy-monitor.home",
                    "messaging.device-exchange=energy-monitor.device",
                    "messaging.dead-letter-exchange=" + DLX);

    @Test
    @DisplayName("declared by default, bound to the welcome, every home event and the device links")
    void declaredByDefault() {
        context.run(started -> {
            Declarables declarables = started.getBean(Declarables.class);

            assertThat(declarables.getDeclarablesByType(Binding.class))
                    .extracting(Binding::getExchange, Binding::getDestination, Binding::getRoutingKey)
                    .containsExactlyInAnyOrder(
                            tuple("energy-monitor.security", QUEUE, "account.first-signed-in"),
                            tuple("energy-monitor.home", QUEUE, "home.created"),
                            tuple("energy-monitor.home", QUEUE, "home.member-joined"),
                            tuple("energy-monitor.home", QUEUE, "home.member-left"),
                            tuple("energy-monitor.home", QUEUE, "home.member-removed"),
                            tuple("energy-monitor.home", QUEUE, "home.thresholds-updated"),
                            tuple("energy-monitor.home", QUEUE, "home.ownership-transferred"),
                            tuple("energy-monitor.home", QUEUE, "home.deleted"),
                            tuple("energy-monitor.device", QUEUE, "device.linked"),
                            tuple("energy-monitor.device", QUEUE, "device.unlinked"),
                            tuple(DLX, QUEUE + ".dlq", QUEUE));
            Queue queue = declarables.getDeclarablesByType(Queue.class).stream()
                    .filter(candidate -> candidate.getName().equals(QUEUE)).findFirst().orElseThrow();
            assertThat(queue.isDurable()).isTrue();
            assertThat(queue.getArguments())
                    .containsEntry("x-dead-letter-exchange", DLX)
                    .containsEntry("x-dead-letter-routing-key", QUEUE);
        });
    }

    @Test
    @DisplayName("not declared when switched off")
    void offWhenSwitchedOff() {
        context.withPropertyValues("messaging.activity.enabled=false")
                .run(started -> assertThat(started).doesNotHaveBean(Declarables.class));
    }
}
