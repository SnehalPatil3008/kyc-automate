package com.kycautomate.verification.config;

import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

import feign.RequestInterceptor;
import io.jsonwebtoken.Jwts;

// No @Configuration: only AuditClient should use this interceptor.
public class AuditFeignConfig {

    @Bean
    public RequestInterceptor auditJwtInterceptor(
            @Value("${security.jwt.service-secret}")
            String serviceSecret,

            @Value("${spring.application.name}")
            String serviceName) {

        byte[] secretBytes;

        try {
            secretBytes =
                Base64.getDecoder()
                    .decode(serviceSecret);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                "Service JWT secret must be Base64 encoded",
                exception
            );
        }

        if (secretBytes.length < 32) {
            throw new IllegalStateException(
                "Service JWT secret must contain at least 32 bytes"
            );
        }

        SecretKey signingKey =
            new SecretKeySpec(
                secretBytes,
                "HmacSHA256"
            );

        return requestTemplate -> {
            Instant now = Instant.now();

            String token = Jwts.builder()
                .header()
                    .type("JWT")
                .and()
                .issuer("kyc-internal")
                .subject(serviceName)
                .audience()
                    .add("kyc-audit-api")
                .and()
                .issuedAt(Date.from(now))
                .notBefore(Date.from(now))
                .expiration(
                    Date.from(now.plusSeconds(300))
                )
                .id(UUID.randomUUID().toString())
                .claim("roles", List.of("SERVICE"))
                .signWith(
                    signingKey,
                    Jwts.SIG.HS256
                )
                .compact();

            requestTemplate.header(
                "Authorization",
                "Bearer " + token
            );
        };
    }
}