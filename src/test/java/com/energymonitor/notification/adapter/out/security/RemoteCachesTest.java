package com.energymonitor.notification.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.energymonitor.notification.application.port.out.SecurityServiceUnavailableException;
import com.energymonitor.notification.application.port.out.UserProfile;
import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * What the cached adapters ask the security service, and when they ask it again. The clock of the
 * caches is moved by hand.
 */
@DisplayName("Cached security service adapters")
class RemoteCachesTest {

    private static final UserProfile ADA =
            new UserProfile("use0000001", "Ada", "Lovelace", "ada@example.com");

    private final SecurityServiceClient client = mock(SecurityServiceClient.class);
    private final AtomicLong nanos = new AtomicLong();
    private final Ticker ticker = nanos::get;

    private void advance(Duration duration) {
        nanos.addAndGet(duration.toNanos());
    }

    @Nested
    @DisplayName("profiles")
    class Profiles {

        private final RemoteUserProfileQuery query =
                new RemoteUserProfileQuery(client, Duration.ofSeconds(60), ticker);

        @Test
        @DisplayName("asks only for the identifiers not cached, absent ones included")
        void asksOnlyForWhatIsMissing() {
            given(client.profiles(Set.of("use0000001", "use0000404"))).willReturn(Map.of("use0000001", ADA));
            given(client.profiles(Set.of("use0000002"))).willReturn(Map.of());

            assertThat(query.findByIds(List.of("use0000001", "use0000404"))).containsExactly(Map.entry("use0000001", ADA));
            assertThat(query.findByIds(List.of("use0000001", "use0000404", "use0000002")))
                    .containsOnlyKeys("use0000001");

            verify(client).profiles(Set.of("use0000001", "use0000404"));
            verify(client).profiles(Set.of("use0000002"));
            verifyNoMoreInteractions(client);
        }

        @Test
        @DisplayName("asks again once the entry expires")
        void expires() {
            given(client.profiles(Set.of("use0000001"))).willReturn(Map.of("use0000001", ADA));

            query.findByIds(List.of("use0000001"));
            advance(Duration.ofSeconds(61));
            query.findByIds(List.of("use0000001"));

            verify(client, times(2)).profiles(Set.of("use0000001"));
        }

        @Test
        @DisplayName("an unavailable service reaches the caller, and nothing is cached")
        void unavailable() {
            given(client.profiles(Set.of("use0000001")))
                    .willThrow(new SecurityServiceUnavailableException("down", null))
                    .willReturn(Map.of("use0000001", ADA));

            assertThatThrownBy(() -> query.findByIds(List.of("use0000001")))
                    .isInstanceOf(SecurityServiceUnavailableException.class);
            assertThat(query.findByIds(List.of("use0000001"))).containsOnlyKeys("use0000001");
        }
    }

    @Nested
    @DisplayName("permissions")
    class Permissions {

        private final RemotePermissionCheck check =
                new RemotePermissionCheck(client, Duration.ofSeconds(30), ticker);

        @Test
        @DisplayName("one request per account for 30 seconds")
        void cachedPerAccount() {
            given(client.permissions("adm0000001")).willReturn(List.of("USER.READ"));

            assertThat(check.holds("adm0000001", "USER.READ")).isTrue();
            assertThat(check.holds("adm0000001", "ROLE.MANAGE")).isFalse();
            advance(Duration.ofSeconds(29));
            check.holds("adm0000001", "USER.READ");
            verify(client, times(1)).permissions("adm0000001");

            advance(Duration.ofSeconds(2));
            check.holds("adm0000001", "USER.READ");
            verify(client, times(2)).permissions("adm0000001");
        }

        @Test
        @DisplayName("an unavailable service reaches the caller")
        void unavailable() {
            given(client.permissions("adm0000001"))
                    .willThrow(new SecurityServiceUnavailableException("down", null));

            assertThatThrownBy(() -> check.holds("adm0000001", "USER.READ"))
                    .isInstanceOf(SecurityServiceUnavailableException.class);
        }
    }

    @Nested
    @DisplayName("administrators")
    class Administrators {

        private final RemoteAdministratorQuery query =
                new RemoteAdministratorQuery(client, Duration.ofSeconds(30), ticker);

        @Test
        @DisplayName("one request for 30 seconds")
        void cached() {
            given(client.administrators()).willReturn(List.of("adm0000001"));

            assertThat(query.activeAdministratorIds()).containsExactly("adm0000001");
            query.activeAdministratorIds();
            verify(client, times(1)).administrators();

            advance(Duration.ofSeconds(31));
            query.activeAdministratorIds();
            verify(client, times(2)).administrators();
        }
    }
}
