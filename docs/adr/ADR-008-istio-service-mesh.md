# ADR-008: Istio Service Mesh for mTLS and Traffic Management

## Status

Accepted

## Date

2026-03-27

## Context

FreightFlow runs on Kubernetes with 8+ microservices communicating over the network. As the
platform handles sensitive freight data (customs declarations, financial records, personally
identifiable shipper information), we need to enforce encryption in transit, authenticate
service-to-service communication, and implement advanced traffic management without embedding
these concerns in application code.

### Requirements
- **Zero-trust networking** — all service-to-service communication must be encrypted (mTLS)
  and mutually authenticated
- **Traffic management** — canary deployments, traffic splitting, circuit breaking, retries,
  and timeouts at the infrastructure layer
- **Observability** — distributed tracing, metrics, and access logging for all mesh traffic
  without application code changes
- **Fault injection** — ability to inject failures (delays, HTTP errors) for chaos testing
  in staging environments
- **Authorization policies** — fine-grained control over which services can communicate
  (e.g., Notification service should not call Billing service directly)
- **Certificate management** — automatic rotation of mTLS certificates without manual
  intervention

### Options Considered
1. **No Service Mesh (application-level)** - Each service implements its own TLS, retry
   logic, circuit breaking, and tracing using libraries (Resilience4j, OpenTelemetry SDK).
   No infrastructure overhead. However, every team must correctly implement security and
   resilience patterns. Inconsistencies are inevitable. Certificate management is manual.
   No traffic splitting capability without custom code.
2. **Linkerd** - Lightweight service mesh with a small resource footprint. Written in Rust
   (data plane). Simple to install and operate. However, it has a smaller feature set than
   Istio — limited traffic management policies, no built-in fault injection, less granular
   authorization policies, and a smaller community/ecosystem.
3. **Istio** - Full-featured service mesh with Envoy proxy sidecars. Comprehensive traffic
   management (VirtualService, DestinationRule), security (mTLS, AuthorizationPolicy),
   and observability (metrics, traces, access logs). Large community, extensive documentation,
   and broad ecosystem integration. Higher resource overhead than Linkerd.
4. **Cilium Service Mesh** - eBPF-based mesh that avoids sidecar proxies. Lower latency
   and resource usage. However, it is newer, requires Linux kernel 5.10+, and has a less
   mature traffic management feature set compared to Istio.

## Decision

We will deploy **Istio** as the service mesh for FreightFlow's Kubernetes clusters to handle
mTLS, traffic management, observability, and chaos testing.

### Why Istio
- FreightFlow requires **advanced traffic management** (canary deployments, traffic mirroring,
  fault injection) that Linkerd and Cilium do not fully support.
- Istio's **AuthorizationPolicy** provides fine-grained service-to-service RBAC that maps
  directly to our bounded context communication rules.
- The **Envoy sidecar** proxy is the most battle-tested L7 proxy, used by Google, Lyft,
  and major cloud providers.
- Istio's **Gateway API** integration aligns with the Kubernetes-native networking evolution.

### mTLS Configuration
- **Strict mTLS** is enforced across the entire mesh:
  ```yaml
  apiVersion: security.istio.io/v1
  kind: PeerAuthentication
  metadata:
    name: default
    namespace: istio-system
  spec:
    mtls:
      mode: STRICT
  ```
- Istio's Citadel CA automatically provisions, distributes, and rotates X.509 certificates
  for every workload. Certificate lifetime is set to 24 hours with automatic renewal.
- No application code changes required — the Envoy sidecar transparently terminates and
  originates mTLS connections.

### Envoy Sidecar Configuration
- Sidecars are automatically injected via namespace label:
  ```yaml
  apiVersion: v1
  kind: Namespace
  metadata:
    name: freightflow
    labels:
      istio-injection: enabled
  ```
- **Resource limits** per sidecar:
  ```yaml
  apiVersion: install.istio.io/v1alpha1
  kind: IstioOperator
  spec:
    values:
      global:
        proxy:
          resources:
            requests:
              cpu: 50m
              memory: 64Mi
            limits:
              cpu: 200m
              memory: 256Mi
  ```
- **Sidecar scope** is restricted to limit the Envoy configuration pushed to each proxy,
  reducing memory usage and configuration propagation time:
  ```yaml
  apiVersion: networking.istio.io/v1
  kind: Sidecar
  metadata:
    name: booking-service
    namespace: freightflow
  spec:
    workloadSelector:
      labels:
        app: booking-service
    egress:
      - hosts:
          - "freightflow/*"           # All services in freightflow namespace
          - "istio-system/*"          # Istio control plane
          - "kafka.infra.svc.cluster.local"  # Kafka cluster
  ```

