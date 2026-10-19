package com.kycautomate.decision.dto;

import java.util.Set;
import jakarta.validation.constraints.*;

public record ReviewActionRequest(
    @NotBlank @Pattern(
        regexp = "APPROVED|REJECTED|RESUBMISSION_REQUIRED|MANUAL_REVIEW")
    String decision,
    @Size(max = 1000) String reason,
    @Size(max = 6) Set<@NotBlank @Pattern(
        regexp = "PAN|AADHAAR|PASSPORT|DRIVING_LICENCE|VOTER_ID|ELECTRICITY_BILL")
        String> requestedDocumentTypes
) {
    public ReviewActionRequest(String decision, String reason) {
        this(decision, reason, Set.of());
    }
}