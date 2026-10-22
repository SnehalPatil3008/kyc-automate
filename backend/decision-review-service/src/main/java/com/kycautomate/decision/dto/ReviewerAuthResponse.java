package com.kycautomate.decision.dto;

public record ReviewerAuthResponse(
    String accessToken,
    String tokenType,
    long expiresIn,
    String username,
    String role
) {}