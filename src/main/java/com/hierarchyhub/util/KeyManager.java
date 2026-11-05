package com.hierarchyhub.util;

import com.hierarchyhub.config.AuthProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The SSO realm's RSA public keys, one PEM file per {@code kid} under {@link AuthProperties#publicKeyBasePath()} (the
 * platform's standard Keycloak public-key layout) - loaded on first use and cached, so a key rotation only needs a new
 * file dropped in, no restart.
 */
@Component
public class KeyManager {

    private static final Logger log = LoggerFactory.getLogger(KeyManager.class);

    private final String publicKeyBasePath;
    private final Map<String, PublicKey> cache = new ConcurrentHashMap<>();

    public KeyManager(AuthProperties authProperties) {
        this.publicKeyBasePath = authProperties.publicKeyBasePath();
    }

    /** The realm's public key for {@code keyId}, or {@code null} if it has no key file (unknown key id, or a config/IO problem). */
    public PublicKey publicKey(String keyId) {
        return cache.computeIfAbsent(keyId, this::loadPublicKey);
    }

    private PublicKey loadPublicKey(String keyId) {
        try {
            String pem = new String(Files.readAllBytes(Paths.get(publicKeyBasePath, keyId)), StandardCharsets.UTF_8)
                    .replaceAll("-----(BEGIN|END) PUBLIC KEY-----", "")
                    .replaceAll("[\\r\\n]+", "");
            byte[] keyBytes = Base64.getDecoder().decode(pem);
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes));
        } catch (Exception e) {
            log.warn("Could not load SSO public key '{}' from {}: {}", keyId, publicKeyBasePath, e.getMessage());
            return null;
        }
    }
}
