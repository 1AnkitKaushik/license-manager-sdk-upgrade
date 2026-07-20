"""
LicenseVault SDK for Python applications

Supports both online and offline license validation with grace period handling.

Installation:
    pip install requests

Usage:
    from licensevault_sdk import LicenseVaultClient, AccessLevel

    client = LicenseVaultClient(
        server_url="https://your-server.com/api",
        signing_secret="your-signing-secret"
    )

    result = client.validate_license("XXXX-XXXX-XXXX-XXXX")

    if result.valid:
        if result.access_level == AccessLevel.FULL:
            # All features enabled
            pass
        elif result.access_level == AccessLevel.LIMITED:
            # Read-only, core features only (grace period)
            pass
        else:
            # No access
            pass
"""

import base64
import hashlib
import hmac
import json
import os
from dataclasses import dataclass, field
from datetime import datetime
from enum import Enum
from typing import Any, Dict, Optional

import requests


class AccessLevel(Enum):
    """License access levels"""
    FULL = "FULL"       # All features enabled
    LIMITED = "LIMITED" # Read-only, core features only (during grace period)
    NONE = "NONE"       # No access


@dataclass
class ValidationResult:
    """Result of license validation"""
    valid: bool
    message: str
    access_level: AccessLevel
    license_key: Optional[str] = None
    status: Optional[str] = None
    subscription_type: Optional[str] = None
    start_date: Optional[str] = None
    expiry_date: Optional[str] = None
    grace_end_date: Optional[str] = None
    grace_period_days: Optional[int] = None
    days_until_expiry: Optional[int] = None
    days_until_grace_end: Optional[int] = None
    expiring_soon: bool = False
    in_grace_period: bool = False
    warning_message: Optional[str] = None
    premium_features_enabled: bool = False
    write_access_enabled: bool = False
    raw_data: Dict[str, Any] = field(default_factory=dict)

    def can_read(self) -> bool:
        """Check if read operations are allowed"""
        return self.valid and self.access_level in (AccessLevel.FULL, AccessLevel.LIMITED)

    def can_write(self) -> bool:
        """Check if write operations are allowed"""
        return self.valid and self.access_level == AccessLevel.FULL and self.write_access_enabled

    def can_use_premium_features(self) -> bool:
        """Check if premium features are allowed"""
        return self.valid and self.access_level == AccessLevel.FULL and self.premium_features_enabled


