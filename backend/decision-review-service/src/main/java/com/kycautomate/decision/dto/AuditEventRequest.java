package com.kycautomate.decision.dto;

public record AuditEventRequest(
        String applicationId,
        String eventType,
        String reason,
        String actor,
        String actorRole
) {}