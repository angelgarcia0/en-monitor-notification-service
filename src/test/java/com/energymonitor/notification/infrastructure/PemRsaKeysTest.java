package com.energymonitor.notification.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Which PEM files {@link JwtConfiguration} accepts as the public key of the security service, and
 * what it says when it refuses one.
 *
 * <p>There is no way to choose a format in the configuration, so the reader has to recognise
 * whatever OpenSSL produced: {@code rsa -pubout} writes X.509 while {@code rsa -RSAPublicKey_out}
 * writes PKCS#1. A deployment that followed either instruction must start.
 */
class PemRsaKeysTest {

    private static final String ISSUER = "https://energy-monitor-backend";

    private static KeyPair keyPair;

    @TempDir
    Path tempDir;

    @BeforeAll
    static void generateKeys() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
    }

    @Test
    void readsAnX509PublicKey() {
        Path path = write("x509_public.pem", "PUBLIC KEY", keyPair.getPublic().getEncoded());

        RSAPublicKey key = PemRsaKeys.readPublicKey(path);

        assertEquals(keyPair.getPublic(), key);
    }

    @Test
    void readsAPkcs1PublicKey() {
        RSAPublicKey expected = (RSAPublicKey) keyPair.getPublic();
        Path path = write("pkcs1_public.pem", "RSA PUBLIC KEY",
                sequence(integer(expected.getModulus()), integer(expected.getPublicExponent())));

        assertEquals(expected, PemRsaKeys.readPublicKey(path));
    }

    @Test
    void readsAPemWithWindowsLineEndings() {
        Path path = writeRaw("crlf_public.pem", "PUBLIC KEY", keyPair.getPublic().getEncoded(), "\r\n");

        assertEquals(keyPair.getPublic(), PemRsaKeys.readPublicKey(path));
    }

    @Test
    void readsABareBase64BodyWithNoHeaderAtAll() {
        Path path = write("bare_public.pem",
                Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));

        assertEquals(keyPair.getPublic(), PemRsaKeys.readPublicKey(path));
    }

    @Test
    void refusesAPrivateKeyOfferedAsThePublicOne() {
        // Only the security service may hold the signing key; handing it to this service is named.
        Path path = write("wrong_public.pem", "PRIVATE KEY", keyPair.getPrivate().getEncoded());

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> PemRsaKeys.readPublicKey(path));

        assertTrue(failure.getMessage().contains("private key"), failure.getMessage());
    }

    @Test
    void refusesAnUnknownPemLabel() throws IOException {
        Path path = Files.writeString(tempDir.resolve("odd.pem"),
                "-----BEGIN DSA PUBLIC KEY-----\nAAAA\n-----END DSA PUBLIC KEY-----\n");

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> PemRsaKeys.readPublicKey(path));

        assertTrue(failure.getMessage().contains("DSA PUBLIC KEY"), failure.getMessage());
    }

    @Test
    void refusesAPemWithNoKeyMaterial() throws IOException {
        Path path = Files.writeString(tempDir.resolve("empty.pem"),
                "-----BEGIN PUBLIC KEY-----\n-----END PUBLIC KEY-----\n");

        assertThrows(IllegalStateException.class, () -> PemRsaKeys.readPublicKey(path));
    }

    @Test
    void refusesAMissingFileByPath() {
        Path path = tempDir.resolve("absent.pem");

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> PemRsaKeys.readPublicKey(path));

        assertTrue(failure.getMessage().contains(path.toString()), failure.getMessage());
    }

    @Test
    void namesTheEnvironmentVariableWhenNoPublicKeyPathIsConfigured() {
        SecurityJwtProperties missing = new SecurityJwtProperties(ISSUER, null);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> new JwtConfiguration().jwtDecoder(missing));

        assertTrue(failure.getMessage().contains("SECURITY_JWT_PUBLIC_KEY_PATH"),
                failure.getMessage());
    }

    @Test
    void toleratesSurroundingWhitespaceInTheConfiguredPath() {
        Path publicKey = write("spaced_public.pem", "PUBLIC KEY", keyPair.getPublic().getEncoded());
        SecurityJwtProperties properties = new SecurityJwtProperties(ISSUER, "  " + publicKey + "  ");

        assertNotNull(new JwtConfiguration().jwtDecoder(properties));
    }

    // ------------------------------------------------------------------ fixtures

    private Path write(String name, String label, byte[] encoded) {
        return writeRaw(name, label, encoded, "\n");
    }

    private Path writeRaw(String name, String label, byte[] encoded, String newline) {
        String base64 = Base64.getEncoder().encodeToString(encoded);
        StringBuilder pem = new StringBuilder("-----BEGIN ").append(label).append("-----").append(newline);
        for (int i = 0; i < base64.length(); i += 64) {
            pem.append(base64, i, Math.min(i + 64, base64.length())).append(newline);
        }
        pem.append("-----END ").append(label).append("-----").append(newline);
        return write(name, pem.toString());
    }

    private Path write(String name, String contents) {
        try {
            return Files.writeString(tempDir.resolve(name), contents, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] sequence(byte[]... values) {
        return tagged(0x30, concat(values));
    }

    private static byte[] integer(BigInteger value) {
        byte[] magnitude = value.toByteArray();
        if (magnitude.length > 1 && magnitude[0] == 0) {
            byte[] trimmed = new byte[magnitude.length - 1];
            System.arraycopy(magnitude, 1, trimmed, 0, trimmed.length);
            magnitude = trimmed;
        }
        return tagged(0x02, magnitude);
    }

    private static byte[] tagged(int tag, byte[] content) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(tag);
        if (content.length < 0x80) {
            out.write(content.length);
        } else if (content.length < 0x100) {
            out.write(0x81);
            out.write(content.length);
        } else {
            out.write(0x82);
            out.write(content.length >>> 8);
            out.write(content.length);
        }
        out.writeBytes(content);
        return out.toByteArray();
    }

    private static byte[] concat(byte[]... values) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] value : values) {
            out.writeBytes(value);
        }
        return out.toByteArray();
    }
}
