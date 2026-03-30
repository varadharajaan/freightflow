locals {
  env = read_terragrunt_config(find_in_parent_folders("env.hcl"))

  project      = local.env.locals.project
  environment  = local.env.locals.environment
  aws_region   = local.env.locals.aws_region
  state_bucket = local.env.locals.state_bucket
  lock_table   = local.env.locals.lock_table

  common_tags = merge(
    {
      Project     = local.project
      Environment = local.environment
      ManagedBy   = "terragrunt"
      Owner       = "platform-engineering"
    },
    try(local.env.locals.extra_tags, {})
  )
}

remote_state {
  backend = "s3"

  config = {
    bucket         = local.state_bucket
    key            = "${path_relative_to_include()}/terraform.tfstate"
    region         = local.aws_region
    encrypt        = true
    dynamodb_table = local.lock_table
  }

  generate = {
    path      = "backend.tf"
    if_exists = "overwrite"
  }
}

generate "provider" {
  path      = "provider.tf"
  if_exists = "overwrite"
  contents  = <<EOF
terraform {
  required_version = ">= 1.8.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.80"
    }
  }
}

provider "aws" {
  region = "${local.aws_region}"
  default_tags {
    tags = ${jsonencode(local.common_tags)}
  }
}
EOF
}

inputs = {
  tags = local.common_tags
}
