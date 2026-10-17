
package com.kycautomate.customer.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.kycautomate.customer.enums.ApplicationStatus;

public record KycApplicationResponse(
    Long applicationId,
    String applicationRef,
    String applicantName,
    LocalDate applicantDob,
    String applicantAddress,
    String applicantCity,
    String applicantState,
    String applicantPostalCode,
    ApplicationStatus applicationStatus,
    String statusReason,
    LocalDateTime createdAt,
    LocalDateTime submittedAt,
    LocalDateTime updatedAt,
    Integer version
) {}
