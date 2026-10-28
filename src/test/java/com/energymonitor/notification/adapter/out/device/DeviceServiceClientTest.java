package com.energymonitor.notification.adapter.out.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.energymonitor.notification.adapter.out.device.DeviceServiceClient.RemoteDevice;
import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@DisplayName("Device service client")
class DeviceServiceClientTest {

    /** The whole answer of the device service: the fields not read here are ignored. */
    private static final String DEVICE = "{\"idDevice\":\"dev0000001\",\"name\":\"Nevera\","
            + "\"applianceTypeId\":\"app0000001\",\"location\":\"Cocina\",\"description\":null,"
            + "\"installationDate\":\"2026-01-15T10:30:00Z\",\"deviceCode\":\"EM-0001\","
            + "\"homeId\":\"hom0000001\"}";

    private final RestClient.Builder builder = RestClient.builder()
            .baseUrl("http://device")
            .defaultHeader(DeviceServiceClient.TOKEN_HEADER, "secret");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final DeviceServiceClient client = new DeviceServiceClient(builder.build());

    @Test
    @DisplayName("reads a device, sending the internal token")
    void device() {
        server.expect(requestTo("http://device/internal/devices/dev0000001"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(DeviceServiceClient.TOKEN_HEADER, "secret"))
                .andRespond(withSuccess(DEVICE, MediaType.APPLICATION_JSON));

        assertThat(client.device("dev0000001")).contains(new RemoteDevice("dev0000001", "Nevera"));
        server.verify();
    }

    @Test
    @DisplayName("a missing device is an answer, not a failure")
    void missingDevice() {
        server.expect(requestTo("http://device/internal/devices/dev0000099"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(client.device("dev0000099")).isEmpty();
    }

    @Test
    @DisplayName("an error is a device service that is unavailable")
    void serverError() {
        server.expect(requestTo("http://device/internal/devices/dev0000001"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.device("dev0000001"))
                .isInstanceOf(DeviceServiceUnavailableException.class)
                .hasMessageContaining("(500)");
    }

    @Test
    @DisplayName("no answer is a device service that is unavailable")
    void unreachable() {
        server.expect(requestTo("http://device/internal/devices/dev0000001"))
                .andRespond(withException(new IOException("connection refused")));

        assertThatThrownBy(() -> client.device("dev0000001"))
                .isInstanceOf(DeviceServiceUnavailableException.class);
    }
}
