"""
Example Flask/FastAPI application using LicenseVault SDK

This shows how to integrate license validation in a Python web application with:
- Startup validation
- Middleware for request-level checks  
- Decorators for protecting endpoints
- Grace period handling
"""

from functools import wraps
from typing import Callable

from flask import Flask, jsonify, request, g
# Or for FastAPI:
# from fastapi import FastAPI, Depends, HTTPException, Request

from licensevault_sdk import LicenseVaultClient, ValidationResult, AccessLevel


# ============================================
# Configuration
# ============================================

LICENSE_SERVER_URL = "https://your-server.com/api"
LICENSE_SIGNING_SECRET = "your-signing-secret"
LICENSE_KEY = "XXXX-XXXX-XXXX-XXXX"
LICENSE_FILE_PATH = "license.lic"


# ============================================
# License Manager Singleton
# ============================================

class LicenseManager:
    """
    Singleton license manager for application-wide license validation
    """
    _instance = None
    _client: LicenseVaultClient = None
    _current_status: ValidationResult = None

    def __new__(cls):
        if cls._instance is None:
            cls._instance = super().__new__(cls)
            cls._client = LicenseVaultClient(
                server_url=LICENSE_SERVER_URL,
                signing_secret=LICENSE_SIGNING_SECRET,
                license_file_path=LICENSE_FILE_PATH
            )
        return cls._instance

    def validate_on_startup(self) -> ValidationResult:
        """Validate license on application startup"""
        print("=" * 50)
        print("LicenseVault: Validating License")
        print("=" * 50)

        self._current_status = self._client.validate_license(LICENSE_KEY)

        print(f"Valid: {self._current_status.valid}")
        print(f"Access Level: {self._current_status.access_level.value}")
        print(f"Message: {self._current_status.message}")

        if self._current_status.warning_message:
            print(f"WARNING: {self._current_status.warning_message}")

        if not self._current_status.valid:
            print("❌ LICENSE INVALID - Application may have limited or no functionality")
        elif self._current_status.access_level == AccessLevel.LIMITED:
            print("⚠️ GRACE PERIOD - Running with limited access (read-only, core features only)")
            print(f"   Days remaining: {self._current_status.days_until_grace_end}")

        print("=" * 50)
        return self._current_status

    def get_status(self) -> ValidationResult:
        """Get current license status"""
        if self._current_status is None:
            self.validate_on_startup()
        return self._current_status

    def refresh(self) -> ValidationResult:
        """Refresh license validation"""
        self._current_status = self._client.validate_license(LICENSE_KEY)
        return self._current_status

    def can_read(self) -> bool:
        return self.get_status().can_read()

    def can_write(self) -> bool:
        return self.get_status().can_write()

    def can_use_premium(self) -> bool:
        return self.get_status().can_use_premium_features()


# Global license manager
license_manager = LicenseManager()


# ============================================
# Flask Application Example
# ============================================

app = Flask(__name__)


# Validate license on startup
@app.before_first_request
def startup_validation():
    license_manager.validate_on_startup()


# Middleware to attach license status to request
@app.before_request
def attach_license_status():
    g.license_status = license_manager.get_status()


# Decorators for protecting endpoints
def require_read_access(f: Callable) -> Callable:
    """Decorator to require read access"""
    @wraps(f)
    def decorated_function(*args, **kwargs):
        if not license_manager.can_read():
            return jsonify({
                "error": "LICENSE_ERROR",
                "message": "License invalid - no read access"
            }), 403
        return f(*args, **kwargs)
    return decorated_function


def require_write_access(f: Callable) -> Callable:
    """Decorator to require write access (not available in grace period)"""
    @wraps(f)
    def decorated_function(*args, **kwargs):
        if not license_manager.can_write():
            status = license_manager.get_status()
            message = "Write operations disabled"
            if status.in_grace_period:
                message = f"Write operations disabled during grace period. {status.days_until_grace_end} days remaining."
            return jsonify({
                "error": "LICENSE_ERROR",
                "message": message
            }), 403
        return f(*args, **kwargs)
    return decorated_function


def require_premium_access(f: Callable) -> Callable:
    """Decorator to require premium features (not available in grace period)"""
    @wraps(f)
    def decorated_function(*args, **kwargs):
        if not license_manager.can_use_premium():
            return jsonify({
                "error": "LICENSE_ERROR",
                "message": "Premium features require an active license"
            }), 403
        return f(*args, **kwargs)
    return decorated_function


# ============================================
# Example Endpoints
# ============================================

@app.route('/api/data', methods=['GET'])
@require_read_access
def get_data():
    """Read endpoint - available in FULL and LIMITED (grace period) modes"""
    return jsonify({
        "data": "Your application data here",
        "license_status": g.license_status.access_level.value
    })


@app.route('/api/data', methods=['POST'])
@require_write_access
def create_data():
    """Write endpoint - only available in FULL access mode"""
    data = request.get_json()
    return jsonify({"saved": True, "data": data})


@app.route('/api/premium/analytics', methods=['GET'])
@require_premium_access
def premium_analytics():
    """Premium endpoint - only available in FULL access mode"""
    return jsonify({
        "premium": True,
        "analytics": {
            "users": 1250,
            "revenue": 45000,
            "growth": 15.5
        }
    })


@app.route('/api/license/status', methods=['GET'])
def license_status():
    """Get current license status"""
    status = license_manager.get_status()
    return jsonify({
        "valid": status.valid,
        "access_level": status.access_level.value,
        "message": status.message,
        "can_read": status.can_read(),
        "can_write": status.can_write(),
        "can_use_premium": status.can_use_premium_features(),
        "in_grace_period": status.in_grace_period,
        "days_until_expiry": status.days_until_expiry,
        "days_until_grace_end": status.days_until_grace_end,
        "warning": status.warning_message
    })


@app.route('/api/license/refresh', methods=['POST'])
def refresh_license():
    """Refresh license validation"""
    status = license_manager.refresh()
    return jsonify({
        "valid": status.valid,
        "access_level": status.access_level.value,
        "message": status.message
    })


# ============================================
# FastAPI Alternative Example
# ============================================

"""
from fastapi import FastAPI, Depends, HTTPException, Request
from fastapi.responses import JSONResponse

app = FastAPI()

@app.on_event("startup")
async def startup_event():
    license_manager.validate_on_startup()

async def get_license_status(request: Request):
    return license_manager.get_status()

async def require_read(status: ValidationResult = Depends(get_license_status)):
    if not status.can_read():
        raise HTTPException(status_code=403, detail="License invalid - no read access")
    return status

async def require_write(status: ValidationResult = Depends(get_license_status)):
    if not status.can_write():
        detail = "Write operations disabled"
        if status.in_grace_period:
            detail = f"Write disabled during grace period. {status.days_until_grace_end} days remaining."
        raise HTTPException(status_code=403, detail=detail)
    return status

async def require_premium(status: ValidationResult = Depends(get_license_status)):
    if not status.can_use_premium_features():
        raise HTTPException(status_code=403, detail="Premium features require active license")
    return status

@app.get("/api/data")
async def get_data(status: ValidationResult = Depends(require_read)):
    return {"data": "Your data", "license_status": status.access_level.value}

@app.post("/api/data")
async def create_data(data: dict, status: ValidationResult = Depends(require_write)):
    return {"saved": True}

@app.get("/api/premium/analytics")
async def premium_analytics(status: ValidationResult = Depends(require_premium)):
    return {"premium": True, "analytics": {...}}
"""


if __name__ == '__main__':
    app.run(debug=True, port=5000)
