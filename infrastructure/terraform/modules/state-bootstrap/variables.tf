variable "state_bucket_name" {
  description = "S3 bucket name used for Terraform remote state."
  type        = string
}

variable "lock_table_name" {
  description = "DynamoDB table used for Terraform state locking."
  type        = string
}

variable "tags" {
  description = "Common tags applied to resources."
  type        = map(string)
  default     = {}
}
