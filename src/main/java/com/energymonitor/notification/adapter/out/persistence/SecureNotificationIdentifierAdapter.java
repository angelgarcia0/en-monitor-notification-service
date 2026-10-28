package com.energymonitor.notification.adapter.out.persistence;

import com.energymonitor.notification.application.port.out.NotificationIdentifierPort;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * Cryptographically secure {@link NotificationIdentifierPort}, following the convention the
 * security module already established: exactly ten characters drawn from {@code [0-9a-z]}.
 *
 * <p>Lower case only, because the schema collation is case insensitive and mixed case values
 * would collide on comparison even when they differ as strings.
 */
@Component
public class SecureNotificationIdentifierAdapter implements NotificationIdentifierPort {

    private static final int IDENTIFIER_LENGTH = 10;

    private static final char[] ALPHABET =
            "0123456789abcdefghijklmnopqrstuvwxyz".toCharArray();

    private final SecureRandom random = new SecureRandom();

    @Override
    public String generate() {
        StringBuilder identifier = new StringBuilder(IDENTIFIER_LENGTH);
        for (int position = 0; position < IDENTIFIER_LENGTH; position++) {
            identifier.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return identifier.toString();
    }
}
