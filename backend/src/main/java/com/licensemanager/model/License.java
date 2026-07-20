package com.licensemanager.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "licenses")
public class License {
    @Id
    private String id;
    
    @Indexed(unique = true)
    private String licenseKey;
    
    private String userId;
    
    private String username;
    
    private String email;
    
    private SubscriptionType subscriptionType;
    
    private LocalDateTime startDate;
    
    private LocalDateTime expiryDate;
    
    private LicenseStatus status = LicenseStatus.ACTIVE;
    
    private boolean notifiedExpiringSoon = false;
    
    private LocalDateTime createdAt = LocalDateTime.now();
    
    private LocalDateTime updatedAt = LocalDateTime.now();
    
    private String machineId;
    
    private int maxActivations = 1;
    
    private int currentActivations = 0;
    
    // Grace period configuration
    private int gracePeriodDays = 7;
    
    private LocalDateTime lastNotificationSent;
    
    private int notificationsSentDuringGrace = 0;
    
    // Feature flags for the license
    private boolean premiumFeaturesEnabled = true;
    
    private boolean writeAccessEnabled = true;
    
    public enum SubscriptionType {
        MONTHLY, QUARTERLY, YEARLY
    }
    
    public enum LicenseStatus {
        ACTIVE, EXPIRED, SUSPENDED, REVOKED, GRACE_PERIOD
    }
    
    public enum AccessLevel {
        FULL,           // All features enabled
        LIMITED,        // Read-only, core features only (during grace period)
        NONE            // No access (after grace period)
    }
    
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiryDate);
    }
    
    public boolean isInGracePeriod() {
        if (!isExpired()) return false;
        LocalDateTime graceEndDate = expiryDate.plusDays(gracePeriodDays);
        return LocalDateTime.now().isBefore(graceEndDate);
    }
    
    public boolean isFullyExpired() {
        LocalDateTime graceEndDate = expiryDate.plusDays(gracePeriodDays);
        return LocalDateTime.now().isAfter(graceEndDate);
    }
    
    public LocalDateTime getGraceEndDate() {
        return expiryDate.plusDays(gracePeriodDays);
    }
    
    public boolean isExpiringSoon(int days) {
        LocalDateTime warningDate = expiryDate.minusDays(days);
        return LocalDateTime.now().isAfter(warningDate) && !isExpired();
    }
    
    public int getDaysUntilExpiry() {
        return (int) java.time.temporal.ChronoUnit.DAYS.between(LocalDateTime.now(), expiryDate);
    }
    
    public int getDaysUntilGraceEnd() {
        return (int) java.time.temporal.ChronoUnit.DAYS.between(LocalDateTime.now(), getGraceEndDate());
    }
    
    public AccessLevel getAccessLevel() {
        if (status == LicenseStatus.SUSPENDED || status == LicenseStatus.REVOKED) {
            return AccessLevel.NONE;
        }
        if (isFullyExpired()) {
            return AccessLevel.NONE;
        }
        if (isInGracePeriod() || status == LicenseStatus.GRACE_PERIOD) {
            return AccessLevel.LIMITED;
        }
        if (status == LicenseStatus.ACTIVE && !isExpired()) {
            return AccessLevel.FULL;
        }
        return AccessLevel.NONE;
    }
}
