package com.licensevault.sdk;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.Base64;

/**
 * Unit tests for LicenseVaultClient
 * 
 * Run with: mvn test
 * 
 * For integration tests, set environment variables:
 * - LICENSE_SERVER_URL
 * - LICENSE_SIGNING_SECRET
 * - TEST_LICENSE_KEY
 */
class LicenseVaultClientTest {
    
    private static final String SIGNING_SECRET = "LicenseSigningSecretKey2024ForOfflineValidation";
    private LicenseVaultClient client;
    
    @BeforeEach
    void setUp() {
        client = new LicenseVaultClient("https://test-server.com/api", SIGNING_SECRET);
    }
    
    // ==================== Offline Validation Tests ====================
    
    @Test
    @DisplayName("Should validate properly formatted active license file")
    void testValidActiveLicenseFile() {
        String licenseFile = createTestLicenseFile(
            LocalDateTime.now().plusDays(30),  // Expires in 30 days
            LocalDateTime.now().plusDays(37),  // Grace ends in 37 days
            "ACTIVE"
        );
        
        LicenseVaultClient.ValidationResult result = client.validateLicenseFile(licenseFile);
        
        assertTrue(result.isValid(), "License should be valid");
        assertEquals(LicenseVaultClient.AccessLevel.FULL, result.getAccessLevel());
        assertTrue(result.canRead());
        assertTrue(result.canWrite());
        assertTrue(result.canUsePremiumFeatures());
        assertFalse(result.isInGracePeriod());
    }
    
    @Test
    @DisplayName("Should return LIMITED access for license in grace period")
    void testGracePeriodLicenseFile() {
        String licenseFile = createTestLicenseFile(
            LocalDateTime.now().minusDays(3),  // Expired 3 days ago
            LocalDateTime.now().plusDays(4),   // Grace ends in 4 days
            "GRACE_PERIOD"
        );
        
        LicenseVaultClient.ValidationResult result = client.validateLicenseFile(licenseFile);
        
        assertTrue(result.isValid(), "License should be valid during grace period");
        assertEquals(LicenseVaultClient.AccessLevel.LIMITED, result.getAccessLevel());
        assertTrue(result.canRead(), "Read should be allowed in grace period");
        assertFalse(result.canWrite(), "Write should be disabled in grace period");
        assertFalse(result.canUsePremiumFeatures(), "Premium should be disabled in grace period");
    }
    
    @Test
    @DisplayName("Should return NONE access for fully expired license")
    void testExpiredLicenseFile() {
        String licenseFile = createTestLicenseFile(
            LocalDateTime.now().minusDays(30),  // Expired 30 days ago
            LocalDateTime.now().minusDays(23),  // Grace ended 23 days ago
            "EXPIRED"
        );
        
        LicenseVaultClient.ValidationResult result = client.validateLicenseFile(licenseFile);
        
        assertFalse(result.isValid(), "Expired license should not be valid");
        assertEquals(LicenseVaultClient.AccessLevel.NONE, result.getAccessLevel());
        assertFalse(result.canRead());
        assertFalse(result.canWrite());
        assertFalse(result.canUsePremiumFeatures());
    }
    
    @Test
    @DisplayName("Should reject license file with invalid signature")
    void testInvalidSignature() {
        String validData = Base64.getEncoder().encodeToString(
            "{\"licenseKey\":\"TEST-1234\",\"status\":\"ACTIVE\"}".getBytes()
        );
        String invalidSignature = "invalid-signature-here";
        String licenseFile = validData + "." + invalidSignature;
        
        LicenseVaultClient.ValidationResult result = client.validateLicenseFile(licenseFile);
        
        assertFalse(result.isValid());
        assertTrue(result.getMessage().toLowerCase().contains("signature"));
        assertEquals(LicenseVaultClient.AccessLevel.NONE, result.getAccessLevel());
    }
    
    @Test
    @DisplayName("Should reject license file with invalid format")
    void testInvalidFormat() {
        String invalidFile = "no-dot-separator-here";
        
        LicenseVaultClient.ValidationResult result = client.validateLicenseFile(invalidFile);
        
        assertFalse(result.isValid());
        assertTrue(result.getMessage().toLowerCase().contains("format"));
    }
    
