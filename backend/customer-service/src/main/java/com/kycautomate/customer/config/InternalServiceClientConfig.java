package com.kycautomate.customer.config;

import feign.auth.BasicAuthRequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

// Deliberately not @Configuration: only clients naming this class receive this interceptor.
public class InternalServiceClientConfig {
    @Bean public BasicAuthRequestInterceptor internalServiceAuth(
            @Value("${internal.outbound.password}") String password) {
        return new BasicAuthRequestInterceptor("customer-service",password);
    }
}