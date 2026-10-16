package com.kycautomate.document.service;

import jakarta.annotation.PostConstruct;
import nu.pattern.OpenCV;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.ArrayList;
import java.util.List;

@Service
public class ImageQualityService {

    private static final Logger log = LoggerFactory.getLogger(ImageQualityService.class);

    @PostConstruct
    public void initOpenCv() {
        try {
            OpenCV.loadLocally();
            log.info("OpenCV native library loaded successfully.");
        } catch (Throwable t) {
            log.error("Failed to load OpenCV native library: {}", t.getMessage(), t);
        }
    }

    public record QualityReport(
            boolean passed,
            String message,
            double blurScore,
            double brightness,
            double contrast,
            double glareRatio,
            double skewAngle
    ) {}

    public QualityReport evaluate(BufferedImage image) {
        double blurScore = calculateBlurScore(image);
        double brightness = calculateBrightness(image);
        double contrast = calculateContrast(image);
        double glareRatio = calculateGlareRatio(image);
        double skewAngle = calculateSkewAngle(image);
        boolean croppingPassed = checkCropping(image);

        log.info("Image Quality Telemetry -> [Blur: {}, Brightness: {}, Contrast: {}, Glare: {}%, Skew: {}°, CroppingOK: {}]",
                Math.round(blurScore), Math.round(brightness), Math.round(contrast),
                String.format("%.2f", glareRatio * 100), String.format("%.1f", skewAngle), croppingPassed);

        if (!croppingPassed) {
            return new QualityReport(false, "Quality failed: Document edges are severely cut off or empty",
                    blurScore, brightness, contrast, glareRatio, skewAngle);
        }
        if (blurScore < 20.0) {
            return new QualityReport(false, "Quality failed: Image is blurry (score: " + Math.round(blurScore) + ")",
                    blurScore, brightness, contrast, glareRatio, skewAngle);
        }
        if (brightness < 15.0 || brightness > 253.0) {
            return new QualityReport(false, "Quality failed: Image is too dark or overexposed",
                    blurScore, brightness, contrast, glareRatio, skewAngle);
        }
        if (contrast < 12.0) {
            return new QualityReport(false, "Quality failed: Image contrast is too low",
                    blurScore, brightness, contrast, glareRatio, skewAngle);
        }

        // Distinguish true flash washout from white paper documents (utility bills / scans)
        boolean hasReadableText = (contrast >= 18.0 && blurScore >= 35.0);
        if (glareRatio > 0.65 && !hasReadableText) {
            return new QualityReport(false, "Quality failed: Excessive flash glare detected",
                    blurScore, brightness, contrast, glareRatio, skewAngle);
        }

        if (Math.abs(skewAngle) > 20.0) {
            return new QualityReport(false, "Quality failed: Document is tilted (" + Math.round(skewAngle) + "°)",
                    blurScore, brightness, contrast, glareRatio, skewAngle);
        }

        return new QualityReport(true, "Quality passed. Ready for OCR.",
                blurScore, brightness, contrast, glareRatio, skewAngle);
    }

    // =========================================================
    // INDIVIDUAL BOOLEAN CHECKS (Synchronized with evaluate)
    // =========================================================

    public boolean checkBlur(BufferedImage image) {
        return calculateBlurScore(image) >= 20.0;
    }

    public boolean checkBrightness(BufferedImage image) {
        double avg = calculateBrightness(image);
        return avg >= 15.0 && avg <= 253.0;
    }

    public boolean checkContrast(BufferedImage image) {
        return calculateContrast(image) >= 12.0;
    }

    public boolean checkGlare(BufferedImage image) {
        double glare = calculateGlareRatio(image);
        if (glare <= 0.65) {
            return true;
        }
        return calculateContrast(image) >= 18.0 && calculateBlurScore(image) >= 35.0;
    }

    public boolean checkSkew(BufferedImage image) {
        return Math.abs(calculateSkewAngle(image)) <= 20.0;
    }

