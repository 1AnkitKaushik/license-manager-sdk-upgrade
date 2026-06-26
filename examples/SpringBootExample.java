package com.example.myapp;

import com.licensevault.sdk.LicenseAccessInterceptor;
import com.licensevault.sdk.LicenseVaultClient;
import com.licensevault.sdk.LicenseVaultClient.ValidationResult;
import com.licensevault.sdk.LicenseVaultClient.AccessLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.annotation.PostConstruct;

/**
 * Example Spring Boot application using LicenseVault SDK
 */
@SpringBootApplication
public class MyLicensedApplication {

    public static void main(String[] args) {
        SpringApplication.run(MyLicensedApplication.class, args);
    }
}

/**
 * License configuration and validation service.
 *
 * getLicenseStatus() now always asks LicenseVaultClient directly instead of
 * returning a snapshot taken at startup -- this is safe to call on every
 * request because the client caches results for 24 hours internally, so it
 * only actually hits the network (or re-reads the offline file) roughly
 * once a day per license key.
 */
@Component
class LicenseManager {

    @Value("${licensevault.server-url:https://your-server.com/api}")
    private String serverUrl;

    @Value("${licensevault.signing-secret:your-signing-secret}")
    private String signingSecret;

    @Value("${licensevault.license-key:}")
    private String licenseKey;

    @Value("${licensevault.license-file:license.lic}")
    private String licenseFilePath;

    private LicenseVaultClient client;
    private ValidationResult currentLicenseStatus;

    @PostConstruct
    public void init() {
        client = new LicenseVaultClient(serverUrl, signingSecret);
        client.setLicenseFilePath(licenseFilePath);
        validateOnStartup();
    }

    /**
     * Validate license on application startup. This also populates the
     * client's 24h cache, so the very first request handled by
     * LicenseAccessInterceptor won't trigger a second network call.
     */
    private void validateOnStartup() {
        System.out.println("=== LicenseVault: Validating License ===");

        currentLicenseStatus = getLicenseStatus();

        System.out.println("License Valid: " + currentLicenseStatus.isValid());
        System.out.println("Access Level: " + currentLicenseStatus.getAccessLevel());
        System.out.println("Message: " + currentLicenseStatus.getMessage());

        if (currentLicenseStatus.getWarningMessage() != null) {
            System.out.println("WARNING: " + currentLicenseStatus.getWarningMessage());
        }

        if (!currentLicenseStatus.isValid()) {
            System.err.println("LICENSE INVALID - Application may have limited or no functionality");
        } else if (currentLicenseStatus.getAccessLevel() == AccessLevel.LIMITED) {
            System.out.println("GRACE PERIOD - Running with limited access (read-only, core features only)");
        }

        System.out.println("=========================================");
    }

    /**
     * Get current license status. Backed by LicenseVaultClient's 24h cache --
     * cheap to call on every request, but always reflects a check no more
     * than 24 hours old.
     */
    public ValidationResult getLicenseStatus() {
        currentLicenseStatus = (licenseKey != null && !licenseKey.isEmpty())
                ? client.validateLicense(licenseKey)
                : client.validateOffline();
        return currentLicenseStatus;
    }

    /**
     * Force a fresh validation, bypassing the cache. Call this right after a
     * renewal completes so the new expiry date takes effect immediately.
     */
    public ValidationResult refreshLicense() {
        currentLicenseStatus = (licenseKey != null && !licenseKey.isEmpty())
                ? client.forceRevalidate(licenseKey, null)
                : client.validateOffline();
        return currentLicenseStatus;
    }

    /**
     * Check if feature is allowed based on license
     */
    public boolean isFeatureAllowed(FeatureType feature) {
        ValidationResult status = getLicenseStatus();
        if (!status.isValid()) {
            return false;
        }

        return switch (feature) {
            case READ -> status.canRead();
            case WRITE -> status.canWrite();
            case PREMIUM -> status.canUsePremiumFeatures();
        };
    }

    /** Exposed so LicenseAccessInterceptor can be registered with this app's client. */
    public LicenseVaultClient getClient() {
        return client;
    }

    /** Exposed so LicenseAccessInterceptor knows which key to check on each request. */
    public String getLicenseKey() {
        return licenseKey;
    }

