
package com.kycautomate.customer.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.kycautomate.customer.dto.DocumentCompletenessResponse;

@FeignClient(
    name = "document-service",
    url = "${services.document.url}"
)
public interface DocumentClient {

    @GetMapping(
        "/api/documents/applications/{applicationId}/completeness"
    )
    DocumentCompletenessResponse checkCompleteness(
        @PathVariable("applicationId") Long applicationId);
}
