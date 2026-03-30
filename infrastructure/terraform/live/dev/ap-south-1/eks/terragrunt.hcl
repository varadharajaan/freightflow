include "root" {
  path = find_in_parent_folders("root.hcl")
}

locals {
  env = read_terragrunt_config(find_in_parent_folders("env.hcl"))
}

dependency "network" {
  config_path = "../network"
}

terraform {
  source = "../../../../modules/eks"
}

inputs = merge(include.root.inputs, {
  name               = "${local.env.locals.project}-${local.env.locals.environment}-eks"
  kubernetes_version = "1.30"
  vpc_id             = dependency.network.outputs.vpc_id
  subnet_ids         = dependency.network.outputs.private_subnet_ids
  desired_size       = 3
  min_size           = 2
  max_size           = 6
  instance_types     = ["t3.large"]
  enable_irsa        = true
})
