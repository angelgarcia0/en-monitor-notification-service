package com.energymonitor.notification.adapter.out.security;

import com.energymonitor.notification.application.port.out.SecurityServiceUnavailableException;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.util.Set;

/**
 * Whether an account holds a permission code, answered by the security service and cached per
 * account. A role granted or revoked, or an account blocked, takes effect here once the entry
 * expires: the price of not asking on every administration request.
 */
public class RemotePermissionCheck {

    private final LoadingCache<String, Set<String>> cache;

    public RemotePermissionCheck(SecurityServiceClient client, Duration ttl, Ticker ticker) {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(10_000)
                .ticker(ticker)
                .build(userId -> Set.copyOf(client.permissions(userId)));
    }

    /**
     * @param userId         the caller
     * @param permissionCode the code the route requires
     * @return whether the caller holds it
     * @throws SecurityServiceUnavailableException when the service does not answer and nothing is
     *                                             cached for the caller
     */
    public boolean holds(String userId, String permissionCode) {
        return cache.get(userId).contains(permissionCode);
    }
}
