package com.energymonitor.notification.infrastructure;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the OpenAPI description and the one security scheme this API authenticates with: a
 * bearer JWT signed by the security service.
 */
@Configuration
@OpenAPIDefinition(info = @Info(
        title = "Energy Monitor Notification API",
        version = "v1",
        description = "A user's notification channels: mail and push preferences, Web Push "
                + "subscriptions of browsers and push devices of the apps; and the in-app "
                + "notifications of the administrators. The bearer token is issued by the "
                + "security service."))
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT")
public class OpenApiSecurityConfig {
}
