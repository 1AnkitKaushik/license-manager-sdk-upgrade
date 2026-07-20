# LicenseVault Python SDK

Python client library for license validation in Flask, FastAPI, and Python applications.

## Features

- Online license validation via REST API
- Offline validation using signed license files
- Grace period handling with limited access
- Flask and FastAPI integration support
- Type hints with dataclasses

## Requirements

- Python 3.8+
- requests library

## Installation

```bash
pip install requests
```

Copy `licensevault_sdk.py` to your project.

## Quick Start

```python
from licensevault_sdk import LicenseVaultClient, AccessLevel

# Initialize
client = LicenseVaultClient(
    server_url="https://your-server.com/api",
    signing_secret="your-signing-secret",
    license_file_path="license.lic"  # Optional
)

# Validate (online first, fallback to offline)
result = client.validate_license("XXXX-XXXX-XXXX-XXXX")

if result.valid:
    print(f"Access Level: {result.access_level.value}")
    
    if result.can_write():
        # Allow write operations
        pass
    
    if result.can_use_premium_features():
        # Enable premium features
        pass
    
    if result.in_grace_period:
        print(f"Grace period: {result.days_until_grace_end} days left")
```

## Environment Variables

```bash
export LICENSE_SERVER_URL=https://your-server.com/api
export LICENSE_SIGNING_SECRET=your-signing-secret
export LICENSE_KEY=XXXX-XXXX-XXXX-XXXX
export LICENSE_FILE_PATH=license.lic
```

## Access Levels

| Level | Read | Write | Premium | Description |
|-------|------|-------|---------|-------------|
| `FULL` | ✅ | ✅ | ✅ | Active license |
| `LIMITED` | ✅ | ❌ | ❌ | Grace period |
| `NONE` | ❌ | ❌ | ❌ | Invalid/Expired |

## API Reference

### LicenseVaultClient

#### Constructor
```python
LicenseVaultClient(
    server_url: str,
    signing_secret: str,
    license_file_path: Optional[str] = None
)
```

#### Methods

| Method | Description |
|--------|-------------|
| `set_license_file_path(path)` | Set offline license file path |
| `set_license_file_content(content)` | Set license content directly |
| `validate_license(key, machine_id=None)` | Validate (online → offline) |
| `validate_online(key, machine_id=None)` | Force online validation |
| `validate_offline()` | Force offline validation |
| `validate_license_file(content)` | Validate license file |
| `download_license_file(key, token)` | Download license file |
| `save_license_file(key, token, path)` | Download and save |

### ValidationResult

```python
@dataclass
class ValidationResult:
    valid: bool
    message: str
    access_level: AccessLevel
    license_key: Optional[str] = None
    status: Optional[str] = None
    subscription_type: Optional[str] = None
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
    
    def can_read(self) -> bool: ...
    def can_write(self) -> bool: ...
    def can_use_premium_features(self) -> bool: ...
```

## Flask Integration

```python
from flask import Flask, jsonify
from functools import wraps
from licensevault_sdk import LicenseVaultClient, AccessLevel
import os

app = Flask(__name__)

# Initialize client
client = LicenseVaultClient(
    server_url=os.environ["LICENSE_SERVER_URL"],
    signing_secret=os.environ["LICENSE_SIGNING_SECRET"]
)

# Global status
license_status = client.validate_license(os.environ["LICENSE_KEY"])

# Decorators
def require_read(f):
    @wraps(f)
    def decorated(*args, **kwargs):
        if not license_status.can_read():
            return jsonify({"error": "License required"}), 403
        return f(*args, **kwargs)
    return decorated

def require_write(f):
    @wraps(f)
    def decorated(*args, **kwargs):
        if not license_status.can_write():
            return jsonify({"error": "Write disabled"}), 403
        return f(*args, **kwargs)
    return decorated

def require_premium(f):
    @wraps(f)
    def decorated(*args, **kwargs):
        if not license_status.can_use_premium_features():
            return jsonify({"error": "Premium required"}), 403
        return f(*args, **kwargs)
    return decorated

# Routes
@app.route('/api/data')
@require_read
def get_data():
    return jsonify({"data": "..."})

@app.route('/api/data', methods=['POST'])
@require_write
def save_data():
    return jsonify({"saved": True})

@app.route('/api/premium')
@require_premium
def premium_feature():
    return jsonify({"premium": True})
```

## FastAPI Integration

```python
from fastapi import FastAPI, Depends, HTTPException
from licensevault_sdk import LicenseVaultClient, ValidationResult
import os

app = FastAPI()

client = LicenseVaultClient(
    server_url=os.environ["LICENSE_SERVER_URL"],
    signing_secret=os.environ["LICENSE_SIGNING_SECRET"]
)

license_status: ValidationResult = None

@app.on_event("startup")
async def startup():
    global license_status
    license_status = client.validate_license(os.environ["LICENSE_KEY"])

# Dependencies
async def require_read():
    if not license_status.can_read():
        raise HTTPException(403, "License required")

async def require_write():
    if not license_status.can_write():
        raise HTTPException(403, "Write disabled")

async def require_premium():
    if not license_status.can_use_premium_features():
        raise HTTPException(403, "Premium required")

# Routes
@app.get("/api/data")
async def get_data(_=Depends(require_read)):
    return {"data": "..."}

@app.post("/api/data")
async def save_data(_=Depends(require_write)):
    return {"saved": True}

@app.get("/api/premium")
async def premium(_=Depends(require_premium)):
    return {"premium": True}
```

## Testing

```bash
# Install pytest
pip install pytest

# Run tests
pytest test_licensevault_sdk.py -v
```

### Test Example

```python
import pytest
from licensevault_sdk import LicenseVaultClient, AccessLevel

@pytest.fixture
def client():
    return LicenseVaultClient(
        server_url="https://test-server.com/api",
        signing_secret="test-secret"
    )

def test_validate_license(client):
    result = client.validate_license("TEST-KEY")
    assert result.valid is True
    assert result.access_level == AccessLevel.FULL

def test_grace_period_access(client):
    # With grace period license
    result = client.validate_license("GRACE-KEY")
    assert result.valid is True
    assert result.access_level == AccessLevel.LIMITED
    assert result.can_read() is True
    assert result.can_write() is False

def test_expired_license(client):
    result = client.validate_license("EXPIRED-KEY")
    assert result.valid is False
    assert result.access_level == AccessLevel.NONE
```

## Convenience Function

```python
from licensevault_sdk import validate_license

# Quick one-liner validation
result = validate_license(
    license_key="XXXX-XXXX-XXXX-XXXX",
    server_url="https://your-server.com/api",
    signing_secret="your-secret"
)
```

## Files

```
/sdks/python/
├── licensevault_sdk.py        # Main SDK
├── examples/
│   └── flask_example.py       # Flask/FastAPI example
└── README.md                  # This file
```

## License

MIT License
