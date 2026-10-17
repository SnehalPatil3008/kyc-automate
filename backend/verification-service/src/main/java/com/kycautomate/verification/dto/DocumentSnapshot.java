package com.kycautomate.verification.dto;

import com.kycautomate.verification.domain.DocumentType;

import java.time.Instant;
import java.util.List;

public record DocumentSnapshot(
        String documentId,
        String applicationId,
        DocumentType documentType,
        String documentNumber,
        String originalFilename,
        String mimeType,
        boolean active,
        String processingStatus,
        String processingMessage,
        String expiryDate,
        Double averageConfidence,
        List<ExtractedFieldSnapshot> extractedFields,
        Instant createdAt,
        Instant updatedAt
) {
    public List<ExtractedFieldSnapshot> safeFields() {
        return extractedFields == null ? List.of() : extractedFields;
    }
}
