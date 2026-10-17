package com.kycautomate.verification.client;

import com.kycautomate.verification.dto.DocumentSnapshot;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@FeignClient(name = "document-service", path = "/api/documents")
public interface DocumentServiceClient {

    @GetMapping("/applications/{applicationId}")
    List<DocumentSnapshot> getActiveDocuments(@PathVariable("applicationId") String applicationId);
}
