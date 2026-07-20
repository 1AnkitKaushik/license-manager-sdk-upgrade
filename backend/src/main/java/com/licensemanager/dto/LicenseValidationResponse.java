package com.licensemanager.dto;

import com.licensemanager.model.License.LicenseStatus;
import com.licensemanager.model.License.SubscriptionType;
import com.licensemanager.model.License.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LicenseValidationResponse {
    private boolean valid;
    private String message;
    private String licenseKey;
    private LicenseStatus status;
    private SubscriptionType subscriptionType;
    private LocalDateTime startDate;
    private LocalDateTime expiryDate;
    private LocalDateTime graceEndDate;
    private int gracePeriodDays;
    private int daysUntilExpiry;
    private int daysUntilGraceEnd;
    private boolean expiringSoon;
    private boolean inGracePeriod;
    private String warningMessage;
    
    // Access control
    private AccessLevel accessLevel;
    private boolean premiumFeaturesEnabled;
    private boolean writeAccessEnabled;
}
