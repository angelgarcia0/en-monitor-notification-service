package com.energymonitor.notification.infrastructure;

import java.nio.file.Path;
import java.security.interfaces.RSAPublicKey;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Builds the validator of the access tokens. The security service signs them with its private
 * key; this service holds only the public one, so it can check a token but never issue one.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SecurityJwtProperties.class)
public class JwtConfiguration {

    @Bean
    public JwtDecoder jwtDecoder(SecurityJwtProperties properties) {
        RSAPublicKey publicKey = loadPublicKey(properties);
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey)
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
        return decoder;
    }

    private RSAPublicKey loadPublicKey(SecurityJwtProperties properties) {
        String path = properties.publicKeyPath();
        if (path == null || path.isBlank()) {
            throw new IllegalStateException("Missing RSA public key: set"
                    + " SECURITY_JWT_PUBLIC_KEY_PATH (security.jwt.public-key-path) to the public"
                    + " key of the security service, the file it was given as"
                    + " SECURITY_JWT_PUBLIC_KEY_PATH too. Without it no incoming token can be"
                    + " validated.");
        }
        return PemRsaKeys.readPublicKey(Path.of(path.trim()));
    }
}
