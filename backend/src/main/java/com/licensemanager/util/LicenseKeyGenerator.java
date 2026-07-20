package com.licensemanager.util;

import java.security.SecureRandom;
import java.util.Base64;

public class LicenseKeyGenerator {
    
    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom random = new SecureRandom();
    private static final int SEGMENT_LENGTH = 4;
    private static final int SEGMENT_COUNT = 4;
    
    /**
     * Generates a unique license key in format: XXXX-XXXX-XXXX-XXXX
     */
    public static String generateLicenseKey() {
        StringBuilder licenseKey = new StringBuilder();
        
        for (int i = 0; i < SEGMENT_COUNT; i++) {
            if (i > 0) {
                licenseKey.append("-");
            }
            for (int j = 0; j < SEGMENT_LENGTH; j++) {
                int index = random.nextInt(CHARACTERS.length());
                licenseKey.append(CHARACTERS.charAt(index));
            }
        }
        
        return licenseKey.toString();
    }
    
    /**
     * Validates the format of a license key
     */
    public static boolean isValidFormat(String licenseKey) {
        if (licenseKey == null) {
            return false;
        }
        return licenseKey.matches("^[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}$");
    }
    
    /**
     * Generates a checksum for offline validation
     */
    public static String generateChecksum(String licenseKey, String secret) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            String data = licenseKey + secret;
            byte[] hash = md.digest(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate checksum", e);
        }
    }
}
