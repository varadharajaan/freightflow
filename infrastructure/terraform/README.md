# FreightFlow Terraform (T14)

This directory contains reusable Terraform modules and Terragrunt live configurations
for AWS infrastructure used by FreightFlow.

## Scope

- VPC with public/private subnets across 3 AZs
- EKS cluster + managed node groups + IRSA OIDC
- RDS PostgreSQL
- MSK Kafka
- ElastiCache Redis
- ALB
- IAM roles with least-privilege defaults
- Secrets Manager
- Remote state bootstrap (S3 + DynamoDB lock table)
- Terragrunt DRY stack configuration
- tfsec baseline config

## Directory Layout

```text
infrastructure/terraform/
├── modules/
│   ├── alb/
│   ├── eks/
│   ├── msk/
│   ├── network/
│   ├── rds-postgres/
│   ├── redis/
│   ├── secrets/
│   └── state-bootstrap/
├── live/
│   ├── root.hcl
│   ├── dev/ap-south-1/
│   └── prod/ap-south-1/
└── .tfsec.yml
```

## Bootstrap Flow

1. Create remote state resources first (one-time):
   - Use `modules/state-bootstrap` with local state.
2. Deploy environment stacks via Terragrunt:
   - `live/dev/ap-south-1/*`
   - `live/prod/ap-south-1/*`

## Suggested Apply Order (per environment)

1. `network`
2. `eks`
3. `rds-postgres`
4. `msk`
5. `redis`
6. `secrets`
7. `alb`

## Terragrunt Usage

From an environment folder, for example:

```bash
cd infrastructure/terraform/live/dev/ap-south-1
terragrunt run-all plan
terragrunt run-all apply
```

## Security Checks

Run tfsec:

```bash
tfsec infrastructure/terraform
```

The baseline config is in [`infrastructure/terraform/.tfsec.yml`](C:/Users/vdamotharan/Desktop/freightflow/infrastructure/terraform/.tfsec.yml).
