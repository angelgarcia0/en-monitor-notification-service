package com.energymonitor.notification.adapter.out.push;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.energymonitor.notification.application.port.out.RelayPushSenderPort;
import com.energymonitor.notification.application.port.out.RelayPushSenderPort.Outcome;
import org.junit.jupiter.api.Test;

/**
 * How {@link ExpoPushSender} reads the relay's answer, against the shapes it documents.
 *
 * <p>No network: only the interpretation is exercised. What the relay does with the request is the
 * relay's business, and a test that depends on it would be a test of the internet.
 */
class ExpoPushSenderTest {

    private final ExpoPushSender sender = new ExpoPushSender("token");

    @Test
    void withoutACredentialNothingIsSent() {
        ExpoPushSender off = new ExpoPushSender("");

        assertFalse(off.isConfigured());
        RelayPushSenderPort.Result result = off.send("ExponentPushToken[x]", "t", "b", "/notifications");
        assertEquals(Outcome.FAILED, result.outcome());
        assertEquals("push not configured", result.detail());
    }

    @Test
    void aBlankCredentialIsTreatedAsNone() {
        assertFalse(new ExpoPushSender("   ").isConfigured());
        assertFalse(new ExpoPushSender(null).isConfigured());
        assertTrue(sender.isConfigured());
    }

    @Test
    void anAcceptedMessageIsDelivered() {
        RelayPushSenderPort.Result result =
                sender.interpret(200, "{\"data\":{\"status\":\"ok\",\"id\":\"receipt-1\"}}");

        assertEquals(Outcome.DELIVERED, result.outcome());
    }

    /** The relay answers one ticket per message, so a batch is read the same way. */
    @Test
    void anAcceptedBatchIsDelivered() {
        RelayPushSenderPort.Result result = sender.interpret(200,
                "{\"data\":[{\"status\":\"ok\",\"id\":\"a\"},{\"status\":\"ok\",\"id\":\"b\"}]}");

        assertEquals(Outcome.DELIVERED, result.outcome());
    }

    /** The one case that justifies deleting the row. */
    @Test
    void anUnregisteredInstallationIsGone() {
        RelayPushSenderPort.Result result = sender.interpret(200,
                "{\"data\":[{\"status\":\"error\",\"message\":\"is not a registered push notification "
                        + "recipient\",\"details\":{\"error\":\"DeviceNotRegistered\"}}]}");

        assertEquals(Outcome.GONE, result.outcome());
        assertEquals("DeviceNotRegistered", result.detail());
    }

    /**
     * A rate limit or an oversized payload says something about the message, not about the phone.
     * Reporting those as GONE would stop writing to a perfectly working installation.
     */
    @Test
    void aProblemWithTheMessageIsAFailureNotAGone() {
        for (String error : new String[]{"MessageTooBig", "MessageRateExceeded"}) {
            RelayPushSenderPort.Result result = sender.interpret(200,
                    "{\"data\":[{\"status\":\"error\",\"details\":{\"error\":\"" + error + "\"}}]}");

            assertEquals(Outcome.FAILED, result.outcome(), error);
            assertTrue(result.detail().contains(error), error);
        }
    }

    @Test
    void aRejectedCredentialIsAFailure() {
        assertEquals(Outcome.FAILED, sender.interpret(401, "{\"errors\":[]}").outcome());
        assertEquals(Outcome.FAILED, sender.interpret(403, "{\"errors\":[]}").outcome());
    }

    @Test
    void aServerSideProblemIsAFailure() {
        RelayPushSenderPort.Result result = sender.interpret(500, "upstream unavailable");

        assertEquals(Outcome.FAILED, result.outcome());
        assertTrue(result.detail().contains("500"));
    }

    /** An error without a recognised code is still not a reason to forget the installation. */
    @Test
    void anUnrecognisedErrorIsAFailure() {
        RelayPushSenderPort.Result result = sender.interpret(200,
                "{\"data\":[{\"status\":\"error\",\"details\":{\"error\":\"SomethingElse\"}}]}");

        assertEquals(Outcome.FAILED, result.outcome());
    }
}