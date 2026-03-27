# ADR-009: ArgoCD GitOps for Continuous Delivery

## Status

Accepted

## Date

2026-03-27

## Context

FreightFlow deploys 8+ microservices across multiple Kubernetes environments (dev, staging,
production). Each service has its own deployment cadence, configuration, and infrastructure
dependencies (databases, Kafka topics, Redis clusters). We need a continuous delivery strategy
that is auditable, reproducible, and scales across teams without introducing environment
drift.

### Requirements
- **Declarative deployments** — desired state of every environment is version-controlled
  and auditable
- **Multi-environment support** — dev, staging, and production with environment-specific
  configuration overrides
- **Self-healing** — if someone manually modifies a resource in the cluster, the system
  automatically reconciles back to the declared state
- **Progressive delivery** — canary and blue-green deployments with automated rollback on
  metric degradation
- **Multi-team scalability** — each service team manages their own deployments without a
  centralized release team bottleneck
- **Rollback** — instant rollback to any previous known-good state via a Git revert

### Options Considered
1. **Push-Based CD (Jenkins / GitHub Actions deploying directly)** - CI pipeline runs
   `kubectl apply` or `helm upgrade` to push changes to the cluster. Simple to set up.
   However: the CI system needs cluster credentials (security risk), there is no continuous
   reconciliation (manual changes drift undetected), rollback requires re-running a pipeline
   (slow), and the CI system becomes a single point of failure for deployments. The cluster
   state and the Git state can diverge silently.
2. **Flux CD** - CNCF GitOps project. Pull-based reconciliation like ArgoCD. Strong Helm
   and Kustomize support. However, it lacks a built-in web UI for visualization, has a
   smaller ecosystem for progressive delivery (requires Flagger as a separate tool), and
   has less adoption momentum than ArgoCD.
3. **ArgoCD** - Declarative GitOps CD tool for Kubernetes. Pull-based reconciliation.
   Rich web UI showing sync status and resource tree. ApplicationSet for templatized
   multi-environment deployments. Native Kustomize, Helm, and plain manifest support.
   Argo Rollouts for progressive delivery. Large community, CNCF graduated project.
4. **Spinnaker** - Feature-rich CD platform by Netflix. Multi-cloud support. However,
   extremely complex to operate (multiple microservices of its own), resource-heavy, and
   not GitOps-native. Designed for a different era of deployment.

## Decision

We will use **ArgoCD** as the GitOps continuous delivery platform for all FreightFlow
Kubernetes deployments. Progressive delivery (canary, blue-green) will be handled by
**Argo Rollouts** integrated with ArgoCD.

### Why GitOps over Push-Based CD
- **Git as the single source of truth** — the Git repository IS the desired state. No
  question about what should be running; `git log` provides the complete deployment history.
- **Pull-based reconciliation** — ArgoCD runs inside the cluster and pulls desired state
  from Git. No need to expose cluster credentials to external CI systems.
- **Continuous drift detection** — ArgoCD continuously compares the live cluster state
  against the Git-declared state and alerts on (or auto-corrects) drift.
- **Instant rollback** — reverting a deployment is a `git revert` + push. ArgoCD detects
  the change and reconciles. No pipeline re-execution, no waiting.
- **Auditability** — every deployment is a Git commit with author, timestamp, review
  approval, and diff. Satisfies SOC 2 and ISO 27001 audit requirements.

### Repository Structure
We use a dedicated **deployment repository** (`freightflow-deployments`) separated from
application source code:

