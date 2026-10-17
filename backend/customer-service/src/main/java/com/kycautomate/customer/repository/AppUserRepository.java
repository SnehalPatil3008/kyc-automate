
package com.kycautomate.customer.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.kycautomate.customer.entity.AppUser;

public interface AppUserRepository
        extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmail(String email);

    boolean existsByEmail(String email);
}
