include "root" {
  path = find_in_parent_folders("root.hcl")
}

locals {
  env = read_terragrunt_config(find_in_parent_folders("env.hcl"))
}

terraform {
  source = "../../../../modules/network"
}

inputs = merge(include.root.inputs, {
  name                 = "${local.env.locals.project}-${local.env.locals.environment}"
  vpc_cidr             = local.env.locals.network.vpc_cidr
  azs                  = local.env.locals.network.azs
  public_subnet_cidrs  = local.env.locals.network.public_subnet_cidrs
  private_subnet_cidrs = local.env.locals.network.private_subnet_cidrs
  cluster_name         = "${local.env.locals.project}-${local.env.locals.environment}-eks"
  enable_nat_gateway   = true
})
