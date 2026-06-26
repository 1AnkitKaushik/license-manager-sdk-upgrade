package com.licensevault.sdk;

/**
 * Thrown when the SDK is asked to check a license but has no way to do so --
 * no license key was supplied for an online check, and no offline license
 * file path was configured either.
 *
 * This is deliberately distinct from a license simply being invalid,
 * expired, suspended, or revoked -- those are normal business states and
 * are reported through ValidationResult, not an exception. This exception
 * signals a setup mistake in the consuming application (e.g. forgetting to
 * set licensevault.license-key or call setLicenseFilePath()), so it's
 * something a developer should fix, not something an end user's license
 * state should ever trigger.
 */
public class LicenseConfigurationException extends RuntimeException {
    public LicenseConfigurationException(String message) {
        super(message);
    }
}
