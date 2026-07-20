# LicenseVault SDK Documentation

This directory contains client SDKs for integrating LicenseVault license validation into your applications.

## Available SDKs

| SDK | Directory | Language/Framework |
|-----|-----------|-------------------|
| Java SDK | `/sdks/java/` | Java 17+, Spring Boot |
| Next.js SDK | `/sdks/nextjs/` | TypeScript, Next.js, React |
| Python SDK | `/sdks/python/` | Python 3.8+, Flask, FastAPI |

---

## Features

All SDKs support:

- **Online Validation**: Real-time license validation via the license server
- **Offline Validation**: Signed license file validation without internet
- **Grace Period Handling**: Configurable grace period with limited access
- **Access Levels**: FULL, LIMITED (grace period), NONE
- **Feature Flags**: Premium features and write access control

---

## Access Levels

| Level | Description | Read | Write | Premium |
|-------|-------------|------|-------|---------|
| `FULL` | Active license | ✅ | ✅ | ✅ |
| `LIMITED` | Grace period (expired but within grace) | ✅ | ❌ | ❌ |
| `NONE` | Invalid/fully expired | ❌ | ❌ | ❌ |

---

## Quick Start

### 1. Generate a License

```bash
# Login and get token
TOKEN=$(curl -s -X POST https://your-server.com/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"password"}' | jq -r '.token')

# Generate license with configurable grace period
curl -X POST https://your-server.com/api/license/generate \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "username": "customer1",
    "email": "customer@example.com",
    "subscriptionType": "YEARLY",
    "gracePeriodDays": 14
  }'
```

### 2. Download License File for Offline Use

```bash
curl -X GET "https://your-server.com/api/license/XXXX-XXXX-XXXX-XXXX/download" \
  -H "Authorization: Bearer $TOKEN"
```

Save the `licenseFile` content to a `.lic` file.

### 3. Validate License

**Online:**
```bash
curl -X POST https://your-server.com/api/license/validate \
  -H "Content-Type: application/json" \
  -d '{"licenseKey": "XXXX-XXXX-XXXX-XXXX"}'
```

**Offline (with license file):**
```bash
curl -X POST https://your-server.com/api/license/validate-file \
  -H "Content-Type: application/json" \
  -d '{"licenseFile": "base64data.signature"}'
```

---

## Java SDK

### Installation

Copy `LicenseVaultClient.java` to your project and add dependencies:

```xml
<!-- pom.xml -->
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>
<dependency>
    <groupId>com.fasterxml.jackson.datatype</groupId>
    <artifactId>jackson-datatype-jsr310</artifactId>
</dependency>
```

### Usage

```java
import com.licensevault.sdk.LicenseVaultClient;
import com.licensevault.sdk.LicenseVaultClient.ValidationResult;
import com.licensevault.sdk.LicenseVaultClient.AccessLevel;

// Initialize client
LicenseVaultClient client = new LicenseVaultClient(
    "https://your-server.com/api",
    "your-signing-secret"
);

// Set offline license file path
client.setLicenseFilePath("license.lic");

// Validate (tries online first, falls back to offline)
ValidationResult result = client.validateLicense("XXXX-XXXX-XXXX-XXXX");

if (result.isValid()) {
    switch (result.getAccessLevel()) {
        case FULL:
            // All features enabled
            break;
        case LIMITED:
            // Grace period - read-only, core features only
            System.out.println("Grace period: " + result.getDaysUntilGraceEnd() + " days left");
            break;
    }
}

// Check specific permissions
if (result.canWrite()) {
    // Allow write operations
}

if (result.canUsePremiumFeatures()) {
    // Enable premium features
}
```

### Spring Boot Integration

See `/sdks/java/examples/SpringBootExample.java` for a complete example with:
- Startup validation
- License manager singleton
- Endpoint decorators
- Exception handling

---

## Next.js SDK

### Installation

```bash
npm install crypto-js
# or
yarn add crypto-js
```

Copy `licensevault-sdk.ts` to your project.

### Usage

```typescript
import LicenseVaultClient, { AccessLevel, canRead, canWrite } from './licensevault-sdk';

const client = new LicenseVaultClient({
  serverUrl: 'https://your-server.com/api',
  signingSecret: 'your-signing-secret'
});

// Validate license
const result = await client.validateLicense('XXXX-XXXX-XXXX-XXXX');

if (result.valid) {
  if (result.accessLevel === AccessLevel.LIMITED) {
    console.warn(`Grace period: ${result.daysUntilGraceEnd} days remaining`);
  }
}

// Use helper functions
if (canRead(result)) {
  // Show read-only content
}

if (canWrite(result)) {
  // Allow modifications
}
```

### React Integration

See `/sdks/nextjs/examples/NextJsExample.tsx` for a complete example with:
- LicenseProvider context
- LicenseGuard component for protected routes
- LicenseBanner for warnings
- useLicense hook

