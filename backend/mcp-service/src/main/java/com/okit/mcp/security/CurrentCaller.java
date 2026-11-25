package com.okit.mcp.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class CurrentCaller {

    public String buyerToken() {
        JwtAuthenticationToken authentication = currentJwt();
        boolean buyer = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_BUYER"));
        if (!buyer) {
            throw new AccessDeniedException("Cet outil est réservé aux acheteurs");
        }
        return authentication.getToken().getTokenValue();
    }

    public String buyerOrSellerToken() {
        JwtAuthenticationToken authentication = currentJwt();
        boolean allowed = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_BUYER")
                        || authority.getAuthority().equals("ROLE_SELLER"));
        if (!allowed) {
            throw new AccessDeniedException("Rôle O'Kit requis");
        }
        return authentication.getToken().getTokenValue();
    }

    private static JwtAuthenticationToken currentJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt) || !jwt.isAuthenticated()) {
            throw new AccessDeniedException("Authentification requise");
        }
        return jwt;
    }
}
