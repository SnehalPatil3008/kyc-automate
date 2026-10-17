
package com.kycautomate.customer.service;

import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kycautomate.customer.dto.RegisterRequest;
import com.kycautomate.customer.dto.RegisterResponse;
import com.kycautomate.customer.entity.AppUser;
import com.kycautomate.customer.entity.Customer;
import com.kycautomate.customer.enums.AccountStatus;
import com.kycautomate.customer.enums.UserRole;
import com.kycautomate.customer.exception.BusinessConflictException;
import com.kycautomate.customer.repository.AppUserRepository;
import com.kycautomate.customer.repository.CustomerRepository;

@Service
public class AuthService {

    private final AppUserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            AppUserRepository userRepository,
            CustomerRepository customerRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.customerRepository = customerRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {

        String email = request.email()
                .trim().toLowerCase(Locale.ROOT);

        String mobile = request.mobileNumber().trim();

        if (userRepository.existsByEmail(email)) {
            throw new BusinessConflictException(
                "Email already registered");
        }

        if (customerRepository.existsByMobileNumber(mobile)) {
            throw new BusinessConflictException(
                "Mobile number already registered");
        }

        AppUser user = new AppUser();
        user.setEmail(email);
        user.setPasswordHash(
            passwordEncoder.encode(request.password()));
        user.setRole(UserRole.CUSTOMER);
        user.setAccountStatus(AccountStatus.ACTIVE);

        AppUser savedUser = userRepository.saveAndFlush(user);

        Customer customer = new Customer();
        customer.setUser(savedUser);
        customer.setFullName(request.fullName().trim());
        customer.setDateOfBirth(request.dateOfBirth());
        customer.setMobileNumber(mobile);
        customer.setAddress(request.address().trim());
        customer.setCity(request.city().trim());
        customer.setState(request.state().trim());
        customer.setPostalCode(request.postalCode().trim());

        Customer savedCustomer =
                customerRepository.saveAndFlush(customer);

        return new RegisterResponse(
            savedUser.getUserId(),
            savedCustomer.getCustomerId(),
            savedUser.getEmail(),
            "Customer registered successfully"
        );
    }
}
