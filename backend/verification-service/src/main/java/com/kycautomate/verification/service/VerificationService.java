package com.kycautomate.verification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kycautomate.verification.client.DocumentServiceClient;
import com.kycautomate.verification.domain.DocumentType;
import com.kycautomate.verification.domain.VerificationResult;
import com.kycautomate.verification.dto.*;
import com.kycautomate.verification.repository.VerificationResultRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class VerificationService {

    private final DocumentServiceClient documentClient;
    private final VerificationRuleEngine ruleEngine;
    private final SimulatedIdentityVerificationService identityService;
    private final VerificationResultRepository repository;
    private final ObjectMapper objectMapper;
    private final DocumentRequirementService requirementService;

    public VerificationService(
            DocumentServiceClient documentClient,
            VerificationRuleEngine ruleEngine,
            SimulatedIdentityVerificationService identityService,
            VerificationResultRepository repository,
            ObjectMapper objectMapper,
            DocumentRequirementService requirementService) {

        this.documentClient = documentClient;
        this.ruleEngine = ruleEngine;
        this.identityService = identityService;
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.requirementService = requirementService;
    }

    @Transactional
    public VerificationResponse verify(
            String applicationId,
            CustomerVerificationRequest customer) {

        List<DocumentSnapshot> documents =
                documentClient.getActiveDocuments(applicationId);

        // Check PAN + OVD requirements first
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

        // Continue with normal name, DOB and address verification
        VerificationRuleEngine.RuleEvaluationResult evaluation =
                ruleEngine.evaluate(customer, documents);

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
