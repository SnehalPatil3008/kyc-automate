package com.kycautomate.verification.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

import feign.auth.BasicAuthRequestInterceptor;

public class DecisionReviewClientConfig {

    @Bean
    public BasicAuthRequestInterceptor decisionReviewAuth(
            @Value("${decision.review.password}")
            String password) {

        return new BasicAuthRequestInterceptor(
            "verification-service",
            password
        );
    }
}