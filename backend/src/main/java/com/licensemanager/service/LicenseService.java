package com.licensemanager.service;

import com.licensemanager.dto.*;
import com.licensemanager.model.License;
import com.licensemanager.model.License.LicenseStatus;
import com.licensemanager.model.License.SubscriptionType;
import com.licensemanager.model.License.AccessLevel;
import com.licensemanager.model.LicenseValidationLog;
import com.licensemanager.model.LicenseValidationLog.ValidationResult;
import com.licensemanager.model.LicenseValidationLog.ValidationType;
import com.licensemanager.repository.LicenseRepository;
import com.licensemanager.repository.LicenseValidationLogRepository;
import com.licensemanager.util.LicenseKeyGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
public class LicenseService {
    
    private static final Logger logger = LoggerFactory.getLogger(LicenseService.class);
    
    private final LicenseRepository licenseRepository;
    private final LicenseValidationLogRepository validationLogRepository;
    private final EmailService emailService;
    private final LicenseFileService licenseFileService;
    
    @Value("${license.expiry-warning-days}")
    private int expiryWarningDays;
    
    @Value("${license.default-grace-period-days}")
    private int defaultGracePeriodDays;
    
    public LicenseService(LicenseRepository licenseRepository, 
                         LicenseValidationLogRepository validationLogRepository,
                         EmailService emailService,
                         LicenseFileService licenseFileService) {
        this.licenseRepository = licenseRepository;
        this.validationLogRepository = validationLogRepository;
        this.emailService = emailService;
        this.licenseFileService = licenseFileService;
    }
    
    public License generateLicense(LicenseRequest request) {
        String licenseKey;
        do {
            licenseKey = LicenseKeyGenerator.generateLicenseKey();
        } while (licenseRepository.existsByLicenseKey(licenseKey));
        
        License license = new License();
        license.setLicenseKey(licenseKey);
        license.setUsername(request.getUsername());
        license.setEmail(request.getEmail());
        license.setSubscriptionType(request.getSubscriptionType());
        license.setStartDate(LocalDateTime.now());
        license.setExpiryDate(calculateExpiryDate(request.getSubscriptionType()));
        license.setMaxActivations(request.getMaxActivations() != null ? request.getMaxActivations() : 1);
        license.setStatus(LicenseStatus.ACTIVE);
        license.setGracePeriodDays(request.getGracePeriodDays() != null ? request.getGracePeriodDays() : defaultGracePeriodDays);
        license.setPremiumFeaturesEnabled(true);
        license.setWriteAccessEnabled(true);
        
        return licenseRepository.save(license);
    }
    
    private LocalDateTime calculateExpiryDate(SubscriptionType type) {
        LocalDateTime now = LocalDateTime.now();
        return switch (type) {
            case MONTHLY -> now.plusMonths(1);
            case QUARTERLY -> now.plusMonths(3);
            case YEARLY -> now.plusYears(1);
        };
    }
    
    public LicenseValidationResponse validateLicense(LicenseValidationRequest request, String ipAddress) {
        return validateLicenseInternal(request, ipAddress, ValidationType.ONLINE);
    }
    
    public LicenseValidationResponse validateLicenseOffline(LicenseValidationRequest request) {
        if (!LicenseKeyGenerator.isValidFormat(request.getLicenseKey())) {
            return LicenseValidationResponse.builder()
                    .valid(false)
                    .message("Invalid license key format")
                    .accessLevel(AccessLevel.NONE)
                    .build();
        }
        return validateLicenseInternal(request, "offline", ValidationType.OFFLINE);
    }
    
