package com.energymonitor.notification.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The token an app reports after it obtained it from a push relay.
 *
 * <p>Not the JSON of a browser {@code PushSubscription}: there is no endpoint and no keys here,
 * because the relay routes on the token alone.
 */
public record PushDeviceRequest(@NotBlank @Size(max = 255) String token, @NotNull Platform platform) {

    public enum Platform {
        ANDROID,
        IOS
    }
}