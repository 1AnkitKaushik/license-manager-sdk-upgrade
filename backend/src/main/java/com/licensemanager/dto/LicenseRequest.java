package com.licensemanager.dto;

import com.licensemanager.model.License.SubscriptionType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LicenseRequest {
    @NotBlank(message = "Username is required")
    private String username;
    
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;
    
    @NotNull(message = "Subscription type is required")
    private SubscriptionType subscriptionType;
    
    private Integer maxActivations = 1;
    
    private Integer gracePeriodDays;
}
