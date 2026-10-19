package com.kycautomate.verification.client;

import com.kycautomate.verification.config.CustomerClientConfig;
import com.kycautomate.verification.dto.CustomerApplicationSnapshot;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
        name = "customer-service",
        url = "${services.customer.url:http://localhost:8081}",
        configuration = CustomerClientConfig.class
)
public interface CustomerApplicationClient {

    @GetMapping("/api/internal/kyc/applications/{applicationId}/snapshot")
    CustomerApplicationSnapshot getSnapshot(
            @PathVariable("applicationId") Long applicationId
    );
}