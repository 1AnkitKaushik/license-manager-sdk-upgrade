/**
 * Example Next.js application using LicenseVault SDK
 * 
 * This shows how to integrate license validation in a Next.js app with:
 * - Server-side validation on app start
 * - Client-side license context
 * - Protected routes and components
 * - Grace period handling
 */

// ============================================
// lib/license-context.tsx - License Context Provider
// ============================================

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

const LICENSE_KEY = process.env.NEXT_PUBLIC_LICENSE_KEY || '';
const SERVER_URL = process.env.NEXT_PUBLIC_LICENSE_SERVER_URL || 'https://your-server.com/api';
const SIGNING_SECRET = process.env.LICENSE_SIGNING_SECRET || 'your-signing-secret';

export function LicenseProvider({ children }: { children: ReactNode }) {
  const [licenseStatus, setLicenseStatus] = useState<ValidationResult | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  const client = new LicenseVaultClient({
    serverUrl: SERVER_URL,
    signingSecret: SIGNING_SECRET,
  });

  const validateLicense = async () => {
    setIsLoading(true);
    try {
      // Try to load offline license from localStorage first
      const offlineLicense = localStorage.getItem('license_file');
      if (offlineLicense) {
        client.setLicenseFileContent(offlineLicense);
      }

      const result = await client.validateLicense(LICENSE_KEY);
      setLicenseStatus(result);

      // Log warnings
      if (result.warningMessage) {
        console.warn('License Warning:', result.warningMessage);
      }

      if (result.inGracePeriod) {
        console.warn('LICENSE IN GRACE PERIOD - Limited access mode');
      }

    } catch (error) {
      console.error('License validation failed:', error);
      setLicenseStatus({
        valid: false,
        message: 'License validation failed',
        accessLevel: AccessLevel.NONE,
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
  if (context === undefined) {
    throw new Error('useLicense must be used within a LicenseProvider');
  }
  return context;
}

// ============================================
// components/LicenseGuard.tsx - Protected Component Wrapper
// ============================================

interface LicenseGuardProps {
  children: ReactNode;
  requiredAccess: 'read' | 'write' | 'premium';
  fallback?: ReactNode;
}

export function LicenseGuard({ children, requiredAccess, fallback }: LicenseGuardProps) {
  const { licenseStatus, isLoading, canRead, canWrite, canUsePremium } = useLicense();

  if (isLoading) {
    return <div className="p-4 text-center">Validating license...</div>;
  }

  let hasAccess = false;
  switch (requiredAccess) {
    case 'read':
      hasAccess = canRead;
      break;
    case 'write':
      hasAccess = canWrite;
      break;
    case 'premium':
      hasAccess = canUsePremium;
      break;
  }

  if (!hasAccess) {
    if (fallback) {
      return <>{fallback}</>;
    }
    return (
      <div className="p-4 bg-red-100 border border-red-400 rounded-lg">
        <h3 className="text-red-800 font-bold">Access Denied</h3>
        <p className="text-red-700">
          {licenseStatus?.inGracePeriod
            ? `This feature is disabled during grace period. ${licenseStatus?.daysUntilGraceEnd} days remaining.`
            : 'Your license does not permit access to this feature.'}
        </p>
        <p className="text-red-600 text-sm mt-2">{licenseStatus?.message}</p>
      </div>
    );
  }

  return <>{children}</>;
}

// ============================================
// components/LicenseBanner.tsx - License Status Banner
// ============================================

export function LicenseBanner() {
  const { licenseStatus, isLoading } = useLicense();

  if (isLoading || !licenseStatus) return null;

  // Show warning banner for grace period
  if (licenseStatus.inGracePeriod) {
    return (
      <div className="bg-orange-500 text-white px-4 py-2 text-center">
        <strong>⚠️ License Expired - Grace Period Active</strong>
        <span className="ml-2">
          {licenseStatus.daysUntilGraceEnd} days remaining. 
          Write operations and premium features are disabled.
        </span>
        <button className="ml-4 bg-white text-orange-500 px-3 py-1 rounded text-sm font-bold">
          Renew Now
        </button>
      </div>
    );
  }

  // Show warning for expiring soon
  if (licenseStatus.expiringSoon && licenseStatus.warningMessage) {
    return (
      <div className="bg-yellow-500 text-black px-4 py-2 text-center">
        <strong>⚠️ {licenseStatus.warningMessage}</strong>
        <button className="ml-4 bg-black text-yellow-500 px-3 py-1 rounded text-sm font-bold">
          Renew Now
        </button>
      </div>
    );
  }

  // Show error for invalid license
  if (!licenseStatus.valid) {
    return (
      <div className="bg-red-600 text-white px-4 py-2 text-center">
        <strong>🔒 License Invalid</strong>
        <span className="ml-2">{licenseStatus.message}</span>
        <button className="ml-4 bg-white text-red-600 px-3 py-1 rounded text-sm font-bold">
          Get License
        </button>
      </div>
    );
  }

  return null;
}

// ============================================
// app/page.tsx - Example Page Component
// ============================================

export default function ExamplePage() {
  return (
    <LicenseProvider>
      <div className="min-h-screen">
        <LicenseBanner />
        
        <main className="p-8">
          <h1 className="text-3xl font-bold mb-8">My Licensed Application</h1>
          
          {/* Always visible - read access */}
          <LicenseGuard requiredAccess="read">
            <section className="mb-8 p-4 bg-gray-100 rounded-lg">
              <h2 className="text-xl font-semibold mb-4">Dashboard (Read Access)</h2>
              <p>This content requires read access - available in both FULL and LIMITED (grace period) modes.</p>
            </section>
          </LicenseGuard>
          
          {/* Write operations - not available in grace period */}
          <LicenseGuard 
            requiredAccess="write"
            fallback={
              <section className="mb-8 p-4 bg-gray-200 rounded-lg opacity-50">
                <h2 className="text-xl font-semibold mb-4">Create/Edit Content (Disabled)</h2>
                <p>Write operations are disabled. Renew your license to enable.</p>
              </section>
            }
          >
            <section className="mb-8 p-4 bg-green-100 rounded-lg">
              <h2 className="text-xl font-semibold mb-4">Create/Edit Content</h2>
              <button className="bg-green-500 text-white px-4 py-2 rounded">
                Create New Item
              </button>
            </section>
          </LicenseGuard>
          
          {/* Premium features - not available in grace period */}
          <LicenseGuard 
            requiredAccess="premium"
            fallback={
              <section className="mb-8 p-4 bg-purple-100 rounded-lg opacity-50">
                <h2 className="text-xl font-semibold mb-4">Premium Features (Locked)</h2>
                <p>Premium features require an active license.</p>
              </section>
            }
          >
            <section className="mb-8 p-4 bg-purple-100 rounded-lg">
              <h2 className="text-xl font-semibold mb-4">Premium Features</h2>
              <p>Advanced analytics, custom reports, and more...</p>
            </section>
          </LicenseGuard>
          
          {/* License Status Display */}
          <LicenseStatusCard />
        </main>
      </div>
    </LicenseProvider>
  );
}

function LicenseStatusCard() {
  const { licenseStatus, refreshLicense, isLoading } = useLicense();

  if (!licenseStatus) return null;

  return (
    <div className="p-4 bg-slate-800 text-white rounded-lg">
      <h3 className="text-lg font-bold mb-4">License Status</h3>
      <div className="grid grid-cols-2 gap-2 text-sm">
        <span>Valid:</span>
        <span className={licenseStatus.valid ? 'text-green-400' : 'text-red-400'}>
          {licenseStatus.valid ? 'Yes' : 'No'}
        </span>
        
        <span>Access Level:</span>
        <span className={
          licenseStatus.accessLevel === 'FULL' ? 'text-green-400' :
          licenseStatus.accessLevel === 'LIMITED' ? 'text-yellow-400' : 'text-red-400'
        }>
          {licenseStatus.accessLevel}
        </span>
        
        <span>Grace Period:</span>
        <span>{licenseStatus.inGracePeriod ? `Yes (${licenseStatus.daysUntilGraceEnd} days left)` : 'No'}</span>
        
        <span>Can Read:</span>
        <span>{canRead(licenseStatus) ? '✅' : '❌'}</span>
        
        <span>Can Write:</span>
        <span>{canWrite(licenseStatus) ? '✅' : '❌'}</span>
        
        <span>Premium:</span>
        <span>{canUsePremiumFeatures(licenseStatus) ? '✅' : '❌'}</span>
      </div>
      
      <button 
        onClick={refreshLicense}
        disabled={isLoading}
        className="mt-4 bg-blue-500 text-white px-4 py-2 rounded disabled:opacity-50"
      >
        {isLoading ? 'Validating...' : 'Refresh License'}
      </button>
    </div>
  );
}
