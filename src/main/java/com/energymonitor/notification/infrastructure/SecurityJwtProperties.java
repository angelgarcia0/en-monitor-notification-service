package com.energymonitor.notification.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * What this service needs to validate the access tokens the security service issues: their
 * issuer and the public half of the service's signing key.
 */
@ConfigurationProperties(prefix = "security.jwt")
public record SecurityJwtProperties(
        String issuer,
        String publicKeyPath) {
}
