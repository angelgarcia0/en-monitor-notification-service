package com.energymonitor.notification.domain.model;

/**
 * Platform of an app installation that receives push.
 *
 * <p>Kept because the relay is the same for both and the payload a phone accepts is not: Android
 * and iOS read the body differently, so a notification meant for one has to be shaped for it.
 */
public enum PushDevicePlatform {

    ANDROID,
    IOS
}