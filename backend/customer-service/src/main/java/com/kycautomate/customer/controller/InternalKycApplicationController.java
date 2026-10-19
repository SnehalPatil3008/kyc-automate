
package com.kycautomate.customer.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.kycautomate.customer.dto.ApplicationStatusUpdateRequest;
import com.kycautomate.customer.dto.ApplicationStatusUpdateResponse;
import com.kycautomate.customer.dto.InternalKycApplicationResponse;
import com.kycautomate.customer.service.KycApplicationService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/internal/kyc/applications")
public class InternalKycApplicationController {

    private final KycApplicationService service;

    public InternalKycApplicationController(
            KycApplicationService service) {
        this.service = service;
    }

    @GetMapping("/{id}/snapshot")
    public ResponseEntity<InternalKycApplicationResponse> snapshot(
            @PathVariable Long id) {

        return ResponseEntity.ok(
            service.getInternalSnapshot(id));
    }

    @RequestMapping(value = "/{id}/status", method = {RequestMethod.PATCH, RequestMethod.POST})
    public ResponseEntity<ApplicationStatusUpdateResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody ApplicationStatusUpdateRequest request) {

        return ResponseEntity.ok(
            service.updateApplicationStatus(id, request));
    }
}
