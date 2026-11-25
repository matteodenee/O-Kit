package com.okit.mcp.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

public final class McpOriginFilter extends OncePerRequestFilter {

    private final Set<String> allowedOrigins;

    public McpOriginFilter(McpServiceProperties properties) {
        this.allowedOrigins = Set.copyOf(properties.security().allowedOrigins());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().equals("/mcp");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String origin = request.getHeader("Origin");
        if (origin != null && !allowedOrigins.contains(origin)) {
            response.sendError(HttpStatus.FORBIDDEN.value(), "Origin non autorisée");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