    public boolean checkCropping(BufferedImage image) {
        if (image.getWidth() < 200 || image.getHeight() < 120) {
            return false;
        }
        double aspectRatio = (double) image.getWidth() / image.getHeight();
        if (aspectRatio < 0.25 || aspectRatio > 4.0) {
            return false;
        }

        Mat mat = bufferedImageToMat(image);
        Mat gray = new Mat();
        Mat edges = new Mat();
        try {
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY);
            Imgproc.Canny(gray, edges, 50, 150);

            int edgePixels = Core.countNonZero(edges);
            double edgeDensity = (double) edgePixels / (image.getWidth() * image.getHeight());
            return edgeDensity >= 0.002;
        } finally {
            mat.release();
            gray.release();
            edges.release();
        }
    }

    // =========================================================
    // CORE METRIC CALCULATORS
    // =========================================================

    public double calculateBlurScore(BufferedImage image) {
        Mat mat = bufferedImageToMat(image);
        Mat gray = new Mat();
        Mat laplacian = new Mat();
        MatOfDouble mean = new MatOfDouble();
        MatOfDouble stdDev = new MatOfDouble();

        try {
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY);
            Imgproc.Laplacian(gray, laplacian, CvType.CV_64F);
            Core.meanStdDev(laplacian, mean, stdDev);
            double sd = stdDev.get(0, 0)[0];
            return sd * sd;
        } finally {
            mat.release();
            gray.release();
            laplacian.release();
            mean.release();
            stdDev.release();
        }
    }

    public double calculateBrightness(BufferedImage image) {
        long totalBrightness = 0;
        int width = image.getWidth();
        int height = image.getHeight();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                int red = (rgb >> 16) & 255;
                int green = (rgb >> 8) & 255;
                int blue = rgb & 255;
                totalBrightness += (red + green + blue) / 3;
            }
        }
        return (double) totalBrightness / (width * height);
    }

    public double calculateContrast(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        double sum = 0;
        double sumSquared = 0;
        int totalPixels = width * height;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                int red = (rgb >> 16) & 255;
                int green = (rgb >> 8) & 255;
                int blue = rgb & 255;
                double gray = (0.299 * red) + (0.587 * green) + (0.114 * blue);
                sum += gray;
                sumSquared += gray * gray;
            }
        }

        double mean = sum / totalPixels;
        double variance = (sumSquared / totalPixels) - (mean * mean);
        return Math.sqrt(Math.max(0, variance));
    }

    public double calculateGlareRatio(BufferedImage image) {
        Mat mat = bufferedImageToMat(image);
        Mat hsv = new Mat();
        Mat glareMask = new Mat();

        try {
            Imgproc.cvtColor(mat, hsv, Imgproc.COLOR_BGR2HSV);
            // Only flag completely blown-out specular hotspots (Saturation <= 5, Value >= 254)
            Core.inRange(hsv, new Scalar(0, 0, 254), new Scalar(180, 5, 255), glareMask);
            double brightPixels = Core.countNonZero(glareMask);
            double totalPixels = (double) image.getWidth() * image.getHeight();
            return brightPixels / totalPixels;
        } finally {
            mat.release();
            hsv.release();
            glareMask.release();
        }
    }

    public double calculateSkewAngle(BufferedImage image) {
        Mat mat = bufferedImageToMat(image);
        Mat gray = new Mat();
        Mat edges = new Mat();
        Mat hierarchy = new Mat();

        try {
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY);
            Imgproc.Canny(gray, edges, 50, 150);

            List<MatOfPoint> contours = new ArrayList<>();
            Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

            if (contours.isEmpty()) {
                return 0.0;
            }

            MatOfPoint largestContour = contours.get(0);
            double largestArea = Imgproc.contourArea(largestContour);

            for (MatOfPoint contour : contours) {
                double area = Imgproc.contourArea(contour);
                if (area > largestArea) {
                    largestArea = area;
                    largestContour = contour;
                }
            }

            double totalArea = (double) image.getWidth() * image.getHeight();
            if (largestArea < totalArea * 0.05) {
                return 0.0;
            }

            MatOfPoint2f points = new MatOfPoint2f(largestContour.toArray());
            RotatedRect rotatedRect = Imgproc.minAreaRect(points);
            points.release();

            double angle = rotatedRect.angle;
            if (rotatedRect.size.width < rotatedRect.size.height) {
                angle += 90;
            }
            if (angle > 45) {
                angle -= 90;
            }
            if (angle < -45) {
                angle += 90;
            }
            return angle;
        } finally {
            mat.release();
            gray.release();
            edges.release();
            hierarchy.release();
        }
    }

    private Mat bufferedImageToMat(BufferedImage image) {
        BufferedImage converted = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D graphics = converted.createGraphics();
        graphics.drawImage(image, 0, 0, null);
        graphics.dispose();

        byte[] data = ((DataBufferByte) converted.getRaster().getDataBuffer()).getData();
        Mat mat = new Mat(image.getHeight(), image.getWidth(), CvType.CV_8UC3);
        mat.put(0, 0, data);
        return mat;
    }
}