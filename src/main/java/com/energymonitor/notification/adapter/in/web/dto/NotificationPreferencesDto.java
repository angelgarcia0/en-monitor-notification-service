package com.energymonitor.notification.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;

/**
 * {@code GET/PUT /api/v1/notifications/preferences}. {@code pushAvailable} (response only) says
 * whether the server can push at all; {@code pushBrowsers} how many browsers of the caller are
 * subscribed.
 */
public record NotificationPreferencesDto(@NotNull Boolean emailEnabled, @NotNull Boolean pushEnabled,
                                         Boolean pushAvailable, Integer pushBrowsers) {
}
