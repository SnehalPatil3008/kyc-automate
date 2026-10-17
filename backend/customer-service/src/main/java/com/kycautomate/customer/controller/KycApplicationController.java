
package com.kycautomate.customer.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.kycautomate.customer.dto.KycApplicationResponse;
import com.kycautomate.customer.service.KycApplicationService;

@RestController
@RequestMapping("/api/kyc/applications")
public class KycApplicationController {

    private final KycApplicationService service;

    public KycApplicationController(
            KycApplicationService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<KycApplicationResponse> create(
            Authentication authentication) {

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(service.createApplication(
                authentication.getName()));
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<KycApplicationResponse> submit(
            @PathVariable Long id,
            Authentication authentication) {

        return ResponseEntity.ok(
            service.submitApplication(
                authentication.getName(), id));
    }

    @PostMapping("/{id}/resubmit")
    public ResponseEntity<KycApplicationResponse> resubmit(
            @PathVariable Long id,
            Authentication authentication) {

        return ResponseEntity.ok(
            service.resubmitApplication(
                authentication.getName(), id));
    }

    @GetMapping("/me")
    public ResponseEntity<List<KycApplicationResponse>> getMine(
            Authentication authentication) {

        return ResponseEntity.ok(
            service.getMyApplications(
                authentication.getName()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<KycApplicationResponse> getById(
            @PathVariable Long id,
            Authentication authentication) {

        return ResponseEntity.ok(
            service.getApplication(
                authentication.getName(), id));
    }
}
