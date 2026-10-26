package com.energymonitor.notification.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.energymonitor.notification.application.port.out.SecurityServiceUnavailableException;
import com.energymonitor.notification.application.port.out.UserProfile;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@DisplayName("Security service client")
class SecurityServiceClientTest {

    private final RestClient.Builder builder = RestClient.builder()
            .baseUrl("http://security")
            .defaultHeader(SecurityServiceClient.TOKEN_HEADER, "secret");
    private final MockRestServiceServer security = MockRestServiceServer.bindTo(builder).build();
    private final SecurityServiceClient client = new SecurityServiceClient(builder.build());

    @Test
    @DisplayName("reads the permission codes of an account, with the internal token")
    void permissions() {
        security.expect(requestTo("http://security/internal/users/use0000001/permissions"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Token", "secret"))
                .andRespond(withSuccess("{\"permissions\":[\"USER.READ\"]}", MediaType.APPLICATION_JSON));

        assertThat(client.permissions("use0000001")).containsExactly("USER.READ");
        security.verify();
    }

    @Test
    @DisplayName("an unknown account holds no permission")
    void unknownAccount() {
        security.expect(requestTo("http://security/internal/users/use0000404/permissions"))
                .andRespond(withResourceNotFound());

        assertThat(client.permissions("use0000404")).isEmpty();
    }

    @Test
    @DisplayName("asks for all the profiles in one request")
    void profiles() {
        security.expect(requestTo("http://security/internal/users/profiles"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"userIds\":[\"use0000001\",\"use0000002\"]}"))
                .andRespond(withSuccess("{\"profiles\":[{\"userId\":\"use0000001\",\"name\":\"Ada\","
                        + "\"lastName\":\"Lovelace\",\"email\":\"ada@example.com\"}]}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.profiles(List.of("use0000001", "use0000002")))
                .containsOnlyKeys("use0000001")
                .containsValue(new UserProfile("use0000001", "Ada", "Lovelace", "ada@example.com"));
    }

    @Test
    @DisplayName("no identifiers, no request")
    void noProfiles() {
        assertThat(client.profiles(List.of())).isEmpty();
        security.verify();
    }

    @Test
    @DisplayName("reads the administrators")
    void administrators() {
        security.expect(requestTo("http://security/internal/administrators"))
                .andRespond(withSuccess("{\"userIds\":[\"adm0000001\"]}", MediaType.APPLICATION_JSON));

        assertThat(client.administrators()).containsExactly("adm0000001");
    }

    @Test
    @DisplayName("a server error is unavailability")
    void serverError() {
        security.expect(requestTo("http://security/internal/administrators")).andRespond(withServerError());

        assertThatThrownBy(client::administrators)
                .isInstanceOf(SecurityServiceUnavailableException.class)
                .hasMessageContaining("500");
    }

    @Test
    @DisplayName("an unreachable service is unavailability")
    void unreachable() {
        security.expect(requestTo("http://security/internal/users/profiles"))
                .andRespond(withException(new IOException("refused")));

        assertThatThrownBy(() -> client.profiles(List.of("use0000001")))
                .isInstanceOf(SecurityServiceUnavailableException.class);
    }
}
