package com.kycautomate.verification.service;

import com.kycautomate.verification.domain.DocumentType;
import com.kycautomate.verification.dto.CustomerVerificationRequest;
import com.kycautomate.verification.dto.DocumentSnapshot;
import com.kycautomate.verification.dto.ExtractedFieldSnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Stream;

@Service
public class VerificationRuleEngine {

    public record RuleEvaluationResult(
            String decision,
            String reason,
            Double nameScore,
            Double addressScore
    ) {}

    private final double nameThreshold;
    private final double addressThreshold;

    public VerificationRuleEngine(
            @Value("${verification.name-threshold:80}") double nameThreshold,
            @Value("${verification.address-threshold:60}") double addressThreshold) {
        this.nameThreshold = nameThreshold;
        this.addressThreshold = addressThreshold;
    }

    public RuleEvaluationResult evaluate(CustomerVerificationRequest customer, List<DocumentSnapshot> documents) {
        if (documents == null || documents.isEmpty()) {
            return result("MANUAL_REVIEW", "No active documents were found.", null, null);
        }
        if (documents.stream().anyMatch(this::requiresResubmission)) {
            return result("RESUBMISSION_REQUIRED", firstFailureMessage(documents), null, null);
        }
        if (documents.stream().anyMatch(this::stillProcessing)) {
            return result("PROCESSING", "One or more documents are still being processed.", null, null);
        }

        List<String> names = values(documents, "NAME");
        if (names.isEmpty()) {
            return result("MANUAL_REVIEW", "Customer name was not extracted from any document.", null, null);
        }
        double nameScore = names.stream()
                .mapToDouble(name -> calculateNameSimilarity(customer.customerName(), name))
                .min().orElse(0);
        if (nameScore < nameThreshold) {
            return result("MANUAL_REVIEW",
                    String.format("Name comparison failed with a minimum similarity of %.1f%%.", nameScore),
                    nameScore, null);
        }

        boolean identityDocumentPresent = documents.stream()
                .anyMatch(document -> !isUtilityBill(document.documentType()));
        if (identityDocumentPresent) {
            List<String> datesOfBirth = values(documents, "DOB");
            if (customer.dob() == null || customer.dob().isBlank() || datesOfBirth.isEmpty()) {
                return result("MANUAL_REVIEW", "Date of birth is missing from the form or identity document.",
                        nameScore, null);
            }
            String formDob = normalizeDate(customer.dob());
            boolean mismatch = datesOfBirth.stream().map(this::normalizeDate).anyMatch(value -> !value.equals(formDob));
            if (mismatch) {
                return result("MANUAL_REVIEW", "Date of birth does not match across the form and documents.",
                        nameScore, null);
            }
        }

        List<String> addresses = values(documents, "ADDRESS");
        Double addressScore = null;
        if (!addresses.isEmpty()) {
            addressScore = addresses.stream()
                    .mapToDouble(address -> calculateAddressSimilarity(customer.address(), address))
                    .min().orElse(0);
            if (addressScore < addressThreshold) {
                return result("MANUAL_REVIEW",
                        String.format("Address comparison failed with a minimum similarity of %.1f%%.", addressScore),
                        nameScore, addressScore);
            }
        } else if (documents.stream().allMatch(document -> isUtilityBill(document.documentType()))) {
            return result("MANUAL_REVIEW", "Address was not extracted from the proof-of-address document.",
                    nameScore, null);
        }

        return result("RECOMMENDED_FOR_APPROVAL",
                "Automated document and cross-document checks passed; authorized-official approval is still required.",
                nameScore, addressScore);
    }

    private boolean requiresResubmission(DocumentSnapshot document) {
        return "UNREADABLE".equalsIgnoreCase(document.processingStatus()) ||
                "FAILED".equalsIgnoreCase(document.processingStatus());
    }

