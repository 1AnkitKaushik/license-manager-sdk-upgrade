/**
 * LicenseVault SDK for Next.js/React applications
 * 
 * Supports both online and offline license validation with grace period handling.
 * 
 * Installation:
 * npm install crypto-js
 * 
 * Usage:
 * ```typescript
 * import { LicenseVaultClient, AccessLevel } from './licensevault-sdk';
 * 
 * const client = new LicenseVaultClient({
 *   serverUrl: 'https://your-server.com/api',
 *   signingSecret: 'your-signing-secret'
 * });
 * 
 * const result = await client.validateLicense('XXXX-XXXX-XXXX-XXXX');
 * 
 * if (result.valid) {
 *   switch (result.accessLevel) {
 *     case AccessLevel.FULL: // All features enabled
 *     case AccessLevel.LIMITED: // Read-only, core features only (grace period)
 *     case AccessLevel.NONE: // No access
 *   }
 * }
 * ```
 */

import CryptoJS from 'crypto-js';

export enum AccessLevel {
  FULL = 'FULL',       // All features enabled
  LIMITED = 'LIMITED', // Read-only, core features only (during grace period)
  NONE = 'NONE'        // No access
}

export interface ValidationResult {
  valid: boolean;
  message: string;
  accessLevel: AccessLevel;
  licenseKey?: string;
  status?: string;
  subscriptionType?: string;
  startDate?: string;
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
  rawData?: Record<string, any>;
}

export interface LicenseVaultConfig {
  serverUrl: string;
  signingSecret: string;
  licenseFilePath?: string;
}

export class LicenseVaultClient {
  private serverUrl: string;
  private signingSecret: string;
  private licenseFileContent: string | null = null;

  constructor(config: LicenseVaultConfig) {
    this.serverUrl = config.serverUrl.replace(/\/$/, '');
    this.signingSecret = config.signingSecret;
  }

  /**
   * Set license file content for offline validation
   */
  setLicenseFileContent(content: string): void {
    this.licenseFileContent = content;
  }

  /**
   * Validate license - tries online first, falls back to offline
   */
  async validateLicense(licenseKey: string, machineId?: string): Promise<ValidationResult> {
    try {
      return await this.validateOnline(licenseKey, machineId);
    } catch (error) {
      console.log('Online validation failed, trying offline:', error);
      return this.validateOffline();
    }
  }

  /**
   * Force online validation
   */
  async validateOnline(licenseKey: string, machineId?: string): Promise<ValidationResult> {
    const response = await fetch(`${this.serverUrl}/license/validate`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        licenseKey,
        machineId: machineId || '',
      }),
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    const data = await response.json();
    return this.parseValidationResponse(data);
  }

  /**
   * Validate using stored offline license file content
   */
  validateOffline(): ValidationResult {
    if (!this.licenseFileContent) {
      return {
        valid: false,
        message: 'No license file content configured',
        accessLevel: AccessLevel.NONE,
      };
    }

    return this.validateLicenseFile(this.licenseFileContent);
  }

  /**
   * Validate license file content directly
   */
  validateLicenseFile(licenseFileContent: string): ValidationResult {
    try {
      const parts = licenseFileContent.trim().split('.');
      if (parts.length !== 2) {
        return {
          valid: false,
          message: 'Invalid license file format',
          accessLevel: AccessLevel.NONE,
        };
      }

      const [encodedData, providedSignature] = parts;

      // Verify signature
      const expectedSignature = this.generateSignature(encodedData);
      if (expectedSignature !== providedSignature) {
        return {
          valid: false,
          message: 'Invalid license signature - file may be tampered',
          accessLevel: AccessLevel.NONE,
        };
      }

      // Decode data
      const jsonData = atob(encodedData);
      const licenseData = JSON.parse(jsonData);

      // Parse dates
      const expiryDate = new Date(licenseData.expiryDate);
      const graceEndDate = new Date(licenseData.graceEndDate);
      const status = licenseData.status;

      // Check status
      if (status === 'REVOKED' || status === 'SUSPENDED') {
        return {
          valid: false,
          message: `License is ${status.toLowerCase()}`,
          accessLevel: AccessLevel.NONE,
          rawData: licenseData,
        };
      }

      const now = new Date();

      if (now < expiryDate) {
        // Active license
        return {
          valid: true,
          message: 'License is valid',
          accessLevel: AccessLevel.FULL,
          premiumFeaturesEnabled: licenseData.premiumFeaturesEnabled,
          writeAccessEnabled: licenseData.writeAccessEnabled,
          rawData: licenseData,
        };
      } else if (now < graceEndDate) {
        // Grace period
        const daysLeft = Math.ceil((graceEndDate.getTime() - now.getTime()) / (1000 * 60 * 60 * 24));
        return {
          valid: true,
          message: `License expired - Grace period active (${daysLeft} days remaining). Limited access only.`,
          accessLevel: AccessLevel.LIMITED,
          inGracePeriod: true,
          daysUntilGraceEnd: daysLeft,
          premiumFeaturesEnabled: false,
          writeAccessEnabled: false,
          rawData: licenseData,
        };
      } else {
        // Fully expired
        return {
          valid: false,
          message: 'License and grace period have expired',
          accessLevel: AccessLevel.NONE,
          rawData: licenseData,
        };
      }
    } catch (error) {
      return {
        valid: false,
        message: `Failed to validate license file: ${error}`,
        accessLevel: AccessLevel.NONE,
      };
    }
  }

  /**
   * Download license file for offline use (requires auth token)
   */
  async downloadLicenseFile(licenseKey: string, authToken: string): Promise<string> {
    const response = await fetch(`${this.serverUrl}/license/${licenseKey}/download`, {
      headers: {
        'Authorization': `Bearer ${authToken}`,
      },
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    const data = await response.json();
    return data.licenseFile;
  }

  private generateSignature(data: string): string {
    const hash = CryptoJS.HmacSHA256(data, this.signingSecret);
    return CryptoJS.enc.Base64url.stringify(hash);
  }

  private parseValidationResponse(data: Record<string, any>): ValidationResult {
    return {
      valid: data.valid || false,
      message: data.message || '',
      accessLevel: (data.accessLevel as AccessLevel) || AccessLevel.NONE,
      licenseKey: data.licenseKey,
      status: data.status,
      subscriptionType: data.subscriptionType,
      startDate: data.startDate,
      expiryDate: data.expiryDate,
      graceEndDate: data.graceEndDate,
      gracePeriodDays: data.gracePeriodDays,
      daysUntilExpiry: data.daysUntilExpiry,
      daysUntilGraceEnd: data.daysUntilGraceEnd,
      expiringSoon: data.expiringSoon,
      inGracePeriod: data.inGracePeriod,
      warningMessage: data.warningMessage,
      premiumFeaturesEnabled: data.premiumFeaturesEnabled || false,
      writeAccessEnabled: data.writeAccessEnabled || false,
      rawData: data,
    };
  }
}

/**
 * Helper functions for checking access
 */
export const canRead = (result: ValidationResult): boolean => {
  return result.valid && (result.accessLevel === AccessLevel.FULL || result.accessLevel === AccessLevel.LIMITED);
};

export const canWrite = (result: ValidationResult): boolean => {
  return result.valid && result.accessLevel === AccessLevel.FULL && (result.writeAccessEnabled ?? false);
};

export const canUsePremiumFeatures = (result: ValidationResult): boolean => {
  return result.valid && result.accessLevel === AccessLevel.FULL && (result.premiumFeaturesEnabled ?? false);
};

export default LicenseVaultClient;
