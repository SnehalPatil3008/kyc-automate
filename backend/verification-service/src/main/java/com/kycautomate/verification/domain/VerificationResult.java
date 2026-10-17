package com.kycautomate.verification.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "verification_results")
public class VerificationResult {

    @Id
    @Column(name = "verification_id", length = 36)
    private String verificationId;

    @Column(name = "application_id", nullable = false, length = 36)
    private String applicationId;

    @Column(name = "decision", nullable = false, length = 40)
    private String decision;

    @Column(name = "reason", length = 2000)
    private String reason;

    @Column(name = "name_score")
    private Double nameScore;

    @Column(name = "address_score")
    private Double addressScore;

    @Lob
    @Column(name = "evidence_json")
    private String evidenceJson;

    @Column(name = "verified_at", nullable = false, updatable = false)
    private Instant verifiedAt;

    @PrePersist
    void prePersist() {
        verifiedAt = Instant.now();
    }

    public String getVerificationId() { return verificationId; }
    public void setVerificationId(String verificationId) { this.verificationId = verificationId; }
    public String getApplicationId() { return applicationId; }
    public void setApplicationId(String applicationId) { this.applicationId = applicationId; }
    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Double getNameScore() { return nameScore; }
    public void setNameScore(Double nameScore) { this.nameScore = nameScore; }
    public Double getAddressScore() { return addressScore; }
    public void setAddressScore(Double addressScore) { this.addressScore = addressScore; }
    public String getEvidenceJson() { return evidenceJson; }
    public void setEvidenceJson(String evidenceJson) { this.evidenceJson = evidenceJson; }
    public Instant getVerifiedAt() { return verifiedAt; }
}
