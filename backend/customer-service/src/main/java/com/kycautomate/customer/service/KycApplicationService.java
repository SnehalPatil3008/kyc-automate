
package com.kycautomate.customer.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kycautomate.customer.dto.ApplicationStatusUpdateRequest;
import com.kycautomate.customer.dto.ApplicationStatusUpdateResponse;
import com.kycautomate.customer.dto.InternalKycApplicationResponse;
import com.kycautomate.customer.dto.KycApplicationResponse;

import com.kycautomate.customer.entity.Customer;
import com.kycautomate.customer.entity.KycApplication;
import com.kycautomate.customer.entity.KycStatusOperation;

import com.kycautomate.customer.enums.ApplicationStatus;

import com.kycautomate.customer.exception.BusinessConflictException;
import com.kycautomate.customer.exception.ResourceNotFoundException;

import com.kycautomate.customer.repository.KycApplicationRepository;
import com.kycautomate.customer.repository.KycStatusOperationRepository;

@Service
public class KycApplicationService {

    private final KycApplicationRepository applicationRepository;
    private final KycStatusOperationRepository operationRepository;
    private final CustomerService customerService;
    private final DocumentValidationService documentValidationService;
    private final ApplicationStatusValidator statusValidator;

    private static final List<ApplicationStatus> ACTIVE_STATUSES =
        List.of(
            ApplicationStatus.DRAFT,
            ApplicationStatus.SUBMITTED,
            ApplicationStatus.PROCESSING,
            ApplicationStatus.MANUAL_REVIEW,
            ApplicationStatus.AUTO_CHECKS_PASSED,
            ApplicationStatus.RESUBMISSION_REQUIRED
        );

    public KycApplicationService(
            KycApplicationRepository applicationRepository,
            KycStatusOperationRepository operationRepository,
            CustomerService customerService,
            DocumentValidationService documentValidationService,
            ApplicationStatusValidator statusValidator) {

        this.applicationRepository = applicationRepository;
        this.operationRepository = operationRepository;
        this.customerService = customerService;
        this.documentValidationService = documentValidationService;
        this.statusValidator = statusValidator;
    }

    // =========================================
    // 1. CREATE KYC APPLICATION
    // =========================================

    @Transactional
    public KycApplicationResponse createApplication(String email) {

        Customer customer = customerService.getCustomerEntity(email);

        boolean activeExists = applicationRepository
            .existsByCustomerCustomerIdAndApplicationStatusIn(
                customer.getCustomerId(), ACTIVE_STATUSES);

        if (activeExists) {
            throw new BusinessConflictException(
                "An active KYC application already exists");
        }

        KycApplication application = new KycApplication();

        application.setCustomer(customer);

        application.setApplicationRef(
            "KYC-" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .toUpperCase(Locale.ROOT));

        application.setApplicantName(customer.getFullName());
        application.setApplicantDob(customer.getDateOfBirth());
        application.setApplicantAddress(customer.getAddress());
        application.setApplicantCity(customer.getCity());
        application.setApplicantState(customer.getState());
        application.setApplicantPostalCode(customer.getPostalCode());

        application.setApplicationStatus(ApplicationStatus.DRAFT);

        validateApplicationDetails(application);

        return toResponse(
            applicationRepository.saveAndFlush(application));
    }

    // =========================================
    // 2. SUBMIT KYC APPLICATION
    // =========================================

    @Transactional
    public KycApplicationResponse submitApplication(
            String email, Long applicationId) {

        KycApplication application =
            getOwnedApplication(email, applicationId);

        statusValidator.validateInitialSubmission(
            application.getApplicationStatus());

        validateApplicationDetails(application);

        documentValidationService.validateDocuments(applicationId);

        application.setApplicationStatus(ApplicationStatus.SUBMITTED);
        application.setSubmittedAt(
            LocalDateTime.now(ZoneOffset.UTC));

        return toResponse(
            applicationRepository.saveAndFlush(application));
    }

    // =========================================
    // 3. RESUBMIT APPLICATION
    // =========================================

    @Transactional
    public KycApplicationResponse resubmitApplication(
            String email, Long applicationId) {

        KycApplication application =
            getOwnedApplication(email, applicationId);

        statusValidator.validateResubmission(
            application.getApplicationStatus());

        validateApplicationDetails(application);

        // This remains blocked until Document Service
        // verifies the specifically requested replacements.

        throw new BusinessConflictException(
            "Resubmission requires verification of newly "
            + "uploaded replacement documents");
    }

    // =========================================
    // 4. GET CUSTOMER APPLICATIONS
    // =========================================

    @Transactional(readOnly = true)
    public List<KycApplicationResponse> getMyApplications(
            String email) {

        Customer customer = customerService.getCustomerEntity(email);

        return applicationRepository
            .findByCustomerCustomerIdOrderByCreatedAtDesc(
                customer.getCustomerId())
            .stream()
            .map(this::toResponse)
            .toList();
    }

    // =========================================
    // 5. GET CUSTOMER APPLICATION BY ID
    // =========================================

    @Transactional(readOnly = true)
    public KycApplicationResponse getApplication(
            String email, Long applicationId) {

        return toResponse(
            getOwnedApplication(email, applicationId));
    }

    // =========================================
    // 6. INTERNAL SNAPSHOT
    // =========================================

