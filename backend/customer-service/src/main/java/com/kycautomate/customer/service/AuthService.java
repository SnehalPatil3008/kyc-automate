package com.kycautomate.customer.service;

import java.util.Locale;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.server.ResponseStatusException;
import com.kycautomate.customer.dto.LoginRequest;
import com.kycautomate.customer.dto.AuthResponse;
import com.kycautomate.customer.security.JwtTokenService;

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
    private final AuthenticationManager authenticationManager;
    private final JwtTokenService tokens;

    public AuthService(
            AppUserRepository userRepository,
            CustomerRepository customerRepository,
            PasswordEncoder passwordEncoder,
            @Qualifier("customerLoginAuthenticationManager") AuthenticationManager authenticationManager,
            JwtTokenService tokens) {
        this.userRepository = userRepository;
        this.customerRepository = customerRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.tokens = tokens;
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        try {
            authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(email, request.password()));
        } catch (AuthenticationException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        AppUser user = userRepository.findByEmail(email).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        if (user.getRole() != UserRole.CUSTOMER || user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        var token = tokens.issue(user.getEmail());
        return new AuthResponse(token.value(), "Bearer", token.expiresIn(),
            user.getUserId(), user.getRole().name());
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {

        if (request.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Password must be at most 72 UTF-8 bytes");
        }

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
