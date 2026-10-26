package com.energymonitor.notification.infrastructure;

import com.energymonitor.notification.adapter.out.home.HomeServiceClient;
import com.energymonitor.notification.adapter.out.home.RemoteHomes;
import com.github.benmanes.caffeine.cache.Ticker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Connects this service to the home service: the client of its internal API, with its address,
 * timeouts and the shared token, and {@link RemoteHomes}, the members and names of homes, cached.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(HomeServiceProperties.class)
public class HomeServiceClientConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(HomeServiceClientConfiguration.class);

    @Bean
    public HomeServiceClient homeServiceClient(HomeServiceProperties properties,
                                               @Value("${internal-api.token:}") String token) {
        if (token == null || token.isBlank()) {
            LOG.warn("internal-api.token is empty: the home service refuses every call, so no"
                    + " alert or recommendation reaches the members of a home");
        }
        return HomeServiceClient.create(properties.url().toString(), token == null ? "" : token,
                properties.connectTimeout(), properties.readTimeout());
    }

    @Bean
    public RemoteHomes remoteHomes(HomeServiceClient client, HomeServiceProperties properties) {
        return new RemoteHomes(client, properties.ttl(), properties.staleTtl(), Ticker.systemTicker());
    }
}
