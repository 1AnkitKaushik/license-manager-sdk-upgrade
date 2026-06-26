package com.licensevault.sdk;

import com.licensevault.sdk.LicenseVaultClient.ValidationResult;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe cache for license validation results, keyed by license key.
 *
 * Used internally by LicenseVaultClient so repeated validateLicense() calls
 * -- e.g. from LicenseAccessInterceptor running on every incoming request --
 * don't hit the network or re-read the offline file more than once per
 * cache window. Defaults to a 24-hour window; pass a custom Duration for
 * testing or for a different refresh cadence.
 */
public class LicenseValidationCache {

    private final Duration ttl;
    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    private record Entry(ValidationResult result, Instant checkedAt) {}

    public LicenseValidationCache() {
        this(Duration.ofHours(24));
    }

    public LicenseValidationCache(Duration ttl) {
        this.ttl = ttl;
    }

    /**
     * Returns the cached result for this key, or null if there is no entry
     * or the entry is older than the cache window.
     */
    public ValidationResult get(String licenseKey) {
        Entry entry = entries.get(licenseKey);
        if (entry == null || isStale(entry.checkedAt())) {
            return null;
        }
        return entry.result();
    }

    /**
     * Stores a freshly-checked result, timestamped at the moment of the call.
     */
    public void put(String licenseKey, ValidationResult result) {
        entries.put(licenseKey, new Entry(result, Instant.now()));
    }

    /**
     * Removes any cached result for this key, forcing the next
     * validateLicense() call for it to perform a fresh check.
     */
    public void invalidate(String licenseKey) {
        entries.remove(licenseKey);
    }

    private boolean isStale(Instant checkedAt) {
        return Duration.between(checkedAt, Instant.now()).compareTo(ttl) >= 0;
    }
}
