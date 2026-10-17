package com.kycautomate.verification.dto;

import jakarta.validation.constraints.NotBlank;

public record CustomerVerificationRequest(
        @NotBlank String customerName,
        String dob,
        @NotBlank String address
) {}
