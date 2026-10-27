package com.deroyque.activation.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.util.Arrays;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

public final class TestJwts {

    public static final String SYSTEM_A = "sys-a";
    public static final String SYSTEM_B = "sys-b";

    private TestJwts() {
    }

    public static JwtRequestPostProcessor client(String clientId, String... scopes) {
        return jwt()
                .jwt(token -> token.claim("azp", clientId))
                .authorities(Arrays.stream(scopes).<GrantedAuthority>map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope)).toList());
    }

    public static JwtRequestPostProcessor fullAccess(String clientId) {
        return client(clientId, "activation:read", "activation:write");
    }

    public static JwtRequestPostProcessor fullAccess() {
        return fullAccess(SYSTEM_A);
    }
}
