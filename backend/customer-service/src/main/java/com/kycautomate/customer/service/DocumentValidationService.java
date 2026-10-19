package com.kycautomate.customer.service;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import com.kycautomate.customer.client.DocumentClient;
import com.kycautomate.customer.dto.*;
import com.kycautomate.customer.exception.*;
import feign.FeignException;

@Service
public class DocumentValidationService {
    private final DocumentClient documentClient;

    public DocumentValidationService(DocumentClient documentClient) {
        this.documentClient = documentClient;
    }

    public void validateDocuments(Long applicationId) {
        try {
            requireComplete(applicationId, documentClient.checkCompleteness(applicationId));
        } catch (FeignException ex) {
            throw new DocumentServiceUnavailableException(
                "Document Service is unavailable", ex);
        }
    }

    private void requireComplete(
            Long applicationId, DocumentCompletenessResponse response) {
        if (response == null || !applicationId.equals(response.applicationId())
                || response.missingDocuments() == null
                || (response.complete() && !response.missingDocuments().isEmpty())) {
            throw new DocumentServiceUnavailableException(
                "Invalid completeness response from Document Service");
        }
        if (!response.complete()) {
            throw new BusinessConflictException(
                "Required documents are missing: " + response.missingDocuments());
        }
    }

    public void validateReplacements(Long applicationId,
            Set<String> requestedTypes, Instant requestedAt) {
        if (applicationId == null || requestedAt == null || requestedTypes == null
                || requestedTypes.isEmpty() || requestedTypes.stream().anyMatch(
                    type -> type == null || !DocumentClient.SUPPORTED.contains(type))) {
            throw new BusinessConflictException(
                "Resubmission request must identify supported document types");
        }
        List<DocumentSnapshot> documents;
        try {
            documents = documentClient.getActiveDocuments(applicationId);
        } catch (FeignException ex) {
            throw new DocumentServiceUnavailableException(
                "Document Service is unavailable", ex);
        }
        requireComplete(applicationId, DocumentClient.completeness(applicationId, documents));
        for (String type : requestedTypes) {
            DocumentSnapshot doc = documents.stream()
                .filter(value -> type.equals(value.documentType())).findFirst()
                .orElseThrow(() -> new BusinessConflictException(
                    "Upload a replacement for " + type));
            if (doc.createdAt() == null || !doc.createdAt().isAfter(requestedAt)) {
                throw new BusinessConflictException("A new " + type + " upload is required");
            }
            if ("PENDING".equals(doc.processingStatus())
                    || "PROCESSING".equals(doc.processingStatus())) {
                throw new BusinessConflictException("Replacement " + type + " is still processing");
            }
            if (!"EXTRACTED".equals(doc.processingStatus())) {
                throw new BusinessConflictException(
                    "Replacement " + type + " could not be read; upload it again");
            }
        }
    }
}