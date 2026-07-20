package com.licensemanager.controller;

import com.licensemanager.dto.*;
import com.licensemanager.model.License;
import com.licensemanager.model.LicenseValidationLog;
import com.licensemanager.service.LicenseService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/license")
public class LicenseController {
    
    private final LicenseService licenseService;
    
    public LicenseController(LicenseService licenseService) {
        this.licenseService = licenseService;
    }
    
    @PostMapping("/generate")
    public ResponseEntity<?> generateLicense(@Valid @RequestBody LicenseRequest request) {
        try {
            License license = licenseService.generateLicense(request);
            return ResponseEntity.ok(license);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    @PostMapping("/validate")
    public ResponseEntity<LicenseValidationResponse> validateLicense(
            @Valid @RequestBody LicenseValidationRequest request,
            HttpServletRequest httpRequest) {
        String ipAddress = getClientIpAddress(httpRequest);
        LicenseValidationResponse response = licenseService.validateLicense(request, ipAddress);
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/validate-offline")
    public ResponseEntity<LicenseValidationResponse> validateLicenseOffline(
            @Valid @RequestBody LicenseValidationRequest request) {
        LicenseValidationResponse response = licenseService.validateLicenseOffline(request);
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/renew")
    public ResponseEntity<?> renewLicense(@Valid @RequestBody LicenseRenewalRequest request) {
        try {
            License license = licenseService.renewLicense(request);
            return ResponseEntity.ok(license);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    @GetMapping("/all")
    public ResponseEntity<List<License>> getAllLicenses() {
        return ResponseEntity.ok(licenseService.getAllLicenses());
    }
    
    @GetMapping("/{licenseKey}")
    public ResponseEntity<?> getLicenseByKey(@PathVariable String licenseKey) {
        return licenseService.getLicenseByKey(licenseKey)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    @GetMapping("/email/{email}")
    public ResponseEntity<List<License>> getLicensesByEmail(@PathVariable String email) {
        return ResponseEntity.ok(licenseService.getLicensesByEmail(email));
    }
    
    @PostMapping("/{licenseKey}/suspend")
    public ResponseEntity<?> suspendLicense(@PathVariable String licenseKey) {
        try {
            License license = licenseService.suspendLicense(licenseKey);
            return ResponseEntity.ok(license);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    @PostMapping("/{licenseKey}/activate")
    public ResponseEntity<?> activateLicense(@PathVariable String licenseKey) {
        try {
            License license = licenseService.activateLicense(licenseKey);
            return ResponseEntity.ok(license);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    @PostMapping("/{licenseKey}/revoke")
    public ResponseEntity<?> revokeLicense(@PathVariable String licenseKey) {
        try {
            License license = licenseService.revokeLicense(licenseKey);
            return ResponseEntity.ok(license);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    @DeleteMapping("/{licenseKey}")
    public ResponseEntity<?> deleteLicense(@PathVariable String licenseKey) {
        try {
            licenseService.deleteLicense(licenseKey);
            return ResponseEntity.ok(Map.of("message", "License deleted successfully"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    @GetMapping("/stats")
    public ResponseEntity<LicenseService.LicenseStats> getStats() {
        return ResponseEntity.ok(licenseService.getStats());
    }
    
    @GetMapping("/validation-logs")
    public ResponseEntity<List<LicenseValidationLog>> getValidationLogs() {
        return ResponseEntity.ok(licenseService.getValidationLogs());
    }
    
    @GetMapping("/{licenseKey}/validation-logs")
    public ResponseEntity<List<LicenseValidationLog>> getValidationLogsByLicense(@PathVariable String licenseKey) {
        return ResponseEntity.ok(licenseService.getValidationLogsByLicense(licenseKey));
    }
    
    @GetMapping("/{licenseKey}/download")
    public ResponseEntity<?> downloadLicenseFile(@PathVariable String licenseKey) {
        try {
            String licenseFile = licenseService.generateLicenseFile(licenseKey);
            return ResponseEntity.ok(Map.of(
                "licenseFile", licenseFile,
                "filename", licenseKey + ".lic",
                "instructions", "Save this content to a .lic file for offline validation"
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    @PostMapping("/validate-file")
    public ResponseEntity<?> validateLicenseFile(@RequestBody Map<String, String> request) {
        try {
            String licenseFileContent = request.get("licenseFile");
            if (licenseFileContent == null || licenseFileContent.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "License file content is required"));
            }
            var result = licenseService.validateLicenseFile(licenseFileContent);
            return ResponseEntity.ok(Map.of(
                "valid", result.valid(),
                "message", result.message(),
                "licenseData", result.licenseData() != null ? result.licenseData().data() : null,
                "accessLevel", result.licenseData() != null ? result.licenseData().accessLevel().name() : "NONE"
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    @PatchMapping("/{licenseKey}/grace-period")
    public ResponseEntity<?> updateGracePeriod(@PathVariable String licenseKey, @RequestBody Map<String, Integer> request) {
        try {
            Integer gracePeriodDays = request.get("gracePeriodDays");
            if (gracePeriodDays == null || gracePeriodDays < 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "Valid grace period days required"));
            }
            License license = licenseService.updateGracePeriod(licenseKey, gracePeriodDays);
            return ResponseEntity.ok(license);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