class LicenseVaultClient:
    """
    LicenseVault client for license validation
    
    Supports:
    - Online validation via license server
    - Offline validation using signed license files
    - Grace period handling with limited access
    """

    def __init__(self, server_url: str, signing_secret: str, license_file_path: Optional[str] = None):
        """
        Initialize the LicenseVault client

        Args:
            server_url: Base URL of the license server API (e.g., "https://server.com/api")
            signing_secret: Secret key for offline license file verification
            license_file_path: Optional path to offline license file
        """
        self.server_url = server_url.rstrip('/')
        self.signing_secret = signing_secret
        self.license_file_path = license_file_path
        self._license_file_content: Optional[str] = None

    def set_license_file_path(self, path: str) -> None:
        """Set the path for offline license file"""
        self.license_file_path = path

    def set_license_file_content(self, content: str) -> None:
        """Set license file content directly"""
        self._license_file_content = content

    def validate_license(self, license_key: str, machine_id: Optional[str] = None) -> ValidationResult:
        """
        Validate license - tries online first, falls back to offline

        Args:
            license_key: The license key to validate
            machine_id: Optional machine identifier

        Returns:
            ValidationResult with license status and access level
        """
        try:
            return self.validate_online(license_key, machine_id)
        except Exception as e:
            print(f"Online validation failed, trying offline: {e}")
            return self.validate_offline()

    def validate_online(self, license_key: str, machine_id: Optional[str] = None) -> ValidationResult:
        """
        Force online validation via license server

        Args:
            license_key: The license key to validate
            machine_id: Optional machine identifier

        Returns:
            ValidationResult from server

        Raises:
            requests.RequestException: If server communication fails
        """
        response = requests.post(
            f"{self.server_url}/license/validate",
            json={
                "licenseKey": license_key,
                "machineId": machine_id or ""
            },
            timeout=10
        )
        response.raise_for_status()
        data = response.json()
        return self._parse_validation_response(data)

    def validate_offline(self) -> ValidationResult:
        """
        Validate using offline license file

        Returns:
            ValidationResult based on local license file
        """
        content = self._license_file_content

        if not content and self.license_file_path:
            try:
                with open(self.license_file_path, 'r') as f:
                    content = f.read()
            except FileNotFoundError:
                return ValidationResult(
                    valid=False,
                    message=f"License file not found: {self.license_file_path}",
                    access_level=AccessLevel.NONE
                )
            except IOError as e:
                return ValidationResult(
                    valid=False,
                    message=f"Failed to read license file: {e}",
                    access_level=AccessLevel.NONE
                )

        if not content:
            return ValidationResult(
                valid=False,
                message="No license file configured",
                access_level=AccessLevel.NONE
            )

        return self.validate_license_file(content)

    def validate_license_file(self, license_file_content: str) -> ValidationResult:
        """
        Validate license file content directly

        Args:
            license_file_content: The signed license file content

        Returns:
            ValidationResult based on license file
        """
        try:
            parts = license_file_content.strip().split('.')
            if len(parts) != 2:
                return ValidationResult(
                    valid=False,
                    message="Invalid license file format",
                    access_level=AccessLevel.NONE
                )

            encoded_data, provided_signature = parts

            # Verify signature
            expected_signature = self._generate_signature(encoded_data)
            if expected_signature != provided_signature:
                return ValidationResult(
                    valid=False,
                    message="Invalid license signature - file may be tampered",
                    access_level=AccessLevel.NONE
                )

            # Decode data
            json_data = base64.b64decode(encoded_data).decode('utf-8')
            license_data = json.loads(json_data)

            # Parse dates
            expiry_date = datetime.fromisoformat(license_data['expiryDate'])
            grace_end_date = datetime.fromisoformat(license_data['graceEndDate'])
            status = license_data.get('status', '')

            # Check status
            if status in ('REVOKED', 'SUSPENDED'):
                return ValidationResult(
                    valid=False,
                    message=f"License is {status.lower()}",
                    access_level=AccessLevel.NONE,
                    raw_data=license_data
                )

            now = datetime.now()

            if now < expiry_date:
                # Active license
                return ValidationResult(
                    valid=True,
                    message="License is valid",
                    access_level=AccessLevel.FULL,
                    premium_features_enabled=license_data.get('premiumFeaturesEnabled', False),
                    write_access_enabled=license_data.get('writeAccessEnabled', False),
                    raw_data=license_data
                )
            elif now < grace_end_date:
                # Grace period
                days_left = (grace_end_date - now).days
                return ValidationResult(
                    valid=True,
                    message=f"License expired - Grace period active ({days_left} days remaining). Limited access only.",
                    access_level=AccessLevel.LIMITED,
                    in_grace_period=True,
                    days_until_grace_end=days_left,
                    premium_features_enabled=False,
                    write_access_enabled=False,
                    raw_data=license_data
                )
            else:
                # Fully expired
                return ValidationResult(
                    valid=False,
                    message="License and grace period have expired",
                    access_level=AccessLevel.NONE,
                    raw_data=license_data
                )

        except Exception as e:
            return ValidationResult(
                valid=False,
                message=f"Failed to validate license file: {e}",
                access_level=AccessLevel.NONE
            )

    def download_license_file(self, license_key: str, auth_token: str) -> str:
        """
        Download license file for offline use

        Args:
            license_key: The license key
            auth_token: JWT authentication token

        Returns:
            License file content string

        Raises:
            requests.RequestException: If download fails
        """
        response = requests.get(
            f"{self.server_url}/license/{license_key}/download",
            headers={"Authorization": f"Bearer {auth_token}"},
            timeout=10
        )
        response.raise_for_status()
        data = response.json()
        return data['licenseFile']

    def save_license_file(self, license_key: str, auth_token: str, file_path: str) -> None:
        """
        Download and save license file to disk

        Args:
            license_key: The license key
            auth_token: JWT authentication token
            file_path: Path to save the license file
        """
        license_file = self.download_license_file(license_key, auth_token)
        with open(file_path, 'w') as f:
            f.write(license_file)
        self.license_file_path = file_path

    def _generate_signature(self, data: str) -> str:
        """Generate HMAC-SHA256 signature"""
        signature = hmac.new(
            self.signing_secret.encode('utf-8'),
            data.encode('utf-8'),
            hashlib.sha256
        ).digest()
        # URL-safe base64 encoding without padding
        return base64.urlsafe_b64encode(signature).rstrip(b'=').decode('utf-8')

    def _parse_validation_response(self, data: Dict[str, Any]) -> ValidationResult:
        """Parse server validation response"""
        access_level_str = data.get('accessLevel', 'NONE')
        try:
            access_level = AccessLevel(access_level_str)
        except ValueError:
            access_level = AccessLevel.NONE

        return ValidationResult(
            valid=data.get('valid', False),
            message=data.get('message', ''),
            access_level=access_level,
            license_key=data.get('licenseKey'),
            status=data.get('status'),
            subscription_type=data.get('subscriptionType'),
            start_date=data.get('startDate'),
            expiry_date=data.get('expiryDate'),
            grace_end_date=data.get('graceEndDate'),
            grace_period_days=data.get('gracePeriodDays'),
            days_until_expiry=data.get('daysUntilExpiry'),
            days_until_grace_end=data.get('daysUntilGraceEnd'),
            expiring_soon=data.get('expiringSoon', False),
            in_grace_period=data.get('inGracePeriod', False),
            warning_message=data.get('warningMessage'),
            premium_features_enabled=data.get('premiumFeaturesEnabled', False),
            write_access_enabled=data.get('writeAccessEnabled', False),
            raw_data=data
        )


# Convenience function for quick validation
def validate_license(
    license_key: str,
    server_url: str,
    signing_secret: str,
    license_file_path: Optional[str] = None
) -> ValidationResult:
    """
    Quick license validation

    Args:
        license_key: The license key to validate
        server_url: License server URL
        signing_secret: Signing secret for offline validation
        license_file_path: Optional offline license file path

    Returns:
        ValidationResult
    """
    client = LicenseVaultClient(server_url, signing_secret, license_file_path)
    return client.validate_license(license_key)


if __name__ == "__main__":
    # Example usage
    client = LicenseVaultClient(
        server_url="https://your-server.com/api",
        signing_secret="your-signing-secret",
        license_file_path="license.lic"
    )

    result = client.validate_license("XXXX-XXXX-XXXX-XXXX")

    print(f"Valid: {result.valid}")
    print(f"Access Level: {result.access_level.value}")
    print(f"Message: {result.message}")
    print(f"Can Read: {result.can_read()}")
    print(f"Can Write: {result.can_write()}")
    print(f"Can Use Premium: {result.can_use_premium_features()}")

    if result.in_grace_period:
        print(f"⚠️ Grace Period: {result.days_until_grace_end} days remaining")

    if result.warning_message:
        print(f"Warning: {result.warning_message}")
