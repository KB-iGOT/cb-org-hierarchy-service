package com.hierarchyhub.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hierarchyhub.config.AuthProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.security.PublicKey;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

/**
 * Verifies a caller's signed access token the same way cb-ext-assessment-service's {@code AccessTokenValidator} does
 * (RS256 signature against the SSO realm's public key, {@code iss} and {@code exp} checked, user id read from
 * {@code sub}) - generic, so any controller can require a caller's identity this way, not only org-hierarchy's.
 */
@Component
public class AccessTokenValidator {

    private static final Logger log = LoggerFactory.getLogger(AccessTokenValidator.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String RS256 = "SHA256withRSA";

    private final KeyManager keyManager;
    private final String issuer;

    public AccessTokenValidator(KeyManager keyManager, AuthProperties authProperties) {
        this.keyManager = keyManager;
        this.issuer = authProperties.issuer();
    }

    /**
     * The caller's user id from a valid, unexpired token issued by this platform's own SSO realm, or {@code null} if
     * the token is missing, malformed, signed by an unknown key, expired, or issued by a different realm.
     */
    public String resolveUserId(String accessToken) {
        if (AppUtil.isBlank(accessToken)) {
            return null;
        }
        try {
            String[] parts = accessToken.split("\\.");
            if (parts.length != 3) {
                return null;
            }
            Map<String, Object> header = readJson(parts[0]);
            if (!verifySignature(parts[0] + "." + parts[1], parts[2], (String) header.get("kid"))) {
                return null;
            }
            Map<String, Object> body = readJson(parts[1]);
            if (isExpired((Number) body.get("exp")) || !issuer.equalsIgnoreCase((String) body.get("iss"))) {
                return null;
            }
            return userIdFromSubject((String) body.get("sub"));
        } catch (Exception e) {
            log.info("Rejecting access token: {}", e.getMessage());
            return null;
        }
    }

    private boolean verifySignature(String signedContent, String signaturePart, String keyId) throws Exception {
        PublicKey publicKey = AppUtil.isBlank(keyId) ? null : keyManager.publicKey(keyId);
        if (publicKey == null) {
            return false;
        }
        Signature signature = Signature.getInstance(RS256);
        signature.initVerify(publicKey);
        signature.update(signedContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return signature.verify(Base64.getUrlDecoder().decode(signaturePart));
    }

    private static boolean isExpired(Number expiresAtEpochSeconds) {
        return expiresAtEpochSeconds == null || Instant.now().getEpochSecond() > expiresAtEpochSeconds.longValue();
    }

    private static String userIdFromSubject(String subject) {
        if (AppUtil.isBlank(subject)) {
            return null;
        }
        int lastColon = subject.lastIndexOf(':');
        return lastColon < 0 ? subject : subject.substring(lastColon + 1);
    }

    private static Map<String, Object> readJson(String base64UrlSegment) throws Exception {
        return MAPPER.readValue(Base64.getUrlDecoder().decode(base64UrlSegment), new TypeReference<Map<String, Object>>() {
        });
    }
}
