package com.kycautomate.decision.security;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration
public class JwtConfig {
    @Bean public JwtCodec jwtCodec(@Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.issuer}") String issuer,@Value("${security.jwt.audience}") String audience) {
        return JwtCodec.single(secret,issuer,audience,"REVIEWER");
    }
}