package com.energymonitor.notification.adapter.out.security;

import com.energymonitor.notification.application.port.out.SecurityServiceUnavailableException;
import com.energymonitor.notification.application.port.out.UserProfile;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Calls the internal API of the security service: the permissions of an account, the profiles of
 * a list of accounts in one request, and the administrators. Every call carries the shared token
 * in {@code X-Internal-Token}.
 *
 * <p>No answer within the timeouts, or an error, is a {@link SecurityServiceUnavailableException};
 * nothing is retried here, so a request waiting on it fails fast.
 */
public class SecurityServiceClient {

    static final String TOKEN_HEADER = "X-Internal-Token";

    private final RestClient security;

    /**
     * @param security client with the service's base URL, the timeouts and the token
     */
    public SecurityServiceClient(RestClient security) {
        this.security = security;
    }

    /**
     * @param baseUrl        where the security service answers, such as {@code http://security:8082}
     * @param token          the shared internal token
     * @param connectTimeout how long to wait for the connection
     * @param readTimeout    how long to wait for the answer
     * @return a client for that service
     */
    public static SecurityServiceClient create(String baseUrl, String token,
                                               Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(connectTimeout);
        requests.setReadTimeout(readTimeout);
        return new SecurityServiceClient(RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requests)
                .defaultHeader(TOKEN_HEADER, token)
                .build());
    }

    /**
     * @param userId the account
     * @return its permission codes; empty when no active account has that identifier
     */
    public List<String> permissions(String userId) {
        try {
            Permissions body = security.get()
                    .uri("/internal/users/{userId}/permissions", userId)
                    .retrieve()
                    .body(Permissions.class);
            return body == null || body.permissions() == null ? List.of() : body.permissions();
        } catch (HttpClientErrorException.NotFound gone) {
            return List.of();
        } catch (RestClientException failure) {
            throw unavailable("permissions", failure);
        }
    }

    /**
     * @param userIds the accounts to look up
     * @return the profile of every active account among them, by identifier; the others are absent
     */
    public Map<String, UserProfile> profiles(Collection<String> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        try {
            Profiles body = security.post()
                    .uri("/internal/users/profiles")
                    .body(new ProfilesRequest(List.copyOf(userIds)))
                    .retrieve()
                    .body(Profiles.class);
            return Optional.ofNullable(body).map(Profiles::profiles).orElse(List.of()).stream()
                    .collect(Collectors.toMap(UserProfile::userId, Function.identity(),
                            (first, second) -> first));
        } catch (RestClientException failure) {
            throw unavailable("profiles", failure);
        }
    }

    /**
     * @return the accounts holding the {@code ADMIN} role that can sign in
     */
    public List<String> administrators() {
        try {
            Administrators body = security.get()
                    .uri("/internal/administrators")
                    .retrieve()
                    .body(Administrators.class);
            return body == null || body.userIds() == null ? List.of() : body.userIds();
        } catch (RestClientException failure) {
            throw unavailable("administrators", failure);
        }
    }

    private static SecurityServiceUnavailableException unavailable(String what,
                                                                   RestClientException failure) {
        String status = failure instanceof RestClientResponseException response
                ? " (" + response.getStatusCode().value() + ")" : "";
        return new SecurityServiceUnavailableException(
                "security service did not answer the " + what + " request" + status, failure);
    }

    record Permissions(List<String> permissions) {
    }

    record ProfilesRequest(List<String> userIds) {
    }

    record Profiles(List<UserProfile> profiles) {
    }

    record Administrators(List<String> userIds) {
    }
}
