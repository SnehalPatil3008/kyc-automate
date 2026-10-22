package com.kycautomate.document.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtConfig {

    @Bean
    public JwtCodec jwtCodec(
            @Value("${security.jwt.customer-secret}") String customerSecret,
            @Value("${security.jwt.review-secret}") String reviewSecret) {

        return JwtCodec.trusted(customerSecret, reviewSecret);
    }
}