# FreightFlow CI/CD and GitOps Architecture

## Overview

FreightFlow uses a fully automated CI/CD pipeline built on **GitHub Actions** for continuous integration,
**ArgoCD** for GitOps-based continuous delivery, and **Argo Rollouts** for progressive canary deployments
with Prometheus-based analysis gates.

---

## Pipeline Flow

```
Developer
    |
    v
Pull Request ──────────────────────────────────────────────────────────┐
    |                                                                  |
    v                                                                  |
GitHub Actions CI                                                      |
    |                                                                  |
    ├── Toolchain Verification (Java 21, Maven Wrapper)                |
    |                                                                  |
    ├── Build & Test                                                   |
    |       ├── Compile all modules (./mvnw compile)                   |
    |       ├── Unit Tests (./mvnw test)                               |
    |       └── Integration Tests + Verify (./mvnw verify)             |
    |              └── Postgres 16 service container                   |
    |                                                                  |
    ├── Code Quality (parallel)                Code Review             |
    |       ├── Architecture Tests             on Pull Request         |
    |       ├── Checkstyle                            |                |
    |       └── SpotBugs                              |                |
    |                                                  |               |
    ├── Security Scan (parallel)                       |               |
    |       └── OWASP Dependency Check                 |               |
    |                                                  v               |
    |                                          Merge to main ──────────┘
    |
    ├── Docker Build & Push (main/master only)
    |       ├── 9 services built in parallel (matrix strategy)
    |       ├── Push to ghcr.io/varadharajaan/freightflow/{service}
    |       └── Tags: {git-sha} + latest
    |
    └── Update K8s Manifests
            ├── Patch image tags in infrastructure/kubernetes/base/
            └── Git commit + push (triggers ArgoCD sync)
                    |
                    v
            ArgoCD detects manifest change
                    |
                    v
            Sync to Kubernetes cluster
                    |
                    v
            Argo Rollouts — Canary Strategy
                    |
                    ├── 10% traffic → pause 2 min → Prometheus analysis
                    ├── 30% traffic → pause 2 min → Prometheus analysis
                    ├── 60% traffic → pause 2 min → Prometheus analysis
                    └── 100% traffic (full promotion)
                            |
                            v
                    Deployment Complete
```

---

## GitOps Principles

FreightFlow follows four core GitOps principles:

1. **Declarative**: All infrastructure and application configuration is defined as YAML manifests
   stored in `infrastructure/kubernetes/` and `infrastructure/argocd/`.

2. **Version-Controlled**: Git is the single source of truth. Every change to the cluster state
   is tracked as a Git commit. Rollback = revert a commit.

3. **Pull-Based**: ArgoCD continuously watches the Git repository and pulls changes into the cluster.
   No `kubectl apply` commands in CI. No push-based deployment.

4. **Continuously Reconciled**: ArgoCD's self-heal ensures the live cluster state always matches
   the desired state in Git. Manual `kubectl` edits are automatically reverted.

---

## Services and Ports

| Service                  | Port | Image                                                           |
|--------------------------|------|-----------------------------------------------------------------|
| api-gateway              | 8080 | ghcr.io/varadharajaan/freightflow/api-gateway                   |
| booking-service          | 8081 | ghcr.io/varadharajaan/freightflow/booking-service               |
| tracking-service         | 8082 | ghcr.io/varadharajaan/freightflow/tracking-service              |
| billing-service          | 8083 | ghcr.io/varadharajaan/freightflow/billing-service               |
| vessel-schedule-service  | 8084 | ghcr.io/varadharajaan/freightflow/vessel-schedule-service       |
| customer-service         | 8085 | ghcr.io/varadharajaan/freightflow/customer-service              |
| notification-service     | 8086 | ghcr.io/varadharajaan/freightflow/notification-service          |
| discovery-server         | 8761 | ghcr.io/varadharajaan/freightflow/discovery-server              |
| config-server            | 8888 | ghcr.io/varadharajaan/freightflow/config-server                 |

---

## Repository Structure

