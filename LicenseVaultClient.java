package com.licensevault.sdk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;

/**
 * LicenseVault SDK for Java/Spring Boot applications
 *
 * Supports both online and offline license validation with grace period handling.
 * Validation results are cached for 24 hours per license key, so repeated calls
 * (e.g. from a request interceptor) don't hit the network or re-read the offline
 * file on every single request.
 *
 * Usage:
 * <pre>
 * LicenseVaultClient client = new LicenseVaultClient("https://your-server.com/api", "signing-secret");
 * ValidationResult result = client.validateLicense("XXXX-XXXX-XXXX-XXXX");
 *
 * if (result.isValid()) {
 *     switch (result.getAccessLevel()) {
 *         case FULL -> // All features enabled
 *         case LIMITED -> // Read-only, core features only (grace period)
 *         case NONE -> // No access
 *     }
 * }
 *
 * // After a renewal completes, force a fresh check instead of waiting for the cache to expire:
 * client.forceRevalidate("XXXX-XXXX-XXXX-XXXX", null);
 * </pre>
 */
public class LicenseVaultClient {

    private final String serverUrl;
    private final String signingSecret;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private String licenseFilePath;

    private final LicenseValidationCache validationCache;

    public enum AccessLevel {
        FULL,       // All features enabled
        LIMITED,    // Read-only, core features only (during grace period)
        NONE        // No access
    }

    public LicenseVaultClient(String serverUrl, String signingSecret) {
        this(serverUrl, signingSecret, new LicenseValidationCache());
    }

    /**
     * Constructor allowing a custom cache -- useful for tests that need a
     * shorter TTL than the default 24 hours, or for sharing one cache across
     * multiple client instances.
     */
    public LicenseVaultClient(String serverUrl, String signingSecret, LicenseValidationCache validationCache) {
        this.serverUrl = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
        this.signingSecret = signingSecret;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.validationCache = validationCache;
    }

    /**
     * Set the path for offline license file
     */
    public void setLicenseFilePath(String path) {
        this.licenseFilePath = path;
    }

    /**
     * Get the currently configured offline license file path, or null if
     * none has been set. Used by LicenseAccessInterceptor to distinguish a
     * genuine misconfiguration (no key AND no file) from a normal check.
     */
    public String getLicenseFilePath() {
        return licenseFilePath;
    }

    /**
     * Validate license - tries online first, falls back to offline.
     * Returns a cached result if checked within the last 24 hours.
     */
    public ValidationResult validateLicense(String licenseKey) {
        return validateLicense(licenseKey, null);
    }

    /**
     * Validate license with machine ID.
     * Returns a cached result if checked within the last 24 hours; otherwise
     * performs a fresh check (online, falling back to offline) and caches it.
     */
    public ValidationResult validateLicense(String licenseKey, String machineId) {
        ValidationResult cached = validationCache.get(licenseKey);
        if (cached != null) {
            return cached;
        }

        ValidationResult result;
        try {
            result = validateOnline(licenseKey, machineId);
        } catch (Exception e) {
            System.out.println("Online validation failed, trying offline: " + e.getMessage());
            result = validateOffline();
        }

        validationCache.put(licenseKey, result);
        return result;
    }

    /**
     * Force a fresh validation, bypassing the cache. Call this right after a
     * renewal succeeds so the new expiry/grace dates take effect immediately
     * instead of waiting up to 24 hours for the cache to expire on its own.
     */
    public ValidationResult forceRevalidate(String licenseKey, String machineId) {
        validationCache.invalidate(licenseKey);
        return validateLicense(licenseKey, machineId);
    }

    /**
     * Clear the cached result for a license key without performing a new check.
     */
    public void clearCache(String licenseKey) {
        validationCache.invalidate(licenseKey);
    }

    /**
     * Force online validation
     */
    public ValidationResult validateOnline(String licenseKey, String machineId) throws IOException, InterruptedException {
        String requestBody = objectMapper.writeValueAsString(Map.of(
            "licenseKey", licenseKey,
            "machineId", machineId != null ? machineId : ""
        ));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serverUrl + "/license/validate"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        @SuppressWarnings("unchecked")
        Map<String, Object> data = objectMapper.readValue(response.body(), Map.class);

        return parseValidationResponse(data);
    }

    /**
     * Validate using offline license file
     */
    public ValidationResult validateOffline() {
        if (licenseFilePath == null) {
            return new ValidationResult(false, "No license file path configured", AccessLevel.NONE);
        }

        try {
            String licenseFileContent = Files.readString(Path.of(licenseFilePath));
            return validateLicenseFile(licenseFileContent);
        } catch (IOException e) {
            return new ValidationResult(false, "Failed to read license file: " + e.getMessage(), AccessLevel.NONE);
        }
    }

