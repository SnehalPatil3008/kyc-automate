package com.kycautomate.audit.dto;

public record AdminAuthResponse(

    String accessToken,
    String tokenType,
    long expiresIn,
    String username,
    String role

) {
}