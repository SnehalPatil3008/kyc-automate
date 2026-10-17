
package com.kycautomate.customer.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kycautomate.customer.entity.KycApplication;
import com.kycautomate.customer.enums.ApplicationStatus;

import jakarta.persistence.LockModeType;

public interface KycApplicationRepository
        extends JpaRepository<KycApplication, Long> {

    List<KycApplication>
        findByCustomerCustomerIdOrderByCreatedAtDesc(
            Long customerId);

    Optional<KycApplication>
        findByApplicationIdAndCustomerCustomerId(
            Long applicationId,
            Long customerId);

    boolean existsByCustomerCustomerIdAndApplicationStatusIn(
        Long customerId,
        List<ApplicationStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT a
        FROM KycApplication a
        WHERE a.applicationId = :applicationId
        """)
    Optional<KycApplication> findByIdForUpdate(
        @Param("applicationId") Long applicationId);
}
