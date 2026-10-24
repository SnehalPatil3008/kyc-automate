package com.kycautomate.verification.dto;

import java.time.Instant;
import java.util.List;

public record VerificationResponse(
        String verificationId,
        String applicationId,
        String decision,
        String reason,
        Double nameScore,
        Double addressScore,
        String customerName,
        String dob,
        String address,
        Instant verifiedAt,
        List<AuthBridgeDto.VerifyResponse> identityVerification
) {
}