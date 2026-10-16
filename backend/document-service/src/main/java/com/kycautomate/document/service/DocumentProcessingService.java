package com.kycautomate.document.service;

import com.kycautomate.document.domain.Document;
import com.kycautomate.document.domain.ExtractedField;
import com.kycautomate.document.dto.OcrFieldResult;
import com.kycautomate.document.repository.DocumentRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.Set;

@Service
public class DocumentProcessingService {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessingService.class);
    private static final Set<String> DOCUMENT_NUMBER_FIELDS = Set.of(
            "DOCUMENT_NUMBER", "PAN_NUMBER", "PASSPORT_NUMBER", "VOTER_ID_NUMBER",
            "DL_NUMBER", "AADHAAR_NUMBER", "CONSUMER_NUMBER");

    private final DocumentRepository repository;
    private final StorageService storageService;
    private final ImageQualityService imageQualityService;
    private final OcrExtractionService ocrService;
    private final double minimumConfidence;

    public DocumentProcessingService(DocumentRepository repository,
                                     StorageService storageService,
                                     ImageQualityService imageQualityService,
                                     OcrExtractionService ocrService,
                                     @Value("${kyc.ocr.minimum-confidence:50}") double minimumConfidence) {
        this.repository = repository;
        this.storageService = storageService;
        this.imageQualityService = imageQualityService;
        this.ocrService = ocrService;
        this.minimumConfidence = minimumConfidence;
    }

    @Async("documentTaskExecutor")
    @Transactional
    public void processAsync(String documentId) {
        Document document = repository.findById(documentId).orElse(null);
        if (document == null) {
            log.error("Cannot process missing document {}", documentId);
            return;
        }

        document.setProcessingStatus("PROCESSING");
        document.setProcessingMessage("Quality checks and OCR are running.");
        repository.save(document);

        try {
            byte[] bytes = storageService.load(document.getStorageReference());
            String qualityFailure = validateQuality(bytes, document.getMimeType());
            if (qualityFailure != null) {
                fail(document, "UNREADABLE", qualityFailure);
                return;
            }

            Map<String, OcrFieldResult> results = ocrService.extract(
                    bytes, document.getMimeType(), document.getDocumentType());
            document.getExtractedFields().clear();

            double confidenceTotal = 0;
            for (Map.Entry<String, OcrFieldResult> entry : results.entrySet()) {
                OcrFieldResult value = entry.getValue();
                document.getExtractedFields().add(new ExtractedField(
                        document, entry.getKey(), value.value(), value.ocrConfidence()));
                confidenceTotal += value.ocrConfidence();
                if (DOCUMENT_NUMBER_FIELDS.contains(entry.getKey().toUpperCase())) {
                    document.setDocumentNumber(value.value());
                }
                if ("EXPIRY_DATE".equalsIgnoreCase(entry.getKey())) {
                    document.setExpiryDate(value.value());
                }
            }

            double average = results.isEmpty() ? 0 : confidenceTotal / results.size();
            document.setAverageConfidence(average);
            if (results.isEmpty() || average < minimumConfidence) {
                fail(document, "UNREADABLE", String.format(
                        "OCR confidence %.1f is below the configured threshold %.1f.", average, minimumConfidence));
                return;
            }

            document.setProcessingStatus("EXTRACTED");
            document.setProcessingMessage("OCR extraction completed successfully.");
            repository.save(document);
        } catch (Exception exception) {
            log.error("Document processing failed for {}", documentId, exception);
            fail(document, "FAILED", "Document processing failed: " + safeMessage(exception));
        }
    }

    private String validateQuality(byte[] bytes, String mimeType) throws Exception {
        if ("application/pdf".equalsIgnoreCase(mimeType)) {
            try (PDDocument pdf = Loader.loadPDF(bytes)) {
                return pdf.getNumberOfPages() == 0 ? "PDF contains no pages." : null;
            }
        }
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null) return "The uploaded image could not be decoded.";
        ImageQualityService.QualityReport report = imageQualityService.evaluate(image);
        return report.passed() ? null : report.message();
    }

    private void fail(Document document, String status, String message) {
        document.setProcessingStatus(status);
        document.setProcessingMessage(message);
        repository.save(document);
    }

    private String safeMessage(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }
}
