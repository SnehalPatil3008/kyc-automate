package com.kycautomate.decision.client;

import com.kycautomate.decision.config.InternalServiceClientConfig;

import com.kycautomate.decision.dto.VerificationResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import com.kycautomate.decision.dto.CustomerVerificationRequest;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(configuration = InternalServiceClientConfig.class, 
    name = "verification-service",
    path = "/api/verifications"
)
public interface VerificationClient {

    @PostMapping("/applications/{applicationId}")
    VerificationResponse verify(
        @PathVariable("applicationId") String applicationId,
        @RequestBody CustomerVerificationRequest request);

    @GetMapping("/applications/{applicationId}/latest")
    VerificationResponse getLatest(
        @PathVariable("applicationId") String applicationId
    );
}
