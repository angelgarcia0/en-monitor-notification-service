package com.energymonitor.notification.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

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
 * What the recovery message says, and what it is recorded as.
 *
 * <p>The two matter together: the record has to say enough to identify the message without saying
 * what it said, or the delivery log becomes a place secrets live.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Recovery mail")
class SendPasswordResetEmailTest {

    private static final String ID = "ntf0000001";
    private static final String USER = "use0000001";
    private static final String SOURCE = "rst0000001";
    private static final String RECIPIENT = "ada@example.com";
    /** Measured from now, because the wording states the window the code actually has. */
    private static final Instant VALID_UNTIL = Instant.now().plus(Duration.ofMinutes(30));

    @Mock
    private EmailSender mailer;

    @Captor
    private ArgumentCaptor<EmailSender.Delivery> delivery;

    private SendPasswordResetEmail useCase;

    @BeforeEach
    void setUp() {
        useCase = new SendPasswordResetEmail(mailer);
    }

    private EmailSender.Delivery send(String firstName) {
        useCase.send(ID, USER, null, SOURCE, RECIPIENT, firstName, "795024", VALID_UNTIL);
        verify(mailer).send(delivery.capture());
        return delivery.getValue();
    }

    @Test
    @DisplayName("records the account and the token it is about")
    void recordsWhereItCameFrom() {
        EmailSender.Delivery sent = send("Ada");

        assertThat(sent.idNotification()).isEqualTo(ID);
        assertThat(sent.userId()).isEqualTo(USER);
        assertThat(sent.sourceType()).isEqualTo(NotificationSourceType.SECURITY);
        assertThat(sent.sourceId()).isEqualTo(SOURCE);
        assertThat(sent.messageKey()).isEqualTo(SendPasswordResetEmail.MESSAGE_KEY);
        assertThat(sent.recipient()).isEqualTo(RECIPIENT);
    }

    @Test
    @DisplayName("leaves the home absent for a message that concerns only the account")
    void leavesTheHomeAbsent() {
        assertThat(send("Ada").homeId()).isNull();
    }

    @Test
    @DisplayName("names the wording rather than carrying it as the message key")
    void namesTheWording() {
        EmailSender.Delivery sent = send("Ada");

        assertThat(sent.messageKey()).doesNotContain("795024");
        assertThat(sent.text()).contains("795024");
    }

    @Test
    @DisplayName("greets the recipient by the name the account gave")
    void greetsByName() {
        assertThat(send("Ada").text()).startsWith("Hola Ada,");
    }

    @Test
    @DisplayName("puts the code in the body and not in the subject")
    void keepsTheCodeOutOfTheSubject() {
        EmailSender.Delivery sent = send("Ada");

        assertThat(sent.text()).contains("795024");
        assertThat(sent.subject()).doesNotContain("795024");
        assertThat(sent.subject()).isEqualTo("Recupera tu contraseña");
    }

    @Test
    @DisplayName("escapes a name that carries markup before it reaches the HTML body")
    void escapesTheName() {
        // Unescaped it would let an account put markup into somebody else's mail client.
        assertThat(send("<script>alert(1)</script>").html())
                .contains("&lt;script&gt;")
                .doesNotContain("<script>alert(1)</script>");
    }

    @Test
    @DisplayName("omits the greeting when no name was supplied")
    void omitsTheGreetingWithoutAName() {
        EmailSender.Delivery sent = send(null);

        assertThat(sent.text()).doesNotContain("Hola").startsWith("Tu código");
        assertThat(sent.html()).doesNotContain("Hola");
    }

    @Test
    @DisplayName("treats a blank name as no name")
    void treatsABlankNameAsNoName() {
        EmailSender.Delivery sent = send("   ");

        assertThat(sent.text()).doesNotContain("Hola").startsWith("Tu código");
    }

    @Test
    @DisplayName("states the window the code is good for")
    void statesTheWindow() {
        assertThat(send("Ada").text()).contains("30 minutos");
    }

    @Test
    @DisplayName("rounds the window up rather than promising less than it has")
    void roundsTheWindowUp() {
        // Twenty-nine and a half minutes of validity must not be described as twenty-nine.
        Instant justOverTwentyNine = Instant.now().plus(Duration.ofMinutes(29)).plusSeconds(40);
        useCase.send(ID, USER, null, SOURCE, RECIPIENT, "Ada", "795024", justOverTwentyNine);

        verify(mailer).send(delivery.capture());
        assertThat(delivery.getValue().text()).contains("30 minutos");
    }

    @Test
    @DisplayName("says a delivered code may only be used once")
    void saysItIsSingleUse() {
        assertThat(send("Ada").text()).contains("una vez");
    }
}