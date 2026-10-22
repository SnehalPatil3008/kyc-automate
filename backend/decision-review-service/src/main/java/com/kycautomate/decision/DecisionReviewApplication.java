
package com.kycautomate.decision;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class DecisionReviewApplication {

    public static void main(String[] args) {
        SpringApplication.run(
            DecisionReviewApplication.class, args);
    }
}