    /**
     * Validate license file content directly
     */
    public ValidationResult validateLicenseFile(String licenseFileContent) {
        try {
            String[] parts = licenseFileContent.trim().split("\\.");
            if (parts.length != 2) {
                return new ValidationResult(false, "Invalid license file format", AccessLevel.NONE);
            }

            String encodedData = parts[0];
            String providedSignature = parts[1];

            // Verify signature
            String expectedSignature = generateSignature(encodedData);
            if (!expectedSignature.equals(providedSignature)) {
                return new ValidationResult(false, "Invalid license signature - file may be tampered", AccessLevel.NONE);
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
                return new ValidationResult(false, "License is " + status.toLowerCase(), AccessLevel.NONE, licenseData);
            }

            LocalDateTime now = LocalDateTime.now();

            if (now.isBefore(expiryDate)) {
                // Active license
                return new ValidationResult(true, "License is valid", AccessLevel.FULL, licenseData);
            } else if (now.isBefore(graceEndDate)) {
                // Grace period
                long daysLeft = java.time.temporal.ChronoUnit.DAYS.between(now, graceEndDate);
                return new ValidationResult(true,
                    "License expired - Grace period active (" + daysLeft + " days remaining). Limited access only.",
                    AccessLevel.LIMITED, licenseData);
            } else {
                // Fully expired
                return new ValidationResult(false, "License and grace period have expired", AccessLevel.NONE, licenseData);
            }

        } catch (Exception e) {
            return new ValidationResult(false, "Failed to validate license file: " + e.getMessage(), AccessLevel.NONE);
        }
    }

    /**
     * Download license file for offline use
     */
    public String downloadLicenseFile(String licenseKey, String authToken) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serverUrl + "/license/" + licenseKey + "/download"))
                .header("Authorization", "Bearer " + authToken)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        @SuppressWarnings("unchecked")
        Map<String, Object> data = objectMapper.readValue(response.body(), Map.class);
        return (String) data.get("licenseFile");
    }

    /**
     * Save license file to disk
     */
    public void saveLicenseFile(String licenseKey, String authToken, String filePath) throws IOException, InterruptedException {
        String licenseFile = downloadLicenseFile(licenseKey, authToken);
        Files.writeString(Path.of(filePath), licenseFile);
        this.licenseFilePath = filePath;
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

    private ValidationResult parseValidationResponse(Map<String, Object> data) {
        boolean valid = (boolean) data.getOrDefault("valid", false);
        String message = (String) data.getOrDefault("message", "");
        String accessLevelStr = (String) data.getOrDefault("accessLevel", "NONE");
        AccessLevel accessLevel = AccessLevel.valueOf(accessLevelStr);

        ValidationResult result = new ValidationResult(valid, message, accessLevel, data);
        result.setPremiumFeaturesEnabled((boolean) data.getOrDefault("premiumFeaturesEnabled", false));
        result.setWriteAccessEnabled((boolean) data.getOrDefault("writeAccessEnabled", false));
        result.setInGracePeriod((boolean) data.getOrDefault("inGracePeriod", false));
        result.setWarningMessage((String) data.get("warningMessage"));

        if (data.containsKey("daysUntilExpiry")) {
            result.setDaysUntilExpiry((int) data.get("daysUntilExpiry"));
        }
        if (data.containsKey("daysUntilGraceEnd")) {
            result.setDaysUntilGraceEnd((int) data.get("daysUntilGraceEnd"));
        }

        return result;
    }

    /**
     * Validation result containing all license information
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String message;
        private final AccessLevel accessLevel;
        private final Map<String, Object> rawData;

        private boolean premiumFeaturesEnabled;
        private boolean writeAccessEnabled;
        private boolean inGracePeriod;
        private String warningMessage;
        private int daysUntilExpiry;
        private int daysUntilGraceEnd;

        public ValidationResult(boolean valid, String message, AccessLevel accessLevel) {
            this(valid, message, accessLevel, null);
        }

        public ValidationResult(boolean valid, String message, AccessLevel accessLevel, Map<String, Object> rawData) {
            this.valid = valid;
            this.message = message;
            this.accessLevel = accessLevel;
            this.rawData = rawData;
        }

        public boolean isValid() { return valid; }
        public String getMessage() { return message; }
        public AccessLevel getAccessLevel() { return accessLevel; }
        public Map<String, Object> getRawData() { return rawData; }

        public boolean isPremiumFeaturesEnabled() { return premiumFeaturesEnabled; }
        public void setPremiumFeaturesEnabled(boolean v) { this.premiumFeaturesEnabled = v; }

        public boolean isWriteAccessEnabled() { return writeAccessEnabled; }
        public void setWriteAccessEnabled(boolean v) { this.writeAccessEnabled = v; }

        public boolean isInGracePeriod() { return inGracePeriod; }
        public void setInGracePeriod(boolean v) { this.inGracePeriod = v; }

        public String getWarningMessage() { return warningMessage; }
        public void setWarningMessage(String v) { this.warningMessage = v; }

        public int getDaysUntilExpiry() { return daysUntilExpiry; }
        public void setDaysUntilExpiry(int v) { this.daysUntilExpiry = v; }

        public int getDaysUntilGraceEnd() { return daysUntilGraceEnd; }
        public void setDaysUntilGraceEnd(int v) { this.daysUntilGraceEnd = v; }

        /**
         * Check if write operations should be allowed
         */
        public boolean canWrite() {
            return valid && accessLevel == AccessLevel.FULL && writeAccessEnabled;
        }

        /**
         * Check if premium features should be enabled
         */
        public boolean canUsePremiumFeatures() {
            return valid && accessLevel == AccessLevel.FULL && premiumFeaturesEnabled;
        }

        /**
         * Check if basic read operations are allowed
         */
        public boolean canRead() {
            return valid && (accessLevel == AccessLevel.FULL || accessLevel == AccessLevel.LIMITED);
        }
    }
}
