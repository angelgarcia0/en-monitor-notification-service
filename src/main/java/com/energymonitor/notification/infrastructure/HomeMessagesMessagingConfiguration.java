package com.energymonitor.notification.infrastructure;

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
 * The queues through which this service hears the alerts and recommendations energy-monitor-backend
 * raises, to deliver them to the members of their home.
 *
 * <ul>
 *   <li>{@value #ALERT_RAISED_QUEUE}: {@code alert.raised} of the topic exchange
 *       {@code messaging.alert-exchange}.</li>
 *   <li>{@value #RECOMMENDATION_CREATED_QUEUE}: {@code recommendation.created} of the topic
 *       exchange {@code messaging.recommendation-exchange}.</li>
 * </ul>
 *
 * <p>Both are durable and dead-letter to a queue of their own once a message has failed every
 * retry, which keeps it when the home service stayed down for every attempt.
 *
 * <p>Only declared with {@code messaging.home-messages.enabled}. While energy-monitor-backend
 * still has its notification module it delivers its alerts in process, and with both every member
 * would get each alert twice; so the switch stays off until the backend's module is removed, in
 * the same deployment.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "messaging.home-messages.enabled", havingValue = "true")
public class HomeMessagesMessagingConfiguration {

    public static final String ALERT_RAISED_QUEUE = "notification-service.notification.alert-raised";
    public static final String RECOMMENDATION_CREATED_QUEUE =
            "notification-service.notification.recommendation-created";

    @Bean
    public Declarables homeMessageQueues(
            @Value("${messaging.alert-exchange}") String alertExchange,
            @Value("${messaging.recommendation-exchange}") String recommendationExchange,
            @Value("${messaging.dead-letter-exchange}") String deadLetterExchange) {
        TopicExchange alerts = new TopicExchange(alertExchange, true, false);
        TopicExchange recommendations = new TopicExchange(recommendationExchange, true, false);
        DirectExchange deadLetters = new DirectExchange(deadLetterExchange, true, false);

        Queue alertRaised = queue(ALERT_RAISED_QUEUE, deadLetterExchange);
        Queue recommendationCreated = queue(RECOMMENDATION_CREATED_QUEUE, deadLetterExchange);
        Queue alertRaisedDead = QueueBuilder.durable(ALERT_RAISED_QUEUE + ".dlq").build();
        Queue recommendationCreatedDead =
                QueueBuilder.durable(RECOMMENDATION_CREATED_QUEUE + ".dlq").build();

        return new Declarables(alerts, recommendations, deadLetters,
                alertRaised, recommendationCreated, alertRaisedDead, recommendationCreatedDead,
                BindingBuilder.bind(alertRaised).to(alerts).with("alert.raised"),
                BindingBuilder.bind(recommendationCreated).to(recommendations)
                        .with("recommendation.created"),
                BindingBuilder.bind(alertRaisedDead).to(deadLetters).with(ALERT_RAISED_QUEUE),
                BindingBuilder.bind(recommendationCreatedDead).to(deadLetters)
                        .with(RECOMMENDATION_CREATED_QUEUE));
    }

    private static Queue queue(String name, String deadLetterExchange) {
        return QueueBuilder.durable(name)
                .deadLetterExchange(deadLetterExchange)
                .deadLetterRoutingKey(name)
                .build();
    }
}
