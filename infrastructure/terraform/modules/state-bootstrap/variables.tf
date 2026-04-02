variable "state_bucket_name" {
  description = "S3 bucket name used for Terraform remote state."
  type        = string
}

variable "lock_table_name" {
  description = "DynamoDB table used for Terraform state locking."
  type        = string
}

variable "logging_bucket_name" {
  description = "S3 bucket name for storing access logs."
  type        = string
  default     = null
}

variable "kms_key_arn" {
  description = "ARN of KMS key for DynamoDB encryption. If null, AWS managed key is used."
  type        = string
  default     = null
}

variable "s3_kms_key_arn" {
  description = "ARN of KMS key for S3 server-side encryption. If null, AES256 is used."
  type        = string
  default     = null
}

variable "tags" {
  description = "Common tags applied to resources."
  type        = map(string)
  default     = {}
}