    @Transactional(readOnly = true)
    public InternalKycApplicationResponse getInternalSnapshot(
            Long applicationId) {

        KycApplication application = applicationRepository
            .findById(applicationId)
            .orElseThrow(() ->
                new ResourceNotFoundException(
                    "KYC application not found"));

        if (application.getApplicationStatus()
                == ApplicationStatus.DRAFT) {
            throw new BusinessConflictException(
                "Draft application is not ready for verification");
        }

        return new InternalKycApplicationResponse(
            application.getApplicationId(),
            application.getApplicationRef(),
            application.getCustomer().getCustomerId(),
            application.getApplicantName(),
            application.getApplicantDob(),
            application.getApplicantAddress(),
            application.getApplicantCity(),
            application.getApplicantState(),
            application.getApplicantPostalCode(),
            application.getApplicationStatus(),
            application.getVersion(),
            application.getSubmittedAt()
        );
    }

    // =========================================
    // 7. IDEMPOTENT INTERNAL STATUS UPDATE
    // =========================================

    @Transactional
    public ApplicationStatusUpdateResponse updateApplicationStatus(
            Long applicationId,
            ApplicationStatusUpdateRequest request) {

        String reference = request.decisionReference().trim();
        String reason = normalizeReason(request.reason());

        // Lock this application so updates to the same
        // application are serialized.
        KycApplication application = applicationRepository
            .findByIdForUpdate(applicationId)
            .orElseThrow(() ->
                new ResourceNotFoundException(
                    "KYC application not found"));

        // Check for a previously completed operation.
        var previousOperation =
            operationRepository.findByDecisionReference(reference);

        if (previousOperation.isPresent()) {

            KycStatusOperation previous = previousOperation.get();

            if (!previous.getApplicationId().equals(applicationId)
                    || previous.getToStatus() != request.newStatus()
                    || !java.util.Objects.equals(
                        previous.getReason(), reason)
                    || !previous.getExpectedVersion()
                        .equals(request.expectedVersion())) {

                throw new BusinessConflictException(
                    "Decision reference already used "
                    + "for a different request");
            }

            return toOperationResponse(previous);
        }

        // Check the expected application version.
        if (!request.expectedVersion()
                .equals(application.getVersion())) {

            throw new BusinessConflictException(
                "Application version has changed. "
                + "Refresh and retry.");
        }

        // Validate the permitted status transition.
        ApplicationStatus oldStatus =
            application.getApplicationStatus();

        statusValidator.validateDecisionTransition(
            oldStatus,
            request.newStatus(),
            reason
        );

        // Update the application.
        application.setApplicationStatus(request.newStatus());
        application.setStatusReason(reason);

        KycApplication saved =
            applicationRepository.saveAndFlush(application);

        // Record the successful operation.
        KycStatusOperation operation = new KycStatusOperation();

        operation.setApplicationId(applicationId);
        operation.setDecisionReference(reference);
        operation.setFromStatus(oldStatus);
        operation.setToStatus(request.newStatus());
        operation.setReason(reason);
        operation.setExpectedVersion(request.expectedVersion());
        operation.setResultVersion(saved.getVersion());

        operationRepository.saveAndFlush(operation);

        return toOperationResponse(operation);
    }

    // =========================================
    // 8. ORIGINAL OPERATION RESULT
    // =========================================

    private ApplicationStatusUpdateResponse toOperationResponse(
            KycStatusOperation operation) {

        return new ApplicationStatusUpdateResponse(
            operation.getApplicationId(),
            operation.getDecisionReference(),
            operation.getFromStatus(),
            operation.getToStatus(),
            operation.getReason(),
            operation.getResultVersion()
        );
    }

    // =========================================
    // 9. NORMALIZE REASON
    // =========================================

    private String normalizeReason(String reason) {

        if (reason == null || reason.isBlank()) {
            return null;
        }

        return reason.trim();
    }

    // =========================================
    // 10. APPLICATION OWNERSHIP
    // =========================================

    private KycApplication getOwnedApplication(
            String email, Long applicationId) {

        Customer customer = customerService.getCustomerEntity(email);

        return applicationRepository
            .findByApplicationIdAndCustomerCustomerId(
                applicationId, customer.getCustomerId())
            .orElseThrow(() ->
                new ResourceNotFoundException(
                    "KYC application not found"));
    }

    // =========================================
    // 11. VALIDATE APPLICATION SNAPSHOT
    // =========================================

    private void validateApplicationDetails(
            KycApplication application) {

        if (application.getApplicantName() == null
                || application.getApplicantName().isBlank()
                || application.getApplicantDob() == null
                || !application.getApplicantDob()
                    .isBefore(LocalDate.now(ZoneOffset.UTC))
                || application.getApplicantAddress() == null
                || application.getApplicantAddress().isBlank()
                || application.getApplicantCity() == null
                || application.getApplicantCity().isBlank()
                || application.getApplicantState() == null
                || application.getApplicantState().isBlank()
                || application.getApplicantPostalCode() == null
                || application.getApplicantPostalCode().isBlank()) {

            throw new BusinessConflictException(
                "Mandatory KYC application details "
                + "are incomplete or invalid");
        }
    }

    // =========================================
    // 12. ENTITY TO RESPONSE DTO
    // =========================================

    private KycApplicationResponse toResponse(
            KycApplication application) {

        return new KycApplicationResponse(
            application.getApplicationId(),
            application.getApplicationRef(),
            application.getApplicantName(),
            application.getApplicantDob(),
            application.getApplicantAddress(),
            application.getApplicantCity(),
            application.getApplicantState(),
            application.getApplicantPostalCode(),
            application.getApplicationStatus(),
            application.getStatusReason(),
            application.getCreatedAt(),
            application.getSubmittedAt(),
            application.getUpdatedAt(),
            application.getVersion()
        );
    }
}
