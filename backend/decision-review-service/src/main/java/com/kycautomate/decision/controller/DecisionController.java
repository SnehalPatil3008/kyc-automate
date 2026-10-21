package com.kycautomate.decision.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.kycautomate.decision.dto.ReviewResponse;
import com.kycautomate.decision.service.DecisionReviewService;

@RestController
@RequestMapping("/api/decisions")
public class DecisionController {

    private final DecisionReviewService service;

    public DecisionController(DecisionReviewService service) {
        this.service = service;
    }

    @PostMapping("/applications/{applicationId}/process")
    public ResponseEntity<ReviewResponse> process(
            @PathVariable Long applicationId) {
        return ResponseEntity.ok(service.processApplication(applicationId));
    }

    @PostMapping("/applications/{applicationId}/evaluate")
    public ResponseEntity<ReviewResponse> evaluate(
            @PathVariable Long applicationId) {

        return ResponseEntity.ok(
            service.evaluate(applicationId));
    }

    @GetMapping("/applications/{applicationId}")
    public ResponseEntity<List<ReviewResponse>> getHistory(
            @PathVariable Long applicationId) {

        return ResponseEntity.ok(
            service.applicationReviews(applicationId));
    }
}