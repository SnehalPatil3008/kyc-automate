package com.kycautomate.verification.repository;

import com.kycautomate.verification.domain.VerificationResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VerificationResultRepository extends JpaRepository<VerificationResult, String> {
    Optional<VerificationResult> findTopByApplicationIdOrderByVerifiedAtDesc(String applicationId);
}
