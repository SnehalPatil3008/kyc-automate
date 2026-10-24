package com.kycautomate.audit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminLoginRequest(

    @NotBlank
    @Size(max = 150)
    String username,

    @NotBlank
    @Size(max = 72)
    String password

) {
}