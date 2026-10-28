package com.energymonitor.notification.adapter.out.directory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.energymonitor.notification.adapter.out.device.DeviceServiceUnavailableException;
import com.energymonitor.notification.adapter.out.device.RemoteDevices;
import com.energymonitor.notification.adapter.out.home.HomeServiceUnavailableException;
import com.energymonitor.notification.adapter.out.home.RemoteHomes;
import com.energymonitor.notification.application.port.out.HomeDirectoryPort.Recipient;
import com.energymonitor.notification.application.port.out.SecurityServiceUnavailableException;
import com.energymonitor.notification.application.port.out.UserProfile;
import com.energymonitor.notification.application.port.out.UserProfileQuery;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Home directory")
class HomeDirectoryAdapterTest {

    private final RemoteHomes homes = mock(RemoteHomes.class);
    private final RemoteDevices devices = mock(RemoteDevices.class);
    private final UserProfileQuery profiles = mock(UserProfileQuery.class);
    private final HomeDirectoryAdapter directory = new HomeDirectoryAdapter(homes, devices, profiles);

    @Test
    @DisplayName("the members and the name of a home come from the home service")
    void home() {
        given(homes.memberIds("hom0000001")).willReturn(List.of("use0000001"));
        given(homes.name("hom0000001")).willReturn(Optional.of("Casa"));

        assertThat(directory.memberIds("hom0000001")).containsExactly("use0000001");
        assertThat(directory.homeName("hom0000001")).contains("Casa");
    }

    @Test
    @DisplayName("a home service that does not answer fails the call, so the message is retried")
    void homeServiceDown() {
        given(homes.memberIds("hom0000001"))
                .willThrow(new HomeServiceUnavailableException("down", null));

        assertThatThrownBy(() -> directory.memberIds("hom0000001"))
                .isInstanceOf(HomeServiceUnavailableException.class);
    }

    @Test
    @DisplayName("the recipients are the profiles of the security service")
    void recipients() {
        given(profiles.findByIds(List.of("use0000001"))).willReturn(Map.of("use0000001",
                new UserProfile("use0000001", "Ada", "Lovelace", "ada@example.com")));

        assertThat(directory.recipients(List.of("use0000001"))).containsExactly(
                Map.entry("use0000001", new Recipient("use0000001", "Ada", "ada@example.com")));
    }

    @Test
    @DisplayName("no recipient while the security service does not answer")
    void securityServiceDown() {
        given(profiles.findByIds(List.of("use0000001")))
                .willThrow(new SecurityServiceUnavailableException("down", null));

        assertThat(directory.recipients(List.of("use0000001"))).isEmpty();
    }

    @Test
    @DisplayName("no device name while the device service does not answer")
    void deviceServiceDown() {
        given(devices.name("dev0000001")).willReturn(Optional.of("Nevera"));
        given(devices.name("dev0000002"))
                .willThrow(new DeviceServiceUnavailableException("down", null));

        assertThat(directory.deviceName("dev0000001")).contains("Nevera");
        assertThat(directory.deviceName("dev0000002")).isEmpty();
    }
}
