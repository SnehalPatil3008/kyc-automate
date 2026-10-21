package com.kycautomate.decision.dto;

import java.time.Instant;

public record ReviewResponse(
    Long reviewId,
    Long applicationId,
    String verificationId,
    String verificationDecision,
    String reviewState,
    String targetStatus,
    String reason,
    String reviewer,
    String operationReference,
    Integer customerVersion,
    Instant createdAt,
    Instant updatedAt,
    java.util.Set<String> requestedDocumentTypes
) {}