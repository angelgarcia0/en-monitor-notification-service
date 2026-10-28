package com.energymonitor.notification.infrastructure;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Configuration for the {@link Clock} bean.
 *
 * <p>The {@code Clock} is injected into application services to make them testable
 * and to avoid direct calls to {@code Instant.now()}.
 */
@Configuration
public class ClockConfiguration {

    @Bean
    @Primary
    public Clock clock() {
        return Clock.systemUTC();
    }
}
