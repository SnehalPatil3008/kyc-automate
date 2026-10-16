package com.kycautomate.document.dto;

import com.kycautomate.document.domain.DocumentType;

import java.time.Instant;
import java.util.List;

public record DocumentResponse(
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
        List<ExtractedFieldResponse> extractedFields,
        Instant createdAt,
        Instant updatedAt
) {}
