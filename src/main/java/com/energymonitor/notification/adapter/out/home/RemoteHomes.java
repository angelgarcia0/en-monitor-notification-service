package com.energymonitor.notification.adapter.out.home;

import com.energymonitor.notification.adapter.out.home.HomeServiceClient.RemoteHome;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * The members and the name of a home, answered by the home service.
 *
 * <p>Answers are reused for a short while, a missing home included, so a burst of alerts for one
 * home costs one request per {@code ttl}. A member added or removed in the home service is reached
 * once its entry expires.
 *
 * <p>The last answer is also kept for {@code staleTtl}: when the service does not answer, that is
 * what is served, so a short outage does not stop the messages of homes already known. Only with
 * nothing kept does a call throw {@link HomeServiceUnavailableException}.
 */
public class RemoteHomes {

    private final HomeServiceClient client;
    private final Cache<String, Optional<RemoteHome>> homes;
    private final Cache<String, Optional<RemoteHome>> lastKnownHomes;

    /**
     * @param client   the home service
     * @param ttl      how long an answer is reused before asking again
     * @param staleTtl how long the last answer is kept to serve while the service does not answer
     * @param ticker   the clock of the caches
     */
    public RemoteHomes(HomeServiceClient client, Duration ttl, Duration staleTtl, Ticker ticker) {
        this.client = client;
        this.homes = cache(ttl, ticker);
        this.lastKnownHomes = cache(staleTtl, ticker);
    }

    private static <V> Cache<String, V> cache(Duration ttl, Ticker ticker) {
        return Caffeine.newBuilder().expireAfterWrite(ttl).maximumSize(10_000).ticker(ticker).build();
    }

    /**
     * @param homeId the home
     * @return its active members, owners included; none when the home does not exist
     */
    public List<String> memberIds(String homeId) {
        return home(homeId).map(RemoteHome::memberIds).orElse(List.of());
    }

    /**
     * @param homeId the home
     * @return its name, when the home exists
     */
    public Optional<String> name(String homeId) {
        return home(homeId).map(RemoteHome::name);
    }

    /**
     * The cached answer, or a new one, which is also kept as the last known; when the service does
     * not answer, the last known one if there is any.
     */
    private Optional<RemoteHome> home(String homeId) {
        if (homeId == null) {
            return Optional.empty();
        }
        Optional<RemoteHome> cached = homes.getIfPresent(homeId);
        if (cached != null) {
            return cached;
        }
        try {
            Optional<RemoteHome> loaded = client.home(homeId);
            homes.put(homeId, loaded);
            lastKnownHomes.put(homeId, loaded);
            return loaded;
        } catch (HomeServiceUnavailableException unavailable) {
            Optional<RemoteHome> stale = lastKnownHomes.getIfPresent(homeId);
            if (stale != null) {
                return stale;
            }
            throw unavailable;
        }
    }
}
