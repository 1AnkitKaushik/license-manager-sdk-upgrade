package com.licensemanager.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.licensemanager.model.License;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
public class LicenseFileService {
    
    @Value("${license.signing-secret}")
    private String signingSecret;
    
    private final ObjectMapper objectMapper;
    
    public LicenseFileService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }
    
    /**
     * Generate a signed license file for offline validation
     */
    public String generateLicenseFile(License license) {
        try {
            Map<String, Object> licenseData = new HashMap<>();
            licenseData.put("licenseKey", license.getLicenseKey());
            licenseData.put("username", license.getUsername());
            licenseData.put("email", license.getEmail());
            licenseData.put("subscriptionType", license.getSubscriptionType().name());
            licenseData.put("startDate", license.getStartDate().toString());
            licenseData.put("expiryDate", license.getExpiryDate().toString());
            licenseData.put("gracePeriodDays", license.getGracePeriodDays());
            licenseData.put("graceEndDate", license.getGraceEndDate().toString());
            licenseData.put("status", license.getStatus().name());
            licenseData.put("maxActivations", license.getMaxActivations());
            licenseData.put("premiumFeaturesEnabled", license.isPremiumFeaturesEnabled());
            licenseData.put("writeAccessEnabled", license.isWriteAccessEnabled());
            licenseData.put("generatedAt", LocalDateTime.now().toString());
            
            String jsonData = objectMapper.writeValueAsString(licenseData);
            String encodedData = Base64.getEncoder().encodeToString(jsonData.getBytes(StandardCharsets.UTF_8));
            String signature = generateSignature(encodedData);
            
            // Format: base64(data).signature
            return encodedData + "." + signature;
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate license file", e);
        }
    }
    
    /**
     * Validate an offline license file and return license data
     */
    public OfflineLicenseValidationResult validateLicenseFile(String licenseFileContent) {
        try {
            String[] parts = licenseFileContent.split("\\.");
            if (parts.length != 2) {
                return new OfflineLicenseValidationResult(false, "Invalid license file format", null);
            }
            
            String encodedData = parts[0];
            String providedSignature = parts[1];
            
            // Verify signature
            String expectedSignature = generateSignature(encodedData);
            if (!expectedSignature.equals(providedSignature)) {
                return new OfflineLicenseValidationResult(false, "Invalid license signature - file may be tampered", null);
            }
            
            // Decode data
            String jsonData = new String(Base64.getDecoder().decode(encodedData), StandardCharsets.UTF_8);
            @SuppressWarnings("unchecked")
            Map<String, Object> licenseData = objectMapper.readValue(jsonData, Map.class);
            
            // Parse dates
            LocalDateTime expiryDate = LocalDateTime.parse((String) licenseData.get("expiryDate"));
            LocalDateTime graceEndDate = LocalDateTime.parse((String) licenseData.get("graceEndDate"));
            String status = (String) licenseData.get("status");
            
            // Check status
            if ("REVOKED".equals(status) || "SUSPENDED".equals(status)) {
                return new OfflineLicenseValidationResult(false, "License is " + status.toLowerCase(), 
                    new OfflineLicenseData(licenseData, License.AccessLevel.NONE));
            }
            
            // Check expiry
            LocalDateTime now = LocalDateTime.now();
            
            if (now.isBefore(expiryDate)) {
                // Active license
                return new OfflineLicenseValidationResult(true, "License is valid", 
                    new OfflineLicenseData(licenseData, License.AccessLevel.FULL));
            } else if (now.isBefore(graceEndDate)) {
                // Grace period
                int daysLeft = (int) java.time.temporal.ChronoUnit.DAYS.between(now, graceEndDate);
                return new OfflineLicenseValidationResult(true, 
                    "License expired - Grace period active (" + daysLeft + " days remaining). Limited access only.", 
                    new OfflineLicenseData(licenseData, License.AccessLevel.LIMITED));
            } else {
                // Fully expired
                return new OfflineLicenseValidationResult(false, "License and grace period have expired", 
                    new OfflineLicenseData(licenseData, License.AccessLevel.NONE));
            }
            
        } catch (Exception e) {
            return new OfflineLicenseValidationResult(false, "Failed to validate license file: " + e.getMessage(), null);
        }
    }
    
    private String generateSignature(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate signature", e);
        }
    }
    
    public record OfflineLicenseValidationResult(boolean valid, String message, OfflineLicenseData licenseData) {}
    
    public record OfflineLicenseData(Map<String, Object> data, License.AccessLevel accessLevel) {
        public String getLicenseKey() { return (String) data.get("licenseKey"); }
        public String getUsername() { return (String) data.get("username"); }
        public String getEmail() { return (String) data.get("email"); }
        public String getSubscriptionType() { return (String) data.get("subscriptionType"); }
        public String getExpiryDate() { return (String) data.get("expiryDate"); }
        public String getGraceEndDate() { return (String) data.get("graceEndDate"); }
        public int getGracePeriodDays() { return (int) data.get("gracePeriodDays"); }
        public boolean isPremiumFeaturesEnabled() { 
            return accessLevel == License.AccessLevel.FULL && (boolean) data.getOrDefault("premiumFeaturesEnabled", false); 
        }
        public boolean isWriteAccessEnabled() { 
            return accessLevel == License.AccessLevel.FULL && (boolean) data.getOrDefault("writeAccessEnabled", false); 
        }
    }
}
