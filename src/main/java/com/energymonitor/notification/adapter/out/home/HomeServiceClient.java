package com.energymonitor.notification.adapter.out.home;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Calls the internal API of the home service for a home with its members. Every call carries the
 * shared token in {@code X-Internal-Token}.
 *
 * <p>No answer within the timeouts, or an error, is a {@link HomeServiceUnavailableException};
 * nothing is retried here, so a caller waiting on it fails fast.
 */
public class HomeServiceClient {

    static final String TOKEN_HEADER = "X-Internal-Token";

    private final RestClient homes;

    /**
     * @param homes client with the service's base URL, the timeouts and the token
     */
    public HomeServiceClient(RestClient homes) {
        this.homes = homes;
    }

    /**
     * @param baseUrl        where the home service answers, such as {@code http://home:8086}
     * @param token          the shared internal token
     * @param connectTimeout how long to wait for the connection
     * @param readTimeout    how long to wait for the answer
     * @return a client for that service
     */
    public static HomeServiceClient create(String baseUrl, String token,
                                           Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(connectTimeout);
        requests.setReadTimeout(readTimeout);
        return new HomeServiceClient(RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requests)
                .defaultHeader(TOKEN_HEADER, token)
                .build());
    }

    /**
     * {@code GET /internal/homes/{homeId}}.
     *
     * @param homeId the home
     * @return the home with its members; empty when the service answers 404
     */
    public Optional<RemoteHome> home(String homeId) {
        try {
            return Optional.ofNullable(homes.get()
                    .uri("/internal/homes/{homeId}", homeId)
                    .retrieve()
                    .body(RemoteHome.class));
        } catch (HttpClientErrorException.NotFound unknown) {
            return Optional.empty();
        } catch (RestClientException failure) {
            String status = failure instanceof RestClientResponseException response
                    ? " (" + response.getStatusCode().value() + ")" : "";
            throw new HomeServiceUnavailableException(
                    "home service did not answer the home request" + status, failure);
        }
    }

    /**
     * A home as the home service describes it, with every active member, owners included. The
     * other fields of the answer are not read here.
     */
    public record RemoteHome(String homeId, String name, List<String> memberIds) {
    }
}
