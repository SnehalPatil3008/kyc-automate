package com.kycautomate.decision.client;

import com.kycautomate.decision.config.CustomerFeignConfig;
import com.kycautomate.decision.dto.ApplicationSnapshot;
import com.kycautomate.decision.dto.ApplicationStatusUpdateRequest;
import com.kycautomate.decision.dto.ApplicationStatusUpdateResponse;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
    name = "customer-service",
    path = "/api/internal/kyc/applications",
    configuration = CustomerFeignConfig.class
)
public interface CustomerClient {

    @GetMapping("/{id}/snapshot")
    ApplicationSnapshot getSnapshot(
            @PathVariable("id") Long id
    );

    @PostMapping("/{id}/status")
    ApplicationStatusUpdateResponse updateStatus(
            @PathVariable("id") Long id,
            @RequestBody ApplicationStatusUpdateRequest request
    );
}