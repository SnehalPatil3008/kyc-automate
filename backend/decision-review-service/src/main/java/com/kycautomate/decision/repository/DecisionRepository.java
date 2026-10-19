
package com.kycautomate.decision.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kycautomate.decision.entity.DecisionRecord;
import com.kycautomate.decision.enums.ApplicationStatus;

import jakarta.persistence.LockModeType;

public interface DecisionRepository
        extends JpaRepository<DecisionRecord, Long> {

    Page<DecisionRecord> findByStatus(
            ApplicationStatus status,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
           SELECT d
           FROM DecisionRecord d
           WHERE d.applicationId = :id
           """)
    Optional<DecisionRecord> lockByApplicationId(
            @Param("id") Long applicationId
    );
}
