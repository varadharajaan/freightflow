variable "name" {
  description = "Name prefix for MSK resources."
  type        = string
}

variable "kafka_version" {
  description = "Apache Kafka version."
  type        = string
  default     = "3.7.1"
}

variable "broker_instance_type" {
  description = "MSK broker instance type."
  type        = string
  default     = "kafka.m5.large"
}

variable "number_of_broker_nodes" {
  description = "Number of broker nodes (multiple of AZ count)."
  type        = number
  default     = 3
}

variable "subnet_ids" {
  description = "Private subnet IDs for brokers."
  type        = list(string)
}

variable "vpc_id" {
  description = "VPC ID."
  type        = string
}

variable "allowed_cidr_blocks" {
  description = "CIDRs allowed to connect to Kafka."
  type        = list(string)
  default     = []
}

variable "ebs_volume_size" {
  description = "Broker EBS volume size in GB."
  type        = number
  default     = 200
}

variable "kms_key_arn" {
  description = "ARN of KMS key for MSK at-rest encryption. If null, encryption_at_rest block is omitted."
  type        = string
  default     = null
}

variable "cloudwatch_log_group" {
  description = "CloudWatch log group name for MSK broker logs. If null, CloudWatch logging is disabled."
  type        = string
  default     = null
}

variable "tags" {
  description = "Common tags applied to resources."
  type        = map(string)
  default     = {}
}
