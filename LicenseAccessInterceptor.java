package com.licensevault.sdk;

import com.licensevault.sdk.LicenseVaultClient.AccessLevel;
import com.licensevault.sdk.LicenseVaultClient.ValidationResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.function.Supplier;

/**
 * Spring MVC interceptor that blocks requests once a license has no access at
 * all (AccessLevel.NONE) -- i.e. fully expired past the grace period,
 * suspended, or revoked. Returns 401 Unauthorized for those requests.
 *
 * Relies on LicenseVaultClient's built-in 24-hour cache, so registering this
 * on every API route does not add a network call (or a file read) to every
 * single request -- only roughly once per day per license key.
 *
 * This intentionally only handles the blanket cutoff. Finer-grained checks
 * (e.g. read-only during grace period vs full write access) are still the
 * application's responsibility -- see LicenseManager.isFeatureAllowed() in
 * the Spring Boot example, which continues to return 403 for those cases
 * since the license itself is still valid, just missing a specific feature.
 *
 * Usage (in a WebMvcConfigurer):
 * <pre>
 * registry.addInterceptor(
 *     new LicenseAccessInterceptor(client, () -> currentLicenseKey)
 * ).addPathPatterns("/api/**")
 *  .excludePathPatterns("/api/license/status", "/api/license/refresh");
 * </pre>
 */
public class LicenseAccessInterceptor implements HandlerInterceptor {

    private final LicenseVaultClient client;
    private final Supplier<String> licenseKeySupplier;
    private final Supplier<String> machineIdSupplier;

    public LicenseAccessInterceptor(LicenseVaultClient client, Supplier<String> licenseKeySupplier) {
        this(client, licenseKeySupplier, () -> null);
    }

    public LicenseAccessInterceptor(LicenseVaultClient client, Supplier<String> licenseKeySupplier, Supplier<String> machineIdSupplier) {
        this.client = client;
        this.licenseKeySupplier = licenseKeySupplier;
        this.machineIdSupplier = machineIdSupplier;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        String licenseKey = licenseKeySupplier.get();
        boolean hasKey = licenseKey != null && !licenseKey.isEmpty();
        boolean hasOfflineFile = client.getLicenseFilePath() != null;

        if (!hasKey && !hasOfflineFile) {
            throw new LicenseConfigurationException(
                "No license key supplied and no offline license file configured -- " +
                "set licensevault.license-key or call client.setLicenseFilePath() " +
                "before registering LicenseAccessInterceptor"
            );
        }

        ValidationResult result = hasKey
                ? client.validateLicense(licenseKey, machineIdSupplier.get())
                : client.validateOffline();

        // Let controllers downstream read the result without re-validating
        request.setAttribute("licenseValidationResult", result);

        if (!result.isValid() || result.getAccessLevel() == AccessLevel.NONE) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            String message = result.getMessage() == null
                    ? "License is invalid or expired"
                    : result.getMessage().replace("\"", "'");
            response.getWriter().write("{\"error\":\"LICENSE_INVALID\",\"message\":\"" + message + "\"}");
            return false;
        }

        return true;
    }
}
