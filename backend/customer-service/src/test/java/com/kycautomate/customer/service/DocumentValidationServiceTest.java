
package com.kycautomate.customer.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.kycautomate.customer.client.DocumentClient;
import com.kycautomate.customer.dto.DocumentCompletenessResponse;
import com.kycautomate.customer.exception.BusinessConflictException;
import com.kycautomate.customer.exception.DocumentServiceUnavailableException;

@ExtendWith(MockitoExtension.class)
class DocumentValidationServiceTest {

    @Mock
    private DocumentClient documentClient;

    @InjectMocks
    private DocumentValidationService service;

    @Test
    void completeDocumentsAllowSubmission() {
        when(documentClient.checkCompleteness(101L))
            .thenReturn(new DocumentCompletenessResponse(
                101L, true, List.of()));

        assertDoesNotThrow(
            () -> service.validateDocuments(101L));
    }

    @Test
    void missingDocumentsBlockSubmission() {
        when(documentClient.checkCompleteness(101L))
            .thenReturn(new DocumentCompletenessResponse(
                101L, false, List.of("ADDRESS_PROOF")));

        assertThrows(
            BusinessConflictException.class,
            () -> service.validateDocuments(101L));
    }

    @Test
    void incorrectApplicationIdIsRejected() {
        when(documentClient.checkCompleteness(101L))
            .thenReturn(new DocumentCompletenessResponse(
                999L, true, List.of()));

        assertThrows(
            DocumentServiceUnavailableException.class,
            () -> service.validateDocuments(101L));
    }

    @Test
    void contradictoryResponseIsRejected() {
        when(documentClient.checkCompleteness(101L))
            .thenReturn(new DocumentCompletenessResponse(
                101L, true, List.of("PAN")));

        assertThrows(
            DocumentServiceUnavailableException.class,
            () -> service.validateDocuments(101L));
    }

    @Test
    void nullResponseIsRejected() {
        when(documentClient.checkCompleteness(101L))
            .thenReturn(null);

        assertThrows(
            DocumentServiceUnavailableException.class,
            () -> service.validateDocuments(101L));
    }

    @Test
    void nullMissingDocumentsIsRejected() {
        when(documentClient.checkCompleteness(101L))
            .thenReturn(new DocumentCompletenessResponse(
                101L, true, null));

        assertThrows(
            DocumentServiceUnavailableException.class,
            () -> service.validateDocuments(101L));
    }
}
