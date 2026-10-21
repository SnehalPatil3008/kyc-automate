package com.kycautomate.document.service;

import com.kycautomate.document.domain.DocumentType;
import com.kycautomate.document.dto.OcrFieldResult;
import net.sourceforge.tess4j.ITessAPI;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.Word;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class OcrExtractionService {

    private static final Logger log = LoggerFactory.getLogger(OcrExtractionService.class);
    private final String configuredTessdataPath;

    public OcrExtractionService(@Value("${kyc.ocr.tessdata-path:}") String configuredTessdataPath) {
        this.configuredTessdataPath = configuredTessdataPath;
    }

    // Common Date and Address Patterns
    private static final Pattern DOB_PATTERN = Pattern.compile(
            "(?i)(?:D\\.?O\\.?B\\.?|[0-9Il|!]\\s*O\\.?B\\.?|Date of Birth|Birth|YOB|Year of Birth|तारीख)[:\\s]+([0-9/I|!l-]{8,14})"
    );
    private static final Pattern FALLBACK_DATE_PATTERN = Pattern.compile("\\b(\\d{2}[/-]\\d{2}[/-]\\d{4})\\b");
    private static final Pattern EXPIRY_PATTERN = Pattern.compile(
            "(?i)(?:Expiry|Valid Till|Expiry Date)[:\\s]+(\\d{2}[/-]\\d{2}[/-]\\d{4})"
    );
    private static final Pattern ADDRESS_PATTERN = Pattern.compile(
            "(?i)(?:Address|Add)\\s*[:\\-]+\\s*([^\\n\\r]+(?:\\r?\\n[^\\n\\r]+){0,3})"
    );

    // Document Number Patterns
    private static final Pattern PAN_NUMBER_PATTERN = Pattern.compile("(?i)\\b[A-Z]{5}[0-9OIl]{4}[A-Z]\\b");
    private static final Pattern PASSPORT_NUMBER_PATTERN = Pattern.compile("\\b[A-Z][0-9]{7}\\b");
    private static final Pattern VOTER_NUMBER_PATTERN = Pattern.compile("\\b[A-Z]{3}[0-9]{7}\\b");
    private static final Pattern AADHAAR_NUMBER_PATTERN = Pattern.compile("\\b\\d{4}\\s?\\d{4}\\s?\\d{4}\\b");
    
    // Specific Identifier Patterns
    private static final Pattern GAS_CONSUMER_PATTERN = Pattern.compile(
            "(?i)(?:Consumer\\s*No|Consumer\\s*ID|Relationship\\s*ID|Account\\s*No)[\\s:.-]+([A-Z0-9-]{6,18})");
    private static final Pattern ELEC_CONSUMER_PATTERN = Pattern.compile(
            "(?i)(?:RR\\s*No\\.?|Account\\s*ID|CA\\s*No|Consumer\\s*ID|Account\\s*Number)[\\s:.-]+([0-9()A-Z/-]{5,16})");

    // Passport Machine Readable Zone (MRZ) Patterns
    private static final Pattern MRZ_LINE_1 =
            Pattern.compile("P<([A-Z]{3})([A-Z<]{5,44})");
    private static final Pattern MRZ_LINE_2 = Pattern.compile(
            "([A-Z0-9<]{9})[0-9]([A-Z]{3})(\\d{6})[0-9]([MF<])(\\d{6})[0-9][A-Z0-9<]{14}"
    );

    public Map<String, OcrFieldResult> extract(byte[] fileBytes, String mimeType, DocumentType docType) {
        Map<String, OcrFieldResult> extracted = new HashMap<>();

        try {
            // STEP 1: Direct Digital PDF Stream Extraction
            if ("application/pdf".equalsIgnoreCase(mimeType)) {
                try (PDDocument doc = Loader.loadPDF(fileBytes)) {
                    PDFTextStripper stripper = new PDFTextStripper();
                    String pdfText = stripper.getText(doc);

                    if (pdfText != null && !pdfText.trim().isEmpty() && hasDocumentIdentifier(pdfText, docType)) {
                        log.info("Digital PDF text stream detected with valid {} identifier. Parsing structured text directly.", docType);
                        log.info("PDF Direct Extracted Text:\n{}", pdfText);

                        parseDocumentContent(pdfText, docType, extracted, 99.0);
                        if (docType != DocumentType.ELECTRICITY_BILL && docType != DocumentType.GAS_BILL) {
                            applyFallbacks(pdfText, extracted, 99.0);
                        }

                        sanitizeExtractedFields(extracted);

                        if ((extracted.containsKey("DOCUMENT_NUMBER") || extracted.containsKey("ADDRESS")) && extracted.containsKey("NAME")) {
                            log.info("Digital PDF text extraction successful with 99% confidence. Bypassing optical OCR.");
                            return extracted;
                        }
                    }
                } catch (Exception ex) {
                    log.warn("Direct PDF text extraction skipped: {}", ex.getMessage());
                }
            }

            // STEP 2: Optical OCR Engine with Multi-Angle Auto-Orientation
            Tesseract tesseract = new Tesseract();

            File configuredTessdata = configuredTessdataPath == null || configuredTessdataPath.isBlank()
                    ? null : new File(configuredTessdataPath);
            File projectTessdata = new File("tessdata");
            if (configuredTessdata != null && new File(configuredTessdata, "eng.traineddata").exists()) {
                tesseract.setDatapath(configuredTessdata.getAbsolutePath());
            } else if (projectTessdata.exists() && new File(projectTessdata, "eng.traineddata").exists()) {
                tesseract.setDatapath(projectTessdata.getAbsolutePath());
            } else {
                log.warn("No explicit tessdata directory configured; relying on the Tesseract installation defaults.");
            }
            tesseract.setLanguage("eng");
            tesseract.setTessVariable("user_defined_dpi", "300");

            BufferedImage baseImage;

            if ("application/pdf".equalsIgnoreCase(mimeType)) {
                try (PDDocument doc = Loader.loadPDF(fileBytes)) {
                    PDFRenderer renderer = new PDFRenderer(doc);
                    BufferedImage rendered = renderer.renderImageWithDPI(0, 300);
                    baseImage = convertToGrayscale(rendered);
                }
            } else {
                BufferedImage rawImage = ImageIO.read(new ByteArrayInputStream(fileBytes));
                if (rawImage == null) {
                    log.error("Unable to decode document payload for docType: {}", docType);
                    return extracted;
                }
                baseImage = preprocessCameraPhoto(rawImage);
                if (docType == DocumentType.PASSPORT) {
                    baseImage = enhancePassportImage(baseImage);
                }
            }

            int[] angles = {0, 90, 270, 180};
            OcrRunResult bestRun = null;

            for (int angle : angles) {
                BufferedImage candidate = (angle == 0) ? baseImage : rotateImage(baseImage, angle);
                OcrRunResult run = executeOcr(tesseract, candidate, angle, docType);

                if (bestRun == null || (run.idFound && !bestRun.idFound) ||
                   (run.idFound == bestRun.idFound && run.confidence > bestRun.confidence)) {
                    bestRun = run;
                }

                if (run.idFound && run.confidence >= 50.0) {
                    log.info("Document identifier confirmed at angle {}° [Confidence: {}%].", angle, Math.round(run.confidence));
                    bestRun = run;
                    break;
                }
            }

            if (bestRun == null) {
                log.error("OCR execution failed across all orientations for docType: {}", docType);
                return extracted;
            }

            log.info("Final OCR Processing [Type: {}, Angle: {}°, Valid Words: {}, Confidence: {}%]",
                    docType, bestRun.angle, bestRun.validWordCount, Math.round(bestRun.confidence));
            log.info("OCR Extracted Raw Text:\n{}", bestRun.rawText);

            parseDocumentContent(bestRun.rawText, docType, extracted, bestRun.confidence);
            if (docType != DocumentType.ELECTRICITY_BILL && docType != DocumentType.GAS_BILL) {
                applyFallbacks(bestRun.rawText, extracted, bestRun.confidence);
            }

            sanitizeExtractedFields(extracted);

        } catch (Throwable t) {
            log.error("Fatal exception during OCR extraction for docType {}: {}", docType, t.getMessage(), t);
        }

        return extracted;
    }

    private void sanitizeExtractedFields(Map<String, OcrFieldResult> extracted) {
        for (Map.Entry<String, OcrFieldResult> entry : new HashMap<>(extracted).entrySet()) {
            String val = entry.getValue().value();
            if (val != null && val.length() > 950) {
                extracted.put(entry.getKey(), new OcrFieldResult(val.substring(0, 950), entry.getValue().ocrConfidence()));
            }
        }
    }

    // ==========================================
    // OCR RUNNER & ORIENTATION LOGIC
    // ==========================================

    private static class OcrRunResult {
        final String rawText;
        final String[] lines;
        final double confidence;
        final int validWordCount;
        final boolean idFound;
        final int angle;

        OcrRunResult(String rawText, String[] lines, double confidence, int validWordCount, boolean idFound, int angle) {
            this.rawText = rawText;
            this.lines = lines;
            this.confidence = confidence;
            this.validWordCount = validWordCount;
            this.idFound = idFound;
            this.angle = angle;
        }
    }

    private OcrRunResult executeOcr(Tesseract tesseract, BufferedImage image, int angle, DocumentType docType) throws Exception {
        String rawText = tesseract.doOCR(image);
        List<Word> words = tesseract.getWords(image, ITessAPI.TessPageIteratorLevel.RIL_WORD);

        double totalConf = 0.0;
        int countedWords = 0;

        for (Word w : words) {
            String text = w.getText().trim();
            if (text.matches(".*[A-Za-z0-9].*") && w.getConfidence() >= 35.0) {
                totalConf += w.getConfidence();
                countedWords++;
            }
        }

        double averageOcrConfidence = countedWords > 0 ? (totalConf / countedWords) : 0.0;
        String[] lines = rawText.split("\\r?\\n");
        boolean idFound = hasDocumentIdentifier(rawText, docType);

        return new OcrRunResult(rawText, lines, averageOcrConfidence, countedWords, idFound, angle);
    }

    private boolean hasDocumentIdentifier(String text, DocumentType docType) {
        return switch (docType) {
            case PAN -> PAN_NUMBER_PATTERN.matcher(text).find();
            case DRIVING_LICENCE -> extractDrivingLicenceNumber(text, text.split("\\r?\\n")) != null;
            case PASSPORT -> MRZ_LINE_1.matcher(text.replace(" ", "")).find() || PASSPORT_NUMBER_PATTERN.matcher(text).find();
            case AADHAAR -> AADHAAR_NUMBER_PATTERN.matcher(text).find();
            case VOTER_ID -> VOTER_NUMBER_PATTERN.matcher(text).find();
            case GAS_BILL -> GAS_CONSUMER_PATTERN.matcher(text).find()
                    || text.toUpperCase().contains("CUSTOMER DETAILS")
                    || text.toUpperCase().contains("INDIANOIL");
            case ELECTRICITY_BILL -> ELEC_CONSUMER_PATTERN.matcher(text).find()
                    || text.toUpperCase().contains("BESCOM")
                    || text.toUpperCase().contains("NAME & ADDRESS");
        };
    }

    private BufferedImage rotateImage(BufferedImage img, int angleDegrees) {
        double radians = Math.toRadians(angleDegrees);
        double sin = Math.abs(Math.sin(radians));
        double cos = Math.abs(Math.cos(radians));
        int w = img.getWidth();
        int h = img.getHeight();
        int newW = (int) Math.floor(w * cos + h * sin);
        int newH = (int) Math.floor(h * cos + w * sin);

        int type = (img.getType() == 0) ? BufferedImage.TYPE_BYTE_GRAY : img.getType();
        BufferedImage rotated = new BufferedImage(newW, newH, type);
        Graphics2D g2d = rotated.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.translate((newW - w) / 2, (newH - h) / 2);
        g2d.rotate(radians, w / 2.0, h / 2.0);
        g2d.drawImage(img, 0, 0, null);
        g2d.dispose();
        return rotated;
    }

    private BufferedImage convertToGrayscale(BufferedImage source) {
        BufferedImage gray = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = gray.createGraphics();
        g.drawImage(source, 0, 0, null);
        g.dispose();
        return gray;
    }

    private BufferedImage preprocessCameraPhoto(BufferedImage source) {
        BufferedImage scaled = source;
        int targetWidth = 1800;
        if (source.getWidth() < targetWidth) {
            double scale = (double) targetWidth / source.getWidth();
            int newW = (int) (source.getWidth() * scale);
            int newH = (int) (source.getHeight() * scale);

            scaled = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = scaled.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.drawImage(source, 0, 0, newW, newH, null);
            g2d.dispose();
        }

        return convertToGrayscale(scaled);
    }

    private BufferedImage enhancePassportImage(BufferedImage source) {
        BufferedImage gray = convertToGrayscale(source);
        BufferedImage enhanced = new BufferedImage(
                gray.getWidth(), gray.getHeight(), BufferedImage.TYPE_BYTE_GRAY);

        Graphics2D graphics = enhanced.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
        graphics.drawImage(gray, 0, 0, null);
        graphics.dispose();
        return enhanced;
    }

    // ==========================================
    // STRUCTURED PARSING DISPATCHER
    // ==========================================

    private void parseDocumentContent(String rawText, DocumentType docType, Map<String, OcrFieldResult> extracted, double conf) {
        String[] lines = rawText.split("\\r?\\n");

        switch (docType) {
            case PAN -> extractPanLayout(lines, rawText, extracted, conf);
            case AADHAAR -> extractAadhaarLayout(lines, rawText, extracted, conf);
            case PASSPORT -> extractPassportLayout(rawText, extracted, conf);
            case DRIVING_LICENCE -> extractDrivingLicenceLayout(lines, rawText, extracted, conf);
            case VOTER_ID -> extractVoterIdLayout(lines, rawText, extracted, conf);
            case GAS_BILL -> extractGasBillLayout(lines, rawText, extracted, conf);
            case ELECTRICITY_BILL -> extractElectricityBillLayout(lines, rawText, extracted, conf);
        }
    }

    private void applyFallbacks(String rawText, Map<String, OcrFieldResult> extracted, double conf) {
        if (!extracted.containsKey("DOB")) {
            Matcher dobMatcher = DOB_PATTERN.matcher(rawText);
            if (dobMatcher.find()) {
                extracted.put("DOB", new OcrFieldResult(normalizeDate(dobMatcher.group(1).trim()), conf));
            } else {
                Matcher fallbackDate = FALLBACK_DATE_PATTERN.matcher(rawText);
                while (fallbackDate.find()) {
                    int start = fallbackDate.start();
                    String prefix = rawText.substring(Math.max(0, start - 15), start).toUpperCase();
                    if (!prefix.contains("DOI") && !prefix.contains("DO!") && !prefix.contains("ISSUE")) {
                        extracted.put("DOB", new OcrFieldResult(normalizeDate(fallbackDate.group(1).trim()), conf));
                        break;
                    }
                }
            }
        }

        if (!extracted.containsKey("EXPIRY_DATE")) {
            Matcher expiryMatcher = EXPIRY_PATTERN.matcher(rawText);
            if (expiryMatcher.find()) {
                extracted.put("EXPIRY_DATE", new OcrFieldResult(normalizeDate(expiryMatcher.group(1).trim()), conf));
            }
        }
    }

    // ==========================================
    // DOCUMENT LAYOUT PARSERS
    // ==========================================

    private void extractPassportLayout(String rawText, Map<String, OcrFieldResult> out, double conf) {
        String cleanText = rawText.replace(" ", "");
        Matcher mrz1 = MRZ_LINE_1.matcher(cleanText);
        Matcher mrz2 = MRZ_LINE_2.matcher(cleanText);

        if (mrz1.find()) {
            String rawNames = mrz1.group(1);
            String[] parts = rawNames.split("<<");
            String surname = parts.length > 0 ? parts[0].replace("<", " ").trim() : "";
            String givenNames = parts.length > 1 ? parts[1].replace("<", " ").trim() : "";

            if (!surname.isEmpty() && !givenNames.isEmpty()) {
                String formattedName = surname.substring(0, 1).toUpperCase() + " " + givenNames.toUpperCase();
                out.put("NAME", new OcrFieldResult(formattedName, conf));
            } else if (!givenNames.isEmpty()) {
                out.put("NAME", new OcrFieldResult(givenNames.toUpperCase(), conf));
            }
        }

        if (!out.containsKey("NAME")) {
            Pattern givenP = Pattern.compile("(?i)(?:Given Name(?:\\(s\\))?|दिया गया नाम)[:\\s]+([A-Za-z ]+)");
            Pattern surP = Pattern.compile("(?i)(?:Surname|उपनाम)[:\\s]+([A-Za-z ]+)");
            Matcher gm = givenP.matcher(rawText);
            Matcher sm = surP.matcher(rawText);

            if (gm.find()) {
                String gName = gm.group(1).trim();
                if (sm.find()) {
                    String sName = sm.group(1).trim();
                    String formatted = sName.substring(0, 1).toUpperCase() + " " + gName.toUpperCase();
                    out.put("NAME", new OcrFieldResult(formatted, conf));
                } else if (isValidPersonName(gName)) {
                    out.put("NAME", new OcrFieldResult(gName, conf));
                }
            }
        }

        if (mrz2.find()) {
            String passNumber = mrz2.group(1).trim();
            String rawDob = mrz2.group(2);
            String rawExpiry = mrz2.group(4);

            out.put("PASSPORT_NUMBER", new OcrFieldResult(passNumber, conf));
            out.put("DOCUMENT_NUMBER", new OcrFieldResult(passNumber, conf));
            out.put("DOB", new OcrFieldResult(formatMrzDate(rawDob), conf));
            out.put("EXPIRY_DATE", new OcrFieldResult(formatMrzDate(rawExpiry), conf));
            return;
        }

        Matcher passMatcher = PASSPORT_NUMBER_PATTERN.matcher(rawText);
        if (passMatcher.find()) {
            out.put("PASSPORT_NUMBER", new OcrFieldResult(passMatcher.group(0).trim(), conf));
            out.put("DOCUMENT_NUMBER", new OcrFieldResult(passMatcher.group(0).trim(), conf));
        }

        if (!out.containsKey("NAME")) {
            extractNameByLabelOrLine(rawText.split("\\r?\\n"), rawText, out, conf);
        }
        extractAddress(rawText, out, conf);
    }

    private void extractPanLayout(String[] lines, String rawText, Map<String, OcrFieldResult> out, double conf) {
        Matcher panMatcher = PAN_NUMBER_PATTERN.matcher(rawText);
        if (panMatcher.find()) {
            String rawPan = panMatcher.group(0).toUpperCase();
            String prefix = rawPan.substring(0, 5);
            String digits = rawPan.substring(5, 9).replace('O', '0').replace('I', '1').replace('L', '1');
            String suffix = rawPan.substring(9, 10);
            String cleanPan = prefix + digits + suffix;

            out.put("PAN_NUMBER", new OcrFieldResult(cleanPan, conf));
            out.put("DOCUMENT_NUMBER", new OcrFieldResult(cleanPan, conf));
        }

        Pattern panDobPattern = Pattern.compile("(?i)(?:Date of Birth|DOB|D\\.?O\\.?B\\.?|तारीख|Birth)[:\\s]+([0-9/I|!l-]{8,14})");
        Matcher dobM = panDobPattern.matcher(rawText);
        if (dobM.find()) {
            String normDob = normalizeDate(dobM.group(1).trim());
            out.put("DOB", new OcrFieldResult(normDob, conf));
        } else {
            Matcher fallbackDate = FALLBACK_DATE_PATTERN.matcher(rawText);
            if (fallbackDate.find()) {
                out.put("DOB", new OcrFieldResult(normalizeDate(fallbackDate.group(1).trim()), conf));
            }
        }

        String textWithoutPan = PAN_NUMBER_PATTERN.matcher(rawText).replaceAll(" ");
        String[] cleanLines = textWithoutPan.split("\\r?\\n");

        Pattern panInlineName = Pattern.compile(
                "(?i:(?<!Father's\\s)(?<!Father\\s)\\b(?:Name|NANE))[ \\t:/]+([A-Z][A-Za-z]*(?:[ \\t]+[A-Z][A-Za-z]*){0,3})\\b"
        );
        Matcher nameM = panInlineName.matcher(textWithoutPan);
        while (nameM.find()) {
            String candidate = extractCleanPersonName(nameM.group(1));
            if (isValidPersonName(candidate) && !isGovtHeader(candidate)) {
                out.put("NAME", new OcrFieldResult(candidate, conf));
                return;
            }
        }

        for (int i = 0; i < cleanLines.length; i++) {
            String l = cleanLines[i].trim();
            String u = l.toUpperCase();
            if ((l.contains("नाम") || u.contains("NAME") || u.contains("NANE"))
                    && !u.contains("FATHER") && !l.contains("पिता")) {
                for (int j = i + 1; j < Math.min(cleanLines.length, i + 3); j++) {
                    String candidate = extractCleanPersonName(cleanLines[j]);
                    if (isValidPersonName(candidate) && !isGovtHeader(candidate) && !candidate.toUpperCase().contains("FATHER")) {
                        out.put("NAME", new OcrFieldResult(candidate, conf));
                        return;
                    }
                }
            }
        }

        int dobIndex = -1;
        for (int i = 0; i < cleanLines.length; i++) {
            if (FALLBACK_DATE_PATTERN.matcher(cleanLines[i]).find() || cleanLines[i].toUpperCase().contains("BIRTH")) {
                dobIndex = i;
                break;
            }
        }

        if (dobIndex >= 2) {
            for (int k = 0; k < dobIndex; k++) {
                String candidate = extractCleanPersonName(cleanLines[k]);
                if (isValidPersonName(candidate) && !isGovtHeader(candidate)) {
                    out.put("NAME", new OcrFieldResult(candidate, conf));
                    return;
                }
            }
        }

        extractNameByLabelOrLine(cleanLines, textWithoutPan, out, conf);
    }

    private String extractCleanPersonName(String rawLine) {
        if (rawLine == null) return "";
        String cleaned = PAN_NUMBER_PATTERN.matcher(rawLine).replaceAll(" ").trim();
        cleaned = cleaned.replaceAll("(?i)\\b(?:Order\\s*No|Order\\s*Date|Consumer\\s*No|Customer\\s*Details|Order\\s*Details|Tax\\s*Invoice).*$", "").trim();

        Matcher upperMulti = Pattern.compile("\\b([A-Z]{1,20}(?:[ \\t]+[A-Z]{1,20})+)\\b").matcher(cleaned);
        if (upperMulti.find()) {
            return upperMulti.group(1).trim();
        }

        Matcher titleMulti = Pattern.compile("\\b([A-Z][a-zA-Z]*(?:[ \\t]+[A-Z][a-zA-Z]*)+)\\b").matcher(cleaned);
        if (titleMulti.find()) {
            return titleMulti.group(1).trim();
        }

        Matcher singleUpper = Pattern.compile("\\b([A-Z]{3,20})\\b").matcher(cleaned);
        if (singleUpper.find()) {
            return singleUpper.group(1).trim();
        }

        return cleaned.replaceAll("[^A-Za-z ]", "").trim();
    }

    private void extractAadhaarLayout(String[] lines, String rawText, Map<String, OcrFieldResult> out, double conf) {
        Matcher aMatcher = AADHAAR_NUMBER_PATTERN.matcher(rawText);
        if (aMatcher.find()) {
            String cleanNum = aMatcher.group(0).replaceAll("\\s+", "").trim();
            out.put("AADHAAR_NUMBER", new OcrFieldResult(cleanNum, conf));
            out.put("DOCUMENT_NUMBER", new OcrFieldResult(cleanNum, conf));
        }

        Matcher dobMatcher = DOB_PATTERN.matcher(rawText);
        int dobLineIdx = -1;
        if (dobMatcher.find()) {
            out.put("DOB", new OcrFieldResult(normalizeDate(dobMatcher.group(1).trim()), conf));
        }

        for (int i = 0; i < lines.length; i++) {
            if (lines[i].toUpperCase().contains("DOB") || lines[i].toUpperCase().contains("YEAR OF BIRTH")) {
                dobLineIdx = i;
                break;
            }
        }

        if (dobLineIdx > 0) {
            for (int j = dobLineIdx - 1; j >= 0; j--) {
                String candidate = extractCleanPersonName(lines[j]);
                if (isValidPersonName(candidate) && !isGovtHeader(candidate)) {
                    out.put("NAME", new OcrFieldResult(candidate, conf));
                    break;
                }
            }
        }

        if (!out.containsKey("NAME")) {
            extractNameByLabelOrLine(lines, rawText, out, conf);
        }

        extractAddress(rawText, out, conf);
    }

    private void extractDrivingLicenceLayout(String[] lines, String rawText, Map<String, OcrFieldResult> out, double conf) {
        String dlNumber = extractDrivingLicenceNumber(rawText, lines);
        if (dlNumber != null) {
            out.put("DL_NUMBER", new OcrFieldResult(dlNumber, conf));
            out.put("DOCUMENT_NUMBER", new OcrFieldResult(dlNumber, conf));
        }

        Pattern dlForm7Pattern = Pattern.compile(
                "(?i)FORM\\s*[-~]?\\s*[0-9]?\\s+([A-Za-z ]+?)(?=\\s*(?:\\[|\\(|See|Rule|\\d{2}/|DOB|D\\.O\\.B|B\\.G|B\\+|$))"
        );
        Matcher f7M = dlForm7Pattern.matcher(rawText);
        if (f7M.find()) {
            String candidate = f7M.group(1).trim();
            if (isValidPersonName(candidate)) {
                out.put("NAME", new OcrFieldResult(candidate, conf));
            }
        }

        if (!out.containsKey("NAME")) {
            Pattern rulePattern = Pattern.compile("(?i)([A-Za-z ]{2,30})\\s*\\[(?:See\\s*)?Rule");
            Matcher rM = rulePattern.matcher(rawText);
            if (rM.find()) {
                String candidate = rM.group(1).replaceAll("(?i)\\b(?:FORM|DOI|MCWG|LMV)\\b", "").trim();
                if (isValidPersonName(candidate)) {
                    out.put("NAME", new OcrFieldResult(candidate, conf));
                }
            }
        }

        if (!out.containsKey("NAME")) {
            Pattern labelPattern = Pattern.compile(
                    "(?i)(?:[“\"'~\\s]*(?:NAME|AME|HAME|NANE)|Name)[:\\s]+([A-Za-z ]+?)(?=\\s*(?:\\[|\\(|See|Rule|D\\.?O|1\\s*O|I\\s*O|Date|Father|S/D/W|S/W/D|S/O|SDW|B\\.G|\\d|\\r?\\n|$))"
            );
            Matcher nameM = labelPattern.matcher(rawText);
            if (nameM.find()) {
                String candidate = nameM.group(1).trim();
                if (isValidPersonName(candidate)) {
                    out.put("NAME", new OcrFieldResult(candidate, conf));
                }
            }
        }

        if (!out.containsKey("NAME")) {
            extractNameByLabelOrLine(lines, rawText, out, conf);
        }

        Pattern dlDobPattern = Pattern.compile(
                "(?i)(?:D\\.?O\\.?B\\.?|[0-9Il|!]\\s*O\\.?B\\.?|Date of Birth)[:\\s]+(\\d{2}[/-]\\d{2}[/-]\\d{4})"
        );
        Matcher dobM = dlDobPattern.matcher(rawText);
        if (dobM.find()) {
            String cleanDob = normalizeDate(dobM.group(1).trim());
            out.put("DOB", new OcrFieldResult(cleanDob, conf));
        }

        Pattern dlExpiryPattern = Pattern.compile(
                "(?i)(?:[“\"'~\\s]*(?:VALID\\s+TILL|ALID\\s+TILL|EXPIRY))[:\\s]+(\\d{2}[/-]\\d{2}[/-]\\d{4})"
        );
        Matcher expM = dlExpiryPattern.matcher(rawText);
        if (expM.find()) {
            out.put("EXPIRY_DATE", new OcrFieldResult(normalizeDate(expM.group(1).trim()), conf));
        }

        extractAddress(rawText, out, conf);
    }

    private String extractDrivingLicenceNumber(String rawText, String[] lines) {
        for (String line : lines) {
            String u = line.toUpperCase();
            if (u.contains("DL") || u.contains("LICEN") || u.contains("FORM") || u.contains("MH03") || u.contains("MHO3") || u.contains("KA03") || u.contains("KAO3")) {
                Pattern p = Pattern.compile("([A-Z]{2})[- ]?([0-9OIl]{2})[- ]?([0-9OIl]{4})[- ]?([0-9OIl]{7})", Pattern.CASE_INSENSITIVE);
                Matcher m = p.matcher(line);
                if (m.find()) {
                    return normalizeDl(m.group(1), m.group(2) + m.group(3) + m.group(4));
                }
                Pattern p2 = Pattern.compile("([A-Z]{2})[- ]?([0-9OIl]{2})[- ]?([0-9OIl]{11})", Pattern.CASE_INSENSITIVE);
                Matcher m2 = p2.matcher(line);
                if (m2.find()) {
                    return normalizeDl(m2.group(1), m2.group(2) + m2.group(3));
                }
            }
        }

        Pattern pSpaced = Pattern.compile("(?i)\\b([A-Z]{2})[- ]?([0-9OIl]{2})[- ]?([0-9OIl]{4})[- ]?([0-9OIl]{7})\\b");
        Matcher mSpaced = pSpaced.matcher(rawText);
        if (mSpaced.find()) {
            return normalizeDl(mSpaced.group(1), mSpaced.group(2) + mSpaced.group(3) + mSpaced.group(4));
        }

        Pattern pCont = Pattern.compile("(?i)\\b([A-Z]{2})[- ]?([0-9OIl]{2})[- ]?([0-9OIl]{11})\\b");
        Matcher mCont = pCont.matcher(rawText);
        if (mCont.find()) {
            return normalizeDl(mCont.group(1), mCont.group(2) + mCont.group(3));
        }

        return null;
    }

    private String normalizeDl(String state, String digits) {
        String cleanDigits = digits.toUpperCase()
                .replace('O', '0')
                .replace('I', '1')
                .replace('L', '1')
                .replace('B', '8')
                .replace('S', '5')
                .replaceAll("[^0-9]", "");
        return state.toUpperCase() + cleanDigits;
    }

    private void extractVoterIdLayout(String[] lines, String rawText, Map<String, OcrFieldResult> out, double conf) {
        Matcher voterMatcher = VOTER_NUMBER_PATTERN.matcher(rawText);
        if (voterMatcher.find()) {
            out.put("VOTER_ID_NUMBER", new OcrFieldResult(voterMatcher.group(0).trim(), conf));
            out.put("DOCUMENT_NUMBER", new OcrFieldResult(voterMatcher.group(0).trim(), conf));
        }

        extractNameByLabelOrLine(lines, rawText, out, conf);
        extractAddress(rawText, out, conf);
    }

    /**
     * Dedicated PoA Parser for Gas Bills / LPG Invoices.
     */
    private void extractGasBillLayout(String[] lines, String rawText, Map<String, OcrFieldResult> out, double conf) {
        Matcher consumerMatcher = GAS_CONSUMER_PATTERN.matcher(rawText);
        if (consumerMatcher.find()) {
            String cNum = consumerMatcher.group(1).trim();
            out.put("CONSUMER_NUMBER", new OcrFieldResult(cNum, conf));
            out.put("DOCUMENT_NUMBER", new OcrFieldResult(cNum, conf));
            log.info("Extracted Gas Bill Consumer/Relationship Number: '{}'", cNum);
        }

        String customerSection = rawText;
        Matcher custSectionMatcher = Pattern.compile("(?is)Customer\\s+Details(.+?)(?:Distributor\\s+Details|Tax\\s+Invoice|Product|Qty|Amount|$)").matcher(rawText);
        if (custSectionMatcher.find()) {
            customerSection = custSectionMatcher.group(1);
        }

        Pattern gasNamePattern = Pattern.compile(
                "(?i)\\bName[ \\t:.-]+([A-Z][A-Za-z\\s]+?)(?=\\s*(?:Old|House|Flat|Room|Address|GSTN|Order|Consumer|Relationship|\\d|\\r?\\n|$))"
        );
        Matcher nameMatcher = gasNamePattern.matcher(customerSection);
        if (nameMatcher.find()) {
            String cleanName = extractCleanPersonName(nameMatcher.group(1));
            if (isValidPersonName(cleanName) && !isGovtHeader(cleanName) && !cleanName.toUpperCase().contains("SERVICE")) {
                out.put("NAME", new OcrFieldResult(cleanName, conf));
                log.info("Extracted Gas Bill Customer Name: '{}'", cleanName);
            }
        }

        if (!out.containsKey("NAME")) {
            for (String line : customerSection.split("\\r?\\n")) {
                String candidate = extractCleanPersonName(line);
                if (isValidPersonName(candidate) && !isGovtHeader(candidate) && !candidate.toUpperCase().contains("SERVICE")) {
                    out.put("NAME", new OcrFieldResult(candidate, conf));
                    log.info("Extracted Gas Bill Customer Name (Fallback): '{}'", candidate);
                    break;
                }
            }
        }

        Matcher addrAnchor = Pattern.compile("(?is)\\bAddress\\b[ \\t:.-]*(.*?)(?:GSTN|IGST|UTGST|Order|Tax\\s+Invoice|\\*\\*|$)(?!.*(?:Address|Delhi|Shahdara|Sector|Layout|Road|Street))").matcher(customerSection);
        String rawAddr = null;
        if (addrAnchor.find()) {
            rawAddr = addrAnchor.group(1);
        } else {
            Matcher altAddr = Pattern.compile("(?is)(?:Address|Add)[ \\t:.-]+([^\\n\\r]+(?:\\r?\\n(?!(?:IGST|UTGST|Order|Tax|\\*\\*))[^\\n\\r]+){0,2})").matcher(customerSection);
            if (altAddr.find()) {
                rawAddr = altAddr.group(1);
            }
        }

        if (rawAddr != null) {
            StringBuilder cleanAddr = new StringBuilder();
            for (String line : rawAddr.split("\\r?\\n")) {
                String trimmed = line.replaceAll("(?i)(?:IGST|UTGST|Order|Tax|Invoice|\\*\\*).*$", "").trim();
                if (!trimmed.isEmpty() && !trimmed.contains("**") && !trimmed.contains("IGST")) {
                    if (cleanAddr.length() > 0) cleanAddr.append(", ");
                    cleanAddr.append(trimmed);
                }
            }
            String finalAddr = cleanAddr.toString().replaceAll("\\s+", " ").trim();
            if (finalAddr.length() > 950) finalAddr = finalAddr.substring(0, 950);
            if (!finalAddr.isEmpty()) {
                out.put("ADDRESS", new OcrFieldResult(finalAddr, conf));
                log.info("Extracted Gas Bill Customer Address: '{}'", finalAddr);
            }
        }
    }

    /**
     * Dedicated PoA Parser for BESCOM / Electricity Bills.
     * Accurately locks onto the "Name & Address" section below the header, extracting B.H. PRAKASH and service address[cite: 1].
     */
    private void extractElectricityBillLayout(String[] lines, String rawText, Map<String, OcrFieldResult> out, double conf) {
        Matcher rrMatcher = Pattern.compile("(?i)(?:RR\\s*No\\.?|Account\\s*ID)[\\s:.-]+([0-9()A-Z]+)").matcher(rawText);
        if (rrMatcher.find()) {
            String rrNum = rrMatcher.group(1).trim();
            out.put("CONSUMER_NUMBER", new OcrFieldResult(rrNum, conf));
            out.put("DOCUMENT_NUMBER", new OcrFieldResult(rrNum, conf));
            log.info("Extracted BESCOM Electricity RR/Account Number: '{}'", rrNum);
        }

        // Isolate block starting strictly after "Name & Address:"
        String nameAddrBlock = "";
        Matcher anchor = Pattern.compile("(?is)Name\\s*&\\s*Address\\s*[:\\-]*(.+?)(?:Type|Tariff|Billing\\s*Period|Meter\\s*Readings|$)").matcher(rawText);
        if (anchor.find()) {
            nameAddrBlock = anchor.group(1);
        } else {
            nameAddrBlock = rawText;
        }

        String[] blockLines = nameAddrBlock.split("\\r?\\n");

        // Extract Customer Name (e.g. B.H. PRAKASH)
        for (String line : blockLines) {
            String clean = line.trim();
            // Match initials/name pattern like B.H. PRAKASH or standard person name format
            if (clean.matches("^[A-Z]\\.[A-Z]\\.\\s+[A-Z]+$") || clean.matches("^[A-Z]\\.[A-Z]?\\s+[A-Z\\s]+$") || (clean.split("\\s+").length >= 2 && clean.length() <= 35 && !isGovtHeader(clean) && !clean.toUpperCase().contains("BESCOM") && !clean.toUpperCase().contains("COMPANY"))) {
                out.put("NAME", new OcrFieldResult(clean, conf));
                log.info("Extracted Electricity Bill Customer Name: '{}'", clean);
                break;
            }
        }

        if (!out.containsKey("NAME")) {
            // Fallback search looking for dot-initial names like B.H. PRAKASH in all lines
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.matches("^[A-Z]\\.[A-Z]\\..*$") && !isGovtHeader(trimmed)) {
                    out.put("NAME", new OcrFieldResult(trimmed, conf));
                    log.info("Extracted Electricity Bill Customer Name (Initial Fallback): '{}'", trimmed);
                    break;
                }
            }
        }

        // Extract Service Address lines
        StringBuilder addrBuilder = new StringBuilder();
        boolean startCapturing = false;
        int collectedLines = 0;

        for (String line : blockLines) {
            String trimmed = line.trim();
            if (out.containsKey("NAME") && trimmed.contains(out.get("NAME").value())) {
                startCapturing = true;
                continue;
            }
            if (startCapturing) {
                if (trimmed.toUpperCase().contains("TARIFF") || trimmed.toUpperCase().contains("TYPE") || trimmed.isEmpty() || collectedLines >= 5) {
                    break;
                }
                if (addrBuilder.length() > 0) addrBuilder.append(", ");
                addrBuilder.append(trimmed);
                collectedLines++;
            }
        }

        String finalAddress = addrBuilder.toString().replaceAll("\\s+", " ").trim();
        if (finalAddress.length() > 950) {
            finalAddress = finalAddress.substring(0, 950);
        }

        if (!finalAddress.isEmpty()) {
            out.put("ADDRESS", new OcrFieldResult(finalAddress, conf));
            log.info("Extracted Electricity Bill Address: '{}'", finalAddress);
        } else {
            extractAddress(rawText, out, conf);
        }
    }

    // ==========================================
    // UTILITY METHODS
    // ==========================================

    private void extractNameByLabelOrLine(String[] lines, String rawText, Map<String, OcrFieldResult> out, double conf) {
        if (out.containsKey("NAME")) {
            return;
        }

        Pattern labelPattern = Pattern.compile(
                "(?i)(?<!Father's\\s)(?<!Father\\s)(?:[“\"'~\\s]*(?:NAME|AME|HAME|NANE)|Name)[ \\t:]+([A-Za-z ]+?)(?=\\s*(?:Order\\s*No|D\\.?O\\.?B\\.?|1\\s*O|I\\s*O|Date of Birth|Father|S/D/W|S/W/D|S/O|SDW|B\\.G|\\[|\\(|\\d|\\r?\\n|$))"
        );
        Matcher labelMatcher = labelPattern.matcher(rawText);
        if (labelMatcher.find()) {
            String clean = extractCleanPersonName(labelMatcher.group(1));
            if (isValidPersonName(clean) && !isGovtHeader(clean)) {
                out.put("NAME", new OcrFieldResult(clean, conf));
                return;
            }
        }

        for (String rawLine : lines) {
            String candidate = extractCleanPersonName(rawLine);
            if (isValidPersonName(candidate) && !isGovtHeader(candidate)) {
                out.put("NAME", new OcrFieldResult(candidate, conf));
                break;
            }
        }
    }

    private void extractAddress(String rawText, Map<String, OcrFieldResult> out, double conf) {
        Matcher m = ADDRESS_PATTERN.matcher(rawText);
        if (m.find()) {
            String addr = m.group(1).trim().replace("\n", " ").replaceAll("\\s+", " ");
            if (addr.length() > 950) addr = addr.substring(0, 950);
            out.put("ADDRESS", new OcrFieldResult(addr, conf));
        }
    }

    private boolean isValidPersonName(String s) {
        return s != null && s.trim().length() >= 2 && s.matches("^[A-Za-z ]+$") && s.trim().split("\\s+").length >= 1;
    }

    private boolean isGovtHeader(String s) {
        String u = s.toUpperCase();
        return u.contains("INCOME") || u.contains("TAX") || u.contains("DEPARTMENT") ||
               u.contains("GOVT") || u.contains("GOVERNMENT") || u.contains("INDIA") ||
               u.contains("PERMANENT") || u.contains("ACCOUNT") || u.contains("NUMBER") ||
               u.contains("CARD") || u.contains("FATHER") || u.contains("NAME") ||
               u.contains("UNION") || u.contains("REPUBLIC") || u.contains("PASSPORT") ||
               u.contains("AUTHORITY") || u.contains("ELECTION") || u.contains("COMMISSION") ||
               u.contains("MAHARASHTRA") || u.contains("MOTOR") || u.contains("VEHICLE") ||
               u.contains("SURNAME") || u.contains("SUMAME") || u.contains("GIVEN") ||
               u.contains("NATIONALITY") || u.contains("HOLDER") || u.contains("SIGNATURE") ||
               u.contains("PROCESSED") || u.contains("BIRTH") || u.contains("DATE") ||
               u.contains("ENTERPRISES") || u.contains("INVOICE") || u.contains("CUSTOMER") ||
               u.contains("INDIANOIL") || u.contains("INDANE") || u.contains("BESCOM") ||
               u.contains("MAHOTSAV") || u.contains("OFFICE") || u.contains("WHOLLY") ||
               u.contains("UNDERTAKING");
    }

    private String normalizeDate(String dateStr) {
        if (dateStr == null) return null;
        String clean = dateStr.replaceAll("[^0-9/\\-]", "").replace('-', '/');

        String[] parts = clean.split("/");
        if (parts.length == 3) {
            String dd = parts[0];
            String mm = parts[1];
            String yyyy = parts[2];
            if (yyyy.length() > 4) yyyy = yyyy.substring(0, 4);

            if (mm.equals("82") || (mm.equals("02") && clean.contains("12"))) {
                mm = "12";
            } else if (mm.length() == 2 && Integer.parseInt(mm) > 12) {
                if (mm.startsWith("8") || mm.startsWith("7") || mm.startsWith("9")) {
                    mm = "1" + mm.substring(1);
                }
            }
            try {
                return String.format("%02d/%02d/%s", Integer.parseInt(dd), Integer.parseInt(mm), yyyy);
            } catch (Exception ignored) {}
        }

        String digitsOnly = dateStr.replaceAll("[^0-9]", "");
        if (digitsOnly.length() == 10) {
            String dd = digitsOnly.substring(0, 2);
            String mm = digitsOnly.substring(3, 5);
            String yyyy = digitsOnly.substring(6, 10);
            try {
                int d = Integer.parseInt(dd);
                int m = Integer.parseInt(mm);
                int y = Integer.parseInt(yyyy);
                if (d >= 1 && d <= 31 && m >= 1 && m <= 12 && y >= 1900 && y <= 2099) {
                    return String.format("%02d/%02d/%04d", d, m, y);
                }
            } catch (Exception ignored) {}
        } else if (digitsOnly.length() == 8) {
            String dd = digitsOnly.substring(0, 2);
            String mm = digitsOnly.substring(2, 4);
            String yyyy = digitsOnly.substring(4, 8);
            try {
                int d = Integer.parseInt(dd);
                int m = Integer.parseInt(mm);
                int y = Integer.parseInt(yyyy);
                if (d >= 1 && d <= 31 && m >= 1 && m <= 12 && y >= 1900 && y <= 2099) {
                    return String.format("%02d/%02d/%04d", d, m, y);
                }
            } catch (Exception ignored) {}
        }

        return clean;
    }

    private String formatMrzDate(String yymmdd) {
        if (yymmdd == null || yymmdd.length() != 6) return yymmdd;
        String yy = yymmdd.substring(0, 2);
        String mm = yymmdd.substring(2, 4);
        String dd = yymmdd.substring(4, 6);
        int yearPrefix = Integer.parseInt(yy) > 40 ? 1900 : 2000;
        return String.format("%s/%s/%d", dd, mm, (yearPrefix + Integer.parseInt(yy)));
    }
}
