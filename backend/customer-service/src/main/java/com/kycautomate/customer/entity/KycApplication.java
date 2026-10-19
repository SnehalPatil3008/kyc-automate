package com.kycautomate.customer.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import com.kycautomate.customer.enums.ApplicationStatus;

import jakarta.persistence.*;

@Entity
@Table(name = "KYC_APPLICATIONS")
public class KycApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "APPLICATION_ID")
    private Long applicationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "CUSTOMER_ID", nullable = false)
    private Customer customer;

    @Column(name = "APPLICATION_REF",
            nullable = false, unique = true, length = 40)
    private String applicationRef;

    @Column(name = "APPLICANT_NAME", nullable = false, length = 150)
    private String applicantName;

    @Column(name = "APPLICANT_DOB", nullable = false)
    private LocalDate applicantDob;

    @Column(name = "APPLICANT_ADDRESS", nullable = false, length = 300)
    private String applicantAddress;

    @Column(name = "APPLICANT_CITY", length = 100)
    private String applicantCity;

    @Column(name = "APPLICANT_STATE", length = 100)
    private String applicantState;

    @Column(name = "APPLICANT_POSTAL_CODE", length = 12)
    private String applicantPostalCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "APPLICATION_STATUS", nullable = false, length = 40)
    private ApplicationStatus applicationStatus =
            ApplicationStatus.DRAFT;

    @Column(name = "STATUS_REASON", length = 1000)
    private String statusReason;


@Column(name = "CREATED_AT", nullable = false, updatable = false)
private LocalDateTime createdAt;


    @Column(name = "SUBMITTED_AT")
    private LocalDateTime submittedAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "REQUESTED_DOCUMENT_TYPES", length = 200)
    private String requestedDocumentTypes;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Integer version;


@PrePersist
public void onCreate() {

    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

    createdAt = now;
    updatedAt = now;
}

@PreUpdate
public void onUpdate() {

    updatedAt = LocalDateTime.now(ZoneOffset.UTC);
}


    public Long getApplicationId() { return applicationId; }

    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public String getApplicationRef() { return applicationRef; }
    public void setApplicationRef(String applicationRef) {
        this.applicationRef = applicationRef;
    }

    public String getApplicantName() { return applicantName; }
    public void setApplicantName(String applicantName) {
        this.applicantName = applicantName;
    }

    public LocalDate getApplicantDob() { return applicantDob; }
    public void setApplicantDob(LocalDate applicantDob) {
        this.applicantDob = applicantDob;
    }

    public String getApplicantAddress() { return applicantAddress; }
    public void setApplicantAddress(String applicantAddress) {
        this.applicantAddress = applicantAddress;
    }

    public String getApplicantCity() { return applicantCity; }
    public void setApplicantCity(String applicantCity) {
        this.applicantCity = applicantCity;
    }

    public String getApplicantState() { return applicantState; }
    public void setApplicantState(String applicantState) {
        this.applicantState = applicantState;
    }

    public String getApplicantPostalCode() {
        return applicantPostalCode;
    }
    public void setApplicantPostalCode(String applicantPostalCode) {
        this.applicantPostalCode = applicantPostalCode;
    }

    public ApplicationStatus getApplicationStatus() {
        return applicationStatus;
    }
    public void setApplicationStatus(ApplicationStatus status) {
        this.applicationStatus = status;
    }

    public String getStatusReason() { return statusReason; }
    public void setStatusReason(String statusReason) {
        this.statusReason = statusReason;
    }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public Integer getVersion() { return version; }

    public String getRequestedDocumentTypes() { return requestedDocumentTypes; }
    public void setRequestedDocumentTypes(String value) {
        requestedDocumentTypes = value;
    }

}