---

## Python SDK

### Installation

```bash
pip install requests
```

Copy `licensevault_sdk.py` to your project.

### Usage

```python
from licensevault_sdk import LicenseVaultClient, AccessLevel

client = LicenseVaultClient(
    server_url="https://your-server.com/api",
    signing_secret="your-signing-secret",
    license_file_path="license.lic"
)

# Validate license
result = client.validate_license("XXXX-XXXX-XXXX-XXXX")

if result.valid:
    if result.access_level == AccessLevel.LIMITED:
        print(f"Grace period: {result.days_until_grace_end} days left")

# Check permissions
if result.can_read():
    # Read operations allowed

if result.can_write():
    # Write operations allowed

if result.can_use_premium_features():
    # Premium features enabled
```

### Flask/FastAPI Integration

See `/sdks/python/examples/flask_example.py` for a complete example with:
- Startup validation
- Request middleware
- Endpoint decorators
- Both Flask and FastAPI patterns

---

## Offline License Files

### File Format

License files are signed JSON data in the format:
```
base64(jsonData).hmacSignature
```

### Generating Offline License

```bash
# Download from server
curl -X GET "https://your-server.com/api/license/{key}/download" \
  -H "Authorization: Bearer $TOKEN" \
  | jq -r '.licenseFile' > license.lic
```

### Validating Offline

All SDKs automatically:
1. Try online validation first
2. Fall back to offline file if online fails
3. Verify HMAC signature
4. Check expiry and grace period dates

---

## Grace Period Behavior

When a license expires:

1. **Expiry → Grace Period Start**
   - Status changes to `GRACE_PERIOD`
   - Access level becomes `LIMITED`
   - Daily email notifications begin

2. **During Grace Period**
   - Read operations: ✅ Allowed
   - Write operations: ❌ Disabled
   - Premium features: ❌ Disabled

3. **Grace Period End**
   - Status changes to `EXPIRED`
   - Access level becomes `NONE`
   - All operations blocked

### Configuring Grace Period

```bash
# Set grace period when generating license
curl -X POST https://your-server.com/api/license/generate \
  -d '{"gracePeriodDays": 14, ...}'

# Update existing license's grace period
curl -X PATCH "https://your-server.com/api/license/{key}/grace-period" \
  -d '{"gracePeriodDays": 7}'
```

---

## Email Notifications

The server sends emails at these times:

| Event | When | Frequency |
|-------|------|-----------|
| Expiry Warning | 7 days before expiry | Once |
| Grace Period | After expiry | Daily |
| Final Expiry | After grace period | Once |

### SMTP Configuration

Set environment variables:
```env
EMAIL_ENABLED=true
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USERNAME=your-email@gmail.com
SMTP_PASSWORD=your-app-password
EMAIL_FROM=noreply@yourdomain.com
```

---

## Environment Variables

### Server

| Variable | Description | Default |
|----------|-------------|---------|
| `LICENSE_SIGNING_SECRET` | Secret for signing license files | Required |
| `GRACE_PERIOD_DAYS` | Default grace period | 7 |
| `EMAIL_ENABLED` | Enable email notifications | false |
| `SMTP_HOST` | SMTP server host | smtp.gmail.com |
| `SMTP_PORT` | SMTP server port | 587 |
| `SMTP_USERNAME` | SMTP username | - |
| `SMTP_PASSWORD` | SMTP password | - |

### Client SDKs

| Variable | Description |
|----------|-------------|
| `LICENSE_SERVER_URL` | License server API URL |
| `LICENSE_SIGNING_SECRET` | Same secret as server |
| `LICENSE_KEY` | Your license key |
| `LICENSE_FILE_PATH` | Path to offline license file |

---

## Security Considerations

1. **Signing Secret**: Keep `LICENSE_SIGNING_SECRET` secure and never expose in client code
2. **License Files**: Signed with HMAC-SHA256 to prevent tampering
3. **Offline Validation**: Still verifies signature and expiry dates
4. **Grace Period**: Provides limited access - not full bypass

---

## API Reference

### Validation Response

```json
{
  "valid": true,
  "message": "License is valid",
  "accessLevel": "FULL",
  "licenseKey": "XXXX-XXXX-XXXX-XXXX",
  "status": "ACTIVE",
  "subscriptionType": "YEARLY",
  "expiryDate": "2027-03-17T00:00:00",
  "graceEndDate": "2027-03-24T00:00:00",
  "gracePeriodDays": 7,
  "daysUntilExpiry": 365,
  "daysUntilGraceEnd": 372,
  "inGracePeriod": false,
  "premiumFeaturesEnabled": true,
  "writeAccessEnabled": true,
  "warningMessage": null
}
```

---

## Support

For issues or questions, please contact support or open an issue on GitHub.
