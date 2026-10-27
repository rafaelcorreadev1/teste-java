package com.deroyque.activation.support;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

public final class TestTokenIssuer {

    public static final String ISSUER = "https://auth.test/realms/activation";
    public static final String AUDIENCE = "activation-service";

    private static final RSAKey KEY = generateKey();
    private static final RSAKey FOREIGN_KEY = generateKey();

    private TestTokenIssuer() {
    }

    public static void publishJwks(WireMockServer server) {
        server.stubFor(get(ProviderMockConfiguration.JWKS_PATH).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody(new JWKSet(KEY.toPublicJWK()).toString())));
    }

    public static Builder token() {
        return new Builder();
    }

    public static final class Builder {

        private String issuer = ISSUER;
        private List<String> audience = List.of(AUDIENCE);
        private String clientId = TestJwts.SYSTEM_A;
        private String scope = "activation:read activation:write";
        private Instant expiresAt = Instant.now().plusSeconds(300);
        private RSAKey signingKey = KEY;

        public Builder issuer(String issuer) {
            this.issuer = issuer;
            return this;
        }

        public Builder audience(String... audience) {
            this.audience = List.of(audience);
            return this;
        }

        public Builder clientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        public Builder scope(String scope) {
            this.scope = scope;
            return this;
        }

        public Builder expiresAt(Instant expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        public Builder signedWithUnknownKey() {
            this.signingKey = FOREIGN_KEY;
            return this;
        }

        public String bearer() {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(issuer)
                    .audience(audience)
                    .subject("service-account-" + clientId)
                    .claim("azp", clientId)
                    .claim("scope", scope)
                    .issueTime(Date.from(expiresAt.minusSeconds(600)))
                    .expirationTime(Date.from(expiresAt))
                    .jwtID(UUID.randomUUID().toString())
                    .build();
            try {
                SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY.getKeyID()).build(), claims);
                jwt.sign(new RSASSASigner(signingKey));
                return "Bearer " + jwt.serialize();
            } catch (JOSEException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private static RSAKey generateKey() {
        try {
            return new RSAKeyGenerator(2048).keyID(UUID.randomUUID().toString()).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }
}
