package com.kycautomate.verification.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.kycautomate.verification.config.AuditFeignConfig;
import com.kycautomate.verification.dto.AuditEventRequest;

@FeignClient(
        name = "audit-service",
        url = "${audit.service.url}",
        configuration = AuditFeignConfig.class
)
public interface AuditClient {

    @PostMapping("/api/audit/events")
    void recordEvent(@RequestBody AuditEventRequest request);
}