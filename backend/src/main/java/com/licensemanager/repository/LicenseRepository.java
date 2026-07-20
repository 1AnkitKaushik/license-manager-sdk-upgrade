package com.licensemanager.repository;

import com.licensemanager.model.License;
import com.licensemanager.model.License.LicenseStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface LicenseRepository extends MongoRepository<License, String> {
    Optional<License> findByLicenseKey(String licenseKey);
    List<License> findByUserId(String userId);
    List<License> findByEmail(String email);
    List<License> findByStatus(LicenseStatus status);
    List<License> findByExpiryDateBeforeAndStatus(LocalDateTime date, LicenseStatus status);
    List<License> findByExpiryDateBetweenAndStatusAndNotifiedExpiringSoon(
        LocalDateTime start, LocalDateTime end, LicenseStatus status, boolean notified);
    boolean existsByLicenseKey(String licenseKey);
}
