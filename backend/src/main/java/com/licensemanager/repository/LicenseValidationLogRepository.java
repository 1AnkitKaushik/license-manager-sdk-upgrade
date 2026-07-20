package com.licensemanager.repository;

import com.licensemanager.model.LicenseValidationLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface LicenseValidationLogRepository extends MongoRepository<LicenseValidationLog, String> {
    List<LicenseValidationLog> findByLicenseKey(String licenseKey);
    List<LicenseValidationLog> findByUserId(String userId);
    List<LicenseValidationLog> findTop100ByOrderByValidatedAtDesc();
}
