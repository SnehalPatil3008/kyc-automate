package com.kycautomate.verification.dto;

import java.time.Instant;

public record VerificationResponse(
        String verificationId,
        String applicationId,
        String decision,
        String reason,
        Double nameScore,
        Double addressScore,
        Instant verifiedAt
) {}
