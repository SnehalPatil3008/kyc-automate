
package com.kycautomate.customer.dto;

import com.kycautomate.customer.enums.ApplicationStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ApplicationStatusUpdateRequest(

    @NotNull
    ApplicationStatus newStatus,

    @Size(max = 1000)
    String reason,

    @NotBlank
    @Size(max = 100)
    String decisionReference,

    @NotNull
    Integer expectedVersion
) {}
