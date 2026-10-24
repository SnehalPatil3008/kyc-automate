package com.kycautomate.document.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.kycautomate.document.config.AuditFeignConfig;
import com.kycautomate.document.dto.AuditEventRequest;

@FeignClient(
        name = "audit-service",
        url = "${audit.service.url}",
        configuration = AuditFeignConfig.class
)
public interface AuditClient {

    @PostMapping("/api/audit/events")
    void recordEvent(@RequestBody AuditEventRequest request);
}