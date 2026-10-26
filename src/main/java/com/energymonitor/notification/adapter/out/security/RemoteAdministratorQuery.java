package com.energymonitor.notification.adapter.out.security;

import com.energymonitor.notification.application.port.out.AdministratorQuery;
import com.energymonitor.notification.application.port.out.SecurityServiceUnavailableException;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.util.List;

/**
 * {@link AdministratorQuery} answered by the security service and kept for a short while, since
 * every administration event asks it again. When the service does not answer and nothing is
 * cached, {@link #activeAdministratorIds} throws {@link SecurityServiceUnavailableException} and
 * the message consumer lets the broker retry.
 */
public class RemoteAdministratorQuery implements AdministratorQuery {

    private final LoadingCache<String, List<String>> cache;

    public RemoteAdministratorQuery(SecurityServiceClient client, Duration ttl, Ticker ticker) {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(1)
                .ticker(ticker)
                .build(key -> client.administrators());
    }

    @Override
    public List<String> activeAdministratorIds() {
        return cache.get("administrators");
    }
}
