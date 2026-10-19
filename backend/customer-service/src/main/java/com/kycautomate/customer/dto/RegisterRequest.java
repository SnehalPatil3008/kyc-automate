
package com.kycautomate.customer.dto;

import java.time.LocalDate;
import jakarta.validation.constraints.*;

public record RegisterRequest(

    @NotBlank
    @Email
    @Size(max = 150)
    String email,

    @NotBlank
    @Size(min = 8, max = 72)
    String password,

    @NotBlank
    @Size(max = 150)
    String fullName,

    @NotNull
    @Past
    LocalDate dateOfBirth,

    @NotBlank
    @Pattern(regexp = "^[0-9]{10}$")
    String mobileNumber,

    @NotBlank
    @Size(max = 300)
    String address,

    @NotBlank
    @Size(max = 100)
    String city,

    @NotBlank
    @Size(max = 100)
    String state,

    @NotBlank
    @Pattern(regexp = "^[1-9][0-9]{5}$")
    String postalCode
) {}
