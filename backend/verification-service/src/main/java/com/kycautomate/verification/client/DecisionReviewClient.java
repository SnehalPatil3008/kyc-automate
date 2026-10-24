package com.kycautomate.verification.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import com.kycautomate.verification.config.DecisionReviewClientConfig;

@FeignClient(
    name = "decision-review-service",
    configuration = DecisionReviewClientConfig.class
)
public interface DecisionReviewClient {

    @PostMapping(
        "/api/internal/decisions/applications/{applicationId}/evaluate"
    )
    void evaluate(
        @PathVariable("applicationId")
        String applicationId
    );
}