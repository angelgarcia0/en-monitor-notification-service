package com.energymonitor.notification.adapter.out.security;

import com.energymonitor.notification.application.port.out.SecurityServiceUnavailableException;
import com.energymonitor.notification.application.port.out.UserProfile;
import com.energymonitor.notification.application.port.out.UserProfileQuery;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@link UserProfileQuery} answered by the security service, one request for every identifier
 * not already cached. An identifier with no active account is cached as absent too, so a list of
 * members with a deleted account in it does not ask for it on every view.
 *
 * <p>A name or address changed in the security service shows here once the entry expires. When
 * the service does not answer for the identifiers not cached, {@link #findByIds} throws
 * {@link SecurityServiceUnavailableException} and each caller degrades in its own way.
 */
public class RemoteUserProfileQuery implements UserProfileQuery {

    private final SecurityServiceClient client;
    private final Cache<String, Optional<UserProfile>> cache;

    public RemoteUserProfileQuery(SecurityServiceClient client, Duration ttl, Ticker ticker) {
        this.client = client;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(10_000)
                .ticker(ticker)
                .build();
    }

    @Override
    public Map<String, UserProfile> findByIds(Collection<String> userIds) {
        var wanted = userIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        Map<String, UserProfile> found = new LinkedHashMap<>();
        cache.getAll(wanted, missing -> {
            Map<String, UserProfile> fetched = client.profiles(Set.copyOf(missing));
            Map<String, Optional<UserProfile>> loaded = new LinkedHashMap<>();
            missing.forEach(userId -> loaded.put(userId, Optional.ofNullable(fetched.get(userId))));
            return loaded;
        }).forEach((userId, profile) -> profile.ifPresent(value -> found.put(userId, value)));
        return found;
    }
}
