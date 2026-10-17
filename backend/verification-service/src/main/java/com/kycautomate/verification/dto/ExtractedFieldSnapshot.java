package com.kycautomate.verification.dto;

public record ExtractedFieldSnapshot(
        String fieldName,
        String fieldValue,
        Double confidenceScore
) {}
