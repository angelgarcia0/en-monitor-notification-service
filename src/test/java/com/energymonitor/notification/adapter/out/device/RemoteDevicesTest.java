package com.energymonitor.notification.adapter.out.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.energymonitor.notification.adapter.out.device.DeviceServiceClient.RemoteDevice;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What the cached devices ask the device service, when they ask again, and what they serve while
 * the service does not answer. The clock of the caches is moved by hand.
 */
@DisplayName("Devices answered by the device service")
class RemoteDevicesTest {

    private static final RemoteDevice FRIDGE = new RemoteDevice("dev0000001", "Nevera");
    private static final DeviceServiceUnavailableException DOWN =
            new DeviceServiceUnavailableException("device service did not answer", null);

    private final DeviceServiceClient client = mock(DeviceServiceClient.class);
    private final AtomicLong nanos = new AtomicLong();
    private final RemoteDevices devices =
            new RemoteDevices(client, Duration.ofSeconds(30), Duration.ofHours(1), nanos::get);

    private void advance(Duration duration) {
        nanos.addAndGet(duration.toNanos());
    }

    @Test
    @DisplayName("one request per device while the entry lasts, a missing one included")
    void cached() {
        given(client.device("dev0000001")).willReturn(Optional.of(FRIDGE));
        given(client.device("dev0000099")).willReturn(Optional.empty());

        assertThat(devices.name("dev0000001")).contains("Nevera");
        assertThat(devices.name("dev0000001")).contains("Nevera");
        assertThat(devices.name("dev0000099")).isEmpty();
        assertThat(devices.name("dev0000099")).isEmpty();

        verify(client, times(1)).device("dev0000001");
        verify(client, times(1)).device("dev0000099");
    }

    @Test
    @DisplayName("asks again once the entry expires")
    void asksAgainAfterTtl() {
        given(client.device("dev0000001")).willReturn(Optional.of(FRIDGE));

        devices.name("dev0000001");
        advance(Duration.ofSeconds(31));
        devices.name("dev0000001");

        verify(client, times(2)).device("dev0000001");
    }

    @Test
    @DisplayName("serves the last device while the service does not answer")
    void staleWhileDown() {
        given(client.device("dev0000001")).willReturn(Optional.of(FRIDGE)).willThrow(DOWN);

        devices.name("dev0000001");
        advance(Duration.ofMinutes(5));

        assertThat(devices.name("dev0000001")).contains("Nevera");
    }

    @Test
    @DisplayName("fails when the service does not answer and nothing was kept")
    void failsWithNothingKept() {
        given(client.device("dev0000001")).willThrow(DOWN);

        assertThatThrownBy(() -> devices.name("dev0000001")).isSameAs(DOWN);
    }

    @Test
    @DisplayName("asks nothing for a message about the whole home")
    void nothingWithoutADevice() {
        assertThat(devices.name(null)).isEmpty();

        verifyNoInteractions(client);
    }
}
