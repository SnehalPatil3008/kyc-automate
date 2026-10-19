
package com.kycautomate.decision.dto;

public record ApplicationStatusUpdateResponse(
    Long applicationId,
    String decisionReference,
    String previousStatus,
    String applicationStatus,
    String statusReason,
    Integer version
) {}