### Canary Deployments
Istio `VirtualService` and `DestinationRule` enable progressive traffic shifting for canary
releases without modifying application code or Kubernetes deployments:
```yaml
apiVersion: networking.istio.io/v1
kind: VirtualService
metadata:
  name: booking-service
spec:
  hosts:
    - booking-service
  http:
    - route:
        - destination:
            host: booking-service
            subset: stable
          weight: 90
        - destination:
            host: booking-service
            subset: canary
          weight: 10
---
apiVersion: networking.istio.io/v1
kind: DestinationRule
metadata:
  name: booking-service
spec:
  host: booking-service
  subsets:
    - name: stable
      labels:
        version: v1
    - name: canary
      labels:
        version: v2
  trafficPolicy:
    connectionPool:
      http:
        h2UpgradePolicy: DEFAULT
        maxRequestsPerConnection: 100
      tcp:
        maxConnections: 1000
    outlierDetection:
      consecutive5xxErrors: 5
      interval: 30s
      baseEjectionTime: 30s
```

### Fault Injection for Chaos Testing
Istio enables infrastructure-level fault injection in staging without modifying application
code:
```yaml
apiVersion: networking.istio.io/v1
kind: VirtualService
metadata:
  name: tracking-service-chaos
  namespace: freightflow-staging
spec:
  hosts:
    - tracking-service
  http:
    - fault:
        delay:
          percentage:
            value: 10
          fixedDelay: 3s
        abort:
          percentage:
            value: 5
          httpStatus: 503
      route:
        - destination:
            host: tracking-service
```
- **Delay injection** simulates slow downstream services to validate timeout/circuit breaker
  behavior.
- **Abort injection** simulates service failures to validate retry and fallback logic.
- Chaos tests are integrated into the CI/CD pipeline (staging gate) and run as part of
  regular resilience validation.

### Authorization Policies
Fine-grained service-to-service authorization enforces communication boundaries:
```yaml
apiVersion: security.istio.io/v1
kind: AuthorizationPolicy
metadata:
  name: billing-service-policy
  namespace: freightflow
spec:
  selector:
    matchLabels:
      app: billing-service
  rules:
    - from:
        - source:
            principals:
              - "cluster.local/ns/freightflow/sa/booking-service"
              - "cluster.local/ns/freightflow/sa/saga-orchestrator"
      to:
        - operation:
            methods: ["GET", "POST"]
            paths: ["/api/v1/billing/*"]
```
- Only Booking service and Saga Orchestrator can call Billing service.
- Notification service cannot access Billing — enforced at the mesh layer, not application.

### Observability
- **Metrics**: Istio automatically generates RED metrics (Rate, Errors, Duration) for every
  service-to-service call, exported to Prometheus.
- **Distributed Tracing**: Envoy propagates tracing headers (W3C Trace Context); traces
  collected by OpenTelemetry Collector and sent to Jaeger/Tempo.
- **Access Logs**: Envoy access logs capture full request/response metadata for debugging.

## Consequences

### Positive
- Zero-trust security — all traffic encrypted and mutually authenticated with no code changes
- Automatic certificate provisioning and rotation eliminates manual PKI management
- Canary deployments and traffic splitting enable safe, progressive rollouts
- Fault injection provides native chaos testing capability without third-party tools
- Fine-grained authorization policies enforce bounded context communication boundaries
- Rich observability (metrics, traces, access logs) for all mesh traffic out of the box
- Infrastructure-level retry, timeout, and circuit breaking reduces application-level
  resilience code

### Negative
- Resource overhead — each pod gets an Envoy sidecar consuming ~50-200m CPU and 64-256Mi
  memory
- Added latency — sidecar proxy adds ~1-3 ms per hop (acceptable for most use cases, but
  notable for latency-sensitive paths)
- Operational complexity — Istio control plane (istiod) is another critical component to
  monitor, upgrade, and troubleshoot
- Learning curve — Istio's configuration model (VirtualService, DestinationRule, Gateway,
  AuthorizationPolicy) is extensive
- Debugging is harder — network issues may be in the application, the sidecar, or the
  mesh configuration
- Istio upgrades require careful planning (control plane + data plane version skew)

### Mitigations
- Use `Sidecar` resources to scope Envoy configuration per workload, reducing memory
  footprint and configuration propagation time
- For latency-critical internal paths (e.g., Tracking telemetry ingestion), evaluate
  bypassing the sidecar with `traffic.sidecar.istio.io/excludeOutboundPorts` annotation
  as a measured exception
- Deploy Istio using the `IstioOperator` CRD with GitOps (ArgoCD — see ADR-009) for
  reproducible, auditable mesh configuration
- Establish an Istio runbook covering common debugging scenarios (503 errors, mTLS failures,
  configuration propagation delays)
- Use Kiali for visual service mesh topology and configuration validation
- Implement canary upgrades of Istio itself using revision-based installation
  (`istioctl install --revision`)

## References
- [Istio Documentation](https://istio.io/latest/docs/)
- [Envoy Proxy](https://www.envoyproxy.io/)
- [Istio Security - mTLS](https://istio.io/latest/docs/concepts/security/)
- [Istio Traffic Management](https://istio.io/latest/docs/concepts/traffic-management/)
- [Chaos Engineering with Istio](https://istio.io/latest/docs/tasks/traffic-management/fault-injection/)
- [Kiali - Service Mesh Observability](https://kiali.io/)
