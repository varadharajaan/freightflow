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
  region = "ap-south-1"
}

module "state_bootstrap" {
  source = "../../modules/state-bootstrap"

  state_bucket_name = "freightflow-terraform-state-dev"
  lock_table_name   = "freightflow-terraform-locks-dev"
  tags = {
    Project     = "freightflow"
    Environment = "dev"
    ManagedBy   = "terraform"
  }
}
