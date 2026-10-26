package com.energymonitor.notification.adapter.in.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The JSON of a browser {@code PushSubscription} ({@code subscription.toJSON()}).
 */
public record PushSubscriptionRequest(@NotBlank @Size(max = 500) String endpoint, @NotNull @Valid Keys keys) {

    public record Keys(@NotBlank @Size(max = 100) String p256dh, @NotBlank @Size(max = 50) String auth) {
    }
}
