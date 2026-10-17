package com.kycautomate.verification.service;

import com.kycautomate.verification.domain.DocumentType;
import com.kycautomate.verification.dto.CustomerVerificationRequest;
import com.kycautomate.verification.dto.DocumentSnapshot;
import com.kycautomate.verification.dto.ExtractedFieldSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VerificationRuleEngineTest {

    private final VerificationRuleEngine engine = new VerificationRuleEngine(80, 60);

    @Test
    void recommendsApprovalWhenNormalizedFieldsMatch() {
        CustomerVerificationRequest customer = new CustomerVerificationRequest(
                "John Ryan Doe", "01/01/1990", "12 MG Road Bangalore 560001");
        DocumentSnapshot document = document(List.of(
                field("NAME", "Doe John Ryan"),
                field("DOB", "01-01-1990"),
                field("ADDRESS", "12, M.G. Road, Bengaluru - 560001")));

        VerificationRuleEngine.RuleEvaluationResult result = engine.evaluate(customer, List.of(document));

        assertEquals("RECOMMENDED_FOR_APPROVAL", result.decision());
    }

    @Test
    void routesNameMismatchToManualReview() {
        CustomerVerificationRequest customer = new CustomerVerificationRequest(
                "John Ryan Doe", "01/01/1990", "12 MG Road Bangalore");
        DocumentSnapshot document = document(List.of(
                field("NAME", "Completely Different Person"),
                field("DOB", "01/01/1990")));

        VerificationRuleEngine.RuleEvaluationResult result = engine.evaluate(customer, List.of(document));

        assertEquals("MANUAL_REVIEW", result.decision());
    }

    private DocumentSnapshot document(List<ExtractedFieldSnapshot> fields) {
        return new DocumentSnapshot("doc-1", "app-1", DocumentType.PAN, "ABCDE1234F",
                "pan.png", "image/png", true, "EXTRACTED", "complete", null,
                98.0, fields, Instant.now(), Instant.now());
    }

    private ExtractedFieldSnapshot field(String name, String value) {
        return new ExtractedFieldSnapshot(name, value, 98.0);
    }
}
