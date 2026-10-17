package com.kycautomate.verification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients
@SpringBootApplication
public class VerificationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(VerificationServiceApplication.class, args);
    }
}
