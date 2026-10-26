package com.energymonitor.notification.adapter.out.push;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/**
 * Decrypts what {@link WebPushSender} produces the way a browser does (RFC 8291 as the user agent).
 */
class WebPushSenderTest {

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    @Test
    void aBrowserCanDecryptThePayload() throws Exception {
        KeyPair browser = p256();
        byte[] uaPublic = uncompressed((ECPublicKey) browser.getPublic());
        byte[] auth = new byte[16];
        new java.security.SecureRandom().nextBytes(auth);

        KeyPair vapid = p256();
        WebPushSender sender = new WebPushSender(B64.encodeToString(uncompressed((ECPublicKey) vapid.getPublic())),
                B64.encodeToString(fixed(((ECPrivateKey) vapid.getPrivate()).getS())), "mailto:test@example.com");
        assertTrue(sender.isConfigured());

        String message = "{\"title\":\"Dispositivo desconectado\",\"body\":\"ñandú ⚡\"}";
        byte[] body = sender.encrypt(message.getBytes(StandardCharsets.UTF_8), uaPublic, auth);

        ByteBuffer in = ByteBuffer.wrap(body);
        byte[] salt = new byte[16];
        in.get(salt);
        assertEquals(4096, in.getInt());
        byte[] asPublic = new byte[in.get()];
        in.get(asPublic);
        byte[] ciphertext = new byte[in.remaining()];
        in.get(ciphertext);

        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(browser.getPrivate());
        agreement.doPhase(publicKey(asPublic), true);
        byte[] ecdh = agreement.generateSecret();
        byte[] prkKey = hmac(auth, ecdh);
        byte[] ikm = Arrays.copyOf(hmac(prkKey, concat("WebPush: info".getBytes(StandardCharsets.US_ASCII),
                new byte[]{0}, uaPublic, asPublic, new byte[]{1})), 32);
        byte[] prk = hmac(salt, ikm);
        byte[] cek = Arrays.copyOf(hmac(prk, concat("Content-Encoding: aes128gcm".getBytes(StandardCharsets.US_ASCII),
                new byte[]{0, 1})), 16);
        byte[] nonce = Arrays.copyOf(hmac(prk, concat("Content-Encoding: nonce".getBytes(StandardCharsets.US_ASCII),
                new byte[]{0, 1})), 12);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] plain = cipher.doFinal(ciphertext);

        assertEquals(2, plain[plain.length - 1], "last record delimiter");
        assertArrayEquals(message.getBytes(StandardCharsets.UTF_8), Arrays.copyOf(plain, plain.length - 1));
    }

    @Test
    void withoutKeysPushIsOff() throws Exception {
        WebPushSender sender = new WebPushSender("", "", "mailto:test@example.com");
        assertFalse(sender.isConfigured());
        assertEquals("", sender.publicKey());
    }

    private static KeyPair p256() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    private static ECPublicKey publicKey(byte[] point) throws Exception {
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec("secp256r1"));
        ECParameterSpec spec = parameters.getParameterSpec(ECParameterSpec.class);
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(new ECPoint(
                new BigInteger(1, Arrays.copyOfRange(point, 1, 33)),
                new BigInteger(1, Arrays.copyOfRange(point, 33, 65))), spec));
    }

    private static byte[] uncompressed(ECPublicKey key) {
        return concat(new byte[]{4}, fixed(key.getW().getAffineX()), fixed(key.getW().getAffineY()));
    }

    private static byte[] fixed(BigInteger value) {
        byte[] bytes = value.toByteArray();
        byte[] out = new byte[32];
        int start = Math.max(0, bytes.length - 32);
        System.arraycopy(bytes, start, out, 32 - (bytes.length - start), bytes.length - start);
        return out;
    }

    private static byte[] hmac(byte[] key, byte[] data) throws Exception {
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
}
