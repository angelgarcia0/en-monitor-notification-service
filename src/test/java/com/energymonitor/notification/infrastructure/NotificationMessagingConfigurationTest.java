package com.energymonitor.notification.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * The queues this service consumes from the security service: when they are declared, what each
 * one is bound to, and where a message goes after failing every retry. A routing key that does
 * not match the one the security service publishes silently stops a kind of message, so the keys
 * are pinned here.
 */
@DisplayName("Security events queues")
class NotificationMessagingConfigurationTest {

    private static final String EXCHANGE = "energy-monitor.security";
    private static final String DLX = "notification-service.dlx";

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(NotificationMessagingConfiguration.class)
            .withPropertyValues(
                    "messaging.security-exchange=" + EXCHANGE,
                    "messaging.dead-letter-exchange=" + DLX);

    @Test
    @DisplayName("not declared while switched off (the backend still consumes the same events)")
    void offByDefault() {
        context.run(started -> assertThat(started).doesNotHaveBean(Declarables.class));
    }

    @Test
    @DisplayName("hears account events, deletions and mail requests when switched on")
    void declaredWhenEnabled() {
        context.withPropertyValues("messaging.security-events.enabled=true").run(started -> {
            Declarables declarables = started.getBean(Declarables.class);

            assertThat(bindingsFrom(declarables, EXCHANGE))
                    .extracting(Binding::getDestination, Binding::getRoutingKey)
                    .containsExactlyInAnyOrder(
                            tuple(NotificationMessagingConfiguration.ACCOUNT_EVENTS_QUEUE, "account.registered"),
                            tuple(NotificationMessagingConfiguration.ACCOUNT_EVENTS_QUEUE, "account.administered"),
                            tuple(NotificationMessagingConfiguration.ACCOUNT_EVENTS_QUEUE, "account.deleted"),
                            tuple(NotificationMessagingConfiguration.MAIL_QUEUE, "mail.email-verification"),
                            tuple(NotificationMessagingConfiguration.MAIL_QUEUE, "mail.password-reset"),
                            tuple(NotificationMessagingConfiguration.MAIL_QUEUE, "mail.account-deletion"));
            assertDeadLettered(declarables, NotificationMessagingConfiguration.ACCOUNT_EVENTS_QUEUE);
            assertDeadLettered(declarables, NotificationMessagingConfiguration.MAIL_QUEUE);
        });
    }

    @Test
    @DisplayName("its queues are its own, not the ones energy-monitor-backend consumes")
    void ownQueueNames() {
        assertThat(List.of(NotificationMessagingConfiguration.ACCOUNT_EVENTS_QUEUE,
                NotificationMessagingConfiguration.MAIL_QUEUE))
                .allSatisfy(name -> assertThat(name).startsWith("notification-service."));
    }

    private static void assertDeadLettered(Declarables declarables, String queueName) {
        Queue queue = declarables.getDeclarablesByType(Queue.class).stream()
                .filter(candidate -> candidate.getName().equals(queueName))
                .findFirst().orElseThrow();
        assertThat(queue.isDurable()).isTrue();
        assertThat(queue.getArguments())
                .containsEntry("x-dead-letter-exchange", DLX)
                .containsEntry("x-dead-letter-routing-key", queueName);
        assertThat(bindingsFrom(declarables, DLX))
                .extracting(Binding::getDestination, Binding::getRoutingKey)
                .contains(tuple(queueName + ".dlq", queueName));
    }

    private static List<Binding> bindingsFrom(Declarables declarables, String exchange) {
        return declarables.getDeclarablesByType(Binding.class).stream()
                .filter(binding -> binding.getExchange().equals(exchange))
                .toList();
    }
}
