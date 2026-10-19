package com.kycautomate.customer.client;

import java.util.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import com.kycautomate.customer.dto.*;
import com.kycautomate.customer.exception.DocumentServiceUnavailableException;

@FeignClient(name = "document-service", url = "${services.document.url}")
public interface DocumentClient {
    Set<String> SUPPORTED = Set.of("PAN", "AADHAAR", "PASSPORT",
        "DRIVING_LICENCE", "VOTER_ID", "ELECTRICITY_BILL");
    Set<String> OVDS = Set.of("AADHAAR", "PASSPORT", "DRIVING_LICENCE", "VOTER_ID");

    @GetMapping("/api/documents/applications/{applicationId}")
    List<DocumentSnapshot> getActiveDocuments(
        @PathVariable("applicationId") Long applicationId);

    default DocumentCompletenessResponse checkCompleteness(Long applicationId) {
        return completeness(applicationId, getActiveDocuments(applicationId));
    }

    // Both initial submission and resubmission can check ONE returned snapshot.
    static DocumentCompletenessResponse completeness(
            Long applicationId, List<DocumentSnapshot> documents) {
        if (applicationId == null || documents == null) throw invalid();
        Set<String> types = new HashSet<>();
        Set<String> ids = new HashSet<>();
        for (DocumentSnapshot doc : documents) {
            if (doc == null || !doc.active() || doc.documentId() == null
                    || doc.documentId().isBlank()
                    || !applicationId.toString().equals(doc.applicationId())
                    || doc.documentType() == null || !SUPPORTED.contains(doc.documentType())
                    || !ids.add(doc.documentId()) || !types.add(doc.documentType())) {
                throw invalid();
            }
        }
        List<String> missing = new ArrayList<>();
        if (!types.contains("PAN")) missing.add("PAN");
        if (Collections.disjoint(types, OVDS)) missing.add("OVD");
        return new DocumentCompletenessResponse(
            applicationId, missing.isEmpty(), List.copyOf(missing));
    }

    private static DocumentServiceUnavailableException invalid() {
        return new DocumentServiceUnavailableException(
            "Invalid or duplicate active documents from Document Service");
    }
}