```
infrastructure/
├── argocd/
│   ├── apps/
│   │   ├── freightflow-project.yaml      # ArgoCD AppProject (RBAC scope)
│   │   ├── booking-service.yaml          # ArgoCD Application per service
│   │   ├── tracking-service.yaml
│   │   ├── billing-service.yaml
│   │   ├── vessel-schedule-service.yaml
│   │   ├── customer-service.yaml
│   │   ├── notification-service.yaml
│   │   ├── api-gateway.yaml
│   │   ├── discovery-server.yaml
│   │   └── config-server.yaml
│   ├── appsets/
│   │   └── freightflow-appset.yaml       # ApplicationSet (dev/staging/prod)
│   └── rollouts/
│       ├── analysis-template.yaml        # Prometheus analysis gates
│       ├── booking-service-rollout.yaml   # Canary Rollout per service
│       ├── tracking-service-rollout.yaml
│       ├── billing-service-rollout.yaml
│       ├── vessel-schedule-service-rollout.yaml
│       ├── customer-service-rollout.yaml
│       ├── notification-service-rollout.yaml
│       └── api-gateway-rollout.yaml
├── kubernetes/
│   ├── base/                             # Kustomize base (all services)
│   │   ├── kustomization.yaml
│   │   ├── namespace.yaml
│   │   ├── booking-service.yaml
│   │   ├── tracking-service.yaml
│   │   ├── billing-service.yaml
│   │   ├── vessel-schedule-service.yaml
│   │   ├── customer-service.yaml
│   │   ├── notification-service.yaml
│   │   ├── api-gateway.yaml
│   │   ├── discovery-server.yaml
│   │   ├── config-server.yaml
│   │   └── network-policy.yaml
│   └── overlays/
│       ├── dev/kustomization.yaml        # Dev: 1 replica, low resources
│       ├── staging/kustomization.yaml    # Staging: 2 replicas, mid resources
│       └── prod/kustomization.yaml       # Prod: 3 replicas, full resources
└── helm/
    └── freightflow/
        └── values.yaml                   # Helm chart values (alternative)
```

---

## ArgoCD Application vs ApplicationSet

### ArgoCD Application

An `Application` is a single deployment unit — it maps **one source** (a Git path) to **one destination**
(a K8s namespace on a cluster). FreightFlow defines individual Applications in `infrastructure/argocd/apps/`
for the base deployment:

```yaml
spec:
  source:
    repoURL: https://github.com/varadharajaan/freightflow.git
    path: infrastructure/kubernetes/base     # Kustomize base
  destination:
    namespace: freightflow                   # Target namespace
```

Each Application watches the same Kustomize base. When the CI pipeline updates image tags in the base
manifests and pushes, ArgoCD detects the diff and syncs automatically.

### ArgoCD ApplicationSet

An `ApplicationSet` is a template that generates multiple Applications from a parameter list.
FreightFlow uses it to deploy the same manifests to multiple environments:

```yaml
generators:
  - list:
      elements:
        - { env: dev,     overlay: dev,     autoSync: "true"  }
        - { env: staging, overlay: staging, autoSync: "true"  }
        - { env: prod,    overlay: prod,    autoSync: "false" }
```

This generates three Applications:
- `freightflow-dev` → `infrastructure/kubernetes/overlays/dev` → namespace `freightflow-dev`
- `freightflow-staging` → `infrastructure/kubernetes/overlays/staging` → namespace `freightflow-staging`
- `freightflow-prod` → `infrastructure/kubernetes/overlays/prod` → namespace `freightflow-prod`

Dev and staging auto-sync on every commit. Production requires manual sync approval.

---

## Argo Rollouts Canary Strategy

### How It Works

When a new image tag is pushed to Git, ArgoCD syncs the Rollout resource. Argo Rollouts then
executes a progressive canary deployment instead of a standard rolling update:

```
Step 1: setWeight 10%   → 10% of traffic to new version
        pause 2 minutes → run Prometheus analysis
Step 2: setWeight 30%   → 30% of traffic to new version
        pause 2 minutes → run Prometheus analysis
Step 3: setWeight 60%   → 60% of traffic to new version
        pause 2 minutes → run Prometheus analysis
Step 4: setWeight 100%  → full promotion, old version scaled down
```

### Traffic Routing

Traffic splitting is handled by Istio VirtualServices. Each service has:
- A **stable** Service (`{service}-stable`) — receives production traffic
- A **canary** Service (`{service}-canary`) — receives canary traffic
- A **VirtualService** (`{service}-vs`) — controls the weight split

Argo Rollouts automatically patches the VirtualService weights at each step.

### Prometheus Analysis Gates

During each pause, Argo Rollouts runs analysis against Prometheus to validate the canary:

| Metric         | Query                                                       | Pass Condition         |
|----------------|-------------------------------------------------------------|------------------------|
| Success Rate   | HTTP 2xx/3xx/4xx rate vs total rate (5m window)             | >= 95%                 |
| P99 Latency    | `histogram_quantile(0.99, http_server_requests_seconds)`    | <= 2.0 seconds         |
| Error Rate     | HTTP 5xx rate vs total rate (5m window)                     | <= 5%                  |

These metrics come from **Spring Boot Micrometer** (`http_server_requests_seconds_count` and
`http_server_requests_seconds_bucket`), exposed at `/actuator/prometheus` on each service.

If any metric fails 3 consecutive times, the canary is **automatically rolled back**.

---

## How to Deploy a New Version

**Just merge to main.** The entire pipeline is automated:

1. Developer opens a Pull Request
2. CI runs: compile, test, quality checks, security scan
3. Reviewer approves and merges to `main`
4. CI builds Docker images for all 9 services, pushes to GHCR with the commit SHA tag
5. CI updates image tags in `infrastructure/kubernetes/base/*.yaml` and commits
6. ArgoCD detects the manifest change and syncs to the cluster
7. Argo Rollouts executes the canary strategy with Prometheus analysis
8. If analysis passes at all steps, the new version is fully promoted

