
package com.kycautomate.customer.service;

import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kycautomate.customer.dto.CustomerResponse;
import com.kycautomate.customer.entity.AppUser;
import com.kycautomate.customer.entity.Customer;
import com.kycautomate.customer.exception.ResourceNotFoundException;
import com.kycautomate.customer.repository.AppUserRepository;
import com.kycautomate.customer.repository.CustomerRepository;

@Service
public class CustomerService {

    private final AppUserRepository appUserRepository;
    private final CustomerRepository customerRepository;

    public CustomerService(
            AppUserRepository appUserRepository,
            CustomerRepository customerRepository) {
        this.appUserRepository = appUserRepository;
        this.customerRepository = customerRepository;
    }

    @Transactional(readOnly = true)
    public Customer getCustomerEntity(String email) {

        String normalizedEmail =
                email.trim().toLowerCase(Locale.ROOT);

        AppUser user = appUserRepository
            .findByEmail(normalizedEmail)
            .orElseThrow(() ->
                new ResourceNotFoundException("User not found"));

        return customerRepository
            .findByUserUserId(user.getUserId())
            .orElseThrow(() ->
                new ResourceNotFoundException(
                    "Customer profile not found"));
    }

    @Transactional(readOnly = true)
    public CustomerResponse getCustomerProfile(String email) {

        Customer customer = getCustomerEntity(email);

        return new CustomerResponse(
            customer.getCustomerId(),
            customer.getUser().getEmail(),
            customer.getFullName(),
            customer.getDateOfBirth(),
            customer.getMobileNumber(),
            customer.getAddress(),
            customer.getCity(),
            customer.getState(),
            customer.getPostalCode()
        );
    }
}
