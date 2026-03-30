include "root" {
  path = find_in_parent_folders("root.hcl")
}

locals {
  env = read_terragrunt_config(find_in_parent_folders("env.hcl"))
}

dependency "eks" {
  config_path = "../eks"
}

terraform {
  source = "../../../../modules/iam"
}

inputs = merge(include.root.inputs, {
  name_prefix       = "${local.env.locals.project}-${local.env.locals.environment}"
  oidc_provider_arn = dependency.eks.outputs.oidc_provider_arn
  oidc_issuer_url   = dependency.eks.outputs.oidc_issuer_url
  irsa_bindings = {
    booking-service = {
      namespace       = "freightflow"
      service_account = "booking-service"
      policy_json = jsonencode({
        Version = "2012-10-17"
        Statement = [
          {
            Effect   = "Allow"
            Action   = ["secretsmanager:GetSecretValue"]
            Resource = "*"
          },
          {
            Effect = "Allow"
            Action = [
              "kafka-cluster:Connect",
              "kafka-cluster:DescribeCluster",
              "kafka-cluster:ReadData",
              "kafka-cluster:WriteData"
            ]
            Resource = "*"
          }
        ]
      })
    }
    api-gateway = {
      namespace       = "freightflow"
      service_account = "api-gateway"
      policy_json = jsonencode({
        Version = "2012-10-17"
        Statement = [
          {
            Effect   = "Allow"
            Action   = ["secretsmanager:GetSecretValue"]
            Resource = "*"
          }
        ]
      })
    }
  }
})
