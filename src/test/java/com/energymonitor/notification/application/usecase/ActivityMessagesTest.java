package com.energymonitor.notification.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Activity wording")
class ActivityMessagesTest {

    private static final Map<String, String> NAMES = Map.of(
            ActivityMessages.HOME_NAME, "Casa",
            ActivityMessages.ACTOR_NAME, "Ana",
            ActivityMessages.SUBJECT_NAME, "Luis",
            ActivityMessages.DEVICE_NAME, "nevera");

    @ParameterizedTest
    @ValueSource(strings = {
        ActivityMessages.WELCOME, ActivityMessages.HOME_CREATED, ActivityMessages.HOME_JOINED,
        ActivityMessages.MEMBER_JOINED, ActivityMessages.THRESHOLDS_UPDATED,
        ActivityMessages.REMOVED_FROM_HOME, ActivityMessages.MEMBER_REMOVED,
        ActivityMessages.MEMBER_LEFT, ActivityMessages.OWNERSHIP_RECEIVED,
        ActivityMessages.OWNERSHIP_GIVEN, ActivityMessages.OWNER_CHANGED,
        ActivityMessages.HOME_DELETED_BY_YOU, ActivityMessages.HOME_DELETED,
        ActivityMessages.DEVICE_LINKED, ActivityMessages.DEVICE_UNLINKED
    })
    @DisplayName("every key has its own title, not the generic one")
    void everyKeyHasItsOwnWording(String key) {
        AlertMessages.Message message = ActivityMessages.render(key, NAMES);

        assertThat(message.title()).isNotEqualTo("Novedades en Energy Monitor").isNotBlank();
        assertThat(message.body()).isNotBlank().doesNotContain("null");
    }

    @Test
    @DisplayName("names the people, the home and the device it is about")
    void namesWhatItIsAbout() {
        assertThat(ActivityMessages.render(ActivityMessages.MEMBER_JOINED, NAMES).body())
                .isEqualTo("Ana se unió a «Casa».");
        assertThat(ActivityMessages.render(ActivityMessages.MEMBER_REMOVED, NAMES).body())
                .isEqualTo("Luis ya no es miembro de «Casa».");
        assertThat(ActivityMessages.render(ActivityMessages.DEVICE_LINKED, NAMES).body())
                .isEqualTo("Ana vinculó «nevera» a «Casa».");
    }

    @Test
    @DisplayName("a name nobody knows leaves no gap in the text")
    void missingNames() {
        assertThat(ActivityMessages.render(ActivityMessages.MEMBER_JOINED, Map.of()).body())
                .isEqualTo("Alguien se unió a tu hogar.");
        assertThat(ActivityMessages.render(ActivityMessages.DEVICE_LINKED, Map.of()).body())
                .isEqualTo("Un dispositivo se vinculó a tu hogar.");
    }

    @Test
    @DisplayName("an unknown key still says something")
    void unknownKey() {
        assertThat(ActivityMessages.render("activity.home.something-new.v1", NAMES).title())
                .isEqualTo("Novedades en Energy Monitor");
    }
}
