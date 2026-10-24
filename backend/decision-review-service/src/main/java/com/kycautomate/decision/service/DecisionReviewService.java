package com.kycautomate.decision.service;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.kycautomate.decision.client.AuditClient;
import com.kycautomate.decision.client.CustomerClient;
import com.kycautomate.decision.client.VerificationClient;
import com.kycautomate.decision.dto.*;
import com.kycautomate.decision.entity.ReviewCase;
import com.kycautomate.decision.enums.ReviewState;
import com.kycautomate.decision.repository.ReviewCaseRepository;

import feign.FeignException;

@Service
public class DecisionReviewService {

    private final CustomerClient customerClient;
    private final VerificationClient verificationClient;
    private final ReviewCaseRepository repository;
    private final TransactionTemplate transactions;
    private final AuditClient auditClient;
    
    private static final List<ReviewState> ACTIVE_STATES =
        List.of(
            ReviewState.PENDING_SYNC,
            ReviewState.OPEN,
            ReviewState.SYNC_CONFLICT
        );

    public DecisionReviewService(
            CustomerClient customerClient,
            VerificationClient verificationClient,
            ReviewCaseRepository repository,
            TransactionTemplate transactions,
            AuditClient auditClient) {

        this.customerClient = customerClient;
        this.verificationClient = verificationClient;
        this.repository = repository;
        this.transactions = transactions;
        this.auditClient = auditClient;
    }

    // Explicit orchestration entry point. /evaluate consumes an existing result;
    // /process starts verification using the authoritative customer snapshot.
    public ReviewResponse processApplication(Long applicationId) {
        ApplicationSnapshot snapshot = customerClient.getSnapshot(applicationId);
        if (snapshot == null || !applicationId.equals(snapshot.applicationId())
                || snapshot.version() == null || snapshot.applicantDob() == null
                || snapshot.applicantName() == null || snapshot.applicantName().isBlank()
                || snapshot.applicantAddress() == null || snapshot.applicantAddress().isBlank()
                || snapshot.submittedAt() == null) {
            throw new IllegalStateException("Invalid submitted application snapshot");
        }
        List<ReviewCase> active = repository
            .findByApplicationIdAndReviewStateInOrderByCreatedAtDesc(
                applicationId, ACTIVE_STATES);
        if (!active.isEmpty()) {
            if (active.size() != 1) throw new IllegalStateException(
                "Multiple active reviews require reconciliation");
            ReviewCase review = active.get(0);
            if ("SUBMITTED".equals(snapshot.applicationStatus())
                    || review.getCreatedAt() == null
                    || review.getCreatedAt().isBefore(snapshot.submittedAt()
                        .toInstant(java.time.ZoneOffset.UTC))) {
                throw new IllegalStateException(
                    "An earlier submission has an unresolved review");
            }
            return review.getReviewState() == ReviewState.PENDING_SYNC
                ? synchronize(review.getReviewId()) : toResponse(review);
        }
        if ("SUBMITTED".equals(snapshot.applicationStatus())) {
            String reference = "START-" + applicationId + "-" + snapshot.version();
            ApplicationStatusUpdateResponse updated = customerClient.updateStatus(
                applicationId, new ApplicationStatusUpdateRequest(
                    "PROCESSING", "Verification started", reference, snapshot.version()));
            if (updated == null || !applicationId.equals(updated.applicationId())
                    || !reference.equals(updated.decisionReference())
                    || !"PROCESSING".equals(updated.applicationStatus())
                    || updated.version() == null
                    || !Objects.equals(updated.version(), snapshot.version() + 1)) {
                throw new IllegalStateException("Invalid processing-start response");
            }
        } else if (!"PROCESSING".equals(snapshot.applicationStatus())) {
            throw new IllegalStateException("Only submitted or processing applications can be processed");
        }
        String address = java.util.stream.Stream.of(
                snapshot.applicantAddress(), snapshot.applicantCity(),
                snapshot.applicantState(), snapshot.applicantPostalCode())
            .filter(value -> value != null && !value.isBlank())
            .collect(java.util.stream.Collectors.joining(", "));
        VerificationResponse verification = verificationClient.verify(applicationId.toString(),
            new CustomerVerificationRequest(
                snapshot.applicantName(),
                snapshot.applicantDob().format(
                    java.time.format.DateTimeFormatter.ofPattern("dd/MM/uuuu")),
                address));
        // If OCR is still running, evaluate returns a conflict; retry /process later.
        return evaluateVerification(applicationId, verification);
    }

