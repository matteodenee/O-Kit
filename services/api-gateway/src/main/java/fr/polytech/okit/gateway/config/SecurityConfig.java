package fr.polytech.okit.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(authorize -> authorize
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .pathMatchers("/health", "/health/**", "/metrics").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/v1/products", "/api/v1/products/**")
                        .access(SecurityConfig::isExclusiveDirectSeller)
                        .pathMatchers(HttpMethod.PATCH, "/api/v1/products", "/api/v1/products/**")
                        .access(SecurityConfig::isExclusiveDirectSeller)
                        .pathMatchers("/api/**").authenticated()
                        .anyExchange().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .build();
    }

    private static Mono<AuthorizationResult> isExclusiveDirectSeller(
            Mono<Authentication> authentication,
            AuthorizationContext context
    ) {
        return authentication
                .<AuthorizationResult>map(current -> new AuthorizationDecision(hasExclusiveDirectSellerRole(current)))
                .defaultIfEmpty(new AuthorizationDecision(false));
    }

    private static boolean hasExclusiveDirectSellerRole(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            return false;
        }

        Object directRolesClaim = jwtAuthentication.getToken()
                .getClaims()
                .get("okit_direct_realm_roles");
        if (!(directRolesClaim instanceof List<?> directRoles)
                || directRoles.stream().anyMatch(role -> !(role instanceof String))) {
            return false;
        }

        return directRoles.contains("SELLER") && !directRoles.contains("BUYER");
    }
}
