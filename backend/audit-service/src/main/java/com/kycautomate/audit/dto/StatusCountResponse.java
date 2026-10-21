package com.kycautomate.audit.dto;

public record StatusCountResponse(
        String status,
        long count
) {
}