    public ReviewResponse evaluate(Long applicationId) {

        VerificationResponse verification =
            verificationClient.getLatest(
                applicationId.toString()
            );

        ApplicationSnapshot snapshot =
            customerClient.getSnapshot(applicationId);

        if (snapshot == null
                || !applicationId.equals(
                    snapshot.applicationId())
                || snapshot.version() == null) {

            throw new IllegalStateException(
                "Invalid application snapshot"
            );
        }

        /*
         * The frontend has already completed verification,
         * but the application is still SUBMITTED.
         * Move it to PROCESSING before creating the review.
         */
        if ("SUBMITTED".equals(
                snapshot.applicationStatus())) {

            String reference =
                "START-" + applicationId
                + "-" + snapshot.version();

            ApplicationStatusUpdateResponse updated =
                customerClient.updateStatus(
                    applicationId,
                    new ApplicationStatusUpdateRequest(
                        "PROCESSING",
                        "Verification completed; evaluating result",
                        reference,
                        snapshot.version()
                    )
                );

            if (updated == null
                    || !applicationId.equals(
                        updated.applicationId())
                    || !reference.equals(
                        updated.decisionReference())
                    || !"PROCESSING".equals(
                        updated.applicationStatus())
                    || updated.version() == null
                    || !Objects.equals(
                        updated.version(),
                        snapshot.version() + 1)) {

                throw new IllegalStateException(
                    "Invalid processing-start response"
                );
            }
        }

        return evaluateVerification(
            applicationId,
            verification
        );
    }

    private ReviewResponse evaluateVerification(
            Long applicationId, VerificationResponse verification) {
        if (verification == null
                || verification.verificationId() == null
                || verification.verificationId().isBlank()
                || verification.verifiedAt() == null
                || verification.decision() == null
                || verification.verificationId().length() > 36
                || !applicationId.toString().equals(
                    verification.applicationId())) {
            throw new IllegalStateException(
                "Invalid verification response");
        }

        ApplicationSnapshot snapshot =
            customerClient.getSnapshot(applicationId);

        if (snapshot == null
                || !applicationId.equals(
                    snapshot.applicationId())
                || snapshot.version() == null) {
            throw new IllegalStateException(
                "Invalid application snapshot");
        }

        // A verification completed before submission cannot be used.
        if (snapshot.submittedAt() == null
                || verification.verifiedAt().isBefore(
                    snapshot.submittedAt()
                        .toInstant(java.time.ZoneOffset.UTC))) {
            throw new IllegalStateException(
                "Verification predates application submission");
        }

        var existing = repository.findByVerificationId(
            verification.verificationId());

        if (existing.isPresent()) {
            ReviewCase found = existing.get();

            if (!applicationId.equals(
                    found.getApplicationId())) {
                throw new IllegalStateException(
                    "Verification belongs to another application");
            }

            return found.getReviewState() == ReviewState.PENDING_SYNC
                ? synchronize(found.getReviewId()) : toResponse(found);
        }

        if (!"PROCESSING".equals(
                snapshot.applicationStatus())) {
            throw new IllegalStateException(
                "Application must be PROCESSING");
        }

        String target = switch (verification.decision()) {
            case "RECOMMENDED_FOR_APPROVAL" ->
                "AUTO_CHECKS_PASSED";
            case "MANUAL_REVIEW" -> "MANUAL_REVIEW";
            // Verification supplies no structured replacement types.
            // Let an official select which documents must be replaced.
            case "RESUBMISSION_REQUIRED" -> "MANUAL_REVIEW";
            case "PROCESSING" -> null;
            default -> throw new IllegalStateException(
                "Unsupported verification outcome");
        };

        if (target == null) {
            throw new IllegalStateException(
                "Verification is still processing");
        }

        String reason = verification.reason();

        if (reason == null || reason.isBlank()) {
            reason = "Verification outcome: "
                + verification.decision();
        }

        if (reason.length() > 1000) {
            throw new IllegalStateException("Verification reason exceeds 1000 characters");
        }
        final String finalReason = reason.trim();

        ReviewCase review = transactions.execute(status -> {

            if (repository.existsByApplicationIdAndReviewStateIn(
                    applicationId, ACTIVE_STATES)) {
                throw new IllegalStateException(
                    "An active review already exists");
            }

            ReviewCase created = new ReviewCase();
            created.setApplicationId(applicationId);
            created.setVerificationId(
                verification.verificationId());
            created.setVerificationDecision(
                verification.decision());
            created.setReviewState(ReviewState.PENDING_SYNC);
            created.setTargetStatus(target);
            created.setReason(finalReason);
            created.setExpectedVersion(snapshot.version());
            created.setOperationReference(
                "DEC-" + UUID.randomUUID());

            return repository.saveAndFlush(created);
        });

        ReviewResponse response =
                synchronize(review.getReviewId());

        if ("MANUAL_REVIEW".equals(target)) {
            recordAudit(
                    applicationId,
                    "MANUAL_REVIEW_ASSIGNED",
                    finalReason,
                    "decision-service",
                    "SERVICE"
            );
        }

        return response;
    }

