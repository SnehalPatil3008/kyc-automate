package com.kycautomate.decision.security;

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
    private final List<Issuer> issuers;
    private record Issuer(SecretKey key,String name,String audience,String role) {}
    public record Identity(String subject,String role) {}
    private JwtCodec(List<Issuer> issuers) { this.issuers=List.copyOf(issuers); }
    public static JwtCodec single(String secret,String issuer,String audience,String role) {
        if(!List.of("CUSTOMER","REVIEWER").contains(role)) throw new IllegalArgumentException("Unsupported JWT role");
        return new JwtCodec(List.of(issuer(secret,issuer,audience,role)));
    }
    public static JwtCodec trusted(String customerSecret,String reviewSecret) {
        return new JwtCodec(List.of(issuer(customerSecret,"kyc-customer","kyc-customer-api","CUSTOMER"),
            issuer(reviewSecret,"kyc-decision","kyc-decision-api","REVIEWER")));
    }
    private static Issuer issuer(String secret,String name,String audience,String role) {
        byte[] bytes;
        try { bytes=Base64.getDecoder().decode(secret); }
        catch(IllegalArgumentException ex) { throw new IllegalStateException("JWT secret must be Base64 encoded",ex); }
        if(bytes.length<32) throw new IllegalStateException("JWT secret must contain at least 32 random bytes");
        return new Issuer(new SecretKeySpec(bytes,"HmacSHA256"),name,audience,role);
    }
    public String issue(String subject,long ttlSeconds) {
        if(issuers.size()!=1) throw new IllegalStateException("This service only validates tokens");
        if(subject==null || subject.isBlank() || ttlSeconds<60 || ttlSeconds>3600)
            throw new IllegalArgumentException("Invalid JWT subject or lifetime");
        Issuer issuer=issuers.get(0); Instant now=Instant.now();
        return Jwts.builder().header().type("JWT").and().issuer(issuer.name()).subject(subject)
            .audience().add(issuer.audience()).and().issuedAt(Date.from(now)).notBefore(Date.from(now))
            .expiration(Date.from(now.plusSeconds(ttlSeconds))).id(UUID.randomUUID().toString())
            .claim("roles",List.of(issuer.role())).signWith(issuer.key(),Jwts.SIG.HS256).compact();
    }
    public Identity verify(String token) {
        if(token==null || token.isBlank() || token.length()>8192) throw new JwtException("Invalid access token");
        for(Issuer issuer:issuers) {
            try {
                var signed=Jwts.parser().verifyWith(issuer.key()).requireIssuer(issuer.name())
                    .clockSkewSeconds(60).build().parseSignedClaims(token);
                Claims claims=signed.getPayload();
                if(!"HS256".equals(signed.getHeader().getAlgorithm())
                        || claims.getSubject()==null || claims.getSubject().isBlank()
                        || claims.getIssuedAt()==null || claims.getExpiration()==null
                        || !claims.getExpiration().after(claims.getIssuedAt())
                        || claims.getIssuedAt().toInstant().isAfter(Instant.now().plusSeconds(60))
                        || claims.getAudience()==null || !claims.getAudience().contains(issuer.audience())
                        || !List.of(issuer.role()).equals(claims.get("roles")))
                    throw new JwtException("Invalid token claims");
                return new Identity(claims.getSubject(),issuer.role());
            } catch(JwtException | IllegalArgumentException ignored) {
                // Only these explicitly configured issuers/keys are tried; no remote key lookup.
            }
        }
        throw new JwtException("Invalid access token");
    }
}
