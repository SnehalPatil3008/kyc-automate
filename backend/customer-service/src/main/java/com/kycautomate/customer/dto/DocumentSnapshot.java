package com.kycautomate.customer.dto;

import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DocumentSnapshot(
    String documentId, String applicationId, String documentType,
    boolean active, String processingStatus, Instant createdAt
) {}