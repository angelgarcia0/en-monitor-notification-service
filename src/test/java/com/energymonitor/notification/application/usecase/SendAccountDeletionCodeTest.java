package com.energymonitor.notification.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.energymonitor.notification.domain.model.NotificationSourceType;
import com.energymonitor.notification.infrastructure.mail.EmailSender;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * What the account deletion message says, and what it is recorded as: the record names the
 * wording, never the code.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Account deletion code mail")
class SendAccountDeletionCodeTest {

    /** Measured from now, because the wording states the window the code actually has. */
    private static final Instant VALID_UNTIL = Instant.now().plus(Duration.ofMinutes(20));

    @Mock
    private EmailSender mailer;

    @Captor
    private ArgumentCaptor<EmailSender.Delivery> delivery;

    private SendAccountDeletionCode useCase;

    @BeforeEach
    void setUp() {
        useCase = new SendAccountDeletionCode(mailer);
    }

    @Test
    @DisplayName("says the code deletes the account, and records only the wording it used")
    void saysWhatTheCodeDoes() {
        useCase.openForDelivery("ntf0000001", "use0000001", "ana@gmail.com", "246810", VALID_UNTIL);

        verify(mailer).sendNow(delivery.capture());
        EmailSender.Delivery sent = delivery.getValue();
        assertThat(sent.recipient()).isEqualTo("ana@gmail.com");
        assertThat(sent.subject()).contains("eliminación de tu cuenta");
        assertThat(sent.text()).contains("246810").contains("eliminar tu cuenta")
                .containsPattern("Vence en (19|20) minutos");
        assertThat(sent.html()).contains("246810");
        assertThat(sent.messageKey()).isEqualTo(SendAccountDeletionCode.MESSAGE_KEY)
                .doesNotContain("246810");
        assertThat(sent.sourceType()).isEqualTo(NotificationSourceType.SECURITY);
        assertThat(sent.userId()).isEqualTo("use0000001");
    }
}
