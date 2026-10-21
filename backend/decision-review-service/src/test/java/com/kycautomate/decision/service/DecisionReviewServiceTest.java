package com.kycautomate.decision.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.*;
import com.kycautomate.decision.client.*;
import com.kycautomate.decision.dto.*;
import com.kycautomate.decision.entity.ReviewCase;
import com.kycautomate.decision.enums.ReviewState;
import com.kycautomate.decision.repository.ReviewCaseRepository;

@ExtendWith(MockitoExtension.class)
class DecisionReviewServiceTest {
    @Mock CustomerClient customer;
    @Mock VerificationClient verification;
    @Mock ReviewCaseRepository repository;
    @Mock TransactionTemplate transactions;
    DecisionReviewService service;

    @BeforeEach @SuppressWarnings("unchecked")
    void setup() {
        lenient().when(transactions.execute(any())).thenAnswer(invocation ->
            ((TransactionCallback<Object>) invocation.getArgument(0))
                .doInTransaction(mock(TransactionStatus.class)));
        lenient().doAnswer(invocation -> {
            ((Consumer<TransactionStatus>) invocation.getArgument(0))
                .accept(mock(TransactionStatus.class));
            return null;
        }).when(transactions).executeWithoutResult(any());
        service = new DecisionReviewService(customer, verification, repository, transactions);
    }
    private ApplicationSnapshot snapshot(LocalDateTime submitted) {
        return new ApplicationSnapshot(4L, "KYC-TEST", 2L, "Test Customer",
            LocalDate.of(1990, 1, 1), "12 MG Road", "Bangalore", "Karnataka",
            "560001", "PROCESSING", 2, submitted);
    }
    private void currentSubmission() {
        when(customer.getSnapshot(4L)).thenReturn(
            snapshot(LocalDateTime.parse("2026-10-18T09:00:00")));
    }
    private void latest() {
        when(verification.getLatest("4")).thenReturn(new VerificationResponse(
            "verification-001", "4", "MANUAL_REVIEW", "Name mismatch",
            50.0, 80.0, Instant.parse("2026-10-18T10:00:00Z")));
    }
    private ReviewCase pending(String reference) {
        ReviewCase row = new ReviewCase();
        ReflectionTestUtils.setField(row, "reviewId", 10L);
        ReflectionTestUtils.setField(row, "createdAt",
            Instant.parse("2026-10-18T10:00:01Z"));
        row.setApplicationId(4L);
        row.setVerificationId("verification-001");
        row.setVerificationDecision("MANUAL_REVIEW");
        row.setReviewState(ReviewState.PENDING_SYNC);
        row.setTargetStatus("MANUAL_REVIEW");
        row.setReason("Name mismatch");
        row.setExpectedVersion(2);
        row.setOperationReference(reference);
        return row;
    }
    @Test void existingCurrentCaseDoesNotUpdateCustomer() {
        latest(); currentSubmission();
        ReviewCase row = pending("DEC-1"); row.setReviewState(ReviewState.OPEN);
        when(repository.findByVerificationId("verification-001")).thenReturn(Optional.of(row));
        assertEquals("OPEN", service.evaluate(4L).reviewState());
        verify(customer, never()).updateStatus(anyLong(), any());
    }
    @Test void previousSubmissionIsRejectedBeforeReturningExistingReview() {
        latest();
        when(customer.getSnapshot(4L)).thenReturn(
            snapshot(LocalDateTime.parse("2026-10-18T11:00:00")));
        assertThrows(IllegalStateException.class, () -> service.evaluate(4L));
        verify(repository, never()).findByVerificationId(anyString());
    }
    @Test void repeatedEvaluationRetriesPendingSyncWithOriginalReference() {
        latest(); currentSubmission();
        ReviewCase row = pending("DEC-1");
        when(repository.findByVerificationId("verification-001")).thenReturn(Optional.of(row));
        when(repository.findById(10L)).thenReturn(Optional.of(row));
        when(repository.findLockedById(10L)).thenReturn(Optional.of(row));
        when(repository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        when(customer.updateStatus(eq(4L), any())).thenReturn(
            new ApplicationStatusUpdateResponse(
                4L, "DEC-1", "PROCESSING", "MANUAL_REVIEW", "Name mismatch", 3));
        assertEquals("OPEN", service.evaluate(4L).reviewState());
        verify(customer).updateStatus(4L, new ApplicationStatusUpdateRequest(
            "MANUAL_REVIEW", "Name mismatch", "DEC-1", 2, Set.of()));
    }
    @Test void processReturnsExistingReviewInsteadOfCreatingAnotherVerification() {
        currentSubmission();
        ReviewCase row = pending("DEC-1"); row.setReviewState(ReviewState.OPEN);
        when(repository.findByApplicationIdAndReviewStateInOrderByCreatedAtDesc(eq(4L), anyList()))
            .thenReturn(List.of(row));
        assertEquals("OPEN", service.processApplication(4L).reviewState());
        verifyNoInteractions(verification);
    }
    @Test void pendingCaseCannotAcceptReviewerDecision() {
        when(repository.findLockedById(10L)).thenReturn(Optional.of(pending("DEC-1")));
        assertThrows(IllegalStateException.class, () ->
            service.submitReview(10L, new ReviewActionRequest("APPROVED", "Checked"), "alice"));
        verifyNoInteractions(customer);
    }
    @Test void oldAcknowledgementCannotCompleteNewerOperation() {
        ReviewCase old = pending("DEC-OLD"), newer = pending("REV-NEW");
        newer.setTargetStatus("APPROVED"); newer.setReviewer("alice"); newer.setExpectedVersion(3);
        when(repository.findById(10L)).thenReturn(Optional.of(old));
        when(repository.findLockedById(10L)).thenReturn(Optional.of(newer));
        when(customer.updateStatus(eq(4L), any())).thenReturn(
            new ApplicationStatusUpdateResponse(
                4L, "DEC-OLD", "PROCESSING", "MANUAL_REVIEW", "Name mismatch", 3));
        ReviewResponse response = service.synchronize(10L);
        assertEquals("REV-NEW", response.operationReference());
        assertEquals("PENDING_SYNC", response.reviewState());
        assertNull(newer.getCustomerVersion());
        verify(repository, never()).saveAndFlush(any());
    }
    @Test void resubmissionRequiresStructuredTypes() {
        assertThrows(IllegalArgumentException.class, () -> service.submitReview(10L,
            new ReviewActionRequest("RESUBMISSION_REQUIRED", "PAN unclear"), "alice"));
        verifyNoInteractions(repository, customer);
    }
    @Test void reviewResponseExposesRequestedReplacementTypes() {
        ReviewCase row = pending("REV-1");
        row.setRequestedDocumentTypes("AADHAAR,PAN");
        when(repository.findById(10L)).thenReturn(Optional.of(row));
        assertEquals(Set.of("PAN", "AADHAAR"), service.getReview(10L).requestedDocumentTypes());
    }
    @Test void missingCaseIsNotFound() {
        when(repository.findById(10L)).thenReturn(Optional.empty());
        assertThrows(com.kycautomate.decision.exception.ReviewNotFoundException.class,
            () -> service.getReview(10L));
    }

    private ApplicationSnapshot processingSnapshot(String status, int version) {
        return new ApplicationSnapshot(4L, "KYC-TEST", 2L, "Test Customer",
            LocalDate.of(1990, 1, 1), "12 MG Road", "Bangalore", "Karnataka",
            "560001", status, version, LocalDateTime.parse("2026-10-18T09:00:00"));
    }

    @Test void submittedProcessingUsesSnapshotVersion() {
        when(customer.getSnapshot(4L)).thenReturn(
            processingSnapshot("SUBMITTED", 2),
            processingSnapshot("PROCESSING", 3));
        when(repository.findByApplicationIdAndReviewStateInOrderByCreatedAtDesc(
                eq(4L), anyList())).thenReturn(List.of());
        when(customer.updateStatus(eq(4L), any())).thenReturn(
            new ApplicationStatusUpdateResponse(4L, "START-4-2", "SUBMITTED",
                "PROCESSING", "Verification started", 3));
        when(verification.verify(eq("4"), any())).thenReturn(
            new VerificationResponse("verification-002", "4", "PROCESSING",
                "OCR still running", null, null,
                Instant.parse("2026-10-18T10:00:00Z")));
        IllegalStateException error = assertThrows(IllegalStateException.class,
            () -> service.processApplication(4L));
        assertEquals("Verification is still processing", error.getMessage());
        verify(customer).updateStatus(4L, new ApplicationStatusUpdateRequest(
            "PROCESSING", "Verification started", "START-4-2", 2));
        verify(verification).verify(eq("4"), any());
    }

    @Test void invalidProcessingStartVersionIsRejected() {
        when(customer.getSnapshot(4L)).thenReturn(processingSnapshot("SUBMITTED", 2));
        when(repository.findByApplicationIdAndReviewStateInOrderByCreatedAtDesc(
                eq(4L), anyList())).thenReturn(List.of());
        when(customer.updateStatus(eq(4L), any())).thenReturn(
            new ApplicationStatusUpdateResponse(4L, "START-4-2", "SUBMITTED",
                "PROCESSING", "Verification started", 4));
        IllegalStateException error = assertThrows(IllegalStateException.class,
            () -> service.processApplication(4L));
        assertEquals("Invalid processing-start response", error.getMessage());
        verifyNoInteractions(verification);
    }

    @Test void invalidSynchronizationVersionIsRejected() {
        ReviewCase row = pending("DEC-1");
        when(repository.findById(10L)).thenReturn(Optional.of(row));
        when(customer.updateStatus(eq(4L), any())).thenReturn(
            new ApplicationStatusUpdateResponse(4L, "DEC-1", "PROCESSING",
                "MANUAL_REVIEW", "Name mismatch", 4));
        IllegalStateException error = assertThrows(IllegalStateException.class,
            () -> service.synchronize(10L));
        assertEquals("Invalid Customer Service update response", error.getMessage());
        verify(repository, never()).findLockedById(anyLong());
        verify(repository, never()).saveAndFlush(any());
    }
}