No manual `kubectl`, `docker push`, or `helm upgrade` commands needed.

---

## How to Rollback

### Option 1: ArgoCD UI (Recommended)

1. Open the ArgoCD dashboard
2. Navigate to the Application (e.g., `booking-service`)
3. Click **History and Rollback**
4. Select a previous sync revision
5. Click **Rollback**

### Option 2: ArgoCD CLI

```bash
# List sync history
argocd app history booking-service

# Rollback to a specific revision
argocd app rollback booking-service <REVISION_NUMBER>
```

### Option 3: Git Revert (True GitOps)

```bash
# Revert the commit that updated image tags
git revert <COMMIT_SHA>
git push origin main
```

ArgoCD will detect the reverted manifest and sync back to the previous image.

### Option 4: Argo Rollouts Abort (During Canary)

```bash
# Abort an in-progress canary and rollback
kubectl argo rollouts abort booking-service -n freightflow

# Or manually promote if you want to skip remaining steps
kubectl argo rollouts promote booking-service -n freightflow
```

---

## Environment Promotion

FreightFlow uses a trunk-based development model with Kustomize overlays for environment
differentiation:

```
main branch
    |
    ├── infrastructure/kubernetes/overlays/dev/        → freightflow-dev   (auto-sync)
    ├── infrastructure/kubernetes/overlays/staging/     → freightflow-staging (auto-sync)
    └── infrastructure/kubernetes/overlays/prod/        → freightflow-prod  (manual sync)
```

### Promotion Flow

```
dev (auto-deploy on merge)
  │
  ├── Tests pass, canary analysis passes
  │
  v
staging (auto-deploy, production-like resources)
  │
  ├── QA validation, load testing
  │
  v
prod (manual sync required in ArgoCD)
  │
  └── Operator clicks "Sync" in ArgoCD UI or runs:
      argocd app sync freightflow-prod
```

### Environment Differences

| Setting          | Dev          | Staging       | Prod          |
|------------------|--------------|---------------|---------------|
| Replicas         | 1            | 2             | 3             |
| CPU Request      | 100m         | 250m          | 500m          |
| Memory Request   | 256Mi        | 512Mi         | 1Gi           |
| CPU Limit        | 500m         | 1000m         | 2000m         |
| Memory Limit     | 512Mi        | 1Gi           | 2Gi           |
| HPA Min          | 1            | 2             | 3             |
| HPA Max          | 3            | 8             | 20            |
| PDB MinAvailable | 1            | 1             | 2             |
| Spring Profile   | dev          | staging       | prod          |
| Sync Policy      | Automated    | Automated     | Manual        |

---

## Prerequisites

### Cluster Requirements

- Kubernetes 1.28+
- ArgoCD 2.9+ installed in `argocd` namespace
- Argo Rollouts controller installed
- Istio service mesh (for VirtualService traffic splitting)
- Prometheus (for analysis gates)
- GHCR credentials configured as image pull secrets

### Install ArgoCD

```bash
kubectl create namespace argocd
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
```

### Install Argo Rollouts

```bash
kubectl create namespace argo-rollouts
kubectl apply -n argo-rollouts -f https://github.com/argoproj/argo-rollouts/releases/latest/download/install.yaml
```

### Bootstrap the Platform

```bash
# 1. Apply the ArgoCD Project
kubectl apply -f infrastructure/argocd/apps/freightflow-project.yaml

# 2. Apply individual service Applications (base deployment)
kubectl apply -f infrastructure/argocd/apps/

# 3. Apply the multi-environment ApplicationSet
kubectl apply -f infrastructure/argocd/appsets/freightflow-appset.yaml

# 4. Apply analysis templates
kubectl apply -f infrastructure/argocd/rollouts/analysis-template.yaml

# 5. Apply Rollout manifests (replaces standard Deployments for canary)
kubectl apply -f infrastructure/argocd/rollouts/
```

---

## Monitoring the Pipeline

### ArgoCD Dashboard

```bash
# Port-forward to access the UI
kubectl port-forward svc/argocd-server -n argocd 8080:443

# Get the initial admin password
argocd admin initial-password -n argocd
```

### Argo Rollouts Dashboard

```bash
# Watch a rollout in real time
kubectl argo rollouts get rollout booking-service -n freightflow --watch

# Dashboard UI
kubectl argo rollouts dashboard -n freightflow
```

### Prometheus Metrics

All services expose Micrometer metrics at `/actuator/prometheus`:
- `http_server_requests_seconds_count` — request count by status, method, URI
- `http_server_requests_seconds_bucket` — request duration histogram
- `jvm_memory_used_bytes` — JVM memory usage
- `process_cpu_usage` — CPU usage
- `spring_kafka_listener_seconds_count` — Kafka consumer metrics