    public enum FeatureType {
        READ, WRITE, PREMIUM
    }
}

/**
 * Registers the SDK's interceptor for a blanket 401 cutoff on any request
 * once the license has no access at all (fully expired, suspended, revoked).
 * Per-feature checks (write/premium during grace period) still happen in
 * the controller via LicenseManager.isFeatureAllowed() below, and still
 * return 403 -- that's a different situation (license is valid, but this
 * feature isn't included), not an access cutoff.
 */
@Configuration
class LicenseWebConfig implements WebMvcConfigurer {

    private final LicenseManager licenseManager;

    LicenseWebConfig(LicenseManager licenseManager) {
        this.licenseManager = licenseManager;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(
                new LicenseAccessInterceptor(licenseManager.getClient(), licenseManager::getLicenseKey)
        ).addPathPatterns("/api/**")
         .excludePathPatterns("/api/license/status", "/api/license/refresh");
    }
}

/**
 * Example REST controller with license-aware endpoints.
 *
 * Note: the blanket "license is fully dead" case (AccessLevel.NONE) is now
 * caught upstream by LicenseAccessInterceptor with a 401, before requests
 * even reach these methods. The checks below now only matter for the
 * finer-grained read/write/premium distinction within an otherwise-valid
 * license (e.g. write disabled during grace period) -- so they're left
 * unchanged from the original example.
 */
@RestController
@RequestMapping("/api")
class MyController {

    private final LicenseManager licenseManager;

    public MyController(LicenseManager licenseManager) {
        this.licenseManager = licenseManager;
    }

    @GetMapping("/data")
    public Object getData() {
        if (!licenseManager.isFeatureAllowed(LicenseManager.FeatureType.READ)) {
            throw new LicenseException("License expired - no read access");
        }

        return java.util.Map.of(
            "data", "Your application data here",
            "licenseStatus", licenseManager.getLicenseStatus().getAccessLevel().name()
        );
    }

    @PostMapping("/data")
    public Object saveData(@RequestBody Object data) {
        if (!licenseManager.isFeatureAllowed(LicenseManager.FeatureType.WRITE)) {
            throw new LicenseException("Write operations disabled - license expired or in grace period");
        }

        return java.util.Map.of("saved", true);
    }

    @GetMapping("/premium-feature")
    public Object premiumFeature() {
        if (!licenseManager.isFeatureAllowed(LicenseManager.FeatureType.PREMIUM)) {
            throw new LicenseException("Premium features disabled - upgrade your license");
        }

        return java.util.Map.of("premium", "Premium feature data");
    }

    @GetMapping("/license/status")
    public Object getLicenseStatus() {
        var status = licenseManager.getLicenseStatus();
        return java.util.Map.of(
            "valid", status.isValid(),
            "accessLevel", status.getAccessLevel().name(),
            "message", status.getMessage(),
            "canRead", status.canRead(),
            "canWrite", status.canWrite(),
            "canUsePremium", status.canUsePremiumFeatures(),
            "inGracePeriod", status.isInGracePeriod(),
            "daysUntilExpiry", status.getDaysUntilExpiry(),
            "warning", status.getWarningMessage()
        );
    }

    @PostMapping("/license/refresh")
    public Object refreshLicense() {
        var status = licenseManager.refreshLicense();
        return java.util.Map.of(
            "valid", status.isValid(),
            "accessLevel", status.getAccessLevel().name(),
            "message", status.getMessage()
        );
    }
}

/**
 * Custom exception for license violations (feature-level, not the blanket cutoff)
 */
class LicenseException extends RuntimeException {
    public LicenseException(String message) {
        super(message);
    }
}

/**
 * Exception handler for feature-level license violations. Returns 403,
 * since the license itself is still valid -- only a specific feature is
 * unavailable. The blanket "no valid license at all" case returns 401 from
 * LicenseAccessInterceptor instead, before requests reach this far.
 */
@ControllerAdvice
class LicenseExceptionHandler {

    @ExceptionHandler(LicenseException.class)
    @ResponseBody
    @ResponseStatus(org.springframework.http.HttpStatus.FORBIDDEN)
    public java.util.Map<String, Object> handleLicenseException(LicenseException e) {
        return java.util.Map.of(
            "error", "LICENSE_ERROR",
            "message", e.getMessage()
        );
    }
}