    private LicenseValidationResponse validateLicenseInternal(LicenseValidationRequest request, 
                                                               String ipAddress, 
                                                               ValidationType validationType) {
        Optional<License> licenseOpt = licenseRepository.findByLicenseKey(request.getLicenseKey());
        
        if (licenseOpt.isEmpty()) {
            logValidation(request.getLicenseKey(), null, validationType, 
                         ValidationResult.INVALID, "License key not found", ipAddress, request.getMachineId());
            return LicenseValidationResponse.builder()
                    .valid(false)
                    .message("Invalid license key")
                    .licenseKey(request.getLicenseKey())
                    .accessLevel(AccessLevel.NONE)
                    .build();
        }
        
        License license = licenseOpt.get();
        
        // Check for suspended/revoked status
        if (license.getStatus() == LicenseStatus.SUSPENDED) {
            logValidation(license.getLicenseKey(), license.getUserId(), validationType,
                         ValidationResult.SUSPENDED, "License is suspended", ipAddress, request.getMachineId());
            return LicenseValidationResponse.builder()
                    .valid(false)
                    .message("License is suspended")
                    .licenseKey(license.getLicenseKey())
                    .status(license.getStatus())
                    .accessLevel(AccessLevel.NONE)
                    .build();
        }
        
        if (license.getStatus() == LicenseStatus.REVOKED) {
            logValidation(license.getLicenseKey(), license.getUserId(), validationType,
                         ValidationResult.FAILED, "License has been revoked", ipAddress, request.getMachineId());
            return LicenseValidationResponse.builder()
                    .valid(false)
                    .message("License has been revoked")
                    .licenseKey(license.getLicenseKey())
                    .status(license.getStatus())
                    .accessLevel(AccessLevel.NONE)
                    .build();
        }
        
        // Check expiry and grace period
        AccessLevel accessLevel = license.getAccessLevel();
        
        if (license.isFullyExpired()) {
            // Fully expired - no access
            if (license.getStatus() != LicenseStatus.EXPIRED) {
                license.setStatus(LicenseStatus.EXPIRED);
                licenseRepository.save(license);
            }
            
            logValidation(license.getLicenseKey(), license.getUserId(), validationType,
                         ValidationResult.EXPIRED, "License and grace period have expired", ipAddress, request.getMachineId());
            return LicenseValidationResponse.builder()
                    .valid(false)
                    .message("License and grace period have expired. Please renew your subscription.")
                    .licenseKey(license.getLicenseKey())
                    .status(LicenseStatus.EXPIRED)
                    .subscriptionType(license.getSubscriptionType())
                    .startDate(license.getStartDate())
                    .expiryDate(license.getExpiryDate())
                    .graceEndDate(license.getGraceEndDate())
                    .accessLevel(AccessLevel.NONE)
                    .premiumFeaturesEnabled(false)
                    .writeAccessEnabled(false)
                    .build();
        }
        
        if (license.isInGracePeriod()) {
            // Grace period - limited access
            if (license.getStatus() != LicenseStatus.GRACE_PERIOD) {
                license.setStatus(LicenseStatus.GRACE_PERIOD);
                licenseRepository.save(license);
            }
            
            int daysUntilGraceEnd = license.getDaysUntilGraceEnd();
            String warningMessage = String.format(
                "GRACE PERIOD: License expired. You have %d days of limited access remaining. " +
                "Write operations and premium features are disabled. Please renew immediately.", 
                daysUntilGraceEnd
            );
            
            logValidation(license.getLicenseKey(), license.getUserId(), validationType,
                         ValidationResult.SUCCESS, "License in grace period - limited access", ipAddress, request.getMachineId());
            
            return LicenseValidationResponse.builder()
                    .valid(true)
                    .message("License in grace period - limited access")
                    .licenseKey(license.getLicenseKey())
                    .status(LicenseStatus.GRACE_PERIOD)
                    .subscriptionType(license.getSubscriptionType())
                    .startDate(license.getStartDate())
                    .expiryDate(license.getExpiryDate())
                    .graceEndDate(license.getGraceEndDate())
                    .gracePeriodDays(license.getGracePeriodDays())
                    .daysUntilExpiry(0)
                    .daysUntilGraceEnd(daysUntilGraceEnd)
                    .expiringSoon(false)
                    .inGracePeriod(true)
                    .warningMessage(warningMessage)
                    .accessLevel(AccessLevel.LIMITED)
                    .premiumFeaturesEnabled(false)
                    .writeAccessEnabled(false)
                    .build();
        }
        
        // Active license - full access
        logValidation(license.getLicenseKey(), license.getUserId(), validationType,
                     ValidationResult.SUCCESS, "License is valid", ipAddress, request.getMachineId());
        
        int daysUntilExpiry = license.getDaysUntilExpiry();
        boolean expiringSoon = license.isExpiringSoon(expiryWarningDays);
        String warningMessage = null;
        
        if (expiringSoon) {
            warningMessage = String.format("Your license will expire in %d days. Please renew soon.", daysUntilExpiry);
        }
        
        return LicenseValidationResponse.builder()
                .valid(true)
                .message("License is valid")
                .licenseKey(license.getLicenseKey())
                .status(license.getStatus())
                .subscriptionType(license.getSubscriptionType())
                .startDate(license.getStartDate())
                .expiryDate(license.getExpiryDate())
                .graceEndDate(license.getGraceEndDate())
                .gracePeriodDays(license.getGracePeriodDays())
                .daysUntilExpiry(daysUntilExpiry)
                .daysUntilGraceEnd(daysUntilExpiry + license.getGracePeriodDays())
                .expiringSoon(expiringSoon)
                .inGracePeriod(false)
                .warningMessage(warningMessage)
                .accessLevel(AccessLevel.FULL)
                .premiumFeaturesEnabled(license.isPremiumFeaturesEnabled())
                .writeAccessEnabled(license.isWriteAccessEnabled())
                .build();
    }
    
