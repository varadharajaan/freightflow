variable "name" {
  description = "Name prefix for ALB resources."
  type        = string
}

variable "vpc_id" {
  description = "VPC ID."
  type        = string
}

variable "public_subnet_ids" {
  description = "Public subnet IDs for ALB."
  type        = list(string)
}

variable "target_port" {
  description = "Backend target port."
  type        = number
  default     = 8080
}

variable "health_check_path" {
  description = "Target group health check path."
  type        = string
  default     = "/actuator/health"
}

variable "certificate_arn" {
  description = "Optional ACM certificate ARN for HTTPS listener."
  type        = string
  default     = null
}

variable "tags" {
  description = "Common tags applied to resources."
  type        = map(string)
  default     = {}
}
