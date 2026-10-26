package com.energymonitor.notification.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Notification")
class NotificationTest {

    private static final String ID = "ntf0a1b2c3";

    private static final String USER = "use0000001";
    private static final String HOME = "hom0000001";
    private static final String SOURCE = "rst0000001";
    private static final String KEY = "security.password_reset.v1";

    private static Notification pending() {
        return Notification.pending(ID, USER, HOME, NotificationChannel.EMAIL,
                NotificationSourceType.SECURITY, SOURCE, KEY);
    }

    @Nested
    @DisplayName("on creation")
    class OnCreation {

        @Test
        @DisplayName("starts as pending, never as sent")
        void startsAsPending() {
            assertThat(pending().deliveryStatus()).isEqualTo(NotificationStatus.PENDING);
        }

        @Test
        @DisplayName("carries no reason while nothing has failed")
        void carriesNoReason() {
            assertThat(pending().failureReason()).isNull();
        }

        @Test
        @DisplayName("carries no send instant while nothing has been sent")
        void carriesNoSendInstant() {
            assertThat(pending().sentAt()).isNull();
        }

        @Test
        @DisplayName("keeps what it was told")
        void keepsWhatItWasTold() {
            Notification notification = pending();

            assertThat(notification.idNotification()).isEqualTo(ID);
            assertThat(notification.userId()).isEqualTo(USER);
            assertThat(notification.homeId()).isEqualTo(HOME);
            assertThat(notification.channel()).isEqualTo(NotificationChannel.EMAIL);
            assertThat(notification.sourceType()).isEqualTo(NotificationSourceType.SECURITY);
            assertThat(notification.sourceId()).isEqualTo(SOURCE);
            assertThat(notification.messageKey()).isEqualTo(KEY);
        }

        @Test
        @DisplayName("refuses an identifier that does not fit its column")
        void refusesALongIdentifier() {
            assertThatThrownBy(() -> Notification.pending("ntf0a1b2c3d4e5", USER, HOME,
                    NotificationChannel.EMAIL, NotificationSourceType.SECURITY, SOURCE, KEY))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("refuses a recipient that does not fit its column")
        void refusesALongRecipient() {
            assertThatThrownBy(() -> Notification.pending(ID, "a".repeat(11), HOME,
                    NotificationChannel.EMAIL, NotificationSourceType.SECURITY, SOURCE, KEY))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("refuses a blank recipient")
        void refusesABlankRecipient() {
            assertThatThrownBy(() -> Notification.pending(ID, "   ", HOME,
                    NotificationChannel.EMAIL, NotificationSourceType.SECURITY, SOURCE, KEY))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("treats a blank subject as no subject")
        void treatsABlankSubjectAsNone() {
            assertThatThrownBy(() -> Notification.pending(ID, USER, HOME, NotificationChannel.EMAIL,
                    NotificationSourceType.SECURITY, SOURCE, "   "))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("refuses a missing channel")
        void refusesAMissingChannel() {
            assertThatThrownBy(() -> Notification.pending(ID, USER, HOME, null,
                    NotificationSourceType.SECURITY, SOURCE, KEY))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("refuses a missing type")
        void refusesAMissingType() {
            assertThatThrownBy(() -> Notification.pending(ID, USER, HOME, NotificationChannel.EMAIL,
                    null, SOURCE, KEY))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("when it is sent")
    class WhenSent {

        @Test
        @DisplayName("records the instant it left")
        void recordsTheInstant() {
            Notification notification = pending();
            LocalDateTime now = LocalDateTime.of(2026, 3, 4, 10, 15);

            notification.markAsSent(now);

            assertThat(notification.deliveryStatus()).isEqualTo(NotificationStatus.SENT);
            assertThat(notification.sentAt()).isEqualTo(now);
        }

        @Test
        @DisplayName("carries no reason, because it did not fail")
        void carriesNoReason() {
            Notification notification = pending();

            notification.markAsSent(LocalDateTime.now());

            assertThat(notification.failureReason()).isNull();
        }

        @Test
        @DisplayName("clears a previous failure, so a retry that works leaves no stale reason")
        void clearsAPreviousFailure() {
            Notification notification = pending();
            notification.markAsFailed("connection refused");

            notification.markAsSent(LocalDateTime.now());

            assertThat(notification.failureReason()).isNull();
            assertThat(notification.deliveryStatus()).isEqualTo(NotificationStatus.SENT);
        }

        @Test
        @DisplayName("refuses to be sent without a moment to record")
        void refusesAMissingInstant() {
            assertThatThrownBy(() -> pending().markAsSent(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("when it fails")
    class WhenFailed {

        @Test
        @DisplayName("records the reason")
        void recordsTheReason() {
            Notification notification = pending();

            notification.markAsFailed("connection refused");

            assertThat(notification.deliveryStatus()).isEqualTo(NotificationStatus.FAILED);
            assertThat(notification.failureReason()).isEqualTo("connection refused");
        }

        @Test
        @DisplayName("keeps no send instant, because nothing went out")
        void keepsNoSendInstant() {
            Notification notification = pending();

            notification.markAsFailed("connection refused");

            assertThat(notification.sentAt()).isNull();
        }

        @Test
        @DisplayName("refuses a blank reason, which would prove nothing")
        void refusesABlankReason() {
            assertThatThrownBy(() -> pending().markAsFailed("   "))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("cuts an oversized reason rather than rejecting the log write")
        void cutsAnOversizedReason() {
            Notification notification = pending();

            notification.markAsFailed("x".repeat(900));

            assertThat(notification.failureReason()).hasSize(200);
        }
    }

    @Nested
    @DisplayName("identity")
    class Identity {

        @Test
        @DisplayName("is the identifier alone")
        void isTheIdentifierAlone() {
            assertThat(pending()).isEqualTo(pending());
            assertThat(pending()).hasSameHashCodeAs(pending());
        }

        @Test
        @DisplayName("separates two sends to the same recipient")
        void separatesTwoSendsToTheSameRecipient() {
            Notification first = pending();
            Notification second = Notification.pending("ntf0a1b2c4", USER, HOME,
                    NotificationChannel.EMAIL, NotificationSourceType.SECURITY, SOURCE, KEY);

            assertThat(first).isNotEqualTo(second);
        }
    }
}
