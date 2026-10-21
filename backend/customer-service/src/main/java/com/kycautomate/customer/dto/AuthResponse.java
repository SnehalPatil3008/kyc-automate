package com.kycautomate.customer.dto;

public record AuthResponse(
    String accessToken,
    String tokenType,
    long expiresIn,
    Long userId,
    String role
) {}