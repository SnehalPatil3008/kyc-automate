package com.kycautomate.decision.dto;

public record CustomerVerificationRequest(
    String customerName, String dob, String address
) {}