package com.energymonitor.notification.infrastructure;

import java.util.ArrayList;
import java.util.List;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
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
 * The queue through which this service hears what happened to accounts, homes and devices, to
 * tell the people it concerns ({@value #ACTIVITY_QUEUE}).
 *
 * <ul>
 *   <li>{@code account.first-signed-in} of the security service: the welcome.</li>
 *   <li>{@code home.*} of the home service: a home created, joined, left, a member removed, the
 *       thresholds changed, the owner changed, the home deleted.</li>
 *   <li>{@code device.linked} and {@code device.unlinked} of the device service.</li>
 * </ul>
 *
 * <p>Durable, and dead-lettered to {@value #ACTIVITY_QUEUE}{@code .dlq} once a message has failed
 * every retry. Declaring it is also what lets those services publish: their outboxes keep a message
 * no queue takes, and stop behind it.
 *
 * <p>Declared with {@code messaging.activity.enabled}, on by default: nothing else delivers these
 * messages, so there is no other consumer to step on.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "messaging.activity.enabled", havingValue = "true", matchIfMissing = true)
public class ActivityMessagingConfiguration {

    public static final String ACTIVITY_QUEUE = "notification-service.notification.activity";

    /** The routing keys of the security service heard here. */
    static final List<String> ACCOUNT_KEYS = List.of("account.first-signed-in");

    /** The routing keys of the home service heard here. */
    static final List<String> HOME_KEYS = List.of("home.created", "home.member-joined",
            "home.member-left", "home.member-removed", "home.thresholds-updated",
            "home.ownership-transferred", "home.deleted");

    /** The routing keys of the device service heard here. */
    static final List<String> DEVICE_KEYS = List.of("device.linked", "device.unlinked");

    @Bean
    public Declarables activityQueue(
            @Value("${messaging.security-exchange}") String securityExchange,
            @Value("${messaging.home-exchange}") String homeExchange,
            @Value("${messaging.device-exchange}") String deviceExchange,
            @Value("${messaging.dead-letter-exchange}") String deadLetterExchange) {
        TopicExchange security = new TopicExchange(securityExchange, true, false);
        TopicExchange homes = new TopicExchange(homeExchange, true, false);
        TopicExchange devices = new TopicExchange(deviceExchange, true, false);
        DirectExchange deadLetters = new DirectExchange(deadLetterExchange, true, false);

        Queue activity = QueueBuilder.durable(ACTIVITY_QUEUE)
                .deadLetterExchange(deadLetterExchange)
                .deadLetterRoutingKey(ACTIVITY_QUEUE)
                .build();
        Queue activityDead = QueueBuilder.durable(ACTIVITY_QUEUE + ".dlq").build();

        List<Declarable> declarables = new ArrayList<>(List.of(security, homes, devices, deadLetters,
                activity, activityDead,
                BindingBuilder.bind(activityDead).to(deadLetters).with(ACTIVITY_QUEUE)));
        ACCOUNT_KEYS.forEach(key -> declarables.add(bind(activity, security, key)));
        HOME_KEYS.forEach(key -> declarables.add(bind(activity, homes, key)));
        DEVICE_KEYS.forEach(key -> declarables.add(bind(activity, devices, key)));
        return new Declarables(declarables);
    }

    private static Binding bind(Queue queue, TopicExchange exchange, String routingKey) {
        return BindingBuilder.bind(queue).to(exchange).with(routingKey);
    }
}
