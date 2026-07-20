"""
Unit tests for LicenseVault Python SDK

Run with: pytest test_licensevault_sdk.py -v
"""

import pytest
import base64
import hashlib
import hmac
import json
from datetime import datetime, timedelta
from unittest.mock import patch, MagicMock

from licensevault_sdk import (
    LicenseVaultClient,
    ValidationResult,
    AccessLevel,
    validate_license,
)

SIGNING_SECRET = "LicenseSigningSecretKey2024ForOfflineValidation"


@pytest.fixture
def client():
    """Create a test client instance"""
    return LicenseVaultClient(
        server_url="https://test-server.com/api",
        signing_secret=SIGNING_SECRET,
    )


# ==================== Helper Functions ====================

def create_test_license_file(
    expiry_date: datetime,
    grace_end_date: datetime,
    status: str = "ACTIVE"
) -> str:
    """Create a signed test license file"""
    data = {
        "licenseKey": "TEST-1234-5678-ABCD",
        "username": "testuser",
        "email": "test@example.com",
        "subscriptionType": "YEARLY",
        "startDate": (datetime.now() - timedelta(days=30)).isoformat(),
        "expiryDate": expiry_date.isoformat(),
        "graceEndDate": grace_end_date.isoformat(),
        "gracePeriodDays": 7,
        "status": status,
        "premiumFeaturesEnabled": True,
        "writeAccessEnabled": True,
        "maxActivations": 1,
        "generatedAt": datetime.now().isoformat(),
    }
    
    json_data = json.dumps(data)
    encoded_data = base64.b64encode(json_data.encode()).decode()
    signature = generate_signature(encoded_data)
    
    return f"{encoded_data}.{signature}"


def generate_signature(data: str) -> str:
    """Generate HMAC-SHA256 signature"""
    signature = hmac.new(
        SIGNING_SECRET.encode(),
        data.encode(),
        hashlib.sha256
    ).digest()
    return base64.urlsafe_b64encode(signature).rstrip(b'=').decode()


# ==================== Offline Validation Tests ====================

class TestOfflineValidation:
    """Tests for offline license file validation"""
    
    def test_validate_active_license(self, client):
        """Should validate properly formatted active license file"""
        license_file = create_test_license_file(
            expiry_date=datetime.now() + timedelta(days=30),
            grace_end_date=datetime.now() + timedelta(days=37),
            status="ACTIVE"
        )
        
        result = client.validate_license_file(license_file)
        
        assert result.valid is True
        assert result.access_level == AccessLevel.FULL
        assert result.can_read() is True
        assert result.can_write() is True
        assert result.can_use_premium_features() is True
        assert result.in_grace_period is False
    
    def test_grace_period_license(self, client):
        """Should return LIMITED access for license in grace period"""
        license_file = create_test_license_file(
            expiry_date=datetime.now() - timedelta(days=3),  # Expired 3 days ago
            grace_end_date=datetime.now() + timedelta(days=4),  # Grace ends in 4 days
            status="GRACE_PERIOD"
        )
        
        result = client.validate_license_file(license_file)
        
        assert result.valid is True
        assert result.access_level == AccessLevel.LIMITED
        assert result.can_read() is True
        assert result.can_write() is False
        assert result.can_use_premium_features() is False
        assert result.in_grace_period is True
    
    def test_expired_license(self, client):
        """Should return NONE access for fully expired license"""
        license_file = create_test_license_file(
            expiry_date=datetime.now() - timedelta(days=30),  # Expired 30 days ago
            grace_end_date=datetime.now() - timedelta(days=23),  # Grace ended 23 days ago
            status="EXPIRED"
        )
        
        result = client.validate_license_file(license_file)
        
        assert result.valid is False
        assert result.access_level == AccessLevel.NONE
        assert result.can_read() is False
        assert result.can_write() is False
        assert result.can_use_premium_features() is False
    
    def test_invalid_signature(self, client):
        """Should reject license file with invalid signature"""
        valid_data = base64.b64encode(json.dumps({
            "licenseKey": "TEST-1234-5678-ABCD",
            "status": "ACTIVE"
        }).encode()).decode()
        invalid_file = f"{valid_data}.invalid-signature"
        
        result = client.validate_license_file(invalid_file)
        
        assert result.valid is False
        assert "signature" in result.message.lower()
        assert result.access_level == AccessLevel.NONE
    
    def test_invalid_format(self, client):
        """Should reject license file with invalid format"""
        result = client.validate_license_file("no-dot-separator")
        
        assert result.valid is False
        assert "format" in result.message.lower()
    
    def test_suspended_license(self, client):
        """Should reject suspended license"""
        license_file = create_test_license_file(
            expiry_date=datetime.now() + timedelta(days=30),
            grace_end_date=datetime.now() + timedelta(days=37),
            status="SUSPENDED"
        )
        
        result = client.validate_license_file(license_file)
        
        assert result.valid is False
        assert "suspended" in result.message.lower()
        assert result.access_level == AccessLevel.NONE
    
    def test_revoked_license(self, client):
        """Should reject revoked license"""
        license_file = create_test_license_file(
            expiry_date=datetime.now() + timedelta(days=30),
            grace_end_date=datetime.now() + timedelta(days=37),
            status="REVOKED"
        )
        
        result = client.validate_license_file(license_file)
        
        assert result.valid is False
        assert "revoked" in result.message.lower()


