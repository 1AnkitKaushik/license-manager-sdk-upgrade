package com.licensemanager.dto;

import com.licensemanager.model.License.SubscriptionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LicenseRenewalRequest {
    @NotBlank(message = "License key is required")
    private String licenseKey;
    
    @NotNull(message = "Subscription type is required")
    private SubscriptionType subscriptionType;
}
