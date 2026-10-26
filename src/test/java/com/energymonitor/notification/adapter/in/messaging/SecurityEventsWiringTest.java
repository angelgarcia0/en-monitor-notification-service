package com.energymonitor.notification.adapter.in.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.energymonitor.notification.infrastructure.JwtKeyedTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Declarables;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/**
 * The application with the security events switched on, as it runs after the cut-over: both
 * consumers and their queues are wired. The listeners do not start (test profile), so no message
 * is taken from a broker.
 */
@SpringBootTest(properties = "messaging.security-events.enabled=true")
@DisplayName("Security events switched on")
class SecurityEventsWiringTest extends JwtKeyedTest {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("both consumers and their queues are in the context")
    void wired() {
        assertThat(context.getBeansOfType(AccountEventsConsumer.class)).hasSize(1);
        assertThat(context.getBeansOfType(MailRequestsConsumer.class)).hasSize(1);
        assertThat(context.getBeansOfType(Declarables.class)).hasSize(1);
    }
}