    private boolean stillProcessing(DocumentSnapshot document) {
        return "PENDING".equalsIgnoreCase(document.processingStatus()) ||
                "PROCESSING".equalsIgnoreCase(document.processingStatus());
    }

    private String firstFailureMessage(List<DocumentSnapshot> documents) {
        return documents.stream().filter(this::requiresResubmission)
                .map(DocumentSnapshot::processingMessage).filter(Objects::nonNull).findFirst()
                .orElse("A document could not be read and must be resubmitted.");
    }

    private List<String> values(List<DocumentSnapshot> documents, String fieldName) {
        return documents.stream().flatMap(document -> document.safeFields().stream())
                .filter(field -> fieldName.equalsIgnoreCase(field.fieldName()))
                .map(ExtractedFieldSnapshot::fieldValue)
                .filter(value -> value != null && !value.isBlank()).toList();
    }

    private boolean isUtilityBill(DocumentType type) {
        return type == DocumentType.ELECTRICITY_BILL || type == DocumentType.GAS_BILL;
    }

    private RuleEvaluationResult result(String decision, String reason, Double nameScore, Double addressScore) {
        return new RuleEvaluationResult(decision, reason, nameScore, addressScore);
    }

    double calculateNameSimilarity(String first, String second) {
        if (first == null || second == null) return 0;
        String one = normalizedText(first);
        String two = normalizedText(second);
        if (one.equals(two)) return 100;
        String sortedOne = sortedTokens(one);
        String sortedTwo = sortedTokens(two);
        if (sortedOne.equals(sortedTwo)) return 100;
        int distance = levenshtein(sortedOne, sortedTwo);
        int maxLength = Math.max(sortedOne.length(), sortedTwo.length());
        return maxLength == 0 ? 100 : Math.max(0, (1.0 - (double) distance / maxLength) * 100);
    }

    double calculateAddressSimilarity(String first, String second) {
        if (first == null || second == null) return 0;
        Set<String> ignored = Set.of("flat", "no", "door", "house", "street", "road", "near", "floor",
                "block", "phase", "layout", "address", "pin", "pincode", "area", "colony", "apartment",
                "building", "society", "cross", "main", "sector", "stage", "india");
        Set<String> one = addressTokens(first, ignored);
        Set<String> two = addressTokens(second, ignored);
        if (one.isEmpty() || two.isEmpty()) return 0;
        long intersection = one.stream().filter(token -> two.stream()
                .anyMatch(other -> token.equals(other) || token.contains(other) || other.contains(token))).count();
        long union = Stream.concat(one.stream(), two.stream()).distinct().count();
        double recall = (double) intersection / Math.min(one.size(), two.size()) * 100;
        double jaccard = (double) intersection / union * 100;
        return Math.min(100, Math.max(recall, jaccard));
    }

    private String normalizedText(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ").trim();
    }

    private String sortedTokens(String value) {
        List<String> tokens = new ArrayList<>(Arrays.asList(value.split(" ")));
        Collections.sort(tokens);
        return String.join(" ", tokens);
    }

    private Set<String> addressTokens(String value, Set<String> ignored) {
        String normalized = normalizedText(value.replace("Bengaluru", "Bangalore"));
        Set<String> result = new LinkedHashSet<>();
        for (String token : normalized.split(" ")) {
            if (token.length() > 1 && !ignored.contains(token)) result.add(token);
        }
        return result;
    }

    private int levenshtein(String first, String second) {
        int[] previous = new int[second.length() + 1];
        for (int j = 0; j <= second.length(); j++) previous[j] = j;
        for (int i = 1; i <= first.length(); i++) {
            int[] current = new int[second.length() + 1];
            current[0] = i;
            for (int j = 1; j <= second.length(); j++) {
                int substitution = first.charAt(i - 1) == second.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + substitution);
            }
            previous = current;
        }
        return previous[second.length()];
    }

    private String normalizeDate(String value) {
        return value == null ? "" : value.replace('-', '/').replaceAll("\\s+", "").trim();
    }
}
