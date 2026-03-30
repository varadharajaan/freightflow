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
  source = "../../../../modules/alb"
}

inputs = merge(include.root.inputs, {
  name              = "${local.env.locals.project}-${local.env.locals.environment}"
  vpc_id            = dependency.network.outputs.vpc_id
  public_subnet_ids = dependency.network.outputs.public_subnet_ids
  target_port       = 8080
  health_check_path = "/actuator/health"
})
