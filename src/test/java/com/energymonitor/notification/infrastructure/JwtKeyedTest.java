package com.energymonitor.notification.infrastructure;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Common configuration for any test that boots the whole application.
 *
 * <p>{@code security.jwt.public-key-path} has no sensible default: an empty value is a
 * deployment mistake the application is meant to refuse. A {@code @SpringBootTest} that inherited
 * it empty would therefore fail on the token validator rather than on what it is actually
 * testing, so the path is supplied here from a key pair generated for the test run, together with
 * the issuer.
 *
 * <p>The security service issues the tokens in production. Here {@link #bearer(String)} signs
 * one with the private half of the generated pair and the claims the service puts in it, so a
 * test authenticates the same way a real client does.
 *
 * <p>Extending this class is enough: Spring Test picks up the inherited {@code @DynamicPropertySource}
 * method, so no test has to repeat it, and no key is ever written into the repository.
 */
public abstract class JwtKeyedTest {

    private static final JwtTestKeys.Keys KEYS = JwtTestKeys.generate();

    private static final JwtEncoder ENCODER = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(
            new RSAKey.Builder((RSAPublicKey) KEYS.keyPair().getPublic())
                    .privateKey((RSAPrivateKey) KEYS.keyPair().getPrivate())
                    .build())));

    @DynamicPropertySource
    static void jwtKeys(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.public-key-path", () -> KEYS.publicKey().toString());
        registry.add("security.jwt.issuer", () -> JwtTestKeys.ISSUER);
    }

    /**
     * The key pair the application is validating with in this test.
     *
     * @return the generated pair, for a test that signs or verifies a token on its own
     */
    protected static JwtTestKeys.Keys keys() {
        return KEYS;
    }

    /**
     * An {@code Authorization} header value for an account, as the security service would issue
     * it: 15 minutes long, with the account in {@code sub}.
     *
     * @param userId the account the token is for
     * @return {@code "Bearer <token>"}
     */
    protected static String bearer(String userId) {
        Instant issuedAt = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(JwtTestKeys.ISSUER)
                .subject(userId)
                .claim("email", userId + "@example.com")
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(Duration.ofMinutes(15)))
                .id(UUID.randomUUID().toString())
                .build();
        return "Bearer " + ENCODER.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }
}
