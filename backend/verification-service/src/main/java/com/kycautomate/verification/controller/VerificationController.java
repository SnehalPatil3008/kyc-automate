package com.kycautomate.verification.controller;
//import org.springframework.web.bind.annotation.CrossOrigin;
import com.kycautomate.verification.dto.CustomerVerificationRequest;
import com.kycautomate.verification.dto.VerificationResponse;
import com.kycautomate.verification.service.VerificationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import com.kycautomate.verification.client.DecisionReviewClient;
import com.kycautomate.verification.client.DecisionReviewClient;
@RestController
@RequestMapping("/api/verifications")
public class VerificationController {

    private final VerificationService verificationService;
    private final DecisionReviewClient decisionReviewClient;
    
    public VerificationController(
            VerificationService verificationService,
            DecisionReviewClient decisionReviewClient) {

        this.verificationService = verificationService;
        this.decisionReviewClient = decisionReviewClient;
    }

    @PostMapping("/applications/{applicationId}")
    public VerificationResponse verify(
            @PathVariable String applicationId,
            @Valid @RequestBody CustomerVerificationRequest request) {

        VerificationResponse response =
                verificationService.verify(
                    applicationId,
                    request
                );

            if (!"PROCESSING".equals(response.decision())) {
                decisionReviewClient.evaluate(applicationId);
            }

            return response;
    }

    @GetMapping("/applications/{applicationId}/latest")
    public VerificationResponse latest(@PathVariable String applicationId) {
        return verificationService.latest(applicationId);
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    ResponseEntity<Map<String, String>> requestFailure(RuntimeException exception) {
        return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
    }
}