    @Test
    @DisplayName("Should reject suspended license")
    void testSuspendedLicense() {
        String licenseFile = createTestLicenseFile(
            LocalDateTime.now().plusDays(30),
            LocalDateTime.now().plusDays(37),
            "SUSPENDED"
        );
        
        LicenseVaultClient.ValidationResult result = client.validateLicenseFile(licenseFile);
        
        assertFalse(result.isValid());
        assertTrue(result.getMessage().toLowerCase().contains("suspended"));
        assertEquals(LicenseVaultClient.AccessLevel.NONE, result.getAccessLevel());
    }
    
    @Test
    @DisplayName("Should reject revoked license")
    void testRevokedLicense() {
        String licenseFile = createTestLicenseFile(
            LocalDateTime.now().plusDays(30),
            LocalDateTime.now().plusDays(37),
            "REVOKED"
        );
        
        LicenseVaultClient.ValidationResult result = client.validateLicenseFile(licenseFile);
        
        assertFalse(result.isValid());
        assertTrue(result.getMessage().toLowerCase().contains("revoked"));
    }
    
    // ==================== License Key Format Tests ====================
    
    @Test
    @DisplayName("Should accept valid license key format")
    void testValidLicenseKeyFormat() {
        assertTrue(isValidFormat("ABCD-1234-EFGH-5678"));
        assertTrue(isValidFormat("A1B2-C3D4-E5F6-G7H8"));
        assertTrue(isValidFormat("0000-0000-0000-0000"));
        assertTrue(isValidFormat("ZZZZ-ZZZZ-ZZZZ-ZZZZ"));
    }
    
    @Test
    @DisplayName("Should reject invalid license key format")
    void testInvalidLicenseKeyFormat() {
        assertFalse(isValidFormat("ABC-1234-EFGH-5678"));    // First segment too short
        assertFalse(isValidFormat("ABCDE-1234-EFGH-5678"));  // First segment too long
        assertFalse(isValidFormat("ABCD-1234-EFGH"));        // Only 3 segments
        assertFalse(isValidFormat("abcd-1234-efgh-5678"));   // Lowercase
        assertFalse(isValidFormat("ABCD_1234_EFGH_5678"));   // Wrong separator
        assertFalse(isValidFormat(""));                       // Empty
        assertFalse(isValidFormat(null));                     // Null
    }
    
    // ==================== Integration Tests ====================
    
    @Test
    @EnabledIfEnvironmentVariable(named = "LICENSE_SERVER_URL", matches = ".+")
    @DisplayName("Integration: Should validate license online")
    void testOnlineValidation() throws Exception {
        String serverUrl = System.getenv("LICENSE_SERVER_URL");
        String signingSecret = System.getenv("LICENSE_SIGNING_SECRET");
        String licenseKey = System.getenv("TEST_LICENSE_KEY");
        
        LicenseVaultClient integrationClient = new LicenseVaultClient(serverUrl, signingSecret);
        LicenseVaultClient.ValidationResult result = integrationClient.validateOnline(licenseKey, null);
        
        assertNotNull(result);
        // Result depends on server state - just verify we got a response
        assertNotNull(result.getMessage());
    }
    
    // ==================== Helper Methods ====================
    
    private String createTestLicenseFile(LocalDateTime expiryDate, LocalDateTime graceEndDate, String status) {
        String jsonData = String.format("""
            {
                "licenseKey": "TEST-1234-5678-ABCD",
                "username": "testuser",
                "email": "test@example.com",
                "subscriptionType": "YEARLY",
                "startDate": "%s",
                "expiryDate": "%s",
                "graceEndDate": "%s",
                "gracePeriodDays": 7,
                "status": "%s",
                "premiumFeaturesEnabled": true,
                "writeAccessEnabled": true,
                "maxActivations": 1,
                "generatedAt": "%s"
            }
            """,
            LocalDateTime.now().minusDays(30).toString(),
            expiryDate.toString(),
            graceEndDate.toString(),
            status,
            LocalDateTime.now().toString()
        );
        
        String encodedData = Base64.getEncoder().encodeToString(jsonData.getBytes());
        String signature = generateSignature(encodedData);
        
        return encodedData + "." + signature;
    }
    
    private String generateSignature(String data) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec secretKey = new javax.crypto.spec.SecretKeySpec(
                SIGNING_SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8), 
                "HmacSHA256"
            );
            mac.init(secretKey);
            byte[] hash = mac.doFinal(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    private boolean isValidFormat(String licenseKey) {
        if (licenseKey == null) return false;
        return licenseKey.matches("^[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}$");
    }
}
