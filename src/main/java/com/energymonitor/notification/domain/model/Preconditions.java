package com.energymonitor.notification.domain.model;

import java.util.Objects;

/**
 * Domain preconditions for the Notification context.
 */
public final class Preconditions {

    private Preconditions() {
    }

    public static String text(String value, int maxLength, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(name + " must not exceed " + maxLength + " characters");
        }
        return trimmed;
    }

    public static <T> T notNull(T value, String name) {
        return Objects.requireNonNull(value, name + " must not be null");
    }

    public static int positive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
