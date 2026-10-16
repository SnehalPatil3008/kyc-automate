package com.kycautomate.document.dto;

public record ExtractedFieldResponse(
        String fieldName,
        String fieldValue,
        Double confidenceScore
) {}