# ==================== Access Level Tests ====================

class TestAccessLevels:
    """Tests for access level checking methods"""
    
    def test_full_access_can_read(self):
        """FULL access should allow read"""
        result = ValidationResult(
            valid=True,
            message="OK",
            access_level=AccessLevel.FULL
        )
        assert result.can_read() is True
    
    def test_limited_access_can_read(self):
        """LIMITED access should allow read"""
        result = ValidationResult(
            valid=True,
            message="OK",
            access_level=AccessLevel.LIMITED
        )
        assert result.can_read() is True
    
    def test_none_access_cannot_read(self):
        """NONE access should not allow read"""
        result = ValidationResult(
            valid=False,
            message="Expired",
            access_level=AccessLevel.NONE
        )
        assert result.can_read() is False
    
    def test_full_access_can_write(self):
        """FULL access with write enabled should allow write"""
        result = ValidationResult(
            valid=True,
            message="OK",
            access_level=AccessLevel.FULL,
            write_access_enabled=True
        )
        assert result.can_write() is True
    
    def test_limited_access_cannot_write(self):
        """LIMITED access should not allow write"""
        result = ValidationResult(
            valid=True,
            message="OK",
            access_level=AccessLevel.LIMITED,
            write_access_enabled=True  # Even if flag is True
        )
        assert result.can_write() is False
    
    def test_full_access_can_use_premium(self):
        """FULL access with premium enabled should allow premium"""
        result = ValidationResult(
            valid=True,
            message="OK",
            access_level=AccessLevel.FULL,
            premium_features_enabled=True
        )
        assert result.can_use_premium_features() is True
    
    def test_limited_access_cannot_use_premium(self):
        """LIMITED access should not allow premium"""
        result = ValidationResult(
            valid=True,
            message="OK",
            access_level=AccessLevel.LIMITED,
            premium_features_enabled=True  # Even if flag is True
        )
        assert result.can_use_premium_features() is False


# ==================== Online Validation Tests (Mocked) ====================

class TestOnlineValidation:
    """Tests for online validation (with mocked HTTP)"""
    
    @patch('requests.post')
    def test_online_validation_success(self, mock_post, client):
        """Should call server API for online validation"""
        mock_response = MagicMock()
        mock_response.status_code = 200
        mock_response.json.return_value = {
            "valid": True,
            "message": "License is valid",
            "accessLevel": "FULL",
            "premiumFeaturesEnabled": True,
            "writeAccessEnabled": True,
        }
        mock_post.return_value = mock_response
        
        result = client.validate_online("TEST-1234-5678-ABCD")
        
        mock_post.assert_called_once()
        assert result.valid is True
        assert result.access_level == AccessLevel.FULL
    
    @patch('requests.post')
    def test_fallback_to_offline(self, mock_post, client):
        """Should fall back to offline when online fails"""
        mock_post.side_effect = Exception("Network error")
        
        license_file = create_test_license_file(
            expiry_date=datetime.now() + timedelta(days=30),
            grace_end_date=datetime.now() + timedelta(days=37),
            status="ACTIVE"
        )
        client.set_license_file_content(license_file)
        
        result = client.validate_license("TEST-1234-5678-ABCD")
        
        assert result.valid is True
        assert result.access_level == AccessLevel.FULL


# ==================== Convenience Function Tests ====================

class TestConvenienceFunction:
    """Tests for the validate_license convenience function"""
    
    @patch('requests.post')
    def test_quick_validation(self, mock_post):
        """Should validate using convenience function"""
        mock_response = MagicMock()
        mock_response.status_code = 200
        mock_response.json.return_value = {
            "valid": True,
            "message": "OK",
            "accessLevel": "FULL",
        }
        mock_post.return_value = mock_response
        
        result = validate_license(
            license_key="TEST-1234-5678-ABCD",
            server_url="https://test.com/api",
            signing_secret="secret"
        )
        
        assert result.valid is True


# ==================== File Operations Tests ====================

class TestFileOperations:
    """Tests for license file operations"""
    
    def test_set_license_file_path(self, client, tmp_path):
        """Should set and read license file from path"""
        license_file = create_test_license_file(
            expiry_date=datetime.now() + timedelta(days=30),
            grace_end_date=datetime.now() + timedelta(days=37),
            status="ACTIVE"
        )
        
        # Write to temp file
        file_path = tmp_path / "license.lic"
        file_path.write_text(license_file)
        
        client.set_license_file_path(str(file_path))
        result = client.validate_offline()
        
        assert result.valid is True
    
    def test_missing_license_file(self, client):
        """Should handle missing license file"""
        client.set_license_file_path("/nonexistent/path/license.lic")
        result = client.validate_offline()
        
        assert result.valid is False
        assert "not found" in result.message.lower()
    
    def test_set_license_file_content(self, client):
        """Should accept license content directly"""
        license_file = create_test_license_file(
            expiry_date=datetime.now() + timedelta(days=30),
            grace_end_date=datetime.now() + timedelta(days=37),
            status="ACTIVE"
        )
        
        client.set_license_file_content(license_file)
        result = client.validate_offline()
        
        assert result.valid is True


# ==================== Run Tests ====================

if __name__ == "__main__":
    pytest.main([__file__, "-v", "--tb=short"])
