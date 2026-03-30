variable "name_prefix" {
  description = "Name prefix for IAM resources."
  type        = string
}

variable "oidc_provider_arn" {
  description = "IAM OIDC provider ARN from EKS module."
  type        = string
}

variable "oidc_issuer_url" {
  description = "OIDC issuer URL from EKS module."
  type        = string
}

variable "irsa_bindings" {
  description = "IRSA role bindings keyed by logical name."
  type = map(object({
    namespace       = string
    service_account = string
    policy_json     = string
  }))
}

variable "tags" {
  description = "Common tags applied to resources."
  type        = map(string)
  default     = {}
}
