package com.energymonitor.notification.application.port.in;

import java.time.Instant;

/**
 * Published by the security service when a new account is created, by registration or by a
 * first Google sign-in; received here as {@code account.registered}.
 *
 * @param userId     the new account
 * @param email      its address
 * @param occurredAt when it was created
 */
public record AccountRegistered(String userId, String email, Instant occurredAt) {
}
