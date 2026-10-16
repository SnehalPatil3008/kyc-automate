package com.kycautomate.document.controller;

import com.kycautomate.document.domain.Document;
import com.kycautomate.document.domain.DocumentType;
import com.kycautomate.document.dto.DocumentResponse;
import com.kycautomate.document.repository.DocumentRepository;
import com.kycautomate.document.service.DocumentService;
import com.kycautomate.document.service.DocumentProcessingService;
import com.kycautomate.document.service.StorageService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentProcessingService processingService;
    private final DocumentRepository repository;
    private final StorageService storageService;

    public DocumentController(DocumentService documentService,
                              DocumentProcessingService processingService,
                              DocumentRepository repository,
                              StorageService storageService) {
        this.documentService = documentService;
        this.processingService = processingService;
        this.repository = repository;
        this.storageService = storageService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> upload(
            @RequestParam String applicationId,
            @RequestParam DocumentType documentType,
            @RequestParam("file") MultipartFile file) throws IOException {
        DocumentResponse document = documentService.upload(applicationId, documentType, file);
        // The upload transaction has committed before the asynchronous worker starts.
        processingService.processAsync(document.documentId());
        return ResponseEntity.accepted().body(document);
    }

    @GetMapping("/{documentId}")
    public DocumentResponse find(@PathVariable String documentId) {
        return documentService.find(documentId);
    }

    @PostMapping("/{documentId}/process")
    public ResponseEntity<Map<String, String>> retryProcessing(@PathVariable String documentId) {
        documentService.find(documentId);
        processingService.processAsync(documentId);
        return ResponseEntity.accepted().body(Map.of(
                "documentId", documentId,
                "status", "PROCESSING_QUEUED"));
    }

    @GetMapping("/applications/{applicationId}")
    public List<DocumentResponse> findActive(@PathVariable String applicationId) {
        return documentService.findActiveByApplication(applicationId);
    }

    @GetMapping("/applications/{applicationId}/history")
    public List<DocumentResponse> findHistory(@PathVariable String applicationId) {
        return documentService.findHistoryByApplication(applicationId);
    }

    @GetMapping("/{documentId}/content")
    public ResponseEntity<byte[]> content(@PathVariable String documentId) throws IOException {
        Document document = repository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));
        MediaType mediaType = MediaType.parseMediaType(document.getMimeType());
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + document.getOriginalFilename().replace("\"", "") + "\"")
                .body(storageService.load(document.getStorageReference()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
    }
}
