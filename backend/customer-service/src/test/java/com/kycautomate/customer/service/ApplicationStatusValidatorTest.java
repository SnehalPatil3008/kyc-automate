
package com.kycautomate.customer.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.kycautomate.customer.enums.ApplicationStatus;
import com.kycautomate.customer.exception.BusinessConflictException;

class ApplicationStatusValidatorTest {

    private final ApplicationStatusValidator validator =
        new ApplicationStatusValidator();

    @Test
    void draftCanBeSubmitted() {
        assertDoesNotThrow(() ->
            validator.validateInitialSubmission(
                ApplicationStatus.DRAFT));
    }

    @Test
    void submittedCannotBeSubmittedAgain() {
        assertThrows(BusinessConflictException.class, () ->
            validator.validateInitialSubmission(
                ApplicationStatus.SUBMITTED));
    }

    @Test
    void submittedCanMoveToProcessing() {
        assertDoesNotThrow(() ->
            validator.validateDecisionTransition(
                ApplicationStatus.SUBMITTED,
                ApplicationStatus.PROCESSING,
                "Verification started"));
    }

    @Test
    void processingCanMoveToManualReview() {
        assertDoesNotThrow(() ->
            validator.validateDecisionTransition(
                ApplicationStatus.PROCESSING,
                ApplicationStatus.MANUAL_REVIEW,
                "Low OCR confidence"));
    }

    @Test
    void cannotApproveDraft() {
        assertThrows(BusinessConflictException.class, () ->
            validator.validateDecisionTransition(
                ApplicationStatus.DRAFT,
                ApplicationStatus.APPROVED,
                "Approved"));
    }

    @Test
    void resubmissionRequiresReason() {
        assertThrows(BusinessConflictException.class, () ->
            validator.validateDecisionTransition(
                ApplicationStatus.MANUAL_REVIEW,
                ApplicationStatus.RESUBMISSION_REQUIRED,
                null));
    }

    @Test
    void approvedApplicationCannotChange() {
        assertThrows(BusinessConflictException.class, () ->
            validator.validateDecisionTransition(
                ApplicationStatus.APPROVED,
                ApplicationStatus.PROCESSING,
                "Retry"));
    }
}
