package com.energymonitor.notification.infrastructure;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The queues through which this service hears from the security service.
 *
 * <ul>
 *   <li>{@value #ACCOUNT_EVENTS_QUEUE}: new accounts and administrators' changes, which become
 *       in-app notifications for the administrators, and deleted accounts, whose mail and push
 *       preferences and devices are dropped.</li>
 *   <li>{@value #MAIL_QUEUE}: verification and recovery codes to mail.</li>
 * </ul>
 *
 * <p>Both are durable and dead-letter to a queue of their own once a message has failed every
 * retry, so a message that cannot be handled is kept for inspection instead of blocking the
 * ones behind it.
 *
 * <p>Only declared with {@code messaging.security-events.enabled}. While energy-monitor-backend
 * still has its notification module it consumes the same events from queues of its own, and with
 * both every code would be mailed twice; so the switch stays off until the backend's module is
 * removed, in the same deployment.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "messaging.security-events.enabled", havingValue = "true")
public class NotificationMessagingConfiguration {

    public static final String ACCOUNT_EVENTS_QUEUE = "notification-service.notification.account-events";
    public static final String MAIL_QUEUE = "notification-service.notification.mail";

    @Bean
    public Declarables notificationQueues(
            @Value("${messaging.security-exchange}") String securityExchange,
            @Value("${messaging.dead-letter-exchange}") String deadLetterExchange) {
        TopicExchange security = new TopicExchange(securityExchange, true, false);
        DirectExchange deadLetters = new DirectExchange(deadLetterExchange, true, false);

        Queue accountEvents = queue(ACCOUNT_EVENTS_QUEUE, deadLetterExchange);
        Queue mail = queue(MAIL_QUEUE, deadLetterExchange);
        Queue accountEventsDead = QueueBuilder.durable(ACCOUNT_EVENTS_QUEUE + ".dlq").build();
        Queue mailDead = QueueBuilder.durable(MAIL_QUEUE + ".dlq").build();

        return new Declarables(security, deadLetters, accountEvents, mail, accountEventsDead, mailDead,
                bind(accountEvents, security, "account.registered"),
                bind(accountEvents, security, "account.administered"),
                bind(accountEvents, security, "account.deleted"),
                bind(mail, security, "mail.email-verification"),
                bind(mail, security, "mail.password-reset"),
                bind(mail, security, "mail.account-deletion"),
                BindingBuilder.bind(accountEventsDead).to(deadLetters).with(ACCOUNT_EVENTS_QUEUE),
                BindingBuilder.bind(mailDead).to(deadLetters).with(MAIL_QUEUE));
    }

    private static Queue queue(String name, String deadLetterExchange) {
        return QueueBuilder.durable(name)
                .deadLetterExchange(deadLetterExchange)
                .deadLetterRoutingKey(name)
                .build();
    }

    private static Binding bind(Queue queue, TopicExchange exchange, String routingKey) {
        return BindingBuilder.bind(queue).to(exchange).with(routingKey);
    }
}