    private void logValidation(String licenseKey, String userId, ValidationType type,
                              ValidationResult result, String message, String ipAddress, String machineId) {
        LicenseValidationLog log = new LicenseValidationLog();
        log.setLicenseKey(licenseKey);
        log.setUserId(userId);
        log.setValidationType(type);
        log.setResult(result);
        log.setMessage(message);
        log.setIpAddress(ipAddress);
        log.setMachineId(machineId);
        validationLogRepository.save(log);
    }
    
    public License renewLicense(LicenseRenewalRequest request) {
        License license = licenseRepository.findByLicenseKey(request.getLicenseKey())
                .orElseThrow(() -> new RuntimeException("License not found"));
        
        LocalDateTime newExpiryDate;
        if (license.isExpired() || license.isInGracePeriod()) {
            newExpiryDate = calculateExpiryDate(request.getSubscriptionType());
        } else {
            newExpiryDate = switch (request.getSubscriptionType()) {
                case MONTHLY -> license.getExpiryDate().plusMonths(1);
                case QUARTERLY -> license.getExpiryDate().plusMonths(3);
                case YEARLY -> license.getExpiryDate().plusYears(1);
            };
        }
        
        license.setExpiryDate(newExpiryDate);
        license.setSubscriptionType(request.getSubscriptionType());
        license.setStatus(LicenseStatus.ACTIVE);
        license.setNotifiedExpiringSoon(false);
        license.setNotificationsSentDuringGrace(0);
        license.setLastNotificationSent(null);
        license.setPremiumFeaturesEnabled(true);
        license.setWriteAccessEnabled(true);
        license.setUpdatedAt(LocalDateTime.now());
        
        return licenseRepository.save(license);
    }
    
    public License updateGracePeriod(String licenseKey, int gracePeriodDays) {
        License license = licenseRepository.findByLicenseKey(licenseKey)
                .orElseThrow(() -> new RuntimeException("License not found"));
        
        license.setGracePeriodDays(gracePeriodDays);
        license.setUpdatedAt(LocalDateTime.now());
        return licenseRepository.save(license);
    }
    
    public String generateLicenseFile(String licenseKey) {
        License license = licenseRepository.findByLicenseKey(licenseKey)
                .orElseThrow(() -> new RuntimeException("License not found"));
        
        return licenseFileService.generateLicenseFile(license);
    }
    
    public LicenseFileService.OfflineLicenseValidationResult validateLicenseFile(String licenseFileContent) {
        return licenseFileService.validateLicenseFile(licenseFileContent);
    }
    
    public List<License> getAllLicenses() {
        return licenseRepository.findAll();
    }
    
    public Optional<License> getLicenseByKey(String licenseKey) {
        return licenseRepository.findByLicenseKey(licenseKey);
    }
    
    public List<License> getLicensesByEmail(String email) {
        return licenseRepository.findByEmail(email);
    }
    
    public License suspendLicense(String licenseKey) {
        License license = licenseRepository.findByLicenseKey(licenseKey)
                .orElseThrow(() -> new RuntimeException("License not found"));
        
        license.setStatus(LicenseStatus.SUSPENDED);
        license.setUpdatedAt(LocalDateTime.now());
        return licenseRepository.save(license);
    }
    
    public License activateLicense(String licenseKey) {
        License license = licenseRepository.findByLicenseKey(licenseKey)
                .orElseThrow(() -> new RuntimeException("License not found"));
        
        if (!license.isFullyExpired()) {
            if (license.isInGracePeriod()) {
                license.setStatus(LicenseStatus.GRACE_PERIOD);
            } else if (!license.isExpired()) {
                license.setStatus(LicenseStatus.ACTIVE);
            }
            license.setUpdatedAt(LocalDateTime.now());
        }
        return licenseRepository.save(license);
    }
    
    public License revokeLicense(String licenseKey) {
        License license = licenseRepository.findByLicenseKey(licenseKey)
                .orElseThrow(() -> new RuntimeException("License not found"));
        
        license.setStatus(LicenseStatus.REVOKED);
        license.setUpdatedAt(LocalDateTime.now());
        return licenseRepository.save(license);
    }
    
