package com.kycautomate.audit.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtConfig {

    @Bean
    public JwtCodec jwtCodec(
            @Value("${security.jwt.customer-secret}")
            String customerSecret,

            @Value("${security.jwt.review-secret}")
            String reviewerSecret,

            @Value("${security.jwt.service-secret}")
            String serviceSecret,

            @Value("${security.jwt.admin-secret}")
            String adminSecret,

            @Value("${security.jwt.admin-issuer}")
            String adminIssuer,

            @Value("${security.jwt.admin-audience}")
            String adminAudience) {

        return JwtCodec.trusted(
            customerSecret,
            reviewerSecret,
            serviceSecret,
            adminSecret,
            adminIssuer,
            adminAudience
        );
    }
}