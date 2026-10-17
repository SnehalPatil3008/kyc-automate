
package com.kycautomate.customer.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.kycautomate.customer.enums.ApplicationStatus;

public record InternalKycApplicationResponse(
    Long applicationId,
    String applicationRef,
    Long customerId,
    String applicantName,
    LocalDate applicantDob,
    String applicantAddress,
    String applicantCity,
    String applicantState,
    String applicantPostalCode,
    ApplicationStatus applicationStatus,
    Integer version,
    LocalDateTime submittedAt
) {}
