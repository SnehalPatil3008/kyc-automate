package com.kycautomate.decision.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kycautomate.decision.entity.ReviewCase;
import com.kycautomate.decision.enums.ReviewState;

import jakarta.persistence.LockModeType;

public interface ReviewCaseRepository
        extends JpaRepository<ReviewCase, Long> {

    List<ReviewCase> findByReviewStateInOrderByCreatedAtAsc(List<ReviewState> states);

    List<ReviewCase> findByApplicationIdAndReviewStateInOrderByCreatedAtDesc(
        Long applicationId, List<ReviewState> states);

    Optional<ReviewCase> findByVerificationId(
        String verificationId
    );

    List<ReviewCase> findByReviewStateOrderByCreatedAtAsc(
        ReviewState state
    );

    List<ReviewCase> findByApplicationIdOrderByCreatedAtDesc(
        Long applicationId
    );

    boolean existsByApplicationIdAndReviewStateIn(
        Long applicationId,
        List<ReviewState> states
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ReviewCase r where r.reviewId = :id")
    Optional<ReviewCase> findLockedById(
        @Param("id") Long id
    );
}