    public void deleteLicense(String licenseKey) {
        License license = licenseRepository.findByLicenseKey(licenseKey)
                .orElseThrow(() -> new RuntimeException("License not found"));
        licenseRepository.delete(license);
    }
    
    public List<LicenseValidationLog> getValidationLogs() {
        return validationLogRepository.findTop100ByOrderByValidatedAtDesc();
    }
    
    public List<LicenseValidationLog> getValidationLogsByLicense(String licenseKey) {
        return validationLogRepository.findByLicenseKey(licenseKey);
    }
    
    // Scheduled task: Run daily at midnight to check licenses and send emails
    @Scheduled(cron = "0 0 8 * * ?") // Run at 8 AM daily
    public void processLicenseNotifications() {
        logger.info("Running scheduled task: processing license notifications");
        
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime warningDate = now.plusDays(expiryWarningDays);
        
        // 1. Send expiry warnings for licenses expiring soon
        List<License> expiringLicenses = licenseRepository
                .findByExpiryDateBetweenAndStatusAndNotifiedExpiringSoon(
                    now, warningDate, LicenseStatus.ACTIVE, false);
        
        for (License license : expiringLicenses) {
            logger.info("Sending expiry warning for license {} to {}", 
                       license.getLicenseKey(), license.getEmail());
            emailService.sendLicenseExpiryWarning(license);
            license.setNotifiedExpiringSoon(true);
            licenseRepository.save(license);
        }
        
        // 2. Update status and send daily notifications for licenses in grace period
        List<License> allLicenses = licenseRepository.findAll();
        for (License license : allLicenses) {
            if (license.getStatus() == LicenseStatus.ACTIVE && license.isExpired()) {
                // Just expired, move to grace period
                license.setStatus(LicenseStatus.GRACE_PERIOD);
                license.setNotificationsSentDuringGrace(0);
                licenseRepository.save(license);
                
                logger.info("License {} entered grace period", license.getLicenseKey());
                emailService.sendGracePeriodNotification(license);
                license.setLastNotificationSent(now);
                license.setNotificationsSentDuringGrace(1);
                licenseRepository.save(license);
            } else if (license.isInGracePeriod() && 
                      (license.getStatus() == LicenseStatus.GRACE_PERIOD || license.getStatus() == LicenseStatus.ACTIVE)) {
                
                // In grace period - send daily notification
                if (license.getLastNotificationSent() == null || 
                    ChronoUnit.DAYS.between(license.getLastNotificationSent(), now) >= 1) {
                    
                    logger.info("Sending daily grace period notification for license {} to {}", 
                               license.getLicenseKey(), license.getEmail());
                    emailService.sendGracePeriodNotification(license);
                    license.setLastNotificationSent(now);
                    license.setNotificationsSentDuringGrace(license.getNotificationsSentDuringGrace() + 1);
                    license.setStatus(LicenseStatus.GRACE_PERIOD);
                    licenseRepository.save(license);
                }
            } else if (license.isFullyExpired() && license.getStatus() != LicenseStatus.EXPIRED) {
                // Grace period ended
                logger.info("License {} has fully expired (grace period ended)", license.getLicenseKey());
                emailService.sendLicenseExpiredNotification(license);
                license.setStatus(LicenseStatus.EXPIRED);
                licenseRepository.save(license);
            }
        }
    }
    
    public LicenseStats getStats() {
        List<License> allLicenses = licenseRepository.findAll();
        
        long total = allLicenses.size();
        long active = allLicenses.stream().filter(l -> l.getStatus() == LicenseStatus.ACTIVE).count();
        long expired = allLicenses.stream().filter(l -> l.getStatus() == LicenseStatus.EXPIRED).count();
        long suspended = allLicenses.stream().filter(l -> l.getStatus() == LicenseStatus.SUSPENDED).count();
        long revoked = allLicenses.stream().filter(l -> l.getStatus() == LicenseStatus.REVOKED).count();
        long gracePeriod = allLicenses.stream().filter(l -> l.getStatus() == LicenseStatus.GRACE_PERIOD).count();
        long expiringSoon = allLicenses.stream()
                .filter(l -> l.getStatus() == LicenseStatus.ACTIVE && l.isExpiringSoon(expiryWarningDays))
                .count();
        
        return new LicenseStats(total, active, expired, suspended, revoked, gracePeriod, expiringSoon);
    }
    
    public record LicenseStats(long total, long active, long expired, long suspended, long revoked, long gracePeriod, long expiringSoon) {}
}
