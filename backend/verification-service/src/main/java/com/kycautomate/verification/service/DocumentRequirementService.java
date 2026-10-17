package com.kycautomate.verification.service;

import com.kycautomate.verification.domain.DocumentType;
import com.kycautomate.verification.dto.DocumentSnapshot;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DocumentRequirementService {

    public record RequirementResult(
            boolean complete,
            List<String> missingDocuments
    ) {}

    public RequirementResult check(List<DocumentSnapshot> documents) {
        boolean panPresent = documents != null && documents.stream()
                .anyMatch(document -> document.documentType() == DocumentType.PAN);

        boolean ovdPresent = documents != null && documents.stream()
                .anyMatch(document -> isOvd(document.documentType()));

        List<String> missing = new ArrayList<>();

        if (!panPresent) {
            missing.add("PAN");
        }

        if (!ovdPresent) {
            missing.add("OVD");
        }

        return new RequirementResult(missing.isEmpty(), missing);
    }

    private boolean isOvd(DocumentType type) {
        return type == DocumentType.AADHAAR
                || type == DocumentType.PASSPORT
                || type == DocumentType.VOTER_ID
                || type == DocumentType.DRIVING_LICENCE;
    }
}
