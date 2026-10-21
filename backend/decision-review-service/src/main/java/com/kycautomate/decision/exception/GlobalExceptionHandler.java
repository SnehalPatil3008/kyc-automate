package com.kycautomate.decision.exception;

import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import feign.FeignException;
import feign.RetryableException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(com.kycautomate.decision.exception.ReviewNotFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(
            com.kycautomate.decision.exception.ReviewNotFoundException ex) {

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalid(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> conflict(
            IllegalStateException ex) {

        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> validation(
            MethodArgumentNotValidException ex) {

        return ResponseEntity.badRequest()
            .body(Map.of(
                "error", "Invalid review request"));
    }

    @ExceptionHandler({
        DataIntegrityViolationException.class,
        ObjectOptimisticLockingFailureException.class
    })
    public ResponseEntity<Map<String, String>> concurrency(
            Exception ex) {

        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(Map.of(
                "error",
                "Duplicate or concurrent operation"));
    }

    @ExceptionHandler({
        FeignException.class,
        RetryableException.class
    })
    public ResponseEntity<Map<String, String>> downstream(
            FeignException ex) {

        if (ex.status() == 409) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "The application or dependent operation is not in the required state"));
        }
        if (ex.status() == 404) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "The requested dependent resource was not found"));
        }
        return ResponseEntity.status(
            HttpStatus.BAD_GATEWAY)
            .body(Map.of(
                "error",
                "Dependent microservice request failed"));
    }
}