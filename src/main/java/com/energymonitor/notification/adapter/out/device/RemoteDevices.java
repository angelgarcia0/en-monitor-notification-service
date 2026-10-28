package com.energymonitor.notification.adapter.out.device;

import com.energymonitor.notification.adapter.out.device.DeviceServiceClient.RemoteDevice;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.util.Optional;

/**
 * The name of a device, answered by the device service, for the wording of a message.
 *
 * <p>Answers are reused for a short while, a device that does not exist included; a device
 * renamed shows here once its entry expires. The last answer is also kept for {@code staleTtl}:
 * when the service does not answer, that is what is served. Only with nothing kept does a call
 * throw {@link DeviceServiceUnavailableException}.
 */
public class RemoteDevices {

    private final DeviceServiceClient client;
    private final Cache<String, Optional<RemoteDevice>> devices;
    private final Cache<String, Optional<RemoteDevice>> lastKnownDevices;

    /**
     * @param client   the device service
     * @param ttl      how long an answer is reused before asking again
     * @param staleTtl how long the last answer is kept to serve while the service does not answer
     * @param ticker   the clock of the caches
     */
    public RemoteDevices(DeviceServiceClient client, Duration ttl, Duration staleTtl, Ticker ticker) {
        this.client = client;
        this.devices = cache(ttl, ticker);
        this.lastKnownDevices = cache(staleTtl, ticker);
    }

    private static <V> Cache<String, V> cache(Duration ttl, Ticker ticker) {
        return Caffeine.newBuilder().expireAfterWrite(ttl).maximumSize(10_000).ticker(ticker).build();
    }

    /**
     * @param deviceId the device, may be null
     * @return its name, when the device exists
     */
    public Optional<String> name(String deviceId) {
        return device(deviceId).map(RemoteDevice::name);
    }

    /**
     * The cached answer, or a new one, which is also kept as the last known; when the service does
     * not answer, the last known one if there is any.
     */
    private Optional<RemoteDevice> device(String deviceId) {
        if (deviceId == null) {
            return Optional.empty();
        }
        Optional<RemoteDevice> cached = devices.getIfPresent(deviceId);
        if (cached != null) {
            return cached;
        }
        try {
            Optional<RemoteDevice> loaded = client.device(deviceId);
            devices.put(deviceId, loaded);
            lastKnownDevices.put(deviceId, loaded);
            return loaded;
        } catch (DeviceServiceUnavailableException unavailable) {
            Optional<RemoteDevice> stale = lastKnownDevices.getIfPresent(deviceId);
            if (stale != null) {
                return stale;
            }
            throw unavailable;
        }
    }
}
