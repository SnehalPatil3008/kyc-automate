
package com.kycautomate.customer.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.kycautomate.customer.dto.CustomerResponse;
import com.kycautomate.customer.service.CustomerService;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping("/me")
    public ResponseEntity<CustomerResponse> getMyProfile(
            Authentication authentication) {

        return ResponseEntity.ok(
            customerService.getCustomerProfile(
                authentication.getName()));
    }
}
