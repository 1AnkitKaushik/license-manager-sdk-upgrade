package com.licensemanager.service;

import com.licensemanager.model.License;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService {
    
    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);
    
    private final JavaMailSender mailSender;
    
    @Value("${email.from}")
    private String fromEmail;
    
    @Value("${email.enabled}")
    private boolean emailEnabled;
    
    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }
    
    public void sendLicenseExpiryWarning(License license) {
        if (!emailEnabled) {
            logger.info("Email disabled. Would send expiry warning to: {}", license.getEmail());
            return;
        }
        
        String subject = "License Expiring Soon - " + license.getLicenseKey();
        String body = buildExpiryWarningEmail(license);
        
        sendEmail(license.getEmail(), subject, body);
    }
    
    public void sendGracePeriodNotification(License license) {
        if (!emailEnabled) {
            logger.info("Email disabled. Would send grace period notification to: {}", license.getEmail());
            return;
        }
        
        String subject = "URGENT: License Expired - Grace Period Active - " + license.getLicenseKey();
        String body = buildGracePeriodEmail(license);
        
        sendEmail(license.getEmail(), subject, body);
    }
    
    public void sendLicenseExpiredNotification(License license) {
        if (!emailEnabled) {
            logger.info("Email disabled. Would send expired notification to: {}", license.getEmail());
            return;
        }
        
        String subject = "License Expired - Access Revoked - " + license.getLicenseKey();
        String body = buildExpiredEmail(license);
        
        sendEmail(license.getEmail(), subject, body);
    }
    
    private void sendEmail(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            
            mailSender.send(message);
            logger.info("Email sent successfully to: {}", to);
        } catch (MessagingException e) {
            logger.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }
    
    private String buildExpiryWarningEmail(License license) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { font-family: 'Segoe UI', Arial, sans-serif; background-color: #0f172a; color: #e2e8f0; margin: 0; padding: 20px; }
                    .container { max-width: 600px; margin: 0 auto; background-color: #1e293b; border-radius: 16px; padding: 32px; }
                    .header { text-align: center; margin-bottom: 24px; }
                    .logo { font-size: 28px; font-weight: bold; color: #10b981; }
                    .warning-box { background: linear-gradient(135deg, #f59e0b20, #f59e0b10); border: 1px solid #f59e0b40; border-radius: 12px; padding: 20px; margin: 20px 0; }
                    .license-key { font-family: monospace; background-color: #10b98120; color: #10b981; padding: 8px 16px; border-radius: 8px; display: inline-block; }
                    .btn { display: inline-block; background: linear-gradient(135deg, #10b981, #06b6d4); color: white; text-decoration: none; padding: 12px 24px; border-radius: 8px; font-weight: 600; margin-top: 16px; }
                    .footer { text-align: center; margin-top: 32px; color: #64748b; font-size: 14px; }
                    .details { background-color: #0f172a; border-radius: 8px; padding: 16px; margin: 16px 0; }
                    .detail-row { display: flex; justify-content: space-between; padding: 8px 0; border-bottom: 1px solid #334155; }
                    .detail-row:last-child { border-bottom: none; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <div class="logo">🔐 LicenseVault</div>
                    </div>
                    
                    <h2 style="color: #f59e0b;">⚠️ License Expiring Soon</h2>
                    
                    <p>Hello <strong>%s</strong>,</p>
                    
                    <p>Your license is expiring soon. Please renew to avoid service interruption.</p>
                    
                    <div class="warning-box">
                        <p style="margin: 0; color: #f59e0b; font-weight: 600;">
                            Your license will expire in <strong>%d days</strong>
                        </p>
                    </div>
                    
                    <div class="details">
                        <div class="detail-row">
                            <span>License Key:</span>
                            <span class="license-key">%s</span>
                        </div>
                        <div class="detail-row">
                            <span>Subscription:</span>
                            <span>%s</span>
                        </div>
                        <div class="detail-row">
                            <span>Expiry Date:</span>
                            <span>%s</span>
                        </div>
                    </div>
                    
                    <p>After expiry, you'll have a <strong>%d-day grace period</strong> with limited access (read-only, core features only).</p>
                    
                    <center>
                        <a href="#" class="btn">Renew Now</a>
                    </center>
                    
                    <div class="footer">
                        <p>© 2024 LicenseVault. All rights reserved.</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
                license.getUsername(),
                license.getDaysUntilExpiry(),
                license.getLicenseKey(),
                license.getSubscriptionType().name(),
                license.getExpiryDate().toLocalDate().toString(),
                license.getGracePeriodDays()
            );
    }
    
    private String buildGracePeriodEmail(License license) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { font-family: 'Segoe UI', Arial, sans-serif; background-color: #0f172a; color: #e2e8f0; margin: 0; padding: 20px; }
                    .container { max-width: 600px; margin: 0 auto; background-color: #1e293b; border-radius: 16px; padding: 32px; }
                    .header { text-align: center; margin-bottom: 24px; }
                    .logo { font-size: 28px; font-weight: bold; color: #10b981; }
                    .danger-box { background: linear-gradient(135deg, #ef444420, #ef444410); border: 1px solid #ef444440; border-radius: 12px; padding: 20px; margin: 20px 0; }
                    .license-key { font-family: monospace; background-color: #10b98120; color: #10b981; padding: 8px 16px; border-radius: 8px; display: inline-block; }
                    .btn { display: inline-block; background: linear-gradient(135deg, #ef4444, #f97316); color: white; text-decoration: none; padding: 12px 24px; border-radius: 8px; font-weight: 600; margin-top: 16px; }
                    .footer { text-align: center; margin-top: 32px; color: #64748b; font-size: 14px; }
                    .details { background-color: #0f172a; border-radius: 8px; padding: 16px; margin: 16px 0; }
                    .detail-row { display: flex; justify-content: space-between; padding: 8px 0; border-bottom: 1px solid #334155; }
                    .restrictions { background-color: #f5920b20; border-radius: 8px; padding: 16px; margin: 16px 0; }
                    .restriction-item { padding: 4px 0; color: #f59e0b; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <div class="logo">🔐 LicenseVault</div>
                    </div>
                    
                    <h2 style="color: #ef4444;">🚨 URGENT: License Expired - Grace Period Active</h2>
                    
                    <p>Hello <strong>%s</strong>,</p>
                    
                    <p>Your license has expired and you are now in the <strong>grace period</strong>.</p>
                    
                    <div class="danger-box">
                        <p style="margin: 0; color: #ef4444; font-weight: 600;">
                            ⏰ Grace period ends in <strong>%d days</strong>
                        </p>
                        <p style="margin: 8px 0 0 0; color: #f87171; font-size: 14px;">
                            After this, all access will be revoked.
                        </p>
                    </div>
                    
                    <div class="restrictions">
                        <p style="margin: 0 0 12px 0; font-weight: 600; color: #f59e0b;">Current Restrictions:</p>
                        <div class="restriction-item">❌ Write operations disabled</div>
                        <div class="restriction-item">❌ Premium features disabled</div>
                        <div class="restriction-item">✅ Read-only access available</div>
                        <div class="restriction-item">✅ Core features available</div>
                    </div>
                    
                    <div class="details">
                        <div class="detail-row">
                            <span>License Key:</span>
                            <span class="license-key">%s</span>
                        </div>
                        <div class="detail-row">
                            <span>Expired On:</span>
                            <span style="color: #ef4444;">%s</span>
                        </div>
                        <div class="detail-row">
                            <span>Grace Period Ends:</span>
                            <span style="color: #f59e0b;">%s</span>
                        </div>
                    </div>
                    
                    <center>
                        <a href="#" class="btn">🔄 Renew Immediately</a>
                    </center>
                    
                    <div class="footer">
                        <p>© 2024 LicenseVault. All rights reserved.</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
                license.getUsername(),
                license.getDaysUntilGraceEnd(),
                license.getLicenseKey(),
                license.getExpiryDate().toLocalDate().toString(),
                license.getGraceEndDate().toLocalDate().toString()
            );
    }
    
    private String buildExpiredEmail(License license) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { font-family: 'Segoe UI', Arial, sans-serif; background-color: #0f172a; color: #e2e8f0; margin: 0; padding: 20px; }
                    .container { max-width: 600px; margin: 0 auto; background-color: #1e293b; border-radius: 16px; padding: 32px; }
                    .header { text-align: center; margin-bottom: 24px; }
                    .logo { font-size: 28px; font-weight: bold; color: #10b981; }
                    .expired-box { background: linear-gradient(135deg, #7f1d1d, #450a0a); border: 1px solid #ef4444; border-radius: 12px; padding: 20px; margin: 20px 0; text-align: center; }
                    .license-key { font-family: monospace; background-color: #ef444420; color: #ef4444; padding: 8px 16px; border-radius: 8px; display: inline-block; text-decoration: line-through; }
                    .btn { display: inline-block; background: linear-gradient(135deg, #10b981, #06b6d4); color: white; text-decoration: none; padding: 12px 24px; border-radius: 8px; font-weight: 600; margin-top: 16px; }
                    .footer { text-align: center; margin-top: 32px; color: #64748b; font-size: 14px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <div class="logo">🔐 LicenseVault</div>
                    </div>
                    
                    <h2 style="color: #ef4444;">🔒 License Expired - Access Revoked</h2>
                    
                    <p>Hello <strong>%s</strong>,</p>
                    
                    <div class="expired-box">
                        <p style="margin: 0; color: #fca5a5; font-size: 18px; font-weight: 600;">
                            Your license and grace period have ended
                        </p>
                        <p style="margin: 8px 0 0 0; color: #f87171;">
                            All access has been revoked.
                        </p>
                    </div>
                    
                    <p>Your license <span class="license-key">%s</span> is no longer valid.</p>
                    
                    <p>To restore access, please purchase a new subscription:</p>
                    
                    <center>
                        <a href="#" class="btn">Get New License</a>
                    </center>
                    
                    <div class="footer">
                        <p>© 2024 LicenseVault. All rights reserved.</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
                license.getUsername(),
                license.getLicenseKey()
            );
    }
}
