
package com.kycautomate.decision.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.kycautomate.decision.dto.ReviewActionRequest;
import com.kycautomate.decision.dto.ReviewResponse;
import com.kycautomate.decision.service.DecisionReviewService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final DecisionReviewService service;

    public ReviewController(DecisionReviewService service) {
        this.service = service;
    }

    @GetMapping("/pending")
    public ResponseEntity<List<ReviewResponse>> pending() {
        return ResponseEntity.ok(service.pendingReviews());
    }

    @GetMapping("/{reviewId}")
    public ResponseEntity<ReviewResponse> getReview(
            @PathVariable Long reviewId) {

        return ResponseEntity.ok(
            service.getReview(reviewId));
    }

    @PostMapping("/{reviewId}/decision")
    public ResponseEntity<ReviewResponse> submitDecision(
            @PathVariable Long reviewId,
            @Valid @RequestBody ReviewActionRequest request,
            Authentication authentication) {

        return ResponseEntity.ok(
            service.submitReview(
                reviewId,
                request,
                authentication.getName()
            )
        );
    }

    @PostMapping("/{reviewId}/retry-sync")
    public ResponseEntity<ReviewResponse> retrySync(
            @PathVariable Long reviewId) {

        return ResponseEntity.ok(
            service.synchronize(reviewId));
    }
}
