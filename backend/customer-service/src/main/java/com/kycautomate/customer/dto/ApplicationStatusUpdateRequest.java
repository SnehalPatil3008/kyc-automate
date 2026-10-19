package com.kycautomate.customer.dto;

import java.util.Set;
import com.kycautomate.customer.enums.ApplicationStatus;
import jakarta.validation.constraints.*;

public record ApplicationStatusUpdateRequest(
    @NotNull ApplicationStatus newStatus,
    @Size(max = 1000) String reason,
    @NotBlank @Size(max = 100) String decisionReference,
    @NotNull @Min(0) Integer expectedVersion,
    @Size(max = 6) Set<@NotBlank @Pattern(
        regexp = "PAN|AADHAAR|PASSPORT|DRIVING_LICENCE|VOTER_ID|ELECTRICITY_BILL")
        String> requestedDocumentTypes
) {
    public ApplicationStatusUpdateRequest(ApplicationStatus newStatus,
            String reason, String decisionReference, Integer expectedVersion) {
        this(newStatus, reason, decisionReference, expectedVersion, Set.of());
    }
}