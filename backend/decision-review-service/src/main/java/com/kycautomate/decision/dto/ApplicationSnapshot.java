
package com.kycautomate.decision.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ApplicationSnapshot(
    Long applicationId,
    String applicationRef,
    Long customerId,
    String applicantName,
    LocalDate applicantDob,
    String applicantAddress,
    String applicantCity,
    String applicantState,
    String applicantPostalCode,
    String applicationStatus,
    Integer version,
    LocalDateTime submittedAt
) {}
