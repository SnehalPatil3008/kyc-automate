package com.kycautomate.document.dto;

public record OcrFieldResult(
        String value,
        double ocrConfidence
) {}