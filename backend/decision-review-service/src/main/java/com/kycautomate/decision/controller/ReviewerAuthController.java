package com.kycautomate.decision.controller;

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

import com.kycautomate.decision.dto.ReviewerAuthResponse;
import com.kycautomate.decision.dto.ReviewerLoginRequest;
import com.kycautomate.decision.security.JwtTokenService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/reviewer-auth")
public class ReviewerAuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenService tokens;

    public ReviewerAuthController(
            @Qualifier("reviewerLoginAuthenticationManager") AuthenticationManager authenticationManager,
            JwtTokenService tokens) {
        this.authenticationManager = authenticationManager;
        this.tokens = tokens;
    }

    @PostMapping("/login")
    public ResponseEntity<ReviewerAuthResponse> login(
            @Valid @RequestBody ReviewerLoginRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        try {
            var authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                    request.username().trim(), request.password()));
            var token = tokens.issue(authentication.getName());
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache").body(new ReviewerAuthResponse(
                    token.value(), "Bearer", token.expiresIn(), authentication.getName(), "REVIEWER"));
        } catch (AuthenticationException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
    }
}
