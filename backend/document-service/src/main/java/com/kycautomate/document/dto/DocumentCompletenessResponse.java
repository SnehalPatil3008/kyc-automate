package com.kycautomate.document.dto;

import java.util.List;

public record DocumentCompletenessResponse(
        Long applicationId,
        boolean complete,
        List<String> missingDocuments
) {}
