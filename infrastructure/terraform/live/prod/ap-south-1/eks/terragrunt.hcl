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
  name                 = "${local.env.locals.project}-${local.env.locals.environment}-eks"
  kubernetes_version   = "1.30"
  vpc_id               = dependency.network.outputs.vpc_id
  subnet_ids           = dependency.network.outputs.private_subnet_ids
  desired_size         = 6
  min_size             = 3
  max_size             = 15
  instance_types       = ["m6i.large"]
  endpoint_public_access = true
  endpoint_private_access = true
  enable_irsa          = true
})
