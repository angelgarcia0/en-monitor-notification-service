package com.energymonitor.notification.adapter.out.home;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.energymonitor.notification.adapter.out.home.HomeServiceClient.RemoteHome;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What the cached homes ask the home service, when they ask again, and what they serve while the
 * service does not answer. The clock of the caches is moved by hand.
 */
@DisplayName("Homes answered by the home service")
class RemoteHomesTest {

    private static final RemoteHome HOME =
            new RemoteHome("hom0000001", "Casa", List.of("use0000001", "use0000002"));
    private static final HomeServiceUnavailableException DOWN =
            new HomeServiceUnavailableException("home service did not answer", null);

    private final HomeServiceClient client = mock(HomeServiceClient.class);
    private final AtomicLong nanos = new AtomicLong();
    private final RemoteHomes homes =
            new RemoteHomes(client, Duration.ofSeconds(30), Duration.ofHours(1), nanos::get);

    private void advance(Duration duration) {
        nanos.addAndGet(duration.toNanos());
    }

    @Test
    @DisplayName("the members and the name come from one cached request")
    void oneRequest() {
        given(client.home("hom0000001")).willReturn(Optional.of(HOME));

        assertThat(homes.memberIds("hom0000001")).containsExactly("use0000001", "use0000002");
        assertThat(homes.name("hom0000001")).contains("Casa");

        verify(client, times(1)).home("hom0000001");
    }

    @Test
    @DisplayName("a missing home is cached too, and has no members")
    void missingHomeCached() {
        given(client.home("hom0000099")).willReturn(Optional.empty());

        assertThat(homes.memberIds("hom0000099")).isEmpty();
        assertThat(homes.name("hom0000099")).isEmpty();

        verify(client, times(1)).home("hom0000099");
    }

    @Test
    @DisplayName("asks again once the entry expires")
    void asksAgainAfterTtl() {
        given(client.home("hom0000001")).willReturn(Optional.of(HOME));

        homes.name("hom0000001");
        advance(Duration.ofSeconds(31));
        homes.name("hom0000001");

        verify(client, times(2)).home("hom0000001");
    }

    @Test
    @DisplayName("serves the last home while the service does not answer")
    void staleHomeWhileDown() {
        given(client.home("hom0000001")).willReturn(Optional.of(HOME)).willThrow(DOWN);

        homes.name("hom0000001");
        advance(Duration.ofMinutes(5));

        assertThat(homes.memberIds("hom0000001")).containsExactly("use0000001", "use0000002");
    }

    @Test
    @DisplayName("fails when the service does not answer and nothing was kept")
    void failsWithNothingKept() {
        given(client.home("hom0000001")).willThrow(DOWN);

        assertThatThrownBy(() -> homes.memberIds("hom0000001")).isSameAs(DOWN);
    }

    @Test
    @DisplayName("asks nothing without a home")
    void nothingWithoutAHome() {
        assertThat(homes.memberIds(null)).isEmpty();
        assertThat(homes.name(null)).isEmpty();

        verifyNoInteractions(client);
    }
}
