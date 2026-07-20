/**
 * Unit tests for LicenseVault Next.js/TypeScript SDK
 * 
 * Run with: npm test or yarn test (using vitest or jest)
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import LicenseVaultClient, { 
  AccessLevel, 
  ValidationResult,
  canRead, 
  canWrite, 
  canUsePremiumFeatures 
} from './licensevault-sdk';
import CryptoJS from 'crypto-js';

const SIGNING_SECRET = 'LicenseSigningSecretKey2024ForOfflineValidation';

describe('LicenseVaultClient', () => {
  let client: LicenseVaultClient;

  beforeEach(() => {
    client = new LicenseVaultClient({
      serverUrl: 'https://test-server.com/api',
      signingSecret: SIGNING_SECRET,
    });
  });

  // ==================== Offline Validation Tests ====================

  describe('Offline Validation', () => {
    it('should validate properly formatted active license file', () => {
      const licenseFile = createTestLicenseFile({
        expiryDate: addDays(new Date(), 30),
        graceEndDate: addDays(new Date(), 37),
        status: 'ACTIVE',
      });

      const result = client.validateLicenseFile(licenseFile);

      expect(result.valid).toBe(true);
      expect(result.accessLevel).toBe(AccessLevel.FULL);
      expect(canRead(result)).toBe(true);
      expect(canWrite(result)).toBe(true);
      expect(canUsePremiumFeatures(result)).toBe(true);
      expect(result.inGracePeriod).toBe(false);
    });

    it('should return LIMITED access for license in grace period', () => {
      const licenseFile = createTestLicenseFile({
        expiryDate: addDays(new Date(), -3), // Expired 3 days ago
        graceEndDate: addDays(new Date(), 4), // Grace ends in 4 days
        status: 'GRACE_PERIOD',
      });

      const result = client.validateLicenseFile(licenseFile);

      expect(result.valid).toBe(true);
      expect(result.accessLevel).toBe(AccessLevel.LIMITED);
      expect(canRead(result)).toBe(true);
      expect(canWrite(result)).toBe(false);
      expect(canUsePremiumFeatures(result)).toBe(false);
      expect(result.inGracePeriod).toBe(true);
    });

    it('should return NONE access for fully expired license', () => {
      const licenseFile = createTestLicenseFile({
        expiryDate: addDays(new Date(), -30), // Expired 30 days ago
        graceEndDate: addDays(new Date(), -23), // Grace ended 23 days ago
        status: 'EXPIRED',
      });

      const result = client.validateLicenseFile(licenseFile);

      expect(result.valid).toBe(false);
      expect(result.accessLevel).toBe(AccessLevel.NONE);
      expect(canRead(result)).toBe(false);
      expect(canWrite(result)).toBe(false);
      expect(canUsePremiumFeatures(result)).toBe(false);
    });

    it('should reject license file with invalid signature', () => {
      const validData = btoa(JSON.stringify({
        licenseKey: 'TEST-1234-5678-ABCD',
        status: 'ACTIVE',
      }));
      const invalidFile = `${validData}.invalid-signature`;

      const result = client.validateLicenseFile(invalidFile);

      expect(result.valid).toBe(false);
      expect(result.message.toLowerCase()).toContain('signature');
      expect(result.accessLevel).toBe(AccessLevel.NONE);
    });

    it('should reject license file with invalid format', () => {
      const result = client.validateLicenseFile('no-dot-separator');

      expect(result.valid).toBe(false);
      expect(result.message.toLowerCase()).toContain('format');
    });

    it('should reject suspended license', () => {
      const licenseFile = createTestLicenseFile({
        expiryDate: addDays(new Date(), 30),
        graceEndDate: addDays(new Date(), 37),
        status: 'SUSPENDED',
      });

      const result = client.validateLicenseFile(licenseFile);

      expect(result.valid).toBe(false);
      expect(result.message.toLowerCase()).toContain('suspended');
      expect(result.accessLevel).toBe(AccessLevel.NONE);
    });

    it('should reject revoked license', () => {
      const licenseFile = createTestLicenseFile({
        expiryDate: addDays(new Date(), 30),
        graceEndDate: addDays(new Date(), 37),
        status: 'REVOKED',
      });

      const result = client.validateLicenseFile(licenseFile);

      expect(result.valid).toBe(false);
      expect(result.message.toLowerCase()).toContain('revoked');
    });
  });

  // ==================== Helper Function Tests ====================

  describe('Helper Functions', () => {
    it('canRead should return true for FULL and LIMITED access', () => {
      const fullResult: ValidationResult = {
        valid: true,
        message: 'OK',
        accessLevel: AccessLevel.FULL,
      };
      const limitedResult: ValidationResult = {
        valid: true,
        message: 'OK',
        accessLevel: AccessLevel.LIMITED,
      };
      const noneResult: ValidationResult = {
        valid: false,
        message: 'Expired',
        accessLevel: AccessLevel.NONE,
      };

      expect(canRead(fullResult)).toBe(true);
      expect(canRead(limitedResult)).toBe(true);
      expect(canRead(noneResult)).toBe(false);
    });

    it('canWrite should return true only for FULL access with write enabled', () => {
      const fullWithWrite: ValidationResult = {
        valid: true,
        message: 'OK',
        accessLevel: AccessLevel.FULL,
        writeAccessEnabled: true,
      };
      const fullWithoutWrite: ValidationResult = {
        valid: true,
        message: 'OK',
        accessLevel: AccessLevel.FULL,
        writeAccessEnabled: false,
      };
      const limited: ValidationResult = {
        valid: true,
        message: 'OK',
        accessLevel: AccessLevel.LIMITED,
        writeAccessEnabled: true,
      };

      expect(canWrite(fullWithWrite)).toBe(true);
      expect(canWrite(fullWithoutWrite)).toBe(false);
      expect(canWrite(limited)).toBe(false);
    });

    it('canUsePremiumFeatures should return true only for FULL access with premium enabled', () => {
      const fullWithPremium: ValidationResult = {
        valid: true,
        message: 'OK',
        accessLevel: AccessLevel.FULL,
        premiumFeaturesEnabled: true,
      };
      const limited: ValidationResult = {
        valid: true,
        message: 'OK',
        accessLevel: AccessLevel.LIMITED,
        premiumFeaturesEnabled: true,
      };

      expect(canUsePremiumFeatures(fullWithPremium)).toBe(true);
      expect(canUsePremiumFeatures(limited)).toBe(false);
    });
  });

  // ==================== Online Validation Tests (Mocked) ====================

  describe('Online Validation', () => {
    it('should call server API for online validation', async () => {
      const mockResponse: ValidationResult = {
        valid: true,
        message: 'License is valid',
        accessLevel: AccessLevel.FULL,
        premiumFeaturesEnabled: true,
        writeAccessEnabled: true,
      };

      global.fetch = vi.fn().mockResolvedValue({
        ok: true,
        json: () => Promise.resolve(mockResponse),
      });

      const result = await client.validateOnline('TEST-1234-5678-ABCD');

      expect(fetch).toHaveBeenCalledWith(
        'https://test-server.com/api/license/validate',
        expect.objectContaining({
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
        })
      );
      expect(result.valid).toBe(true);
      expect(result.accessLevel).toBe(AccessLevel.FULL);
    });

    it('should fall back to offline when online fails', async () => {
      global.fetch = vi.fn().mockRejectedValue(new Error('Network error'));

      const licenseFile = createTestLicenseFile({
        expiryDate: addDays(new Date(), 30),
        graceEndDate: addDays(new Date(), 37),
        status: 'ACTIVE',
      });
      client.setLicenseFileContent(licenseFile);

      const result = await client.validateLicense('TEST-1234-5678-ABCD');

      expect(result.valid).toBe(true);
      expect(result.accessLevel).toBe(AccessLevel.FULL);
    });
  });
});

// ==================== Test Helpers ====================

interface TestLicenseOptions {
  expiryDate: Date;
  graceEndDate: Date;
  status: string;
}

function createTestLicenseFile(options: TestLicenseOptions): string {
  const data = {
    licenseKey: 'TEST-1234-5678-ABCD',
    username: 'testuser',
    email: 'test@example.com',
    subscriptionType: 'YEARLY',
    startDate: addDays(new Date(), -30).toISOString(),
    expiryDate: options.expiryDate.toISOString(),
    graceEndDate: options.graceEndDate.toISOString(),
    gracePeriodDays: 7,
    status: options.status,
    premiumFeaturesEnabled: true,
    writeAccessEnabled: true,
    maxActivations: 1,
    generatedAt: new Date().toISOString(),
  };

  const jsonData = JSON.stringify(data);
  const encodedData = btoa(jsonData);
  const signature = generateSignature(encodedData);

  return `${encodedData}.${signature}`;
}

function generateSignature(data: string): string {
  const hash = CryptoJS.HmacSHA256(data, SIGNING_SECRET);
  return CryptoJS.enc.Base64url.stringify(hash);
}

function addDays(date: Date, days: number): Date {
  const result = new Date(date);
  result.setDate(result.getDate() + days);
  return result;
}
