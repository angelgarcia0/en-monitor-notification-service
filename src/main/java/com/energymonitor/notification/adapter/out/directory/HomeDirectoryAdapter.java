package com.energymonitor.notification.adapter.out.directory;

import com.energymonitor.notification.adapter.out.device.DeviceServiceUnavailableException;
import com.energymonitor.notification.adapter.out.device.RemoteDevices;
import com.energymonitor.notification.adapter.out.home.RemoteHomes;
import com.energymonitor.notification.application.port.out.HomeDirectoryPort;
import com.energymonitor.notification.application.port.out.SecurityServiceUnavailableException;
import com.energymonitor.notification.application.port.out.UserProfile;
import com.energymonitor.notification.application.port.out.UserProfileQuery;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Reads members and names from the home service, device names from the device service and
 * addresses from the security service.
 *
 * <p>Each service that does not answer costs something different:
 * <ul>
 *   <li>the home service: no one is known to send to, so the call fails and the message is
 *       retried later;</li>
 *   <li>the security service: no recipient is known; the alert still goes by push, which needs no
 *       address, and its mail is skipped rather than held back;</li>
 *   <li>the device service: the message is worded without the name of the device.</li>
 * </ul>
 */
@Component
public class HomeDirectoryAdapter implements HomeDirectoryPort {

    private static final Logger LOG = LoggerFactory.getLogger(HomeDirectoryAdapter.class);

    private final RemoteHomes homes;
    private final RemoteDevices devices;
    private final UserProfileQuery profiles;

    public HomeDirectoryAdapter(RemoteHomes homes, RemoteDevices devices, UserProfileQuery profiles) {
        this.homes = homes;
        this.devices = devices;
        this.profiles = profiles;
    }

    @Override
    public List<String> memberIds(String homeId) {
        return homes.memberIds(homeId);
    }

    @Override
    public Map<String, Recipient> recipients(List<String> userIds) {
        try {
            return profiles.findByIds(userIds).values().stream()
                    .collect(Collectors.toMap(UserProfile::userId,
                            p -> new Recipient(p.userId(), p.name(), p.email())));
        } catch (SecurityServiceUnavailableException unavailable) {
            LOG.warn("No mail recipients while the security service does not answer: {}",
                    unavailable.getMessage());
            return Map.of();
        }
    }

    @Override
    public Optional<String> homeName(String homeId) {
        return homes.name(homeId);
    }

    @Override
    public Optional<String> deviceName(String deviceId) {
        try {
            return devices.name(deviceId);
        } catch (DeviceServiceUnavailableException unavailable) {
            LOG.warn("Wording a message without the device name while the device service does not"
                    + " answer: {}", unavailable.getMessage());
            return Optional.empty();
        }
    }
}
