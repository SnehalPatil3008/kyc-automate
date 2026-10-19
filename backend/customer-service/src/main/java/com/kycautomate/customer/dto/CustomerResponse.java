
package com.kycautomate.customer.dto;

import java.time.LocalDate;

public record CustomerResponse(
    Long customerId,
    String email,
    String fullName,
    LocalDate dateOfBirth,
    String mobileNumber,
    String address,
    String city,
    String state,
    String postalCode
) {}
