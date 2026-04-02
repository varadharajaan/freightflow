variable "name" {
  description = "Name prefix for RDS resources."
  type        = string
}

variable "db_name" {
  description = "Initial database name."
  type        = string
}

variable "engine_version" {
  description = "PostgreSQL engine version."
  type        = string
  default     = "16.3"
}

variable "instance_class" {
  description = "RDS instance class."
  type        = string
  default     = "db.t4g.medium"
}

variable "allocated_storage" {
  description = "Allocated storage in GB."
  type        = number
  default     = 100
}

variable "max_allocated_storage" {
  description = "Maximum autoscaled storage in GB."
  type        = number
  default     = 500
}

variable "subnet_ids" {
  description = "Private subnet IDs for DB subnet group."
  type        = list(string)
}

variable "vpc_id" {
  description = "VPC ID."
  type        = string
}

variable "allowed_cidr_blocks" {
  description = "CIDRs allowed to connect to PostgreSQL."
  type        = list(string)
  default     = []
}

variable "port" {
  description = "Database port."
  type        = number
  default     = 5432
}

variable "backup_retention_period" {
  description = "Backup retention period in days."
  type        = number
  default     = 14
}

variable "multi_az" {
  description = "Enable multi-AZ deployment."
  type        = bool
  default     = true
}

variable "deletion_protection" {
  description = "Enable deletion protection."
  type        = bool
  default     = true
}

variable "performance_insights_kms_key_id" {
  description = "ARN of KMS key for Performance Insights encryption. If null, AWS managed key is used."
  type        = string
  default     = null
}

variable "tags" {
  description = "Common tags applied to resources."
  type        = map(string)
  default     = {}
}
