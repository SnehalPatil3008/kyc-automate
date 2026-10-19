package com.kycautomate.verification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kycautomate.verification.client.CustomerApplicationClient;
import com.kycautomate.verification.client.DocumentServiceClient;
import com.kycautomate.verification.domain.DocumentType;
import com.kycautomate.verification.domain.VerificationResult;
import com.kycautomate.verification.dto.AuthBridgeDto;
import com.kycautomate.verification.dto.CustomerApplicationSnapshot;
import com.kycautomate.verification.dto.CustomerVerificationRequest;
import com.kycautomate.verification.dto.DocumentSnapshot;
import com.kycautomate.verification.dto.VerificationResponse;
import com.kycautomate.verification.repository.VerificationResultRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class VerificationService {

    private static final DateTimeFormatter DOB_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final DocumentServiceClient documentClient;
    private final CustomerApplicationClient customerApplicationClient;
    private final VerificationRuleEngine ruleEngine;
    private final SimulatedIdentityVerificationService identityService;
    private final VerificationResultRepository repository;
    private final ObjectMapper objectMapper;
    private final DocumentRequirementService requirementService;

    public VerificationService(
            DocumentServiceClient documentClient,
            CustomerApplicationClient customerApplicationClient,
            VerificationRuleEngine ruleEngine,
            SimulatedIdentityVerificationService identityService,
            VerificationResultRepository repository,
            ObjectMapper objectMapper,
            DocumentRequirementService requirementService) {

        this.documentClient = documentClient;
        this.customerApplicationClient = customerApplicationClient;
        this.ruleEngine = ruleEngine;
        this.identityService = identityService;
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.requirementService = requirementService;
    }

    @Transactional
    public VerificationResponse verify(
            String applicationId,
            CustomerVerificationRequest ignoredCustomerData) {

        long numericApplicationId;

        try {
            numericApplicationId = Long.parseLong(applicationId);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "Application ID must be a numeric Customer Service application ID."
            );
        }

        /*
         * Retrieve the trusted CAF details from Customer Service.
         * The values sent by the browser are intentionally ignored.
         */
        CustomerApplicationSnapshot snapshot =
                customerApplicationClient.getSnapshot(numericApplicationId);

        CustomerVerificationRequest customer =
                createCustomerVerificationRequest(snapshot);

        List<DocumentSnapshot> documents =
                documentClient.getActiveDocuments(applicationId);

        // Check PAN + OVD requirements first.
        DocumentRequirementService.RequirementResult requirements =
                requirementService.check(documents);

        if (!requirements.complete()) {

            String reason = "Mandatory document(s) missing: "
                    + String.join(", ", requirements.missingDocuments());

            VerificationResult result = new VerificationResult();
            result.setVerificationId(UUID.randomUUID().toString());
            result.setApplicationId(applicationId);
            result.setDecision("RESUBMISSION_REQUIRED");
            result.setReason(reason);
            result.setEvidenceJson(toJson(documents));

            repository.save(result);

            return toResponse(result);
        }

        // Compare trusted CAF details with OCR values.
        VerificationRuleEngine.RuleEvaluationResult evaluation =
                ruleEngine.evaluate(customer, documents);

        // Run simulated external identity verification only after
        // completeness and CAF/OCR comparison pass.
        if ("RECOMMENDED_FOR_APPROVAL".equals(evaluation.decision())) {

            String registryFailure =
                    verifyIdentityDocuments(customer, documents);

            if (registryFailure != null) {
                evaluation = new VerificationRuleEngine.RuleEvaluationResult(
                        "MANUAL_REVIEW",
                        registryFailure,
                        evaluation.nameScore(),
                        evaluation.addressScore()
                );
            }
        }

        VerificationResult result = new VerificationResult();
        result.setVerificationId(UUID.randomUUID().toString());
        result.setApplicationId(applicationId);
        result.setDecision(evaluation.decision());
        result.setReason(evaluation.reason());
        result.setNameScore(evaluation.nameScore());
        result.setAddressScore(evaluation.addressScore());
        result.setEvidenceJson(toJson(documents));

        repository.save(result);

        return toResponse(result);
    }

    private CustomerVerificationRequest createCustomerVerificationRequest(
            CustomerApplicationSnapshot snapshot) {

        String fullAddress = List.of(
                    snapshot.applicantAddress(),
                    snapshot.applicantCity(),
                    snapshot.applicantState(),
                    snapshot.applicantPostalCode()
                )
                .stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(Collectors.joining(", "));

        String dob = snapshot.applicantDob() == null
                ? null
                : snapshot.applicantDob().format(DOB_FORMAT);

        return new CustomerVerificationRequest(
                snapshot.applicantName(),
                dob,
                fullAddress
        );
    }

    @Transactional(readOnly = true)
    public VerificationResponse latest(String applicationId) {
        return repository
                .findTopByApplicationIdOrderByVerifiedAtDesc(applicationId)
                .map(this::toResponse)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No verification result found for application: "
                                + applicationId
                ));
    }

    private String verifyIdentityDocuments(
            CustomerVerificationRequest customer,
            List<DocumentSnapshot> documents) {

        for (DocumentSnapshot document : documents) {

            if (document.documentType() == DocumentType.ELECTRICITY_BILL
                    || document.documentType() == DocumentType.GAS_BILL) {
                continue;
            }

            AuthBridgeDto.VerifyResponse response =
                    identityService.verify(
                            new AuthBridgeDto.VerifyRequest(
                                    UUID.randomUUID().toString(),
                                    document.documentType(),
                                    document.documentNumber(),
                                    customer.customerName(),
                                    customer.dob()
                            )
                    );

            if (!response.verified()) {
                return String.format(
                        "Identity verification failed for %s: %s",
                        document.documentType(),
                        response.statusMessage()
                );
            }
        }

        return null;
    }

    private String toJson(List<DocumentSnapshot> documents) {
        try {
            return objectMapper.writeValueAsString(documents);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Unable to serialize verification evidence.",
                    exception
            );
        }
    }

    private VerificationResponse toResponse(VerificationResult result) {
        return new VerificationResponse(
                result.getVerificationId(),
                result.getApplicationId(),
                result.getDecision(),
                result.getReason(),
                result.getNameScore(),
                result.getAddressScore(),
                result.getVerifiedAt()
        );
    }
}