    public ReviewResponse submitReview(
            Long reviewId,
            ReviewActionRequest request,
            String reviewer) {

        if (reviewer == null || reviewer.isBlank() || reviewer.length() > 150) {
            throw new IllegalArgumentException("Invalid reviewer identity");
        }
        String replacementTypes = normalizeRequestedTypes(request);
        String reviewerReason = request.reason() == null ? null : request.reason().trim();
        if (!"APPROVED".equals(request.decision())
                && (reviewerReason == null || reviewerReason.isBlank())) {
            throw new IllegalStateException("A reason is required for this review decision");
        }
        if (reviewerReason != null && reviewerReason.length() > 1000) {
            throw new IllegalArgumentException("Reason exceeds 1000 characters");
        }
        final String actionReason = reviewerReason == null || reviewerReason.isBlank()
            ? "Approved by authorized reviewer" : reviewerReason;

        ReviewCase review = transactions.execute(status -> {

            ReviewCase found = repository.findLockedById(reviewId)
                .orElseThrow(() ->
                    new com.kycautomate.decision.exception.ReviewNotFoundException(
                        "Review case not found"));

            if (found.getReviewState() != ReviewState.OPEN) {
                throw new IllegalStateException(
                    "Review case is not open");
            }

            String current = found.getTargetStatus();
            String target = request.decision();

            if ("AUTO_CHECKS_PASSED".equals(current)) {
                if (!Set.of("APPROVED", "MANUAL_REVIEW").contains(target)) {
                    throw new IllegalStateException(
                        "Approve this case or escalate it to MANUAL_REVIEW");
                }
            } else if ("MANUAL_REVIEW".equals(current)) {
                if (!Set.of(
                        "APPROVED",
                        "REJECTED",
                        "RESUBMISSION_REQUIRED"
                    ).contains(target)) {
                    throw new IllegalStateException(
                        "Invalid reviewer decision");
                }
            } else {
                throw new IllegalStateException(
                    "Case is not eligible for reviewer decision");
            }

            if (found.getCustomerVersion() == null) {
                throw new IllegalStateException(
                    "Customer application version unavailable");
            }

            found.setExpectedVersion(
                found.getCustomerVersion());
            found.setTargetStatus(target);
            found.setReason(actionReason);
            found.setRequestedDocumentTypes(replacementTypes);
            found.setReviewer(reviewer);
            found.setOperationReference(
                "REV-" + UUID.randomUUID());
            found.setReviewState(ReviewState.PENDING_SYNC);

            return repository.saveAndFlush(found);
        });

        ReviewResponse response =
                synchronize(review.getReviewId());

        String eventType = switch (request.decision()) {
            case "APPROVED" -> "REVIEWER_APPROVED";
            case "REJECTED" -> "REVIEWER_REJECTED";
            case "RESUBMISSION_REQUIRED" ->
                    "RESUBMISSION_REQUESTED";
            case "MANUAL_REVIEW" ->
                    "MANUAL_REVIEW_ASSIGNED";
            default -> null;
        };

        if (eventType != null) {
            recordAudit(
                    review.getApplicationId(),
                    eventType,
                    actionReason,
                    reviewer,
                    "REVIEWER"
            );
        }

        return response;
    }

