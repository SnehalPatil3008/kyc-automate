package com.kycautomate.decision.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReviewerLoginRequest(
    @NotBlank @Size(max = 150) String username,
    @NotBlank @Size(max = 72) String password
) {}