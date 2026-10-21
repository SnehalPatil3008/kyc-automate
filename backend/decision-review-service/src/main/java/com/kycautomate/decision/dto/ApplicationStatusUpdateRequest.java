package com.kycautomate.decision.dto;

import java.util.Set;

public record ApplicationStatusUpdateRequest(
    String newStatus, String reason, String decisionReference,
    Integer expectedVersion, Set<String> requestedDocumentTypes
) {
    public ApplicationStatusUpdateRequest(String newStatus, String reason,
            String decisionReference, Integer expectedVersion) {
        this(newStatus, reason, decisionReference, expectedVersion, Set.of());
    }
}