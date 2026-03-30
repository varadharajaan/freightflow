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

  state_bucket_name = "freightflow-terraform-state-prod"
  lock_table_name   = "freightflow-terraform-locks-prod"
  tags = {
    Project     = "freightflow"
    Environment = "prod"
    ManagedBy   = "terraform"
  }
}
