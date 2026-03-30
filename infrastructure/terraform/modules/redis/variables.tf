variable "name" {
  description = "Name prefix for Redis resources."
  type        = string
}

variable "node_type" {
  description = "ElastiCache node type."
  type        = string
  default     = "cache.t4g.medium"
}

variable "engine_version" {
  description = "Redis engine version."
  type        = string
  default     = "7.1"
}

variable "subnet_ids" {
  description = "Private subnet IDs for Redis."
  type        = list(string)
}

variable "vpc_id" {
  description = "VPC ID."
  type        = string
}

variable "allowed_cidr_blocks" {
  description = "CIDRs allowed to connect to Redis."
  type        = list(string)
  default     = []
}

variable "number_cache_clusters" {
  description = "Number of cache nodes."
  type        = number
  default     = 2
}

variable "at_rest_encryption_enabled" {
  description = "Enable at-rest encryption."
  type        = bool
  default     = true
}

variable "transit_encryption_enabled" {
  description = "Enable in-transit encryption."
  type        = bool
  default     = true
}

variable "tags" {
  description = "Common tags applied to resources."
  type        = map(string)
  default     = {}
}
