variable "name_prefix" {
  description = "Prefix for Secrets Manager secret names."
  type        = string
}

variable "secrets" {
  description = "Map of secret definitions keyed by logical name."
  type = map(object({
    description   = optional(string, "")
    secret_string = string
    kms_key_id    = optional(string)
    recovery_days = optional(number, 7)
  }))
}

variable "tags" {
  description = "Common tags applied to resources."
  type        = map(string)
  default     = {}
}
