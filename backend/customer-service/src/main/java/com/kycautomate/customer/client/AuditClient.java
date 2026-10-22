package com.kycautomate.customer.client;

import com.kycautomate.customer.config.InternalServiceClientConfig;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.kycautomate.customer.dto.AuditEventRequest;

@FeignClient(configuration = InternalServiceClientConfig.class, 
        name = "audit-service",
        url = "${audit.service.url}"
)
public interface AuditClient {

    @PostMapping("/api/audit/events")
    void recordEvent(@RequestBody AuditEventRequest request);
}