variable "name" {
  description = "EKS cluster name."
  type        = string
}

variable "kubernetes_version" {
  description = "EKS Kubernetes version."
  type        = string
  default     = "1.30"
}

variable "subnet_ids" {
  description = "Subnet IDs used by EKS control plane and nodes."
  type        = list(string)
}

variable "vpc_id" {
  description = "VPC ID for node security group."
  type        = string
}

variable "desired_size" {
  description = "Desired node count."
  type        = number
  default     = 3
}

variable "min_size" {
  description = "Minimum node count."
  type        = number
  default     = 2
}

variable "max_size" {
  description = "Maximum node count."
  type        = number
  default     = 10
}

variable "instance_types" {
  description = "Worker node instance types."
  type        = list(string)
  default     = ["t3.large"]
}

variable "endpoint_private_access" {
  description = "Enable private API endpoint access."
  type        = bool
  default     = true
}

variable "endpoint_public_access" {
  description = "Enable public API endpoint access."
  type        = bool
  default     = true
}

variable "public_access_cidrs" {
  description = "CIDR blocks that can access the public API endpoint. Restrict for production."
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "kms_key_arn" {
  description = "ARN of KMS key for encrypting Kubernetes secrets. If null, encryption config is omitted."
  type        = string
  default     = null
}

variable "enable_irsa" {
  description = "Enable OIDC provider for IRSA."
  type        = bool
  default     = true
}

variable "tags" {
  description = "Common tags applied to resources."
  type        = map(string)
  default     = {}
}
