package fr.polytech.okit.user.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RealmRoleJwtAuthenticationConverterTest {

    private final RealmRoleJwtAuthenticationConverter converter =
            new RealmRoleJwtAuthenticationConverter();

    @Test
    void convertsRealmRolesToSpringAuthorities() {
        Jwt jwt = new Jwt(
                "token",
                Instant.parse("2026-10-19T10:00:00Z"),
                Instant.parse("2026-10-19T10:05:00Z"),
                Map.of("alg", "none"),
                Map.of(
                        "sub", "buyer-123",
                        "scope", "openid profile",
                        "realm_access", Map.of("roles", List.of("BUYER", "SELLER"))));

        assertThat(converter.convert(jwt).getAuthorities())
                .extracting("authority")
                .contains("SCOPE_openid", "SCOPE_profile", "ROLE_BUYER", "ROLE_SELLER");
    }
}
