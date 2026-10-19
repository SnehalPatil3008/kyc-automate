
package com.kycautomate.customer.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kycautomate.customer.entity.KycStatusOperation;

public interface KycStatusOperationRepository
        extends JpaRepository<KycStatusOperation, Long> {

    Optional<KycStatusOperation> findByDecisionReference(
            String decisionReference);
}
