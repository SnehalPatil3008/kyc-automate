package com.kycautomate.document.service;

import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Service
public class ImagePreprocessingService {

    public byte[] preprocessImage(byte[] imageBytes) throws IOException {
        BufferedImage original = ImageIO.read(new ByteArrayInputStream(imageBytes));
        if (original == null) {
            return imageBytes;
        }

        BufferedImage gray = new BufferedImage(original.getWidth(), original.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = gray.createGraphics();
        g.drawImage(original, 0, 0, null);
        g.dispose();

        BufferedImage binarized = new BufferedImage(gray.getWidth(), gray.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
        int threshold = calculateOtsuThreshold(gray);

        for (int x = 0; x < gray.getWidth(); x++) {
            for (int y = 0; y < gray.getHeight(); y++) {
                int rgb = gray.getRGB(x, y);
                int luminance = (rgb >> 16) & 0xFF;
                if (luminance < threshold) {
                    binarized.setRGB(x, y, Color.BLACK.getRGB());
                } else {
                    binarized.setRGB(x, y, Color.WHITE.getRGB());
                }
            }
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(binarized, "png", baos);
        return baos.toByteArray();
    }

    private int calculateOtsuThreshold(BufferedImage original) {
        int[] histogram = new int[256];
        for (int x = 0; x < original.getWidth(); x++) {
            for (int y = 0; y < original.getHeight(); y++) {
                int val = (original.getRGB(x, y) >> 16) & 0xFF;
                histogram[val]++;
            }
        }

        int total = original.getWidth() * original.getHeight();
        float sum = 0;
        for (int t = 0; t < 256; t++) sum += t * histogram[t];

        float sumB = 0;
        int wB = 0;
        int wF = 0;
        float varMax = 0;
        int threshold = 128;

        for (int t = 0; t < 256; t++) {
            wB += histogram[t];
            if (wB == 0) continue;
            wF = total - wB;
            if (wF == 0) break;

            sumB += (float) (t * histogram[t]);
            float mB = sumB / wB;
            float mF = (sum - sumB) / wF;

            float varBetween = (float) wB * (float) wF * (mB - mF) * (mB - mF);
            if (varBetween > varMax) {
                varMax = varBetween;
                threshold = t;
            }
        }
        return threshold;
    }
}