    public ReviewResponse synchronize(Long reviewId) {

        ReviewCase review = repository.findById(reviewId)
            .orElseThrow(() ->
                new com.kycautomate.decision.exception.ReviewNotFoundException(
                    "Review case not found"));

        if (review.getReviewState()
                != ReviewState.PENDING_SYNC) {
            return toResponse(review);
        }

        ApplicationStatusUpdateResponse updated;

        try {
            updated = customerClient.updateStatus(
                review.getApplicationId(),
                new ApplicationStatusUpdateRequest(
                    review.getTargetStatus(),
                    review.getReason(),
                    review.getOperationReference(),
                    review.getExpectedVersion(),
                    requestedTypes(review.getRequestedDocumentTypes())
                )
            );
        } catch (FeignException.Conflict ex) {
            transactions.executeWithoutResult(status -> {
                ReviewCase locked = repository
                    .findLockedById(reviewId)
                    .orElseThrow();

                if (locked.getReviewState() == ReviewState.PENDING_SYNC
                        && Objects.equals(locked.getOperationReference(),
                            review.getOperationReference())) {
                    locked.setReviewState(
                        ReviewState.SYNC_CONFLICT);
                    repository.saveAndFlush(locked);
                }
            });

            throw new IllegalStateException(
                "Customer application version or status conflict");
        }

        if (updated == null
                || !Objects.equals(
                    review.getApplicationId(),
                    updated.applicationId())
                || !Objects.equals(
                    review.getOperationReference(),
                    updated.decisionReference())
                || !Objects.equals(
                    review.getTargetStatus(),
                    updated.applicationStatus())
                || updated.version() == null
                || !Objects.equals(
                    updated.version(), review.getExpectedVersion() + 1)) {
            throw new IllegalStateException(
                "Invalid Customer Service update response");
        }

        return transactions.execute(status -> {

            ReviewCase locked = repository
                .findLockedById(reviewId)
                .orElseThrow();

            // Another request may have completed this operation and then
            // accepted a newer reviewer action while this HTTP call was in flight.
            if (locked.getReviewState() != ReviewState.PENDING_SYNC
                    || !Objects.equals(locked.getOperationReference(),
                        review.getOperationReference())) {
                return toResponse(locked);
            }

            locked.setCustomerVersion(updated.version());

            boolean needsReviewer =
                Set.of(
                    "AUTO_CHECKS_PASSED",
                    "MANUAL_REVIEW"
                ).contains(locked.getTargetStatus());

            locked.setReviewState(
                needsReviewer
                    ? ReviewState.OPEN
                    : ReviewState.COMPLETED
            );

            return toResponse(repository.saveAndFlush(locked));
        });
    }

    public ReviewResponse getReview(Long reviewId) {
        return toResponse(repository.findById(reviewId)
            .orElseThrow(() ->
                new com.kycautomate.decision.exception.ReviewNotFoundException(
                    "Review case not found")));
    }

    public List<ReviewResponse> pendingReviews() {
        return repository
            .findByReviewStateInOrderByCreatedAtAsc(ACTIVE_STATES)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    public List<ReviewResponse> applicationReviews(
            Long applicationId) {
        return repository
            .findByApplicationIdOrderByCreatedAtDesc(
                applicationId)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    private ReviewResponse toResponse(ReviewCase review) {
        return new ReviewResponse(
            review.getReviewId(),
            review.getApplicationId(),
            review.getVerificationId(),
            review.getVerificationDecision(),
            review.getReviewState().name(),
            review.getTargetStatus(),
            review.getReason(),
            review.getReviewer(),
            review.getOperationReference(),
            review.getCustomerVersion(),
            review.getCreatedAt(),
            review.getUpdatedAt(),
            requestedTypes(review.getRequestedDocumentTypes())
        );
    }

    private Set<String> requestedTypes(String value) {
        return value == null || value.isBlank() ? Set.of()
            : Set.copyOf(java.util.Arrays.asList(value.split(",")));
    }

    private String normalizeRequestedTypes(ReviewActionRequest request) {
        Set<String> types = request.requestedDocumentTypes() == null
            ? Set.of() : request.requestedDocumentTypes();
        Set<String> supported = Set.of("PAN", "AADHAAR", "PASSPORT",
            "DRIVING_LICENCE", "VOTER_ID", "ELECTRICITY_BILL");
        if (types.stream().anyMatch(type -> type == null || !supported.contains(type))) {
            throw new IllegalArgumentException("Unsupported replacement document type");
        }
        if ("RESUBMISSION_REQUIRED".equals(request.decision())) {
            if (types.isEmpty()) throw new IllegalArgumentException(
                "Specify requestedDocumentTypes for resubmission");
            return types.stream().sorted().collect(
                java.util.stream.Collectors.joining(","));
        }
        if (!types.isEmpty()) throw new IllegalArgumentException(
            "requestedDocumentTypes is only valid for RESUBMISSION_REQUIRED");
        return null;
    }
    
    private void recordAudit(
            Long applicationId,
            String eventType,
            String reason,
            String actor,
            String actorRole) {

        try {
            auditClient.recordEvent(new AuditEventRequest(
                    String.valueOf(applicationId),
                    eventType,
                    reason,
                    actor,
                    actorRole
            ));
        } catch (Exception exception) {
            System.err.println(
                    "Audit event failed: " + exception.getMessage());
        }
    }
}
