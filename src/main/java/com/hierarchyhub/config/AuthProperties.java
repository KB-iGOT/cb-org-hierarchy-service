package com.hierarchyhub.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Where to verify a caller's access token from: the realm that must have issued it, and the directory holding the
 * realm's RSA public keys (one file per {@code kid}, as published by the platform's SSO/Keycloak instance). App-wide,
 * not scoped to org-hierarchy - any controller can require {@link com.hierarchyhub.util.AccessTokenValidator}. Keys
 * match the platform's own {@code sso.url} / {@code sso.realm} / {@code accesstoken.publickey.basepath} convention
 * (see cb-ext-assessment-service), so the same environment values can be reused here.
 */
@Configuration
public class AuthProperties {

    private final String ssoUrl;
    private final String ssoRealm;
    private final String publicKeyBasePath;

    public AuthProperties(@Value("${sso.url}") String ssoUrl,
                           @Value("${sso.realm}") String ssoRealm,
                           @Value("${accesstoken.publickey.basepath}") String publicKeyBasePath) {
        this.ssoUrl = ssoUrl;
        this.ssoRealm = ssoRealm;
        this.publicKeyBasePath = publicKeyBasePath;
    }

    /** The {@code iss} claim a token must carry: {@code ssoUrl + "realms/" + ssoRealm}. */
    public String issuer() {
        return ssoUrl + "realms/" + ssoRealm;
    }

    public String publicKeyBasePath() {
        return publicKeyBasePath;
    }
}
