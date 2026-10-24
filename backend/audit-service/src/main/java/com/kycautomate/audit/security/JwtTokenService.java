package com.kycautomate.audit.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {

    private final JwtCodec jwtCodec;
    private final long ttlSeconds;

    public JwtTokenService(
            JwtCodec jwtCodec,
            @Value("${security.jwt.ttl-seconds:900}")
            long ttlSeconds) {

        if (ttlSeconds < 60 || ttlSeconds > 3600) {
            throw new IllegalStateException(
                "JWT lifetime must be between 60 and 3600 seconds"
            );
        }

        this.jwtCodec = jwtCodec;
        this.ttlSeconds = ttlSeconds;
    }

    public IssuedToken issue(String subject) {

        return new IssuedToken(
            jwtCodec.issue(subject, ttlSeconds),
            ttlSeconds
        );
    }

    public record IssuedToken(
            String value,
            long expiresIn) {
    }
}