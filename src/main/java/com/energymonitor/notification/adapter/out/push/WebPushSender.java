package com.energymonitor.notification.adapter.out.push;

import com.energymonitor.notification.application.port.out.PushSenderPort;
import com.energymonitor.notification.domain.model.PushSubscription;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Web Push sender built on the JDK only.
 *
 * <ul>
 *   <li><strong>Payload encryption</strong>: RFC 8291 ({@code aes128gcm}, RFC 8188). A fresh
 *       P-256 key pair and salt per message; the browser's {@code p256dh} and {@code auth} derive
 *       the content key, so only that browser can read it and the push service sees ciphertext.</li>
 *   <li><strong>Server identity</strong>: RFC 8292 (VAPID). An ES256 JWT for the push service's
 *       origin, signed with the private key from {@code notification.push.vapid-private-key}.
 *       Browsers bind each subscription to the public key, so it must not change once users
 *       subscribed.</li>
 * </ul>
 *
 * <p>Keys are base64url: the public one as the 65-byte uncompressed point, the private one as the
 * 32-byte scalar (the format of {@code npx web-push generate-vapid-keys}). Without them push is
 * off: {@link #isConfigured()} is false and nothing is sent.
 */
@Component
public class WebPushSender implements PushSenderPort {

    private static final Logger log = LoggerFactory.getLogger(WebPushSender.class);
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();
    private static final int RECORD_SIZE = 4096;

    private final String publicKey;
    private final ECPrivateKey privateKey;
    private final String subject;
    private final SecureRandom random = new SecureRandom();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ECParameterSpec p256;

    public WebPushSender(@Value("${notification.push.vapid-public-key:}") String publicKey,
                         @Value("${notification.push.vapid-private-key:}") String privateKey,
                         @Value("${notification.push.subject:mailto:support.energymonitor@gmail.com}") String subject)
            throws GeneralSecurityException {
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec("secp256r1"));
        this.p256 = parameters.getParameterSpec(ECParameterSpec.class);
        this.subject = subject;
        if (publicKey.isBlank() || privateKey.isBlank()) {
            this.publicKey = "";
            this.privateKey = null;
            log.info("Web Push disabled: notification.push.vapid-* not configured");
            return;
        }
        this.publicKey = publicKey.trim();
        this.privateKey = (ECPrivateKey) KeyFactory.getInstance("EC")
                .generatePrivate(new ECPrivateKeySpec(new BigInteger(1, B64D.decode(privateKey.trim())), p256));
        // Fails at startup, not at the first alert, when the pair is malformed.
        publicKeyFrom(B64D.decode(this.publicKey));
    }

    @Override
    public boolean isConfigured() {
        return privateKey != null;
    }

    @Override
    public String publicKey() {
        return publicKey;
    }

    @Override
    public Result send(PushSubscription subscription, String jsonPayload) {
        if (!isConfigured()) {
            return new Result(Outcome.FAILED, "push not configured");
        }
        try {
            URI endpoint = URI.create(subscription.endpoint());
            byte[] body = encrypt(jsonPayload.getBytes(StandardCharsets.UTF_8),
                    B64D.decode(subscription.p256dh()), B64D.decode(subscription.auth()));
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Encoding", "aes128gcm")
                    .header("Content-Type", "application/octet-stream")
                    .header("TTL", "86400")
                    .header("Urgency", "high")
                    .header("Authorization", "vapid t=" + vapidToken(endpoint) + ", k=" + publicKey)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                return new Result(Outcome.DELIVERED, null);
            }
            if (status == 404 || status == 410) {
                return new Result(Outcome.GONE, "HTTP " + status);
            }
            return new Result(Outcome.FAILED, "HTTP " + status + " " + abbreviate(response.body()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(Outcome.FAILED, "interrupted");
        } catch (Exception e) {
            return new Result(Outcome.FAILED, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /** RFC 8291 section 3.4 / RFC 8188: one record, padding delimiter 0x02. */
    byte[] encrypt(byte[] plaintext, byte[] uaPublic, byte[] authSecret) throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"), random);
        KeyPair ephemeral = generator.generateKeyPair();
        byte[] asPublic = uncompressed((ECPublicKey) ephemeral.getPublic());

        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(ephemeral.getPrivate());
        agreement.doPhase(publicKeyFrom(uaPublic), true);
        byte[] ecdhSecret = agreement.generateSecret();

        byte[] prkKey = hmac(authSecret, ecdhSecret);
        byte[] keyInfo = concat("WebPush: info".getBytes(StandardCharsets.US_ASCII), new byte[]{0}, uaPublic, asPublic,
                new byte[]{1});
        byte[] ikm = Arrays.copyOf(hmac(prkKey, keyInfo), 32);

        byte[] salt = new byte[16];
        random.nextBytes(salt);
        byte[] prk = hmac(salt, ikm);
        byte[] cek = Arrays.copyOf(hmac(prk, concat("Content-Encoding: aes128gcm".getBytes(StandardCharsets.US_ASCII),
                new byte[]{0, 1})), 16);
        byte[] nonce = Arrays.copyOf(hmac(prk, concat("Content-Encoding: nonce".getBytes(StandardCharsets.US_ASCII),
                new byte[]{0, 1})), 12);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] ciphertext = cipher.doFinal(concat(plaintext, new byte[]{2}));

        return ByteBuffer.allocate(16 + 4 + 1 + asPublic.length + ciphertext.length)
                .put(salt)
                .putInt(RECORD_SIZE)
                .put((byte) asPublic.length)
                .put(asPublic)
                .put(ciphertext)
                .array();
    }

    /** RFC 8292: ES256 JWT, raw R||S signature, audience = origin of the push service. */
    private String vapidToken(URI endpoint) throws GeneralSecurityException {
        String audience = endpoint.getScheme() + "://" + endpoint.getHost()
                + (endpoint.getPort() > 0 ? ":" + endpoint.getPort() : "");
        String header = B64.encodeToString("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
        String claims = B64.encodeToString(("{\"aud\":\"" + audience + "\",\"exp\":"
                + Instant.now().plus(Duration.ofHours(12)).getEpochSecond() + ",\"sub\":\"" + subject + "\"}")
                .getBytes(StandardCharsets.UTF_8));
        Signature signer = Signature.getInstance("SHA256withECDSAinP1363Format");
        signer.initSign(privateKey);
        signer.update((header + "." + claims).getBytes(StandardCharsets.US_ASCII));
        return header + "." + claims + "." + B64.encodeToString(signer.sign());
    }

    private ECPublicKey publicKeyFrom(byte[] uncompressed) throws GeneralSecurityException {
        if (uncompressed.length != 65 || uncompressed[0] != 4) {
            throw new GeneralSecurityException("expected a 65-byte uncompressed P-256 point");
        }
        ECPoint point = new ECPoint(new BigInteger(1, Arrays.copyOfRange(uncompressed, 1, 33)),
                new BigInteger(1, Arrays.copyOfRange(uncompressed, 33, 65)));
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(point, p256));
    }

    private static byte[] uncompressed(ECPublicKey key) {
        byte[] out = new byte[65];
        out[0] = 4;
        copyFixed(key.getW().getAffineX(), out, 1);
        copyFixed(key.getW().getAffineY(), out, 33);
        return out;
    }

    private static void copyFixed(BigInteger value, byte[] out, int offset) {
        byte[] bytes = value.toByteArray();
        int start = Math.max(0, bytes.length - 32);
        int length = bytes.length - start;
        System.arraycopy(bytes, start, out, offset + 32 - length, length);
    }

    private static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static byte[] concat(byte[]... parts) {
        int length = 0;
        for (byte[] part : parts) {
            length += part.length;
        }
        ByteBuffer buffer = ByteBuffer.allocate(length);
        for (byte[] part : parts) {
            buffer.put(part);
        }
        return buffer.array();
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 120 ? text.substring(0, 120) : text;
    }
}
