package com.kycautomate.verification.dto;

import com.kycautomate.verification.domain.DocumentType;

public class AuthBridgeDto {

    public record VerifyRequest(
            String clientTransactionId,
            DocumentType documentType,
            String documentNumber,
            String customerName,
            String dob
    ) {}

    public record RegistryDetails(
            String issuingAuthority,
            String nameOnRecord,
            String status,
            String expiryDate,
            String verificationTimestamp
    ) {}

    public record VerifyResponse(
            String referenceId,
            boolean verified,
            String statusCode,
            String statusMessage,
            RegistryDetails registryDetails,
            Double confidenceScore
    ) {}
}