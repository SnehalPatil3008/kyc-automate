
package com.kycautomate.customer.exception;

import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 1. Resource not found - 404
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(
            ResourceNotFoundException ex) {

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }

    // 2. Business conflict - 409
    @ExceptionHandler(BusinessConflictException.class)
    public ResponseEntity<Map<String, String>> conflict(
            BusinessConflictException ex) {

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", ex.getMessage()));
    }

    // 3. Invalid request - 400
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalidRequest(
            IllegalArgumentException ex) {

        return ResponseEntity.badRequest()
                .body(Map.of("error", "Invalid request"));
    }

    // 4. Request validation failure - 400
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> validation(
            MethodArgumentNotValidException ex) {

        return ResponseEntity.badRequest()
                .body(Map.of("error", "Invalid request details"));
    }

    // 5. Database constraint conflict - 409
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> databaseConflict(
            DataIntegrityViolationException ex) {

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of(
                    "error",
                    "Request conflicts with existing data"
                ));
    }

    // 6. Optimistic locking conflict - 409
    @ExceptionHandler(
            ObjectOptimisticLockingFailureException.class
    )
    public ResponseEntity<Map<String, String>> optimisticLock(
            ObjectOptimisticLockingFailureException ex) {

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of(
                    "error",
                    "Application was updated by another request. "
                    + "Refresh and retry."
                ));
    }

    // 7. Document Service unavailable - 503
    @ExceptionHandler(DocumentServiceUnavailableException.class)
    public ResponseEntity<Map<String, String>> documentFailure(
            DocumentServiceUnavailableException ex) {

        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                    "error",
                    "Document validation is temporarily unavailable"
                ));
    }
}
