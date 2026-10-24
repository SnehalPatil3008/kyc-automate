package com.kycautomate.decision.controller;

import org.springframework.web.bind.annotation.*;

import com.kycautomate.decision.dto.ReviewResponse;
import com.kycautomate.decision.service.DecisionReviewService;

@RestController
@RequestMapping("/api/internal/decisions")
public class InternalDecisionController {

    private final DecisionReviewService service;

    public InternalDecisionController(
            DecisionReviewService service) {
        this.service = service;
    }

    @PostMapping("/applications/{applicationId}/evaluate")
    public ReviewResponse evaluate(
            @PathVariable Long applicationId) {

        return service.evaluate(applicationId);
    }
}