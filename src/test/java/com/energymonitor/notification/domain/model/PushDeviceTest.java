package com.energymonitor.notification.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The invariants of an app installation: it has to be addressable, or the relay has nowhere to
 * route to.
 */
class PushDeviceTest {

    @Test
    void keepsWhatItWasGiven() {
        PushDevice device = new PushDevice("dev0000001", "usr0000001", "ExponentPushToken[x]", PushDevicePlatform.IOS);

        assertEquals("dev0000001", device.idDevice());
        assertEquals("usr0000001", device.userId());
        assertEquals("ExponentPushToken[x]", device.token());
        assertEquals(PushDevicePlatform.IOS, device.platform());
    }

    @Nested
    class Installation {

        @Test
        void mustHaveAToken() {
            assertThrows(NullPointerException.class,
                    () -> new PushDevice("dev0000001", "usr0000001", null, PushDevicePlatform.ANDROID));
            assertThrows(IllegalArgumentException.class,
                    () -> new PushDevice("dev0000001", "usr0000001", "   ", PushDevicePlatform.ANDROID));
        }

        /** Room for the prefix and the relay's own padding, and no more: it is an address. */
        @Test
        void hasALengthTheColumnCanHold() {
            String atLimit = "E".repeat(PushDevice.TOKEN_MAX_LENGTH);

            assertEquals(atLimit, new PushDevice("dev0000001", "usr0000001", atLimit,
                    PushDevicePlatform.ANDROID).token());
            assertThrows(IllegalArgumentException.class,
                    () -> new PushDevice("dev0000001", "usr0000001", "E".repeat(PushDevice.TOKEN_MAX_LENGTH + 1),
                            PushDevicePlatform.ANDROID));
        }

        @Test
        void mustBelongToAUser() {
            assertThrows(IllegalArgumentException.class,
                    () -> new PushDevice("dev0000001", "  ", "ExponentPushToken[x]", PushDevicePlatform.ANDROID));
            assertThrows(NullPointerException.class,
                    () -> new PushDevice("dev0000001", null, "ExponentPushToken[x]", PushDevicePlatform.ANDROID));
        }

        /** The payload is shaped differently for each, so an installation without one cannot be sent to. */
        @Test
        void mustDeclareAPlatform() {
            assertThrows(NullPointerException.class,
                    () -> new PushDevice("dev0000001", "usr0000001", "ExponentPushToken[x]", null));
        }
    }
}