package com.kycautomate.decision.entity;

import java.time.Instant;

import com.kycautomate.decision.enums.ReviewState;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "REVIEW_CASES")
public class ReviewCase {

    // =========================================
    // 1. PRIMARY KEY
    // =========================================

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "REVIEW_ID")
    private Long reviewId;

    // =========================================
    // 2. APPLICATION AND VERIFICATION DETAILS
    // =========================================

    @Column(name = "APPLICATION_ID", nullable = false)
    private Long applicationId;

    @Column(
        name = "VERIFICATION_ID",
        nullable = false,
        length = 36,
        unique = true
    )
    private String verificationId;

    @Column(
        name = "VERIFICATION_DECISION",
        nullable = false,
        length = 40
    )
    private String verificationDecision;

    // =========================================
    // 3. REVIEW WORKFLOW
    // =========================================

    @Enumerated(EnumType.STRING)
    @Column(
        name = "REVIEW_STATE",
        nullable = false,
        length = 30
    )
    private ReviewState reviewState =
        ReviewState.PENDING_SYNC;

    @Column(name = "TARGET_STATUS", length = 40)
    private String targetStatus;

    @Column(name = "REASON", length = 1000)
    private String reason;

    @Column(name = "REQUESTED_DOCUMENT_TYPES", length = 200)
    private String requestedDocumentTypes;

    @Column(name = "REVIEWER", length = 150)
    private String reviewer;

    // =========================================
    // 4. IDEMPOTENCY AND VERSION TRACKING
    // =========================================

    @Column(
        name = "OPERATION_REFERENCE",
        length = 100,
        unique = true
    )
    private String operationReference;

    @Column(name = "EXPECTED_VERSION")
    private Integer expectedVersion;

    @Column(name = "CUSTOMER_VERSION")
    private Integer customerVersion;

    // =========================================
    // 5. AUDIT TIMESTAMPS
    // =========================================

    @Column(
        name = "CREATED_AT",
        nullable = false,
        updatable = false
    )
    private Instant createdAt;

    @Column(
        name = "UPDATED_AT",
        nullable = false
    )
    private Instant updatedAt;

    // =========================================
    // 6. OPTIMISTIC LOCKING
    // =========================================

    @Version
    @Column(name = "ROW_VERSION", nullable = false)
    private Long rowVersion;

    // =========================================
    // 7. JPA LIFECYCLE CALLBACKS
    // =========================================

    @PrePersist
    public void onCreate() {
        Instant now = Instant.now();

        if (createdAt == null) {
            createdAt = now;
        }

        updatedAt = now;
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = Instant.now();
    }

    // =========================================
    // 8. GETTERS AND SETTERS
    // =========================================

    public Long getReviewId() {
        return reviewId;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }

    public String getVerificationId() {
        return verificationId;
    }

    public void setVerificationId(String verificationId) {
        this.verificationId = verificationId;
    }

    public String getVerificationDecision() {
        return verificationDecision;
    }

    public void setVerificationDecision(
            String verificationDecision) {
        this.verificationDecision = verificationDecision;
    }

    public ReviewState getReviewState() {
        return reviewState;
    }

    public void setReviewState(ReviewState reviewState) {
        this.reviewState = reviewState;
    }

    public String getTargetStatus() {
        return targetStatus;
    }

    public void setTargetStatus(String targetStatus) {
        this.targetStatus = targetStatus;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getReviewer() {
        return reviewer;
    }

    public void setReviewer(String reviewer) {
        this.reviewer = reviewer;
    }

    public String getOperationReference() {
        return operationReference;
    }

    public void setOperationReference(
            String operationReference) {
        this.operationReference = operationReference;
    }

    public Integer getExpectedVersion() {
        return expectedVersion;
    }

    public void setExpectedVersion(
            Integer expectedVersion) {
        this.expectedVersion = expectedVersion;
    }

    public Integer getCustomerVersion() {
        return customerVersion;
    }

    public void setCustomerVersion(
            Integer customerVersion) {
        this.customerVersion = customerVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getRowVersion() {
        return rowVersion;
    }

    public String getRequestedDocumentTypes() { return requestedDocumentTypes; }
    public void setRequestedDocumentTypes(String value) {
        requestedDocumentTypes = value;
    }
}