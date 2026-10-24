package com.kycautomate.audit.controller;

import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.kycautomate.audit.dto.AdminAuthResponse;
import com.kycautomate.audit.dto.AdminLoginRequest;
import com.kycautomate.audit.security.JwtTokenService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin-auth")
public class AdminAuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenService jwtTokenService;

    public AdminAuthController(
            @Qualifier("adminLoginAuthenticationManager")
            AuthenticationManager authenticationManager,
            JwtTokenService jwtTokenService) {

        this.authenticationManager =
            authenticationManager;

        this.jwtTokenService =
            jwtTokenService;
    }

    @PostMapping("/login")
    public ResponseEntity<AdminAuthResponse> login(
            @Valid
            @RequestBody
            AdminLoginRequest request) {

        if (request.password()
                .getBytes(StandardCharsets.UTF_8)
                .length > 72) {

            throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Invalid credentials"
            );
        }

        try {
            var authentication =
                authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken
                        .unauthenticated(
                            request.username().trim(),
                            request.password()
                        )
                );

            var issuedToken =
                jwtTokenService.issue(
                    authentication.getName()
                );

            AdminAuthResponse response =
                new AdminAuthResponse(
                    issuedToken.value(),
                    "Bearer",
                    issuedToken.expiresIn(),
                    authentication.getName(),
                    "ADMIN"
                );

            return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache")
                .body(response);

        } catch (AuthenticationException exception) {
            throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Invalid credentials"
            );
        }
    }
}