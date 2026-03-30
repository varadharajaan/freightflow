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
  source = "../../../../modules/rds-postgres"
}

inputs = merge(include.root.inputs, {
  name                    = "${local.env.locals.project}-${local.env.locals.environment}"
  db_name                 = "freightflow"
  vpc_id                  = dependency.network.outputs.vpc_id
  subnet_ids              = dependency.network.outputs.private_subnet_ids
  allowed_cidr_blocks     = [dependency.network.outputs.vpc_cidr_block]
  instance_class          = "db.r6g.large"
  allocated_storage       = 200
  max_allocated_storage   = 1000
  backup_retention_period = 30
  multi_az                = true
  deletion_protection     = true
})
