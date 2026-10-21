
package com.kycautomate.decision.config;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

import feign.RequestInterceptor;

public class CustomerFeignConfig {

    @Bean
    public RequestInterceptor customerBasicAuth(
            @Value("${internal.customer.username}")
            String username,

            @Value("${internal.customer.password}")
            String password) {

        return template -> {

            String credentials = username + ":" + password;

            String encoded = Base64.getEncoder()
                    .encodeToString(
                        credentials.getBytes(
                            StandardCharsets.UTF_8
                        )
                    );

            template.header(
                "Authorization",
                "Basic " + encoded
            );
        };
    }
}
