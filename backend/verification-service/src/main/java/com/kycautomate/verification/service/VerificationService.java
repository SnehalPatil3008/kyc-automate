package com.kycautomate.verification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kycautomate.verification.client.AuditClient;
import com.kycautomate.verification.client.DocumentServiceClient;
import com.kycautomate.verification.domain.DocumentType;
import com.kycautomate.verification.domain.VerificationResult;
import com.kycautomate.verification.dto.AuditEventRequest;
import com.kycautomate.verification.dto.AuthBridgeDto;
import com.kycautomate.verification.dto.CustomerVerificationRequest;
import com.kycautomate.verification.dto.DocumentSnapshot;
import com.kycautomate.verification.dto.VerificationResponse;
import com.kycautomate.verification.repository.VerificationResultRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class VerificationService {

    private final DocumentServiceClient documentClient;
    private final VerificationRuleEngine ruleEngine;
    private final SimulatedIdentityVerificationService identityService;
    private final VerificationResultRepository repository;
    private final ObjectMapper objectMapper;
    private final DocumentRequirementService requirementService;
    private final AuditClient auditClient;

    public VerificationService(
            DocumentServiceClient documentClient,
            VerificationRuleEngine ruleEngine,
            SimulatedIdentityVerificationService identityService,
            VerificationResultRepository repository,
            ObjectMapper objectMapper,
            DocumentRequirementService requirementService,
            AuditClient auditClient) {

        this.documentClient = documentClient;
        this.ruleEngine = ruleEngine;
        this.identityService = identityService;
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.requirementService = requirementService;
        this.auditClient = auditClient;
    }

    @Transactional
    public VerificationResponse verify(
            String applicationId,
            CustomerVerificationRequest customer) {

        List<DocumentSnapshot> documents =
                documentClient.getActiveDocuments(applicationId);

        List<AuthBridgeDto.VerifyResponse> identityResults =
                new ArrayList<>();

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
            result.setEvidenceJson(toJson(documents, identityResults));
            result.setCustomerName(customer.customerName());
            result.setCustomerDob(customer.dob());
            result.setCustomerAddress(customer.address());

            result = repository.saveAndFlush(result);

            recordAudit(
                    applicationId,
                    "RESUBMISSION_REQUESTED",
                    reason
            );

            return toResponse(result, identityResults);
        }

        VerificationRuleEngine.RuleEvaluationResult evaluation =
                ruleEngine.evaluate(customer, documents);

        if ("RECOMMENDED_FOR_APPROVAL".equals(evaluation.decision())) {

            IdentityVerificationResult identityResult =
                    verifyIdentityDocuments(customer, documents);

            identityResults = identityResult.responses();

            if (identityResult.failureReason() != null) {
                evaluation = new VerificationRuleEngine.RuleEvaluationResult(
                        "MANUAL_REVIEW",
                        identityResult.failureReason(),
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
        result.setEvidenceJson(toJson(documents, identityResults));
        result.setCustomerName(customer.customerName());
        result.setCustomerDob(customer.dob());
        result.setCustomerAddress(customer.address());
        result = repository.saveAndFlush(result);

        String eventType = switch (evaluation.decision()) {
            case "RESUBMISSION_REQUIRED" ->
                    "RESUBMISSION_REQUESTED";

            case "RECOMMENDED_FOR_APPROVAL",
                 "MANUAL_REVIEW" ->
                    "VERIFICATION_COMPLETED";

            case "PROCESSING" ->
                    "DOCUMENT_PROCESSING_STARTED";

            default ->
                    "VERIFICATION_COMPLETED";
        };

        recordAudit(
                applicationId,
                eventType,
                "Decision: " + evaluation.decision()
                        + ". " + evaluation.reason()
        );

        return toResponse(result, identityResults);
    }

    @Transactional(readOnly = true)
    public VerificationResponse latest(String applicationId) {
        return repository
                .findTopByApplicationIdOrderByVerifiedAtDesc(applicationId)
                .map(result -> toResponse(
                        result,
                        readIdentityVerification(result.getEvidenceJson())
                ))
                .orElseThrow(() -> new IllegalArgumentException(
                        "No verification result found for application: "
                                + applicationId
                ));
    }

    private IdentityVerificationResult verifyIdentityDocuments(
            CustomerVerificationRequest customer,
            List<DocumentSnapshot> documents) {

        List<AuthBridgeDto.VerifyResponse> responses =
                new ArrayList<>();

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

            responses.add(response);

            if (!response.verified()) {
                return new IdentityVerificationResult(
                        "Identity verification failed for "
                                + document.documentType()
                                + ": "
                                + response.statusMessage(),
                        responses
                );
            }
        }

        return new IdentityVerificationResult(null, responses);
    }

    private String toJson(
            List<DocumentSnapshot> documents,
            List<AuthBridgeDto.VerifyResponse> identityResults) {

        try {
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("documents", documents);
            evidence.put("identityVerification", identityResults);

            return objectMapper.writeValueAsString(evidence);

        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Unable to serialize verification evidence.",
                    exception
            );
        }
    }

    private List<AuthBridgeDto.VerifyResponse> readIdentityVerification(
            String evidenceJson) {

        if (evidenceJson == null || evidenceJson.isBlank()) {
            return List.of();
        }

        try {
            JsonNode root = objectMapper.readTree(evidenceJson);

            if (!root.isObject()) {
                return List.of();
            }

            JsonNode identityNode =
                    root.get("identityVerification");

            if (identityNode == null || !identityNode.isArray()) {
                return List.of();
            }

            return objectMapper.convertValue(
                    identityNode,
                    objectMapper.getTypeFactory()
                            .constructCollectionType(
                                    List.class,
                                    AuthBridgeDto.VerifyResponse.class
                            )
            );

        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Unable to read identity-verification evidence.",
                    exception
            );
        }
    }

    private VerificationResponse toResponse(
            VerificationResult result,
            List<AuthBridgeDto.VerifyResponse> identityResults) {

        return new VerificationResponse(
                result.getVerificationId(),
                result.getApplicationId(),
                result.getDecision(),
                result.getReason(),
                result.getNameScore(),
                result.getAddressScore(),
                result.getCustomerName(),
                result.getCustomerDob(),
                result.getCustomerAddress(),
                result.getVerifiedAt(),
                identityResults
        );
    }

    private void recordAudit(
            String applicationId,
            String eventType,
            String reason) {

        auditClient.recordEvent(new AuditEventRequest(
                applicationId,
                eventType,
                reason,
                "verification-service",
                "SERVICE"
        ));
    }

    private record IdentityVerificationResult(
            String failureReason,
            List<AuthBridgeDto.VerifyResponse> responses
    ) {
    }
}