package com.licensemanager.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LicenseValidationRequest {
    @NotBlank(message = "License key is required")
    private String licenseKey;
    
    private String machineId;
}
