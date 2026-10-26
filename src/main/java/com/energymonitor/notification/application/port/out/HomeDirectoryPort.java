package com.energymonitor.notification.application.port.out;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Who belongs to a home and how to reach them, read from the home, device and security services.
 */
public interface HomeDirectoryPort {

    record Recipient(String userId, String name, String email) {
    }

    List<String> memberIds(String homeId);

    Map<String, Recipient> recipients(List<String> userIds);

    Optional<String> homeName(String homeId);

    Optional<String> deviceName(String deviceId);
}
