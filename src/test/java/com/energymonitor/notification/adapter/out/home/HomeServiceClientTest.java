package com.energymonitor.notification.adapter.out.home;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.energymonitor.notification.adapter.out.home.HomeServiceClient.RemoteHome;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@DisplayName("Home service client")
class HomeServiceClientTest {

    /** The whole answer of the home service: the fields not read here are ignored. */
    private static final String HOME = "{\"homeId\":\"hom0000001\",\"name\":\"Casa\","
            + "\"homeTypeId\":\"hous000001\",\"address\":\"Calle 1\","
            + "\"creationDate\":\"2026-01-15T10:30:00Z\",\"memberIds\":[\"use0000001\",\"use0000002\"]}";

    private final RestClient.Builder builder = RestClient.builder()
            .baseUrl("http://home")
            .defaultHeader(HomeServiceClient.TOKEN_HEADER, "secret");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final HomeServiceClient client = new HomeServiceClient(builder.build());

    @Test
    @DisplayName("reads a home with its members, sending the internal token")
    void home() {
        server.expect(requestTo("http://home/internal/homes/hom0000001"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HomeServiceClient.TOKEN_HEADER, "secret"))
                .andRespond(withSuccess(HOME, MediaType.APPLICATION_JSON));

        assertThat(client.home("hom0000001")).contains(
                new RemoteHome("hom0000001", "Casa", List.of("use0000001", "use0000002")));
        server.verify();
    }

    @Test
    @DisplayName("a missing home is an answer, not a failure")
    void missingHome() {
        server.expect(requestTo("http://home/internal/homes/hom0000099"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(client.home("hom0000099")).isEmpty();
    }

    @Test
    @DisplayName("an error is a home service that is unavailable")
    void serverError() {
        server.expect(requestTo("http://home/internal/homes/hom0000001"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.home("hom0000001"))
                .isInstanceOf(HomeServiceUnavailableException.class)
                .hasMessageContaining("(500)");
    }

    @Test
    @DisplayName("no answer is a home service that is unavailable")
    void unreachable() {
        server.expect(requestTo("http://home/internal/homes/hom0000001"))
                .andRespond(withException(new IOException("connection refused")));

        assertThatThrownBy(() -> client.home("hom0000001"))
                .isInstanceOf(HomeServiceUnavailableException.class);
    }
}
