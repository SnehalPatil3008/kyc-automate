package com.kycautomate.customer.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.kycautomate.customer.client.DocumentClient;
import com.kycautomate.customer.dto.DocumentSnapshot;
import com.kycautomate.customer.exception.*;

class ReplacementDocumentValidationTest {
    private final Instant requested = Instant.parse("2026-10-18T10:00:00Z");
    private DocumentSnapshot doc(String id, String type, Instant created, String status) {
        return new DocumentSnapshot(id, "4", type, true, status, created);
    }
    private DocumentSnapshot ovd() {
        return doc("ovd", "AADHAAR", requested.minusSeconds(100), "EXTRACTED");
    }
    @Test void completenessUsesExistingActiveDocumentEndpoint() {
        DocumentClient client = mock(DocumentClient.class, CALLS_REAL_METHODS);
        when(client.getActiveDocuments(4L)).thenReturn(List.of(
            doc("pan", "PAN", requested, "PENDING"), ovd()));
        assertTrue(client.checkCompleteness(4L).complete());
    }
    @Test void utilityBillDoesNotReplaceOvdRequirement() {
        DocumentClient client = mock(DocumentClient.class, CALLS_REAL_METHODS);
        when(client.getActiveDocuments(4L)).thenReturn(List.of(
            doc("pan", "PAN", requested, "EXTRACTED"),
            doc("bill", "ELECTRICITY_BILL", requested, "EXTRACTED")));
        assertEquals(List.of("OVD"), client.checkCompleteness(4L).missingDocuments());
    }
    @Test void oldDocumentCannotSatisfyReplacementRequest() {
        DocumentClient client = mock(DocumentClient.class);
        when(client.getActiveDocuments(4L)).thenReturn(List.of(
            doc("pan", "PAN", requested.minusSeconds(1), "EXTRACTED"), ovd()));
        assertThrows(BusinessConflictException.class, () ->
            new DocumentValidationService(client).validateReplacements(4L, Set.of("PAN"), requested));
    }
    @Test void newReadableReplacementUsesOnlyOneRemoteSnapshot() {
        DocumentClient client = mock(DocumentClient.class);
        when(client.getActiveDocuments(4L)).thenReturn(List.of(
            doc("pan", "PAN", requested.plusSeconds(1), "EXTRACTED"), ovd()));
        assertDoesNotThrow(() ->
            new DocumentValidationService(client).validateReplacements(4L, Set.of("PAN"), requested));
        verify(client, times(1)).getActiveDocuments(4L);
        verify(client, never()).checkCompleteness(anyLong());
    }
    @Test void processingReplacementCannotBeResubmittedYet() {
        DocumentClient client = mock(DocumentClient.class);
        when(client.getActiveDocuments(4L)).thenReturn(List.of(
            doc("pan", "PAN", requested.plusSeconds(1), "PROCESSING"), ovd()));
        assertThrows(BusinessConflictException.class, () ->
            new DocumentValidationService(client).validateReplacements(4L, Set.of("PAN"), requested));
    }
    @Test void duplicateActiveTypesAreRejected() {
        DocumentClient client = mock(DocumentClient.class);
        when(client.getActiveDocuments(4L)).thenReturn(List.of(
            doc("pan1", "PAN", requested.plusSeconds(1), "EXTRACTED"),
            doc("pan2", "PAN", requested.plusSeconds(2), "EXTRACTED"), ovd()));
        assertThrows(DocumentServiceUnavailableException.class, () ->
            new DocumentValidationService(client).validateReplacements(4L, Set.of("PAN"), requested));
    }
}