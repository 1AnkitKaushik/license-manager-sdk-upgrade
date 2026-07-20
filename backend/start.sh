#!/bin/bash
cd /app/backend
export MONGO_URL="mongodb://localhost:27017"
export DB_NAME="license_management"
export CORS_ORIGINS="*"
export JWT_SECRET="LicenseManagementSystemSecretKey2024VeryLongAndSecureKeyForJWTSigning"
java -jar target/license-key-management-1.0.0.jar
