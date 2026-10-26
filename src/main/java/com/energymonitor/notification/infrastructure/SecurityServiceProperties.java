package com.energymonitor.notification.infrastructure;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Where the security service answers, how long to wait for it and how long its answers are
 * reused.
 *
 * @param url               base URL of the service, such as {@code http://security:8082}
 * @param connectTimeout    how long to wait for the connection
 * @param readTimeout       how long to wait for the answer
 * @param permissionsTtl    how long the permissions of an account are reused; a role revoked or
 *                          an account blocked reaches the admin routes within this time
 * @param administratorsTtl how long the list of administrators is reused
 * @param profilesTtl       how long the name and address of an account are reused
 */
@ConfigurationProperties("security.service")
public record SecurityServiceProperties(
        @DefaultValue("http://localhost:8082") URI url,
        @DefaultValue("PT1S") Duration connectTimeout,
        @DefaultValue("PT2S") Duration readTimeout,
        @DefaultValue("PT30S") Duration permissionsTtl,
        @DefaultValue("PT30S") Duration administratorsTtl,
        @DefaultValue("PT60S") Duration profilesTtl) {
}
