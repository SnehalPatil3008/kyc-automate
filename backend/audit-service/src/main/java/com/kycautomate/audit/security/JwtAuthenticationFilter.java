package com.kycautomate.audit.security;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public final class JwtAuthenticationFilter
        extends OncePerRequestFilter {

    private final JwtCodec jwtCodec;

    public JwtAuthenticationFilter(JwtCodec jwtCodec) {
        this.jwtCodec = jwtCodec;
    }

    @Override
    protected boolean shouldNotFilter(
            HttpServletRequest request) {

        String path = request.getServletPath();

        return path.equals("/actuator/health") ||
               path.equals("/api/admin-auth/login");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String authorization =
            request.getHeader("Authorization");

        if (authorization == null ||
                !authorization.regionMatches(
                    true, 0, "Bearer ", 0, 7)) {

            filterChain.doFilter(request, response);
            return;
        }

        try {
            if (Collections.list(
                    request.getHeaders("Authorization"))
                    .size() != 1) {

                throw new JwtException(
                    "Multiple authorization headers");
            }

            String token =
                authorization.substring(7).trim();

            JwtCodec.Identity identity =
                jwtCodec.verify(token);

            var authorities = List.of(
                new SimpleGrantedAuthority(
                    "ROLE_" + identity.role())
            );

            var authentication =
                new JwtAuthenticationToken(
                    identity.subject(),
                    token,
                    authorities
                );

            authentication.setDetails(
                new WebAuthenticationDetailsSource()
                    .buildDetails(request)
            );

            var context =
                SecurityContextHolder.createEmptyContext();

            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);

            filterChain.doFilter(request, response);

        } catch (JwtException |
                 IllegalArgumentException exception) {

            SecurityContextHolder.clearContext();

            response.setStatus(
                HttpServletResponse.SC_UNAUTHORIZED);

            response.setHeader(
                "WWW-Authenticate", "Bearer");

            response.setContentType("application/json");

            response.getWriter().write(
                """
                {"error":"Invalid or expired access token"}
                """
            );
        }
    }
}