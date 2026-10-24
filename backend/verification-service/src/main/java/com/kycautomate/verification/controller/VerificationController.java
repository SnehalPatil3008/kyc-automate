package com.kycautomate.verification.controller;
//import org.springframework.web.bind.annotation.CrossOrigin;
import com.kycautomate.verification.dto.CustomerVerificationRequest;
import com.kycautomate.verification.dto.VerificationResponse;
import com.kycautomate.verification.service.VerificationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/verifications")
public class VerificationController {

    private final VerificationService verificationService;

    public VerificationController(VerificationService verificationService) {
        this.verificationService = verificationService;
    }

    @PostMapping("/applications/{applicationId}")
    public VerificationResponse verify(
            @PathVariable String applicationId,
            @Valid @RequestBody CustomerVerificationRequest request) {

        return verificationService.verify(applicationId, request);
    }

    @GetMapping("/applications/{applicationId}/latest")
    public VerificationResponse latest(@PathVariable String applicationId) {
        return verificationService.latest(applicationId);
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    ResponseEntity<Map<String, String>> requestFailure(RuntimeException exception) {
        return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
    }
}
