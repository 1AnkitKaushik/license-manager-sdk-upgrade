# LicenseVault Next.js/TypeScript SDK

TypeScript client library for license validation in Next.js and React applications.

## Features

- Online license validation via REST API
- Offline validation using signed license files
- Grace period handling with limited access
- React Context provider for state management
- Type-safe with full TypeScript support

## Requirements

- Node.js 18+
- React 18+ / Next.js 13+

## Installation

```bash
npm install crypto-js
# or
yarn add crypto-js

# TypeScript types
npm install -D @types/crypto-js
```

Copy `licensevault-sdk.ts` to your project (e.g., `lib/licensevault-sdk.ts`).

## Quick Start

```typescript
import LicenseVaultClient, { AccessLevel, canRead, canWrite } from './licensevault-sdk';

const client = new LicenseVaultClient({
  serverUrl: 'https://your-server.com/api',
  signingSecret: 'your-signing-secret'
});

// Validate
const result = await client.validateLicense('XXXX-XXXX-XXXX-XXXX');

if (result.valid) {
  console.log('Access Level:', result.accessLevel);
  
  if (canWrite(result)) {
    // Allow write operations
  }
  
  if (result.inGracePeriod) {
    console.log(`Grace period: ${result.daysUntilGraceEnd} days left`);
  }
}
```

## Environment Variables

```env
NEXT_PUBLIC_LICENSE_KEY=XXXX-XXXX-XXXX-XXXX
NEXT_PUBLIC_LICENSE_SERVER_URL=https://your-server.com/api
LICENSE_SIGNING_SECRET=your-signing-secret
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
```typescript
new LicenseVaultClient({
  serverUrl: string,
  signingSecret: string
})
```

#### Methods

| Method | Description |
|--------|-------------|
| `setLicenseFileContent(content)` | Set offline license content |
| `validateLicense(key, machineId?)` | Validate (online → offline) |
| `validateOnline(key, machineId?)` | Force online validation |
| `validateOffline()` | Force offline validation |
| `validateLicenseFile(content)` | Validate license file |
| `downloadLicenseFile(key, token)` | Download license file |

### ValidationResult

```typescript
interface ValidationResult {
  valid: boolean;
  message: string;
  accessLevel: AccessLevel;
  licenseKey?: string;
  status?: string;
  subscriptionType?: string;
  expiryDate?: string;
  graceEndDate?: string;
  gracePeriodDays?: number;
  daysUntilExpiry?: number;
  daysUntilGraceEnd?: number;
  expiringSoon?: boolean;
  inGracePeriod?: boolean;
  warningMessage?: string;
  premiumFeaturesEnabled?: boolean;
  writeAccessEnabled?: boolean;
}
```

### Helper Functions

```typescript
import { canRead, canWrite, canUsePremiumFeatures } from './licensevault-sdk';

canRead(result)              // true if read allowed
canWrite(result)             // true if write allowed
canUsePremiumFeatures(result) // true if premium allowed
```

## React Integration

### Context Provider

```tsx
// lib/license-context.tsx
import { createContext, useContext, useState, useEffect } from 'react';
import LicenseVaultClient, { ValidationResult, AccessLevel } from './licensevault-sdk';

interface LicenseContextType {
  licenseStatus: ValidationResult | null;
  isLoading: boolean;
  refreshLicense: () => Promise<void>;
  canRead: boolean;
  canWrite: boolean;
  canUsePremium: boolean;
}

const LicenseContext = createContext<LicenseContextType | undefined>(undefined);

export function LicenseProvider({ children }) {
  const [licenseStatus, setLicenseStatus] = useState<ValidationResult | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  
  // ... implementation
  
  return (
    <LicenseContext.Provider value={{...}}>
      {children}
    </LicenseContext.Provider>
  );
}

export function useLicense() {
  const context = useContext(LicenseContext);
  if (!context) throw new Error('useLicense must be used within LicenseProvider');
  return context;
}
```

### Protected Component

```tsx
// components/LicenseGuard.tsx
export function LicenseGuard({ children, requiredAccess, fallback }) {
  const { canRead, canWrite, canUsePremium, isLoading } = useLicense();
  
  if (isLoading) return <div>Loading...</div>;
  
  let hasAccess = false;
  switch (requiredAccess) {
    case 'read': hasAccess = canRead; break;
    case 'write': hasAccess = canWrite; break;
    case 'premium': hasAccess = canUsePremium; break;
  }
  
  if (!hasAccess) return fallback || <AccessDenied />;
  
  return <>{children}</>;
}
```

### Usage

```tsx
// app/page.tsx
<LicenseProvider>
  <LicenseGuard requiredAccess="read">
    <Dashboard />
  </LicenseGuard>
  
  <LicenseGuard requiredAccess="write" fallback={<ReadOnlyMessage />}>
    <Editor />
  </LicenseGuard>
  
  <LicenseGuard requiredAccess="premium">
    <PremiumFeatures />
  </LicenseGuard>
</LicenseProvider>
```

## Testing

```bash
npm test
# or
yarn test
```

## Files

```
/sdks/nextjs/
├── licensevault-sdk.ts        # Main SDK
├── examples/
│   └── NextJsExample.tsx      # Complete React example
└── README.md                  # This file
```

## License

MIT License
