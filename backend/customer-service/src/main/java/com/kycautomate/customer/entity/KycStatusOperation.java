
package com.kycautomate.customer.entity;

import java.time.LocalDateTime;

import com.kycautomate.customer.enums.ApplicationStatus;

import jakarta.persistence.*;

@Entity
@Table(name = "KYC_STATUS_OPERATIONS")
public class KycStatusOperation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "OPERATION_ID")
    private Long operationId;

    @Column(name = "APPLICATION_ID", nullable = false)
    private Long applicationId;

    @Column(name = "DECISION_REFERENCE",
            nullable = false, length = 100, unique = true)
    private String decisionReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "FROM_STATUS", nullable = false, length = 40)
    private ApplicationStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "TO_STATUS", nullable = false, length = 40)
    private ApplicationStatus toStatus;

    @Column(name = "REASON", length = 1000)
    private String reason;

    @Column(name = "EXPECTED_VERSION", nullable = false)
    private Integer expectedVersion;

    @Column(name = "RESULT_VERSION", nullable = false)
    private Integer resultVersion;

    @Column(name = "CREATED_AT", nullable = false,
            insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public Long getOperationId() {
        return operationId;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }

    public String getDecisionReference() {
        return decisionReference;
    }

    public void setDecisionReference(String decisionReference) {
        this.decisionReference = decisionReference;
    }

    public ApplicationStatus getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(ApplicationStatus fromStatus) {
        this.fromStatus = fromStatus;
    }

    public ApplicationStatus getToStatus() {
        return toStatus;
    }

    public void setToStatus(ApplicationStatus toStatus) {
        this.toStatus = toStatus;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Integer getExpectedVersion() {
        return expectedVersion;
    }

    public void setExpectedVersion(Integer expectedVersion) {
        this.expectedVersion = expectedVersion;
    }

    public Integer getResultVersion() {
        return resultVersion;
    }

    public void setResultVersion(Integer resultVersion) {
        this.resultVersion = resultVersion;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
