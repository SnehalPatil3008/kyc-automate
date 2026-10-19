package com.kycautomate.verification.config;

import feign.auth.BasicAuthRequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CustomerClientConfig {

    @Bean
    public BasicAuthRequestInterceptor customerServiceAuth(
            @Value("${customer.service.username:verification-service}")
            String username,
            @Value("${customer.service.password}") String password) {

        return new BasicAuthRequestInterceptor(username, password);
    }
}