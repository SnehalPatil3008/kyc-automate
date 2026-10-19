package com.kycautomate.audit.dto;

import com.kycautomate.audit.enums.EventType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AuditEventRequest(

        @NotBlank
        String applicationId,

        @NotNull
        EventType eventType,

        String reason,

        @NotBlank
        String actor,

        @NotBlank
        String actorRole
) {
}