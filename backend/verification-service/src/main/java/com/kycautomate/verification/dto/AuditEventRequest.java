package com.kycautomate.verification.dto;

public record AuditEventRequest(
        String applicationId,
        String eventType,
        String reason,
        String actor,
        String actorRole
) {
}