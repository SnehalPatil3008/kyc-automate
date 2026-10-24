package com.kycautomate.audit.security;

import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;

public final class JwtCodec {

    private final List<Issuer> trustedIssuers;
    private final Issuer adminIssuer;

    private record Issuer(
            SecretKey key,
            String name,
            String audience,
            String role) {
    }

    public record Identity(
            String subject,
            String role) {
    }

    private JwtCodec(
            List<Issuer> trustedIssuers,
            Issuer adminIssuer) {

        this.trustedIssuers =
            List.copyOf(trustedIssuers);

        this.adminIssuer = adminIssuer;
    }

    public static JwtCodec trusted(
            String customerSecret,
            String reviewerSecret,
            String serviceSecret,
            String adminSecret,
            String adminIssuerName,
            String adminAudience) {

        Issuer customerIssuer = createIssuer(
            customerSecret,
            "kyc-customer",
            "kyc-customer-api",
            "CUSTOMER"
        );

        Issuer reviewerIssuer = createIssuer(
            reviewerSecret,
            "kyc-decision",
            "kyc-decision-api",
            "REVIEWER"
        );

        Issuer serviceIssuer = createIssuer(
            serviceSecret,
            "kyc-internal",
            "kyc-audit-api",
            "SERVICE"
        );

        Issuer adminIssuer = createIssuer(
            adminSecret,
            adminIssuerName,
            adminAudience,
            "ADMIN"
        );

        return new JwtCodec(
            List.of(
                customerIssuer,
                reviewerIssuer,
                serviceIssuer,
                adminIssuer
            ),
            adminIssuer
        );
    }

    private static Issuer createIssuer(
            String secret,
            String issuer,
            String audience,
            String role) {

        byte[] secretBytes;

        try {
            secretBytes =
                Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                "JWT secret must be Base64 encoded",
                exception
            );
        }

        if (secretBytes.length < 32) {
            throw new IllegalStateException(
                "JWT secret must contain at least 32 random bytes"
            );
        }

        return new Issuer(
            new SecretKeySpec(
                secretBytes,
                "HmacSHA256"
            ),
            issuer,
            audience,
            role
        );
    }

    public String issue(
            String subject,
            long ttlSeconds) {

        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException(
                "JWT subject is required"
            );
        }

        if (ttlSeconds < 60 || ttlSeconds > 3600) {
            throw new IllegalArgumentException(
                "JWT lifetime must be between 60 and 3600 seconds"
            );
        }

        Instant now = Instant.now();

        return Jwts.builder()
            .header()
                .type("JWT")
            .and()
            .issuer(adminIssuer.name())
            .subject(subject)
            .audience()
                .add(adminIssuer.audience())
            .and()
            .issuedAt(Date.from(now))
            .notBefore(Date.from(now))
            .expiration(
                Date.from(now.plusSeconds(ttlSeconds))
            )
            .id(UUID.randomUUID().toString())
            .claim(
                "roles",
                List.of(adminIssuer.role())
            )
            .signWith(
                adminIssuer.key(),
                Jwts.SIG.HS256
            )
            .compact();
    }

    public Identity verify(String token) {

        if (token == null ||
                token.isBlank() ||
                token.length() > 8192) {

            throw new JwtException(
                "Invalid access token"
            );
        }

        for (Issuer issuer : trustedIssuers) {
            try {
                var signedToken = Jwts.parser()
                    .verifyWith(issuer.key())
                    .requireIssuer(issuer.name())
                    .clockSkewSeconds(60)
                    .build()
                    .parseSignedClaims(token);

                Claims claims =
                    signedToken.getPayload();

                boolean valid =
                    "HS256".equals(
                        signedToken.getHeader()
                            .getAlgorithm()) &&
                    claims.getSubject() != null &&
                    !claims.getSubject().isBlank() &&
                    claims.getIssuedAt() != null &&
                    claims.getExpiration() != null &&
                    claims.getExpiration()
                        .after(claims.getIssuedAt()) &&
                    !claims.getIssuedAt()
                        .toInstant()
                        .isAfter(
                            Instant.now()
                                .plusSeconds(60)) &&
                    claims.getAudience() != null &&
                    claims.getAudience()
                        .contains(issuer.audience()) &&
                    List.of(issuer.role())
                        .equals(claims.get("roles"));

                if (!valid) {
                    throw new JwtException(
                        "Invalid token claims"
                    );
                }

                return new Identity(
                    claims.getSubject(),
                    issuer.role()
                );

            } catch (JwtException |
                     IllegalArgumentException ignored) {
                // Try the next trusted issuer.
            }
        }

        throw new JwtException(
            "Invalid access token"
        );
    }
}