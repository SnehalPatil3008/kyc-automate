package com.kycautomate.document.service;

import com.kycautomate.document.domain.Document;
import com.kycautomate.document.domain.DocumentType;
import com.kycautomate.document.dto.DocumentCompletenessResponse;
import com.kycautomate.document.dto.DocumentResponse;
import com.kycautomate.document.dto.ExtractedFieldResponse;
import com.kycautomate.document.repository.DocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class DocumentService {

    private final DocumentRepository repository;
    private final StorageService storageService;

    public DocumentService(DocumentRepository repository,
                           StorageService storageService) {
        this.repository = repository;
        this.storageService = storageService;
    }

    @Transactional
    public DocumentResponse upload(String applicationId,
                                   DocumentType documentType,
                                   MultipartFile file) throws IOException {

        validateUpload(file);

        repository.findByApplicationIdAndActiveTrueOrderByCreatedAtAsc(applicationId)
                .stream()
                .filter(existing -> existing.getDocumentType() == documentType)
                .forEach(existing -> existing.setActive(false));

        String originalName = file.getOriginalFilename() == null
                ? "document"
                : file.getOriginalFilename();

        String extension = originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf('.'))
                : ".dat";

        Document document = new Document();
        document.setDocumentId(UUID.randomUUID().toString());
        document.setApplicationId(applicationId);
        document.setDocumentType(documentType);
        document.setStorageReference(
                storageService.store(file.getBytes(), extension));
        document.setOriginalFilename(originalName);
        document.setMimeType(resolveMimeType(file));
        document.setActive(true);
        document.setProcessingStatus("PENDING");
        document.setProcessingMessage(
                "Document stored; OCR processing queued.");

        repository.saveAndFlush(document);

        return toResponse(document);
    }

    @Transactional(readOnly = true)
    public DocumentResponse find(String documentId) {
        return repository.findById(documentId)
                .map(this::toResponse)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Document not found: " + documentId));
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> findActiveByApplication(
            String applicationId) {

        return repository
                .findByApplicationIdAndActiveTrueOrderByCreatedAtAsc(
                        applicationId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> findHistoryByApplication(
            String applicationId) {

        return repository
                .findByApplicationIdOrderByCreatedAtAsc(applicationId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public DocumentCompletenessResponse checkCompleteness(
            String applicationId) {

        List<Document> documents =
                repository.findByApplicationIdAndActiveTrueOrderByCreatedAtAsc(
                        applicationId);

        boolean panPresent = documents.stream()
                .anyMatch(document ->
                        document.getDocumentType() == DocumentType.PAN);

        boolean ovdPresent = documents.stream()
                .anyMatch(document ->
                        document.getDocumentType() == DocumentType.AADHAAR
                                || document.getDocumentType() == DocumentType.PASSPORT
                                || document.getDocumentType() == DocumentType.VOTER_ID
                                || document.getDocumentType() == DocumentType.DRIVING_LICENCE);

        List<String> missingDocuments = new ArrayList<>();

        if (!panPresent) {
            missingDocuments.add("PAN");
        }

        if (!ovdPresent) {
            missingDocuments.add("OVD");
        }

        return new DocumentCompletenessResponse(
                Long.valueOf(applicationId),
                missingDocuments.isEmpty(),
                missingDocuments
        );
    }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(
                    "Uploaded document must not be empty.");
        }

        String name = file.getOriginalFilename() == null
                ? ""
                : file.getOriginalFilename()
                        .toLowerCase(Locale.ROOT);

        String type = file.getContentType() == null
                ? ""
                : file.getContentType()
                        .toLowerCase(Locale.ROOT);

        boolean supported =
                type.equals("application/pdf")
                        || type.equals("image/png")
                        || type.equals("image/jpeg")
                        || name.endsWith(".pdf")
                        || name.endsWith(".png")
                        || name.endsWith(".jpg")
                        || name.endsWith(".jpeg");

        if (!supported) {
            throw new IllegalArgumentException(
                    "Only PDF, PNG, JPG, and JPEG files are supported.");
        }
    }

    private String resolveMimeType(MultipartFile file) {
        if (file.getContentType() != null
                && !file.getContentType().isBlank()) {
            return file.getContentType();
        }

        String name = file.getOriginalFilename() == null
                ? ""
                : file.getOriginalFilename()
                        .toLowerCase(Locale.ROOT);

        if (name.endsWith(".pdf")) {
            return "application/pdf";
        }

        if (name.endsWith(".png")) {
            return "image/png";
        }

        return "image/jpeg";
    }

    private DocumentResponse toResponse(Document document) {
        List<ExtractedFieldResponse> fields =
                document.getExtractedFields()
                        .stream()
                        .map(field -> new ExtractedFieldResponse(
                                field.getFieldName(),
                                field.getFieldValue(),
                                field.getConfidenceScore()))
                        .toList();

        return new DocumentResponse(
                document.getDocumentId(),
                document.getApplicationId(),
                document.getDocumentType(),
                document.getDocumentNumber(),
                document.getOriginalFilename(),
                document.getMimeType(),
                document.isActive(),
                document.getProcessingStatus(),
                document.getProcessingMessage(),
                document.getExpiryDate(),
                document.getAverageConfidence(),
                fields,
                document.getCreatedAt(),
                document.getUpdatedAt());
    }
}
