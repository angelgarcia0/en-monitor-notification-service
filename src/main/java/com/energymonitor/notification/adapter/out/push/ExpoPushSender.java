package com.energymonitor.notification.adapter.out.push;

import com.energymonitor.notification.application.port.out.RelayPushSenderPort;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Push sender for app installations, built on the JDK only, like {@link WebPushSender}.
 *
 * <p>Unlike web push, nothing here is encrypted: the relay routes the message on and reads it. That
 * is why this sends the notification title and body as text, and why the token is treated as an
 * address rather than a credential.
 *
 * <p>Without a credential nothing is sent: {@link #isConfigured()} is false and alerts fall back to
 * mail, which is the same degradation web push already has when no VAPID key is configured.
 */
@Component
public class ExpoPushSender implements RelayPushSenderPort {

    private static final Logger log = LoggerFactory.getLogger(ExpoPushSender.class);
    private static final String SEND_URL = "https://exp.host/--/api/v2/push/send";
    private static final Pattern ERROR_STATUS =
            Pattern.compile("\"status\"\\s*:\\s*\"([A-Za-z]+)\"");
    private static final Pattern DETAIL = Pattern.compile("\"details\"\\s*:\\s*\\{([^}]*)}");
    private static final Pattern DETAIL_ERROR = Pattern.compile("\"error\"\\s*:\\s*\"([^\"]+)\"");

    /**
     * An alert the user never sees is not an alert, so the message is sent with high priority: on
     * Android a normal-priority message is not even delivered to a sleeping device until it wakes
     * on its own, and a fridge that stopped reporting would be news hours late.
     */
    private static final String PRIORITY = "high";

    /**
     * The channel the alert is shown on.
     *
     * <p>Not cosmetic: if the channel does not exist on the device the notification is not displayed
     * at all, so the app has to create this exact id before it ever asks for a push token.
     */
    private static final String CHANNEL_ID = "alerts";

    private final String accessToken;
    private final HttpClient http =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public ExpoPushSender(@Value("${notification.push.expo-access-token:}") String accessToken) {
        this.accessToken = accessToken == null ? "" : accessToken.trim();
        if (this.accessToken.isEmpty()) {
            log.info("App push disabled: notification.push.expo-access-token not configured");
        }
    }

    @Override
    public boolean isConfigured() {
        return !accessToken.isEmpty();
    }

    @Override
    public Result send(String token, String title, String body, String path) {
        if (!isConfigured()) {
            return new Result(Outcome.FAILED, "push not configured");
        }
        try {
            // sound is ignored on Android, where the channel decides it, and read on iOS.
            String payload = "{\"to\":" + quote(token)
                    + ",\"title\":" + quote(title)
                    + ",\"body\":" + quote(body)
                    + ",\"sound\":\"default\""
                    + ",\"priority\":" + quote(PRIORITY)
                    + ",\"channelId\":" + quote(CHANNEL_ID)
                    + ",\"data\":" + quote("{\"path\":" + quote(path == null ? "" : path) + "}") + "}";
            HttpRequest request = HttpRequest.newBuilder(URI.create(SEND_URL))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + accessToken)
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            return interpret(response.statusCode(), response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(Outcome.FAILED, "interrupted");
        } catch (Exception e) {
            return new Result(Outcome.FAILED, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * A 2xx still does not mean delivered: the relay answers per message, and a token it no longer
     * knows comes back as an error inside a successful response. Treating that as delivered would
     * keep pushing to an installation that will never read it.
     *
     * <p>Package-private so it can be read without a network, the way {@code WebPushSender#encrypt}
     * is exposed for its own test.
     */
    Result interpret(int status, String body) {
        String text = body == null ? "" : body;
        if (status == 401 || status == 403) {
            return new Result(Outcome.FAILED, "relay rejected the credential, HTTP " + status);
        }
        if (status < 200 || status >= 300) {
            return new Result(Outcome.FAILED, "HTTP " + status + " " + abbreviate(text));
        }

        Matcher statusMatcher = ERROR_STATUS.matcher(text);
        if (!statusMatcher.find() || "ok".equalsIgnoreCase(statusMatcher.group(1))) {
            return new Result(Outcome.DELIVERED, null);
        }

        String detail = firstGroup(DETAIL, text);
        String error = firstGroup(DETAIL_ERROR, detail);
        // Only DeviceNotRegistered means the installation is gone: the app was uninstalled, or its
        // token was rotated. The other codes a ticket can carry describe the message, not the
        // recipient, and dropping the row on those would silence a working phone because of a
        // transient condition. (MessageTooBig and MessageRateExceeded show up on receipts, which
        // are not read here, and would be failures anyway.)
        if (error != null && "DeviceNotRegistered".equalsIgnoreCase(error)) {
            return new Result(Outcome.GONE, error);
        }
        return new Result(Outcome.FAILED, (error == null ? "no error code" : error) + ": " + detail);
    }

    private static String firstGroup(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        String raw = matcher.group(1).trim();
        return raw.isEmpty() ? null : raw;
    }

    /** Minimal JSON string escape: the payload values are ours, but the relay still parses them. */
    private static String quote(String value) {
        StringBuilder out = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 200 ? text.substring(0, 200) : text;
    }
}