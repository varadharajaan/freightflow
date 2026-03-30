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
  source = "../../../../modules/redis"
}

inputs = merge(include.root.inputs, {
  name                  = "${local.env.locals.project}-${local.env.locals.environment}"
  vpc_id                = dependency.network.outputs.vpc_id
  subnet_ids            = dependency.network.outputs.private_subnet_ids
  allowed_cidr_blocks   = [dependency.network.outputs.vpc_cidr_block]
  node_type             = "cache.t4g.medium"
  number_cache_clusters = 2
})
