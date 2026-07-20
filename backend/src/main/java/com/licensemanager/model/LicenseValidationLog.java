package com.licensemanager.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "license_validation_logs")
public class LicenseValidationLog {
    @Id
    private String id;
    
    private String licenseKey;
    
    private String userId;
    
    private ValidationType validationType;
    
    private ValidationResult result;
    
    private String message;
    
    private String ipAddress;
    
    private String machineId;
    
    private LocalDateTime validatedAt = LocalDateTime.now();
    
    public enum ValidationType {
        ONLINE, OFFLINE
    }
    
    public enum ValidationResult {
        SUCCESS, FAILED, EXPIRED, INVALID, SUSPENDED
    }
}
