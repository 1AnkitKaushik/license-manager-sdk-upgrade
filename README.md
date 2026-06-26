# LicenseVault Java SDK

Java client library for high-performance license validation in Spring Boot and Java applications.

## Features

- **Automated Interception:** Global API route protection via Spring MVC handler interceptor layer.
- **High-Performance Caching:** Thread-safe memory cache with a 24-hour Time-To-Live (TTL) window to minimize I/O overhead.
- **Dual Validation Modes:** Online validation via centralized REST API with automatic fallback to cryptographically signed local license files.
- **Grace Period Support:** Granular capability tracking allowing structured read-only access during active grace windows.

## Requirements

- Java 17+
- Jackson Databind 2.15+
- Jakarta Servlet API (for Web MVC deployment)

---

## 🔄 Core Evolution: Baseline vs. Upgraded Architecture

| Technical Dimension | Baseline Implementation | Upgraded Architecture |
| :--- | :--- | :--- |
| **Enforcement Model** | **Passive/Manual:** Required explicit conditional guarding checks inside individual endpoint execution blocks. | **Automated:** Interceptor uniformly shields targeted API endpoints natively using standard configuration mapping rules. |
| **HTTP Boundary Error Handling** | Returned a blanket **403 Forbidden** status code for both dead licenses and feature-level restrictions. | Structural separation: **401 Unauthorized** issued at the gateway filter for dead/expired keys; **403 Forbidden** reserved for granular feature restrictions. |
| **System Overhead** | **High Context Cost:** Triggered an active network REST handshake or physical disk read on every validation invocation. | **Negligible:** Thread-safe memory lookups serve verification states instantly, reducing remote handshakes to **once every 24 hours** per key. |
| **Cache Eviction Lifecycle** | Required manual service orchestration or application restarts to re-evaluate state configurations. | **Instant Eviction:** Exposes `client.forceRevalidate()`, allowing active subscription renewals to flush stale cache windows instantly. |

---

## 🛠️ Architectural Component Breakdown

* **LicenseAccessInterceptor:** Intercepts incoming HTTP traffic before it reaches downstream REST controllers. It evaluates the current license context dynamically and enforces a 401 Unauthorized block if access level evaluates to `AccessLevel.NONE`. Valid states are attached to the request attributes context so downstream layers skip redundant processing.
* **LicenseValidationCache:** Eliminates high-frequency network and local file I/O operations by storing verification statuses within a thread-safe `ConcurrentHashMap`. Entries automatically expire after a 24-hour window.
* **LicenseConfigurationException:** A runtime guardrail that alerts developers at startup if the interceptor framework is initialized without providing an active license key or a valid path pointing to a local license file backup.

---

## 🔧 Integration Example

To activate the automated gateway enforcement, register the interceptor within your application's standard `WebMvcConfigurer` configuration:

```java
@Configuration
class LicenseWebConfig implements WebMvcConfigurer {

    private final LicenseManager licenseManager;

    LicenseWebConfig(LicenseManager licenseManager) {
        this.licenseManager = licenseManager;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(
            new LicenseAccessInterceptor(licenseManager.getClient(), licenseManager::getLicenseKey)
        ).addPathPatterns("/api/**")
         .excludePathPatterns("/api/license/status", "/api/license/refresh");
    }
}