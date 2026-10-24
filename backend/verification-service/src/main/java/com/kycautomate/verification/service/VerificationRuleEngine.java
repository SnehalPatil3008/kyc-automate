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

    public RuleEvaluationResult evaluate(
            CustomerVerificationRequest customer,
            List<DocumentSnapshot> documents) {

        if (documents == null || documents.isEmpty()) {
            return result(
                    "MANUAL_REVIEW",
                    "No active documents were found.",
                    null,
                    null
            );
        }

        if (documents.stream().anyMatch(this::requiresResubmission)) {
            return result(
                    "RESUBMISSION_REQUIRED",
                    firstFailureMessage(documents),
                    null,
                    null
            );
        }

        if (documents.stream().anyMatch(this::stillProcessing)) {
            return result(
                    "PROCESSING",
                    "One or more documents are still being processed.",
                    null,
                    null
            );
        }

        List<String> findings = new ArrayList<>();
        boolean verificationFailed = false;

        /*
         * NAME CHECK
         */
        List<String> names = values(documents, "NAME");
        Double nameScore = null;

        if (names.isEmpty()) {
            findings.add(
                    "Name comparison unavailable because the name was not extracted."
            );
            verificationFailed = true;
        } else {
            nameScore = names.stream()
                    .mapToDouble(name ->
                            calculateNameSimilarity(
                                    customer.customerName(),
                                    name
                            ))
                    .min()
                    .orElse(0);

            if (nameScore < nameThreshold) {
                findings.add(
                        String.format(
                                "Name mismatch: %.1f%%.",
                                nameScore
                        )
                );
                verificationFailed = true;
            } else {
                findings.add(
                        String.format(
                                "Name matched: %.1f%%.",
                                nameScore
                        )
                );
            }
        }

        /*
         * DATE-OF-BIRTH CHECK
         */
        boolean identityDocumentPresent = documents.stream()
                .anyMatch(document ->
                        isOvd(document.documentType()));

        if (!identityDocumentPresent) {
            findings.add(
                    "Date of birth: unavailable because no identity document was found."
            );
            verificationFailed = true;
        } else {
            List<String> datesOfBirth = values(documents, "DOB");

            if (customer.dob() == null
                    || customer.dob().isBlank()
                    || datesOfBirth.isEmpty()) {

                findings.add(
                        "Date of birth: unavailable in the form or identity document."
                );
                verificationFailed = true;
            } else {
                String formDob = normalizeDate(customer.dob());

                boolean dobMismatch = datesOfBirth.stream()
                        .map(this::normalizeDate)
                        .anyMatch(documentDob ->
                                !documentDob.equals(formDob));

                if (dobMismatch) {
                    findings.add("Date of birth: mismatch.");
                    verificationFailed = true;
                } else {
                    findings.add("Date of birth: matched.");
                }
            }
        }

        /*
         * ADDRESS CHECK
         *
         * If an electricity/gas bill exists, use only its address.
         * Otherwise, use only the OVD address.
         */
        List<DocumentSnapshot> utilityBills = documents.stream()
                .filter(document ->
                        isUtilityBill(document.documentType()))
                .toList();

        List<DocumentSnapshot> addressDocuments;

        if (!utilityBills.isEmpty()) {
            // Address proof takes priority over OVD
            addressDocuments = utilityBills;
        } else {
            // PAN is ignored; only OVD address is considered
            addressDocuments = documents.stream()
                    .filter(document ->
                            isOvd(document.documentType()))
                    .toList();
        }

        List<String> addresses = values(addressDocuments, "ADDRESS");
        Double addressScore = null;

        if (addresses.isEmpty()) {
            if (!utilityBills.isEmpty()) {
                findings.add(
                        "Address was not extracted from the electricity or gas bill."
                );
            } else {
                findings.add(
                        "Address was not extracted from the OVD. "
                        + "Upload an electricity or gas bill as address proof."
                );
            }

            verificationFailed = true;
        } else {
            addressScore = addresses.stream()
                    .mapToDouble(address ->
                            calculateAddressSimilarity(
                                    customer.address(),
                                    address
                            ))
                    .min()
                    .orElse(0);

            if (addressScore < addressThreshold) {
                findings.add(
                        String.format(
                                "Address mismatch: %.1f%%.",
                                addressScore
                        )
                );
                verificationFailed = true;
            } else {
                findings.add(
                        String.format(
                                "Address matched: %.1f%%.",
                                addressScore
                        )
                );
            }
        }

        String reason = String.join(" ", findings);

        if (verificationFailed) {
            return result(
                    "MANUAL_REVIEW",
                    reason,
                    nameScore,
                    addressScore
            );
        }

        return result(
                "RECOMMENDED_FOR_APPROVAL",
                reason
                        + " Automated checks passed; "
                        + "authorized-official approval is still required.",
                nameScore,
                addressScore
        );
    }

    private boolean requiresResubmission(DocumentSnapshot document) {
        return "UNREADABLE".equalsIgnoreCase(document.processingStatus())
                || "FAILED".equalsIgnoreCase(document.processingStatus());
    }

    private boolean stillProcessing(DocumentSnapshot document) {
        return "PENDING".equalsIgnoreCase(document.processingStatus())
                || "PROCESSING".equalsIgnoreCase(document.processingStatus());
    }

    private String firstFailureMessage(
            List<DocumentSnapshot> documents) {

        return documents.stream()
                .filter(this::requiresResubmission)
                .map(DocumentSnapshot::processingMessage)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(
                        "A document could not be read and must be resubmitted."
                );
    }

    private List<String> values(
            List<DocumentSnapshot> documents,
            String fieldName) {

        return documents.stream()
                .flatMap(document ->
                        document.safeFields().stream())
                .filter(field ->
                        fieldName.equalsIgnoreCase(field.fieldName()))
                .map(ExtractedFieldSnapshot::fieldValue)
                .filter(value ->
                        value != null && !value.isBlank())
                .toList();
    }

    private boolean isOvd(DocumentType type) {
        return type == DocumentType.AADHAAR
                || type == DocumentType.PASSPORT
                || type == DocumentType.VOTER_ID
                || type == DocumentType.DRIVING_LICENCE;
    }

    private boolean isUtilityBill(DocumentType type) {
        return type == DocumentType.ELECTRICITY_BILL
                || type == DocumentType.GAS_BILL;
    }

    private RuleEvaluationResult result(
            String decision,
            String reason,
            Double nameScore,
            Double addressScore) {

        return new RuleEvaluationResult(
                decision,
                reason,
                nameScore,
                addressScore
        );
    }

    double calculateNameSimilarity(String first, String second) {
        if (first == null || second == null) {
            return 0;
        }

        String one = normalizedText(first);
        String two = normalizedText(second);

        if (one.equals(two)) {
            return 100;
        }

        String sortedOne = sortedTokens(one);
        String sortedTwo = sortedTokens(two);

        if (sortedOne.equals(sortedTwo)) {
            return 100;
        }

        int distance = levenshtein(sortedOne, sortedTwo);
        int maxLength = Math.max(
                sortedOne.length(),
                sortedTwo.length()
        );

        return maxLength == 0
                ? 100
                : Math.max(
                        0,
                        (1.0 - (double) distance / maxLength) * 100
                );
    }

    double calculateAddressSimilarity(String first, String second) {
        if (first == null || second == null) {
            return 0;
        }

        Set<String> ignored = Set.of(
                "flat", "no", "door", "house", "street", "road",
                "near", "floor", "block", "phase", "layout",
                "address", "pin", "pincode", "area", "colony",
                "apartment", "building", "society", "cross",
                "main", "sector", "stage", "india"
        );

        Set<String> one = addressTokens(first, ignored);
        Set<String> two = addressTokens(second, ignored);

        if (one.isEmpty() || two.isEmpty()) {
            return 0;
        }

        long intersection = one.stream()
                .filter(token ->
                        two.stream().anyMatch(other ->
                                token.equals(other)
                                        || token.contains(other)
                                        || other.contains(token)))
                .count();

        long union = Stream.concat(
                        one.stream(),
                        two.stream())
                .distinct()
                .count();

        double recall =
                (double) intersection
                        / Math.min(one.size(), two.size())
                        * 100;

        double jaccard =
                (double) intersection / union * 100;

        return Math.min(
                100,
                Math.max(recall, jaccard)
        );
    }

    private String normalizedText(String value) {
        return value
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String sortedTokens(String value) {
        List<String> tokens = new ArrayList<>(
                Arrays.asList(value.split(" "))
        );

        Collections.sort(tokens);

        return String.join(" ", tokens);
    }

    private Set<String> addressTokens(
            String value,
            Set<String> ignored) {

        String normalized = normalizedText(
                value.replace("Bengaluru", "Bangalore")
        );

        Set<String> result = new LinkedHashSet<>();

        for (String token : normalized.split(" ")) {
            if (token.length() > 1
                    && !ignored.contains(token)) {
                result.add(token);
            }
        }

        return result;
    }

    private int levenshtein(String first, String second) {
        int[] previous = new int[second.length() + 1];

        for (int j = 0; j <= second.length(); j++) {
            previous[j] = j;
        }

        for (int i = 1; i <= first.length(); i++) {
            int[] current = new int[second.length() + 1];
            current[0] = i;

            for (int j = 1; j <= second.length(); j++) {
                int substitution =
                        first.charAt(i - 1)
                                == second.charAt(j - 1)
                                ? 0
                                : 1;

                current[j] = Math.min(
                        Math.min(
                                current[j - 1] + 1,
                                previous[j] + 1
                        ),
                        previous[j - 1] + substitution
                );
            }

            previous = current;
        }

        return previous[second.length()];
    }

    private String normalizeDate(String value) {
        return value == null
                ? ""
                : value.replace('-', '/')
                        .replaceAll("\\s+", "")
                        .trim();
    }
}