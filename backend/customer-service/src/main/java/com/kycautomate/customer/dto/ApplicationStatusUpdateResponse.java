
package com.kycautomate.customer.dto;

import com.kycautomate.customer.enums.ApplicationStatus;

public record ApplicationStatusUpdateResponse(
    Long applicationId,
    String decisionReference,
    ApplicationStatus previousStatus,
    ApplicationStatus applicationStatus,
    String statusReason,
    Integer version
) {}
