package com.kycautomate.verification.service;

import com.kycautomate.verification.domain.DocumentType;
import com.kycautomate.verification.dto.AuthBridgeDto.RegistryDetails;
import com.kycautomate.verification.dto.AuthBridgeDto.VerifyRequest;
import com.kycautomate.verification.dto.AuthBridgeDto.VerifyResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class SimulatedIdentityVerificationService {

    private static final Logger log = LoggerFactory.getLogger(SimulatedIdentityVerificationService.class);

    private static final Pattern PAN_PATTERN = Pattern.compile("^[A-Z]{5}[0-9]{4}[A-Z]$");
    private static final Pattern PASSPORT_PATTERN = Pattern.compile("^[A-Z][0-9]{7}$");
    private static final Pattern VOTER_PATTERN = Pattern.compile("^[A-Z]{3}[0-9]{7}$");
    private static final Pattern DL_PATTERN = Pattern.compile("^[A-Z]{2}[0-9]{13,15}$");

    public VerifyResponse verify(VerifyRequest request) {
        log.info("Simulating AuthBridge registry verification for DocumentType: {}, Number: {}", 
                request.documentType(), request.documentNumber());

        String docNumber = request.documentNumber() != null ? request.documentNumber().trim().toUpperCase() : "";
        String refId = "AUTHBRG-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String authority = resolveAuthority(request.documentType());
        String now = Instant.now().toString();

        // 1. Simulation Trigger: Expired Document
        if (docNumber.contains("EXPIRED")) {
            return new VerifyResponse(
                    refId,
                    false,
                    "DOCUMENT_EXPIRED",
                    "The document validity period has expired in official records.",
                    new RegistryDetails(authority, request.customerName(), "EXPIRED", "2024-01-01", now),
                    0.0
            );
        }

        // 2. Simulation Trigger: Record Not Found / Invalid
        if (docNumber.contains("FAIL") || docNumber.contains("INVALID")) {
            return new VerifyResponse(
                    refId,
                    false,
                    "REGISTRY_RECORD_NOT_FOUND",
                    "Document number not found in issuing authority database.",
                    new RegistryDetails(authority, null, "NOT_FOUND", null, now),
                    0.0
            );
        }

        // 3. Format / Syntax Validation
        boolean syntaxValid = switch (request.documentType()) {
            case PAN -> PAN_PATTERN.matcher(docNumber).matches();
            case PASSPORT -> PASSPORT_PATTERN.matcher(docNumber).matches();
            case VOTER_ID -> VOTER_PATTERN.matcher(docNumber).matches();
            case DRIVING_LICENCE -> DL_PATTERN.matcher(docNumber.replaceAll("\\s+", "")).matches();
            case AADHAAR, ELECTRICITY_BILL, GAS_BILL -> docNumber.length() >= 6;
        };

        if (!syntaxValid) {
            return new VerifyResponse(
                    refId,
                    false,
                    "SYNTAX_VALIDATION_FAILED",
                    "The document number format does not comply with official national standards.",
                    new RegistryDetails(authority, null, "SYNTAX_ERROR", null, now),
                    0.0
            );
        }

        // 4. Default Success Response
        return new VerifyResponse(
                refId,
                true,
                "RECORD_VERIFIED_ACTIVE",
                "Document confirmed authentic and active in the central registry.",
                new RegistryDetails(authority, request.customerName().toUpperCase(), "ACTIVE", "2035-12-31", now),
                98.5
        );
    }

    private String resolveAuthority(DocumentType type) {
        return switch (type) {
            case PAN -> "NSDL / Income Tax Department";
            case AADHAAR -> "UIDAI Verification Gateway";
            case PASSPORT -> "Passport Seva Kendra Database";
            case VOTER_ID -> "Election Commission of India (ECI)";
            case DRIVING_LICENCE -> "MoRTH - Sarathi Portal";
            case ELECTRICITY_BILL -> "State Electricity Distribution Portal";
            case GAS_BILL -> "LPG Distributor / Petroleum Ministry Portal";
        };
    }
}