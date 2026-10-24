package com.kycautomate.decision.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.kycautomate.decision.config.AuditFeignConfig;
import com.kycautomate.decision.dto.AuditEventRequest;

@FeignClient(name = "audit-service",    
configuration = AuditFeignConfig.class)
public interface AuditClient {

    @PostMapping("/api/audit/events")
    void recordEvent(@RequestBody AuditEventRequest request);
}