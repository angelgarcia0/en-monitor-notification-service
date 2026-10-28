package com.energymonitor.notification.infrastructure;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Where the device service answers, how long to wait for it and how long its answers are reused.
 *
 * @param url            base URL of the service, such as {@code http://device:8084}
 * @param connectTimeout how long to wait for the connection
 * @param readTimeout    how long to wait for the answer
 * @param ttl            how long a device is reused; a device renamed shows here within this time
 * @param staleTtl       how long the last answer is kept to serve while the service does not
 *                       answer
 */
@ConfigurationProperties("device.service")
public record DeviceServiceProperties(
        @DefaultValue("http://localhost:8084") URI url,
        @DefaultValue("PT1S") Duration connectTimeout,
        @DefaultValue("PT2S") Duration readTimeout,
        @DefaultValue("PT30S") Duration ttl,
        @DefaultValue("PT1H") Duration staleTtl) {
}
