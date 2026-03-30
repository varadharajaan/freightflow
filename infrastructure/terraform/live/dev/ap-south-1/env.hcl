locals {
  project      = "freightflow"
  environment  = "dev"
  aws_region   = "ap-south-1"
  state_bucket = "freightflow-terraform-state-dev"
  lock_table   = "freightflow-terraform-locks-dev"

  network = {
    vpc_cidr             = "10.20.0.0/16"
    azs                  = ["ap-south-1a", "ap-south-1b", "ap-south-1c"]
    public_subnet_cidrs  = ["10.20.0.0/24", "10.20.1.0/24", "10.20.2.0/24"]
    private_subnet_cidrs = ["10.20.10.0/24", "10.20.11.0/24", "10.20.12.0/24"]
  }

  extra_tags = {
    CostCenter = "platform-dev"
  }
}
