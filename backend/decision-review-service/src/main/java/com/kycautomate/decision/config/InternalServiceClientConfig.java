//package com.kycautomate.decision.config;
//
//import feign.auth.BasicAuthRequestInterceptor;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.context.annotation.Bean;
//
//// Deliberately not @Configuration: only clients naming this class receive this interceptor.
//public class InternalServiceClientConfig {
//    @Bean public BasicAuthRequestInterceptor internalServiceAuth(
//            @Value("${internal.outbound.password}") String password) {
//        return new BasicAuthRequestInterceptor("decision-service",password);
//    }
//}

package com.kycautomate.decision.config;

import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

import feign.RequestInterceptor;

import com.kycautomate.decision.security.JwtTokenService;

public class InternalServiceClientConfig {

    @Bean
    public RequestInterceptor internalServiceAuth(
            JwtTokenService jwtTokenService) {

        return requestTemplate -> {
            String token = jwtTokenService.issue("decision-service").value();

            requestTemplate.header(
                HttpHeaders.AUTHORIZATION,
                "Bearer " + token
            );
        };
    }
}