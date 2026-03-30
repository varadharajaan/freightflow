# FreightFlow — Kubernetes Deployment Guide

## Deployment Architecture

```
                          Internet
                             |
                    ┌────────┴────────┐
                    │  Istio Gateway   │  (port 80/443)
                    │  (Ingress)       │
                    └────────┬────────┘
                             |
                    ┌────────┴────────┐
                    │  API Gateway     │  (port 8080)
                    │  Spring Cloud GW │
                    └────────┬────────┘
                             |
              ┌──────────────┼──────────────┐
              |              |              |
     ┌────────┴───┐  ┌──────┴─────┐  ┌─────┴──────┐
     │  booking   │  │  tracking  │  │  billing   │
     │  :8081     │  │  :8082     │  │  :8083     │
     └────────────┘  └────────────┘  └────────────┘
              |              |              |
     ┌────────┴───┐  ┌──────┴─────┐  ┌─────┴──────┐
     │  vessel    │  │  customer  │  │notification│
     │  :8084     │  │  :8085     │  │  :8086     │
     └────────────┘  └────────────┘  └────────────┘
              |              |
     ┌────────┴───┐  ┌──────┴─────┐
     │  discovery │  │  config    │
     │  :8761     │  │  :8888     │
     └────────────┘  └────────────┘

All pod-to-pod traffic is encrypted via Istio mTLS (STRICT mode).
```

### Service Port Map

| Service | Container Port | K8s Service Port | Spring Profile |
|---|---|---|---|
| api-gateway | 8080 | 8080 | prod |
| booking-service | 8081 | 8081 | prod |
| tracking-service | 8082 | 8082 | prod |
| billing-service | 8083 | 8083 | prod |
| vessel-schedule-service | 8084 | 8084 | prod |
| customer-service | 8085 | 8085 | prod |
| notification-service | 8086 | 8086 | prod |
| discovery-server | 8761 | 8761 | prod |
| config-server | 8888 | 8888 | prod |

---

## Kustomize Overlays

