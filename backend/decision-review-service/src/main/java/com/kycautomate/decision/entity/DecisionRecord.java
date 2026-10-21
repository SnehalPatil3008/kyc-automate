
package com.kycautomate.decision.entity;

import java.time.Instant;

import com.kycautomate.decision.enums.ApplicationStatus;

import jakarta.persistence.*;

@Entity
@Table(
    name = "KYC_DECISIONS",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "UQ_DECISION_APPLICATION",
            columnNames = "APPLICATION_ID"
        )
    }
)
public class DecisionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "DECISION_ID")
    private Long decisionId;

    @Column(name = "APPLICATION_ID", nullable = false)
    private Long applicationId;

    @Column(name = "VERIFICATION_ID", length = 36)
    private String verificationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 40)
    private ApplicationStatus status;

    @Column(name = "REASON", length = 1000)
    private String reason;

    @Column(name = "NAME_SCORE")
    private Double nameScore;

    @Column(name = "ADDRESS_SCORE")
    private Double addressScore;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Integer version;

    @PrePersist
    public void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getDecisionId() {
        return decisionId;
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

    public ApplicationStatus getStatus() {
        return status;
    }

    public void setStatus(ApplicationStatus status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Double getNameScore() {
        return nameScore;
    }

    public void setNameScore(Double nameScore) {
        this.nameScore = nameScore;
    }

    public Double getAddressScore() {
        return addressScore;
    }

    public void setAddressScore(Double addressScore) {
        this.addressScore = addressScore;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Integer getVersion() {
        return version;
    }
}
