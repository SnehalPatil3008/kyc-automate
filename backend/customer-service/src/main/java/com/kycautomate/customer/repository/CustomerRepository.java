
package com.kycautomate.customer.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.kycautomate.customer.entity.Customer;

public interface CustomerRepository
        extends JpaRepository<Customer, Long> {

    Optional<Customer> findByUserUserId(Long userId);

    boolean existsByMobileNumber(String mobileNumber);
}
