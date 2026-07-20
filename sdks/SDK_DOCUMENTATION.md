# LicenseVault SDK - Complete Documentation

## Table of Contents

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Java SDK](#java-sdk)
4. [Next.js/TypeScript SDK](#nextjstypescript-sdk)
5. [Python SDK](#python-sdk)
6. [Testing Guide](#testing-guide)
7. [Troubleshooting](#troubleshooting)

---

## Overview

LicenseVault SDKs provide seamless integration of license validation into your applications. All SDKs support:

- **Online Validation**: Real-time license checks via the license server
- **Offline Validation**: Signed license file validation without internet
- **Grace Period**: Configurable limited access period after license expiry
- **Access Control**: Feature flags for read/write/premium access

### Access Levels

| Level | When | Read Access | Write Access | Premium Features |
|-------|------|-------------|--------------|------------------|
| `FULL` | Active license | ✅ Yes | ✅ Yes | ✅ Yes |
| `LIMITED` | Grace period (expired but within grace) | ✅ Yes | ❌ No | ❌ No |
| `NONE` | Invalid, revoked, or fully expired | ❌ No | ❌ No | ❌ No |

### License Lifecycle

```
ACTIVE → [Expiry] → GRACE_PERIOD → [Grace End] → EXPIRED
   │                     │                           │
   │                     │                           └── Access: NONE
   │                     └── Access: LIMITED (read-only)
   └── Access: FULL
```

---

## Architecture

### License File Format

License files are Base64-encoded JSON signed with HMAC-SHA256:

```
base64(jsonData).hmacSignature
```

**JSON Structure:**
```json
{
  "licenseKey": "XXXX-XXXX-XXXX-XXXX",
  "username": "customer1",
  "email": "customer@example.com",
  "subscriptionType": "YEARLY",
  "startDate": "2026-03-17T00:00:00",
  "expiryDate": "2027-03-17T00:00:00",
  "graceEndDate": "2027-03-24T00:00:00",
  "gracePeriodDays": 7,
  "status": "ACTIVE",
  "premiumFeaturesEnabled": true,
  "writeAccessEnabled": true,
  "maxActivations": 1,
  "generatedAt": "2026-03-17T12:00:00"
}
```

### Validation Flow

```
┌─────────────────────────────────────────────────────────────┐
│                      Application Start                       │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    Try Online Validation                     │
│              POST /api/license/validate                      │
└─────────────────────────────────────────────────────────────┘
                              │
                    ┌─────────┴─────────┐
                    │                   │
               Success              Failure
                    │                   │
                    ▼                   ▼
            ┌───────────────┐   ┌───────────────────────┐
            │ Return Result │   │ Try Offline Validation │
            └───────────────┘   │ (Read license.lic)     │
                                └───────────────────────┘
                                          │
                                          ▼
                                ┌───────────────────────┐
                                │ Verify HMAC Signature │
                                └───────────────────────┘
                                          │
                                          ▼
                                ┌───────────────────────┐
                                │ Check Expiry & Grace  │
                                └───────────────────────┘
                                          │
                                          ▼
                                ┌───────────────────────┐
                                │ Return Access Level   │
                                └───────────────────────┘
```

---

## Java SDK

### Installation

**Maven (pom.xml):**
```xml
<dependencies>
    <!-- Required dependencies -->
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
        <version>2.15.3</version>
    </dependency>
    <dependency>
        <groupId>com.fasterxml.jackson.datatype</groupId>
        <artifactId>jackson-datatype-jsr310</artifactId>
        <version>2.15.3</version>
    </dependency>
</dependencies>
```

**Gradle (build.gradle):**
```groovy
dependencies {
    implementation 'com.fasterxml.jackson.core:jackson-databind:2.15.3'
    implementation 'com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.15.3'
}
```

### Configuration

Copy `LicenseVaultClient.java` from `/sdks/java/` to your project.

**application.properties:**
```properties
licensevault.server-url=https://your-license-server.com/api
licensevault.signing-secret=your-signing-secret-key
licensevault.license-key=XXXX-XXXX-XXXX-XXXX
licensevault.license-file=license.lic
```

### Basic Usage

```java
import com.licensevault.sdk.LicenseVaultClient;
import com.licensevault.sdk.LicenseVaultClient.ValidationResult;
import com.licensevault.sdk.LicenseVaultClient.AccessLevel;

public class MyApp {
    public static void main(String[] args) {
        // Initialize client
        LicenseVaultClient client = new LicenseVaultClient(
            "https://your-server.com/api",
            "your-signing-secret"
        );
        
        // Set offline license file path (optional)
        client.setLicenseFilePath("license.lic");
        
        // Validate license (tries online first, falls back to offline)
        ValidationResult result = client.validateLicense("XXXX-XXXX-XXXX-XXXX");
        
        // Check validation result
        if (result.isValid()) {
            System.out.println("License is valid!");
            System.out.println("Access Level: " + result.getAccessLevel());
            
            // Check specific permissions
            if (result.canWrite()) {
                System.out.println("Write operations allowed");
            }
            
            if (result.canUsePremiumFeatures()) {
                System.out.println("Premium features enabled");
            }
            
            // Handle grace period
            if (result.isInGracePeriod()) {
                System.out.println("WARNING: License in grace period!");
                System.out.println("Days remaining: " + result.getDaysUntilGraceEnd());
            }
        } else {
            System.err.println("License invalid: " + result.getMessage());
        }
    }
}
```

### Spring Boot Integration

```java
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import jakarta.annotation.PostConstruct;

@Component
public class LicenseManager {
    
    @Value("${licensevault.server-url}")
    private String serverUrl;
    
    @Value("${licensevault.signing-secret}")
    private String signingSecret;
    
    @Value("${licensevault.license-key}")
    private String licenseKey;
    
    @Value("${licensevault.license-file:license.lic}")
    private String licenseFilePath;
    
    private LicenseVaultClient client;
    private ValidationResult currentStatus;
    
    @PostConstruct
    public void init() {
        client = new LicenseVaultClient(serverUrl, signingSecret);
        client.setLicenseFilePath(licenseFilePath);
        validateOnStartup();
    }
    
    private void validateOnStartup() {
        currentStatus = client.validateLicense(licenseKey);
        
        if (!currentStatus.isValid()) {
            throw new RuntimeException("License validation failed: " + currentStatus.getMessage());
        }
        
        if (currentStatus.getAccessLevel() == AccessLevel.LIMITED) {
            System.out.println("WARNING: Running in grace period with limited access");
        }
    }
    
    public boolean canRead() {
        return currentStatus.canRead();
    }
    
    public boolean canWrite() {
        return currentStatus.canWrite();
    }
    
    public boolean canUsePremium() {
        return currentStatus.canUsePremiumFeatures();
    }
    
    public ValidationResult getStatus() {
        return currentStatus;
    }
    
    public void refresh() {
        currentStatus = client.validateLicense(licenseKey);
    }
}
```

### Protecting Endpoints

```java
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class MyController {
    
    private final LicenseManager licenseManager;
    
    public MyController(LicenseManager licenseManager) {
        this.licenseManager = licenseManager;
    }
    
    @GetMapping("/data")
    public Object getData() {
        if (!licenseManager.canRead()) {
            throw new LicenseException("License required for read access");
        }
        return Map.of("data", "Your data here");
    }
    
    @PostMapping("/data")
    public Object saveData(@RequestBody Object data) {
        if (!licenseManager.canWrite()) {
            throw new LicenseException("Write access disabled (grace period or expired)");
        }
        return Map.of("saved", true);
    }
    
    @GetMapping("/premium/analytics")
    public Object premiumAnalytics() {
        if (!licenseManager.canUsePremium()) {
            throw new LicenseException("Premium features require active license");
        }
        return Map.of("analytics", "Premium data");
    }
}
```

### Download License File

```java
// Requires authentication token
String authToken = "your-jwt-token";
String licenseFile = client.downloadLicenseFile("XXXX-XXXX-XXXX-XXXX", authToken);

// Or save directly to file
client.saveLicenseFile("XXXX-XXXX-XXXX-XXXX", authToken, "license.lic");
```

---

## Next.js/TypeScript SDK

### Installation

```bash
npm install crypto-js
# or
yarn add crypto-js

# TypeScript types
npm install -D @types/crypto-js
```

### Configuration

Copy `licensevault-sdk.ts` from `/sdks/nextjs/` to your project (e.g., `lib/licensevault-sdk.ts`).

**Environment Variables (.env.local):**
```env
NEXT_PUBLIC_LICENSE_KEY=XXXX-XXXX-XXXX-XXXX
NEXT_PUBLIC_LICENSE_SERVER_URL=https://your-license-server.com/api
LICENSE_SIGNING_SECRET=your-signing-secret-key
```

### Basic Usage

```typescript
import LicenseVaultClient, { 
  AccessLevel, 
  ValidationResult,
  canRead,
  canWrite,
  canUsePremiumFeatures 
} from '@/lib/licensevault-sdk';

// Initialize client
const client = new LicenseVaultClient({
  serverUrl: process.env.NEXT_PUBLIC_LICENSE_SERVER_URL!,
  signingSecret: process.env.LICENSE_SIGNING_SECRET!
});

// Validate license
async function validateLicense() {
  const result = await client.validateLicense('XXXX-XXXX-XXXX-XXXX');
  
  if (result.valid) {
    console.log('License valid!');
    console.log('Access Level:', result.accessLevel);
    
    // Use helper functions
    if (canRead(result)) {
      console.log('Read access granted');
    }
    
    if (canWrite(result)) {
      console.log('Write access granted');
    }
    
    if (result.inGracePeriod) {
      console.warn(`Grace period: ${result.daysUntilGraceEnd} days left`);
    }
  } else {
    console.error('License invalid:', result.message);
  }
}
```

### React Context Provider

```tsx
// lib/license-context.tsx
'use client';

import React, { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import LicenseVaultClient, { ValidationResult, AccessLevel, canRead, canWrite, canUsePremiumFeatures } from './licensevault-sdk';

interface LicenseContextType {
  licenseStatus: ValidationResult | null;
  isLoading: boolean;
  refreshLicense: () => Promise<void>;
  canRead: boolean;
  canWrite: boolean;
  canUsePremium: boolean;
}

const LicenseContext = createContext<LicenseContextType | undefined>(undefined);

export function LicenseProvider({ children }: { children: ReactNode }) {
  const [licenseStatus, setLicenseStatus] = useState<ValidationResult | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  const client = new LicenseVaultClient({
    serverUrl: process.env.NEXT_PUBLIC_LICENSE_SERVER_URL!,
    signingSecret: process.env.LICENSE_SIGNING_SECRET!
  });

  const validateLicense = async () => {
    setIsLoading(true);
    try {
      // Load offline license from localStorage if available
      const offlineLicense = localStorage.getItem('license_file');
      if (offlineLicense) {
        client.setLicenseFileContent(offlineLicense);
      }

      const result = await client.validateLicense(
        process.env.NEXT_PUBLIC_LICENSE_KEY!
      );
      setLicenseStatus(result);
    } catch (error) {
      console.error('License validation failed:', error);
      setLicenseStatus({
        valid: false,
        message: 'License validation failed',
        accessLevel: AccessLevel.NONE
      });
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    validateLicense();
  }, []);

  return (
    <LicenseContext.Provider
      value={{
        licenseStatus,
        isLoading,
        refreshLicense: validateLicense,
        canRead: licenseStatus ? canRead(licenseStatus) : false,
        canWrite: licenseStatus ? canWrite(licenseStatus) : false,
        canUsePremium: licenseStatus ? canUsePremiumFeatures(licenseStatus) : false,
      }}
    >
      {children}
    </LicenseContext.Provider>
  );
}

export function useLicense() {
  const context = useContext(LicenseContext);
  if (!context) {
    throw new Error('useLicense must be used within LicenseProvider');
  }
  return context;
}
```

### Protected Components

```tsx
// components/LicenseGuard.tsx
import { useLicense } from '@/lib/license-context';
import { ReactNode } from 'react';

interface LicenseGuardProps {
  children: ReactNode;
  requiredAccess: 'read' | 'write' | 'premium';
  fallback?: ReactNode;
}

export function LicenseGuard({ children, requiredAccess, fallback }: LicenseGuardProps) {
  const { licenseStatus, isLoading, canRead, canWrite, canUsePremium } = useLicense();

  if (isLoading) {
    return <div>Validating license...</div>;
  }

  let hasAccess = false;
  switch (requiredAccess) {
    case 'read': hasAccess = canRead; break;
    case 'write': hasAccess = canWrite; break;
    case 'premium': hasAccess = canUsePremium; break;
  }

  if (!hasAccess) {
    return fallback || (
      <div className="p-4 bg-red-100 text-red-800 rounded">
        <h3 className="font-bold">Access Denied</h3>
        <p>{licenseStatus?.message || 'License required for this feature'}</p>
      </div>
    );
  }

  return <>{children}</>;
}
```

### Usage in Pages

```tsx
// app/page.tsx
import { LicenseProvider } from '@/lib/license-context';
import { LicenseGuard } from '@/components/LicenseGuard';

export default function HomePage() {
  return (
    <LicenseProvider>
      <div className="p-8">
        {/* Always visible with read access */}
        <LicenseGuard requiredAccess="read">
          <section>
            <h2>Dashboard</h2>
            <p>Read-only content available during grace period</p>
          </section>
        </LicenseGuard>

        {/* Only with write access (not in grace period) */}
        <LicenseGuard 
          requiredAccess="write"
          fallback={<div className="opacity-50">Write access disabled</div>}
        >
          <section>
            <h2>Create Content</h2>
            <button>Create New Item</button>
          </section>
        </LicenseGuard>

        {/* Premium features only */}
        <LicenseGuard 
          requiredAccess="premium"
          fallback={<div>Upgrade to access premium features</div>}
        >
          <section>
            <h2>Premium Analytics</h2>
          </section>
        </LicenseGuard>
      </div>
    </LicenseProvider>
  );
}
```

### License Warning Banner

```tsx
// components/LicenseBanner.tsx
import { useLicense } from '@/lib/license-context';

export function LicenseBanner() {
  const { licenseStatus, isLoading } = useLicense();

  if (isLoading || !licenseStatus) return null;

  if (licenseStatus.inGracePeriod) {
    return (
      <div className="bg-orange-500 text-white px-4 py-2 text-center">
        <strong>⚠️ License Expired - Grace Period Active</strong>
        <span className="ml-2">
          {licenseStatus.daysUntilGraceEnd} days remaining. 
          Write operations disabled.
        </span>
        <button className="ml-4 bg-white text-orange-500 px-3 py-1 rounded">
          Renew Now
        </button>
      </div>
    );
  }

  if (licenseStatus.expiringSoon) {
    return (
      <div className="bg-yellow-500 text-black px-4 py-2 text-center">
        ⚠️ {licenseStatus.warningMessage}
      </div>
    );
  }

  if (!licenseStatus.valid) {
    return (
      <div className="bg-red-600 text-white px-4 py-2 text-center">
        🔒 License Invalid: {licenseStatus.message}
      </div>
    );
  }

  return null;
}
```

---

## Python SDK

### Installation

```bash
pip install requests
```

### Configuration

Copy `licensevault_sdk.py` from `/sdks/python/` to your project.

**Environment Variables:**
```bash
export LICENSE_SERVER_URL=https://your-license-server.com/api
export LICENSE_SIGNING_SECRET=your-signing-secret-key
export LICENSE_KEY=XXXX-XXXX-XXXX-XXXX
export LICENSE_FILE_PATH=license.lic
```

### Basic Usage

```python
from licensevault_sdk import LicenseVaultClient, AccessLevel

# Initialize client
client = LicenseVaultClient(
    server_url="https://your-server.com/api",
    signing_secret="your-signing-secret",
    license_file_path="license.lic"  # Optional for offline validation
)

# Validate license (tries online first, falls back to offline)
result = client.validate_license("XXXX-XXXX-XXXX-XXXX")

# Check validation result
if result.valid:
    print(f"License valid! Access Level: {result.access_level.value}")
    
    # Check permissions
    if result.can_read():
        print("Read access granted")
    
    if result.can_write():
        print("Write access granted")
    
    if result.can_use_premium_features():
        print("Premium features enabled")
    
    # Handle grace period
    if result.in_grace_period:
        print(f"WARNING: Grace period - {result.days_until_grace_end} days left")
else:
    print(f"License invalid: {result.message}")
```

### Flask Integration

```python
from flask import Flask, jsonify, request, g
from functools import wraps
from licensevault_sdk import LicenseVaultClient, AccessLevel
import os

app = Flask(__name__)

# Initialize license client
license_client = LicenseVaultClient(
    server_url=os.environ["LICENSE_SERVER_URL"],
    signing_secret=os.environ["LICENSE_SIGNING_SECRET"],
    license_file_path=os.environ.get("LICENSE_FILE_PATH", "license.lic")
)

# Global license status
license_status = None

def validate_on_startup():
    global license_status
    license_status = license_client.validate_license(os.environ["LICENSE_KEY"])
    
    if not license_status.valid:
        raise RuntimeError(f"License invalid: {license_status.message}")
    
    if license_status.access_level == AccessLevel.LIMITED:
        print("WARNING: Running in grace period with limited access")

# Validate on startup
validate_on_startup()

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
            msg = "Write disabled"
            if license_status.in_grace_period:
                msg = f"Write disabled (grace period: {license_status.days_until_grace_end} days left)"
            return jsonify({"error": msg}), 403
        return f(*args, **kwargs)
    return decorated

def require_premium(f):
    @wraps(f)
    def decorated(*args, **kwargs):
        if not license_status.can_use_premium_features():
            return jsonify({"error": "Premium license required"}), 403
        return f(*args, **kwargs)
    return decorated

# Routes
@app.route('/api/data', methods=['GET'])
@require_read
def get_data():
    return jsonify({"data": "Your data here"})

@app.route('/api/data', methods=['POST'])
@require_write
def save_data():
    return jsonify({"saved": True})

@app.route('/api/premium/analytics', methods=['GET'])
@require_premium
def premium_analytics():
    return jsonify({"analytics": "Premium data"})

@app.route('/api/license/status', methods=['GET'])
def get_license_status():
    return jsonify({
        "valid": license_status.valid,
        "accessLevel": license_status.access_level.value,
        "canRead": license_status.can_read(),
        "canWrite": license_status.can_write(),
        "canUsePremium": license_status.can_use_premium_features(),
        "inGracePeriod": license_status.in_grace_period,
        "daysUntilGraceEnd": license_status.days_until_grace_end
    })

if __name__ == '__main__':
    app.run(port=5000)
```

### FastAPI Integration

```python
from fastapi import FastAPI, Depends, HTTPException
from licensevault_sdk import LicenseVaultClient, ValidationResult, AccessLevel
import os

app = FastAPI()

# Initialize client
license_client = LicenseVaultClient(
    server_url=os.environ["LICENSE_SERVER_URL"],
    signing_secret=os.environ["LICENSE_SIGNING_SECRET"]
)

license_status: ValidationResult = None

@app.on_event("startup")
async def startup():
    global license_status
    license_status = license_client.validate_license(os.environ["LICENSE_KEY"])
    if not license_status.valid:
        raise RuntimeError(f"License invalid: {license_status.message}")

# Dependencies
async def require_read():
    if not license_status.can_read():
        raise HTTPException(403, "License required")
    return license_status

async def require_write():
    if not license_status.can_write():
        detail = "Write disabled"
        if license_status.in_grace_period:
            detail = f"Write disabled (grace: {license_status.days_until_grace_end} days)"
        raise HTTPException(403, detail)
    return license_status

async def require_premium():
    if not license_status.can_use_premium_features():
        raise HTTPException(403, "Premium license required")
    return license_status

# Routes
@app.get("/api/data")
async def get_data(_: ValidationResult = Depends(require_read)):
    return {"data": "Your data here"}

@app.post("/api/data")
async def save_data(_: ValidationResult = Depends(require_write)):
    return {"saved": True}

@app.get("/api/premium/analytics")
async def premium_analytics(_: ValidationResult = Depends(require_premium)):
    return {"analytics": "Premium data"}
```

### Download License File

```python
# Download and save license file for offline use
auth_token = "your-jwt-token"

# Method 1: Get content
license_file = client.download_license_file("XXXX-XXXX-XXXX-XXXX", auth_token)
print(license_file)

# Method 2: Save directly to file
client.save_license_file("XXXX-XXXX-XXXX-XXXX", auth_token, "license.lic")
```

---

## Testing Guide

### Server API Tests

```bash
# Set base URL
BASE_URL="https://your-server.com/api"

# 1. Register user
curl -X POST "$BASE_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","email":"test@example.com","password":"password123"}'

# 2. Login and get token
TOKEN=$(curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"password123"}' | jq -r '.token')

# 3. Generate license with grace period
curl -X POST "$BASE_URL/license/generate" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "username": "customer1",
    "email": "customer@test.com",
    "subscriptionType": "MONTHLY",
    "gracePeriodDays": 7
  }'

# 4. Validate license (online)
curl -X POST "$BASE_URL/license/validate" \
  -H "Content-Type: application/json" \
  -d '{"licenseKey": "XXXX-XXXX-XXXX-XXXX"}'

# 5. Download license file
curl -X GET "$BASE_URL/license/XXXX-XXXX-XXXX-XXXX/download" \
  -H "Authorization: Bearer $TOKEN"

# 6. Validate license file (offline)
curl -X POST "$BASE_URL/license/validate-file" \
  -H "Content-Type: application/json" \
  -d '{"licenseFile": "base64data.signature"}'

# 7. Update grace period
curl -X PATCH "$BASE_URL/license/XXXX-XXXX-XXXX-XXXX/grace-period" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"gracePeriodDays": 14}'

# 8. Get stats
curl -X GET "$BASE_URL/license/stats" \
  -H "Authorization: Bearer $TOKEN"
```

### Java SDK Tests

```java
// LicenseVaultClientTest.java
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

class LicenseVaultClientTest {
    
    private LicenseVaultClient client;
    private static final String SERVER_URL = "https://your-server.com/api";
    private static final String SIGNING_SECRET = "your-signing-secret";
    private static final String TEST_LICENSE_KEY = "TEST-1234-5678-ABCD";
    
    @BeforeEach
    void setUp() {
        client = new LicenseVaultClient(SERVER_URL, SIGNING_SECRET);
    }
    
    @Test
    void testOnlineValidation() throws Exception {
        ValidationResult result = client.validateOnline(TEST_LICENSE_KEY, null);
        
        assertNotNull(result);
        assertTrue(result.isValid());
        assertEquals(AccessLevel.FULL, result.getAccessLevel());
    }
    
    @Test
    void testOfflineValidation() {
        // Valid license file content
        String validLicenseFile = "eyJsaWNlbnNlS2V5IjoiVEVTVC0xMjM0..."; // Your test data
        
        ValidationResult result = client.validateLicenseFile(validLicenseFile);
        
        assertNotNull(result);
        assertTrue(result.isValid());
    }
    
    @Test
    void testInvalidSignature() {
        String tamperedFile = "eyJsaWNlbnNlS2V5IjoiVEVTVC0xMjM0...invalid";
        
        ValidationResult result = client.validateLicenseFile(tamperedFile);
        
        assertFalse(result.isValid());
        assertTrue(result.getMessage().contains("signature"));
    }
    
    @Test
    void testGracePeriodAccess() {
        // Create a license file with expired date but within grace period
        // ... setup test data ...
        
        ValidationResult result = client.validateLicenseFile(gracePeriodLicense);
        
        assertTrue(result.isValid());
        assertEquals(AccessLevel.LIMITED, result.getAccessLevel());
        assertTrue(result.isInGracePeriod());
        assertTrue(result.canRead());
        assertFalse(result.canWrite());
        assertFalse(result.canUsePremiumFeatures());
    }
    
    @Test
    void testExpiredLicense() {
        // Create fully expired license file
        // ... setup test data ...
        
        ValidationResult result = client.validateLicenseFile(expiredLicense);
        
        assertFalse(result.isValid());
        assertEquals(AccessLevel.NONE, result.getAccessLevel());
    }
}
```

### TypeScript SDK Tests

```typescript
// licensevault-sdk.test.ts
import { describe, it, expect, beforeEach } from 'vitest';
import LicenseVaultClient, { AccessLevel, canRead, canWrite, canUsePremiumFeatures } from './licensevault-sdk';

describe('LicenseVaultClient', () => {
  let client: LicenseVaultClient;
  
  beforeEach(() => {
    client = new LicenseVaultClient({
      serverUrl: 'https://your-server.com/api',
      signingSecret: 'your-signing-secret'
    });
  });
  
  describe('Online Validation', () => {
    it('should validate active license', async () => {
      const result = await client.validateOnline('TEST-1234-5678-ABCD');
      
      expect(result.valid).toBe(true);
      expect(result.accessLevel).toBe(AccessLevel.FULL);
    });
    
    it('should return proper access flags', async () => {
      const result = await client.validateOnline('TEST-1234-5678-ABCD');
      
      expect(canRead(result)).toBe(true);
      expect(canWrite(result)).toBe(true);
      expect(canUsePremiumFeatures(result)).toBe(true);
    });
  });
  
  describe('Offline Validation', () => {
    it('should validate signed license file', () => {
      const validLicenseFile = 'base64data.signature';
      client.setLicenseFileContent(validLicenseFile);
      
      const result = client.validateOffline();
      
      expect(result.valid).toBe(true);
    });
    
    it('should reject tampered license file', () => {
      const tamperedFile = 'base64data.invalid-signature';
      
      const result = client.validateLicenseFile(tamperedFile);
      
      expect(result.valid).toBe(false);
      expect(result.message).toContain('signature');
    });
  });
  
  describe('Grace Period', () => {
    it('should return LIMITED access during grace period', () => {
      // Test with grace period license file
      const gracePeriodLicense = '...'; // Your test data
      
      const result = client.validateLicenseFile(gracePeriodLicense);
      
      expect(result.valid).toBe(true);
      expect(result.accessLevel).toBe(AccessLevel.LIMITED);
      expect(result.inGracePeriod).toBe(true);
      expect(canRead(result)).toBe(true);
      expect(canWrite(result)).toBe(false);
      expect(canUsePremiumFeatures(result)).toBe(false);
    });
  });
});
```

### Python SDK Tests

```python
# test_licensevault_sdk.py
import pytest
from licensevault_sdk import LicenseVaultClient, AccessLevel, ValidationResult

SERVER_URL = "https://your-server.com/api"
SIGNING_SECRET = "your-signing-secret"
TEST_LICENSE_KEY = "TEST-1234-5678-ABCD"

@pytest.fixture
def client():
    return LicenseVaultClient(
        server_url=SERVER_URL,
        signing_secret=SIGNING_SECRET
    )

class TestOnlineValidation:
    def test_validate_active_license(self, client):
        result = client.validate_license(TEST_LICENSE_KEY)
        
        assert result.valid is True
        assert result.access_level == AccessLevel.FULL
    
    def test_access_flags(self, client):
        result = client.validate_license(TEST_LICENSE_KEY)
        
        assert result.can_read() is True
        assert result.can_write() is True
        assert result.can_use_premium_features() is True

class TestOfflineValidation:
    def test_validate_signed_file(self, client):
        valid_license_file = "base64data.signature"
        
        result = client.validate_license_file(valid_license_file)
        
        assert result.valid is True
    
    def test_reject_tampered_file(self, client):
        tampered_file = "base64data.invalid-signature"
        
        result = client.validate_license_file(tampered_file)
        
        assert result.valid is False
        assert "signature" in result.message.lower()
    
    def test_reject_invalid_format(self, client):
        invalid_file = "no-dot-separator"
        
        result = client.validate_license_file(invalid_file)
        
        assert result.valid is False
        assert "format" in result.message.lower()

class TestGracePeriod:
    def test_limited_access_during_grace(self, client):
        # Create grace period license file for testing
        grace_period_license = "..."  # Your test data
        
        result = client.validate_license_file(grace_period_license)
        
        assert result.valid is True
        assert result.access_level == AccessLevel.LIMITED
        assert result.in_grace_period is True
        assert result.can_read() is True
        assert result.can_write() is False
        assert result.can_use_premium_features() is False

class TestExpiredLicense:
    def test_no_access_after_grace(self, client):
        expired_license = "..."  # Your test data
        
        result = client.validate_license_file(expired_license)
        
        assert result.valid is False
        assert result.access_level == AccessLevel.NONE
        assert result.can_read() is False
        assert result.can_write() is False

# Run tests
if __name__ == "__main__":
    pytest.main([__file__, "-v"])
```

---

## Troubleshooting

### Common Issues

| Issue | Cause | Solution |
|-------|-------|----------|
| "Invalid signature" | Wrong signing secret | Ensure `LICENSE_SIGNING_SECRET` matches server |
| "License file not found" | Missing offline file | Download license file or check path |
| Online validation fails | Network/server issue | Check server URL and connectivity |
| "Access denied" on write | Grace period active | Renew license to restore write access |

### Debug Mode

**Java:**
```java
// Enable debug logging
System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "DEBUG");
```

**TypeScript:**
```typescript
// Add console logging
const result = await client.validateLicense(key);
console.log('Validation result:', JSON.stringify(result, null, 2));
```

**Python:**
```python
import logging
logging.basicConfig(level=logging.DEBUG)
```

### Verifying License File

```python
# Decode and inspect license file
import base64
import json

license_file = "eyJsaWNlbnNlS2V5Ijo..."
parts = license_file.split('.')
data = json.loads(base64.b64decode(parts[0]))
print(json.dumps(data, indent=2))
```

---

## Support

For issues or questions:
- Check the `/sdks/README.md` for quick reference
- Review example files in `/sdks/{java,nextjs,python}/examples/`
- Contact support or open an issue on GitHub