```
freightflow-deployments/
├── base/                          # Shared base manifests
│   ├── booking-service/
│   │   ├── deployment.yaml
│   │   ├── service.yaml
│   │   ├── hpa.yaml
│   │   └── kustomization.yaml
│   ├── tracking-service/
│   ├── billing-service/
│   └── ...
├── overlays/                      # Environment-specific overrides
│   ├── dev/
│   │   ├── booking-service/
│   │   │   ├── kustomization.yaml  # image tag, replica count, resource limits
│   │   │   └── config-patch.yaml
│   │   └── ...
│   ├── staging/
│   └── production/
├── applicationsets/               # ArgoCD ApplicationSet templates
│   ├── all-services.yaml
│   └── infrastructure.yaml
└── argo-rollouts/                 # Rollout strategies
    ├── canary-strategy.yaml
    └── bluegreen-strategy.yaml
```

- **Kustomize** for manifest management — base + overlays pattern for DRY configuration.
- Application source repos contain `Dockerfile` and CI pipelines. On successful CI build,
  the pipeline updates the image tag in the deployment repo (via automated PR or direct
  commit to a dev branch).

### ApplicationSet for Multi-Environment Deployments
ArgoCD `ApplicationSet` automatically generates ArgoCD `Application` resources for each
service × environment combination, eliminating repetitive Application definitions:

```yaml
apiVersion: argoproj.io/v1alpha1
kind: ApplicationSet
metadata:
  name: freightflow-services
  namespace: argocd
spec:
  goTemplate: true
  generators:
    - matrix:
        generators:
          - git:
              repoURL: https://github.com/freightflow/freightflow-deployments
              revision: HEAD
              directories:
                - path: overlays/*/booking-service
                - path: overlays/*/tracking-service
                - path: overlays/*/billing-service
                - path: overlays/*/notification-service
                - path: overlays/*/route-optimization-service
          - list:
              elements: []  # Matrix populated by git generator paths
  template:
    metadata:
      name: '{{.path.basename}}-{{index .path.segments 1}}'
    spec:
      project: freightflow
      source:
        repoURL: https://github.com/freightflow/freightflow-deployments
        targetRevision: HEAD
        path: '{{.path.path}}'
      destination:
        server: https://kubernetes.default.svc
        namespace: 'freightflow-{{index .path.segments 1}}'
      syncPolicy:
        automated:
          prune: true
          selfHeal: true
        syncOptions:
          - CreateNamespace=true
          - ApplyOutOfSyncOnly=true
        retry:
          limit: 3
          backoff:
            duration: 5s
            factor: 2
            maxDuration: 1m
```

### Sync Policies by Environment

| Environment | Auto-Sync | Self-Heal | Prune | Approval Required |
|-------------|-----------|-----------|-------|-------------------|
| Dev         | Yes       | Yes       | Yes   | No                |
| Staging     | Yes       | Yes       | Yes   | No                |
| Production  | No        | Yes       | No    | Yes (manual sync) |

- **Dev and Staging**: Fully automated — commits to the deployment repo automatically sync.
- **Production**: Manual sync trigger required. Self-heal is enabled to prevent drift, but
  new deployments require an explicit sync action in the ArgoCD UI or CLI (with RBAC
  restricting who can trigger production syncs).

### Argo Rollouts for Progressive Delivery
`Argo Rollouts` replaces standard Kubernetes `Deployment` with a `Rollout` resource that
supports canary and blue-green strategies:

```yaml
apiVersion: argoproj.io/v1alpha1
kind: Rollout
metadata:
  name: booking-service
spec:
  replicas: 5
  strategy:
    canary:
      canaryService: booking-service-canary
      stableService: booking-service-stable
      trafficRouting:
        istio:
          virtualServices:
            - name: booking-service
              routes:
                - primary
      steps:
        - setWeight: 10
        - pause: { duration: 5m }
        - analysis:
            templates:
              - templateName: success-rate
            args:
              - name: service-name
                value: booking-service
        - setWeight: 30
        - pause: { duration: 5m }
        - analysis:
            templates:
              - templateName: success-rate
        - setWeight: 60
        - pause: { duration: 5m }
        - setWeight: 100
  revisionHistoryLimit: 3
---
apiVersion: argoproj.io/v1alpha1
kind: AnalysisTemplate
metadata:
  name: success-rate
spec:
  metrics:
    - name: success-rate
      interval: 60s
      count: 5
      successCondition: result[0] >= 0.99
      failureLimit: 2
      provider:
        prometheus:
          address: http://prometheus.monitoring:9090
          query: |
            sum(rate(istio_requests_total{
              destination_service_name="{{args.service-name}}",
              response_code!~"5.*"
            }[2m])) /
            sum(rate(istio_requests_total{
              destination_service_name="{{args.service-name}}"
            }[2m]))
```

