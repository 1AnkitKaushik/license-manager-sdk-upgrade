# License Service Management Platform

An enterprise-grade, containerized Spring Boot backend service deployed and orchestrated inside a localized Kubernetes cluster environment.

---

## 🛠️ Project Architecture & Layout

This repository is structured as a monorepo separating our core service logical execution layers:
* **/backend**: Standalone executable Spring Boot web server application (Tomcat runtime engine on Port 8001).
* **/k8s**: Declarative Kubernetes orchestration manifests for isolated deployment topologies.

---

## 🚀 DevOps & Containerization Workflow

### 1. Prerequisites
Ensure you have the following tools installed and active on your system:
* Docker Desktop (with Kubernetes enabled)
* kubectl CLI tool
* Postman (for API integration testing)

### 2. Multi-Stage Dockerization
The backend service utilizes a highly optimized multi-stage Dockerfile to guarantee small image footprints and maximum layer caching efficiency. To compile and build the local Docker image container, run:

docker build -t licensevault-backend:latest ./backend

### 3. Kubernetes Orchestration Deployment
Cluster resources are securely partitioned to prevent environmental configuration collisions.

# 1. Initialize the isolated cluster sandbox partition
kubectl apply -f k8s/namespace.yaml

# 2. Deploy application state controllers and routing layers
kubectl apply -f k8s/backend.yaml

# 3. Monitor rollout deployment health status
kubectl get pods -n licensevault -w

---

## 📡 API Verification & Testing Gateway

Due to localized network virtualization environments on Windows workstations, utilize direct secure cluster tunnel port-forwarding to execute external API validation loops cleanly:

kubectl port-forward deployment/licensevault-backend 8001:8001 -n licensevault

### Active Integration Test Parameters (Postman)
* Target Endpoint: POST http://localhost:8001/login
* Security Context: Standard Spring Security active filter chains intercept unauthenticated payloads, returning 403 Forbidden statuses natively until credentials/CSRF requirements are verified.