FreightFlow uses [Kustomize](https://kustomize.io/) for environment-specific configuration layering.

### Base (shared across all environments)

```
infrastructure/kubernetes/base/
├── kustomization.yaml          # Aggregates all resources
├── namespace.yaml              # freightflow namespace
├── service-accounts.yaml       # per-service Kubernetes identities
├── network-policy.yaml         # NetworkPolicies (internal + Prometheus)
├── api-gateway.yaml            # Deployment + Service + HPA + PDB
├── booking-service.yaml
├── tracking-service.yaml
├── billing-service.yaml
├── vessel-schedule-service.yaml
├── customer-service.yaml
├── notification-service.yaml
├── discovery-server.yaml       # Single replica — no HPA
└── config-server.yaml          # Single replica — no HPA
```

### Dev Overlay

Applies reduced resource allocation for development clusters:

- **Replicas:** 1 per service (saves cluster resources)
- **CPU requests:** 100m (vs 250m base)
- **Memory requests:** 256Mi (vs 512Mi base)
- **HPA max:** 3 (vs 10 base)
- **Spring profile:** `dev`

```bash
kubectl apply -k infrastructure/kubernetes/overlays/dev/
```

### Prod Overlay

Applies production-grade resource allocation:

- **Replicas:** 3 per service (high availability)
- **CPU requests:** 500m (full allocation)
- **Memory requests:** 1Gi (full allocation)
- **HPA max:** 20 (handles traffic spikes)
- **PDB minAvailable:** 2 (ensures availability during rollouts)
- **Spring profile:** `prod`

```bash
kubectl apply -k infrastructure/kubernetes/overlays/prod/
```

---

## Helm Chart

The Helm chart provides a single deployment unit for the entire FreightFlow platform.

### Chart Structure

```
infrastructure/helm/freightflow/
├── Chart.yaml                  # Chart metadata (v0.1.0)
├── values.yaml                 # Default values (all services)
└── templates/
    ├── deployment.yaml         # Templated Deployment (iterates services)
    ├── serviceaccount.yaml     # Templated per-service ServiceAccounts
    ├── service.yaml            # Templated Service
    ├── hpa.yaml                # Templated HPA
    └── ingress.yaml            # NGINX Ingress -> api-gateway
```

### Install

```bash
# Install with default values
helm install freightflow infrastructure/helm/freightflow/ \
  --namespace freightflow --create-namespace

# Install with custom values
helm install freightflow infrastructure/helm/freightflow/ \
  --namespace freightflow --create-namespace \
  -f infrastructure/helm/freightflow/values-prod.yaml

# Install with inline overrides
helm install freightflow infrastructure/helm/freightflow/ \
  --namespace freightflow --create-namespace \
  --set global.imageTag=v1.2.3 \
  --set services.booking-service.replicas=5
```

### Upgrade

```bash
# Upgrade to a new image tag
helm upgrade freightflow infrastructure/helm/freightflow/ \
  --set global.imageTag=v1.3.0

# Upgrade with new values file
helm upgrade freightflow infrastructure/helm/freightflow/ \
  -f values-prod.yaml
```

### Rollback

```bash
# View release history
helm history freightflow

# Rollback to previous revision
helm rollback freightflow 1

# Rollback to a specific revision
helm rollback freightflow 3
```

### Uninstall

```bash
helm uninstall freightflow --namespace freightflow
```

---

## Istio Service Mesh

### mTLS (Mutual TLS)

All pod-to-pod communication within the `freightflow` namespace is automatically encrypted using Istio's strict mTLS mode.

**Configuration:** `infrastructure/istio/peer-authentication.yaml`

```yaml
# PeerAuthentication enforces STRICT mTLS
# Every sidecar proxy encrypts traffic — no application changes needed
spec:
  mtls:
    mode: STRICT
```

**What this means:**
- No plaintext traffic between services
- Certificate rotation is handled automatically by Istio
- External traffic must enter through the Istio Gateway
- Health checks from kubelet bypass mTLS (Istio handles this)

### Authorization Policies

Fine-grained access control at the mesh level, enforcing the inter-service communication map.

**Configuration:** `infrastructure/istio/authorization-policy.yaml`

| Source | Allowed Destinations | Reason |
|---|---|---|
| api-gateway | booking, tracking, billing, vessel, customer, notification | Routes client requests |
| booking-service | vessel-schedule-service, customer-service | Capacity check, customer validation |
| billing-service | customer-service | Contract pricing lookup |
| platform services (explicit principals) | discovery-server | Eureka registration |
| platform services (explicit principals) | config-server | Centralized configuration |
| monitoring namespace | ALL services (GET /actuator/*) | Prometheus scraping |
| notification-service | (none — pure Kafka consumer) | No outbound REST calls |

### Traffic Management

**VirtualService** (`infrastructure/istio/virtual-service.yaml`):
- Routes external traffic from Istio Gateway to api-gateway
- Canary deployment: 90% stable / 10% canary
- Automatic retries on 5xx errors (3 attempts, 10s per try)

**DestinationRules** (`infrastructure/istio/destination-rule.yaml`):
- Connection pooling: 100 max connections, 5s connect timeout
- Outlier detection: eject after 5 consecutive 5xx errors
- Load balancing: ROUND_ROBIN
- Subset definitions: `stable` (v1) and `canary` (v2) for traffic splitting

---

## Local Deployment (Minikube / Kind)

### Prerequisites

- [minikube](https://minikube.sigs.k8s.io/) or [kind](https://kind.sigs.k8s.io/)
- [kubectl](https://kubernetes.io/docs/tasks/tools/) 1.28+
- [Istio CLI](https://istio.io/latest/docs/setup/getting-started/) (optional, for mesh features)

### Minikube Setup

```bash
# Start minikube with sufficient resources
minikube start --cpus 4 --memory 8192 --driver docker

# Enable ingress addon
minikube addons enable ingress

# Install Istio (optional)
istioctl install --set profile=demo -y
kubectl label namespace freightflow istio-injection=enabled

# Build and load Docker images into minikube
eval $(minikube docker-env)
docker build -t ghcr.io/varadharajaan/freightflow/booking-service:latest -f booking-service/Dockerfile .
# ... repeat for each service

# Deploy with Kustomize (dev overlay)
kubectl apply -k infrastructure/kubernetes/overlays/dev/

# Verify deployment
kubectl get pods -n freightflow
kubectl get services -n freightflow

# Access via minikube tunnel
minikube tunnel
# API is available at http://localhost:8080/api/v1/bookings
```

### Kind Setup

```bash
# Create cluster with port mappings
cat <<EOF | kind create cluster --config=-
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
nodes:
  - role: control-plane
    extraPortMappings:
      - containerPort: 80
        hostPort: 80
        protocol: TCP
      - containerPort: 443
        hostPort: 443
        protocol: TCP
EOF

# Load images into Kind
kind load docker-image ghcr.io/varadharajaan/freightflow/booking-service:latest
# ... repeat for each service

# Deploy
kubectl apply -k infrastructure/kubernetes/overlays/dev/
```

---

## AWS EKS Deployment

### Prerequisites

- AWS CLI configured with appropriate IAM permissions
- [eksctl](https://eksctl.io/) installed
- ECR (Elastic Container Registry) for Docker images

### Create EKS Cluster

```bash
# Create cluster with managed node group
eksctl create cluster \
  --name freightflow \
  --region us-east-1 \
  --version 1.29 \
  --nodegroup-name standard-workers \
  --node-type t3.large \
  --nodes 3 \
  --nodes-min 2 \
  --nodes-max 10 \
  --managed

# Update kubeconfig
aws eks update-kubeconfig --name freightflow --region us-east-1
```

### Install Istio on EKS

```bash
# Install Istio with production profile
istioctl install --set profile=default -y

# Enable sidecar injection for freightflow namespace
kubectl label namespace freightflow istio-injection=enabled
```

### Deploy with Helm

```bash
# Create namespace
kubectl create namespace freightflow

# Create secrets (example — use AWS Secrets Manager in production)
kubectl create secret generic booking-db-credentials \
  --namespace freightflow \
  --from-literal=url='jdbc:postgresql://freightflow-rds.xxx.us-east-1.rds.amazonaws.com:5432/freightflow_booking' \
  --from-literal=username='freightflow' \
  --from-literal=password='<secure-password>'

# Create ConfigMaps
kubectl create configmap kafka-config \
  --namespace freightflow \
  --from-literal=bootstrap-servers='b-1.freightflow-msk.xxx.kafka.us-east-1.amazonaws.com:9092'

kubectl create configmap keycloak-config \
  --namespace freightflow \
  --from-literal=issuer-uri='https://auth.freightflow.com/realms/freightflow'

# Deploy using Helm
helm install freightflow infrastructure/helm/freightflow/ \
  --namespace freightflow \
  --set global.imageRegistry=123456789.dkr.ecr.us-east-1.amazonaws.com/freightflow \
  --set global.imageTag=v1.0.0 \
  --set ingress.host=api.freightflow.com \
  --set ingress.tls.enabled=true

# Apply Istio configuration
kubectl apply -f infrastructure/istio/
```

### Verify Deployment

```bash
# Check all pods are running
kubectl get pods -n freightflow -w

# Check services
kubectl get svc -n freightflow

# Check Istio proxy status
istioctl proxy-status

# Test health endpoint
kubectl port-forward svc/api-gateway 8080:8080 -n freightflow
curl http://localhost:8080/actuator/health
```

---

## Monitoring Integration

### Prometheus Annotations

Every FreightFlow service pod includes Prometheus scraping annotations:

```yaml
annotations:
  prometheus.io/scrape: "true"
  prometheus.io/port: "<service-port>"    # Matches containerPort
  prometheus.io/path: "/actuator/prometheus"
```

The `/actuator/prometheus` endpoint is listed in the security `PUBLIC_PATHS` array, so it requires no authentication.

### Grafana Dashboards

Pre-built dashboards are available in `infrastructure/monitoring/grafana/dashboards/`:
- JVM metrics (heap, threads, GC)
- HTTP request rates and latencies
- Kafka consumer lag
- Circuit breaker state (Resilience4j)

### Distributed Tracing (Jaeger)

All services export traces via OpenTelemetry to Jaeger:

```yaml
env:
  - name: OTEL_EXPORTER_OTLP_ENDPOINT
    value: "http://jaeger-collector:4318/v1/traces"
```

Access the Jaeger UI to trace requests across the entire service mesh.

---

## Health Check Verification

All health check endpoints are in the Spring Security `PUBLIC_PATHS`:

| Path | Used By | Authentication |
|---|---|---|
| `/actuator/health` | Kubernetes startupProbe | None (public) |
| `/actuator/health/liveness` | Kubernetes livenessProbe | None (public) |
| `/actuator/health/readiness` | Kubernetes readinessProbe | None (public) |
| `/actuator/health/**` | Wildcard — covers all health groups | None (public) |
| `/actuator/prometheus` | Prometheus scraping | None (public) |

This ensures Kubernetes probes and Prometheus scraping work without JWT tokens.
