
package com.kycautomate.customer.dto;

public record RegisterResponse(
    Long userId,
    Long customerId,
    String email,
    String message
) {}
