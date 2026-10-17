
package com.kycautomate.customer.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.kycautomate.customer.client.DocumentClient;
import com.kycautomate.customer.dto.DocumentCompletenessResponse;
import com.kycautomate.customer.exception.BusinessConflictException;
import com.kycautomate.customer.exception.DocumentServiceUnavailableException;

import feign.FeignException;
import feign.RetryableException;

@Service
public class DocumentValidationService {

    private final DocumentClient documentClient;

    public DocumentValidationService(DocumentClient documentClient) {
        this.documentClient = documentClient;
    }

    public void validateDocuments(Long applicationId) {

        DocumentCompletenessResponse response;

        try {
            response = documentClient.checkCompleteness(applicationId);
        } catch (RetryableException ex) {
            throw new DocumentServiceUnavailableException(
                "Document Service is unavailable", ex);
        } catch (FeignException ex) {
            throw new DocumentServiceUnavailableException(
                "Document validation request failed", ex);
        }

        if (response == null
                || response.applicationId() == null
                || !applicationId.equals(response.applicationId())
                || response.missingDocuments() == null) {
            throw new DocumentServiceUnavailableException(
                "Invalid response from Document Service");
        }

        List<String> missing = response.missingDocuments();

        if (response.complete() && !missing.isEmpty()) {
            throw new DocumentServiceUnavailableException(
                "Contradictory document completeness response");
        }

        if (!response.complete()) {
            throw new BusinessConflictException(
                "Required documents are missing: " + missing);
        }
    }
}