- **Canary progression**: 10% → 30% → 60% → 100% with 5-minute bake periods.
- **Automated analysis**: Prometheus queries validate that the canary maintains ≥99%
  success rate at each step.
- **Automatic rollback**: If the analysis fails (success rate < 99%), the rollout
  automatically reverts to the stable version.
- **Istio integration**: Traffic splitting is managed via Istio VirtualService (see ADR-008),
  providing precise, weighted traffic control.

### CI/CD Integration Flow
```
[Developer Push] → [GitHub Actions CI] → Build → Test → Push Image
                                                           ↓
                                        Update image tag in freightflow-deployments repo
                                                           ↓
                                        [ArgoCD detects Git change]
                                                           ↓
                                        [Auto-sync dev/staging] or [Manual sync production]
                                                           ↓
                                        [Argo Rollouts canary progression]
                                                           ↓
                                        [Prometheus analysis gates]
                                                           ↓
                                        [Full rollout or automatic rollback]
```

## Consequences

### Positive
- Git is the single source of truth — complete deployment audit trail via commit history
- Pull-based model eliminates the need to expose cluster credentials to CI systems
- Continuous drift detection and self-healing prevent configuration drift across environments
- ApplicationSet scales effortlessly as new services are added — just add a directory
- Argo Rollouts with Prometheus analysis gates provide safe, automated progressive delivery
- Instant rollback via `git revert` — no pipeline re-execution required
- Rich ArgoCD web UI provides real-time visibility into sync status and resource health
- Satisfies compliance requirements (SOC 2, ISO 27001) for deployment auditability

### Negative
- Requires a separate deployment repository and a process for updating image tags
- ArgoCD itself is a critical system that must be highly available and monitored
- ApplicationSet complexity grows with many generators and template parameters
- Argo Rollouts requires replacing `Deployment` resources with `Rollout` — not a drop-in
- Learning curve for teams unfamiliar with Kustomize overlays and ArgoCD concepts
- Secret management requires additional tooling (Sealed Secrets, External Secrets Operator,
  or SOPS) since secrets should not be stored in Git plaintext

### Mitigations
- Automate image tag updates via a CI pipeline step that creates a PR or commits to the
  deployment repo (using a bot account with minimal permissions)
- Deploy ArgoCD in HA mode (3 replicas of `argocd-server`, `argocd-repo-server`,
  `argocd-application-controller`) with Redis sentinel for caching
- Provide Kustomize and ArgoCD training sessions for all service teams
- Use **External Secrets Operator** to sync secrets from AWS Secrets Manager / HashiCorp
  Vault into Kubernetes, keeping secrets out of Git entirely
- Establish a `freightflow-deployment-template` repository that teams clone when creating
  a new service, pre-configured with Kustomize base, overlays, and ArgoCD Application

## References
- [ArgoCD Documentation](https://argo-cd.readthedocs.io/en/stable/)
- [Argo Rollouts Documentation](https://argo-rollouts.readthedocs.io/en/stable/)
- [ArgoCD ApplicationSet](https://argo-cd.readthedocs.io/en/stable/operator-manual/applicationset/)
- [GitOps Principles - OpenGitOps](https://opengitops.dev/)
- [Weaveworks - Guide to GitOps](https://www.weave.works/technologies/gitops/)
- [Kustomize Documentation](https://kustomize.io/)
