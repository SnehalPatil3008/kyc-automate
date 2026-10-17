
package com.kycautomate.customer.service;

import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.kycautomate.customer.enums.ApplicationStatus;
import com.kycautomate.customer.exception.BusinessConflictException;

@Component
public class ApplicationStatusValidator {

    private static final Map<ApplicationStatus,
            Set<ApplicationStatus>> DECISION_TRANSITIONS =
            Map.of(
                ApplicationStatus.SUBMITTED,
                Set.of(ApplicationStatus.PROCESSING),

                ApplicationStatus.PROCESSING,
                Set.of(
                    ApplicationStatus.AUTO_CHECKS_PASSED,
                    ApplicationStatus.MANUAL_REVIEW,
                    ApplicationStatus.RESUBMISSION_REQUIRED
                ),

                ApplicationStatus.AUTO_CHECKS_PASSED,
                Set.of(
                    ApplicationStatus.APPROVED,
                    ApplicationStatus.MANUAL_REVIEW
                ),

                ApplicationStatus.MANUAL_REVIEW,
                Set.of(
                    ApplicationStatus.APPROVED,
                    ApplicationStatus.REJECTED,
                    ApplicationStatus.RESUBMISSION_REQUIRED
                )
            );

    public void validateInitialSubmission(
            ApplicationStatus current) {

        if (current != ApplicationStatus.DRAFT) {
            throw new BusinessConflictException(
                "Only DRAFT applications can be submitted");
        }
    }

    public void validateResubmission(
            ApplicationStatus current) {

        if (current != ApplicationStatus.RESUBMISSION_REQUIRED) {
            throw new BusinessConflictException(
                "Application is not awaiting resubmission");
        }
    }

    public void validateDecisionTransition(
            ApplicationStatus current,
            ApplicationStatus next,
            String reason) {

        if (current == null || next == null ||
                !DECISION_TRANSITIONS
                    .getOrDefault(current, Set.of())
                    .contains(next)) {

            throw new BusinessConflictException(
                "Invalid KYC status transition: "
                    + current + " -> " + next);
        }

        if (requiresReason(next) &&
                (reason == null || reason.isBlank())) {

            throw new BusinessConflictException(
                "A reason is required for status " + next);
        }
    }

    private boolean requiresReason(ApplicationStatus status) {
        return status == ApplicationStatus.REJECTED
            || status == ApplicationStatus.MANUAL_REVIEW
            || status == ApplicationStatus.RESUBMISSION_REQUIRED;
    }
}
