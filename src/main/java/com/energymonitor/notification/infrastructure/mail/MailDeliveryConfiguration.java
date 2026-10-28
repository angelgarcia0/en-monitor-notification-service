package com.energymonitor.notification.infrastructure.mail;

import com.energymonitor.notification.infrastructure.MailDeliveryProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Wiring for the sweep of the delivery record.
 *
 * <p>No queue of its own is declared here: the verification and recovery codes arrive from the
 * security service over the message broker, and {@code MailRequestsConsumer} mails them on the
 * listener thread, so a request of that service never waits for SMTP.
 *
 * <p>This class keeps only what is about the delivery record: the sweep that closes deliveries
 * abandoned by an earlier run. It measures age against the UTC clock of
 * {@code ClockConfiguration}.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MailDeliveryProperties.class)
@EnableScheduling
public class MailDeliveryConfiguration {
}
