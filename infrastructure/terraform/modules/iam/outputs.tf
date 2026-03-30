output "irsa_role_arns" {
  description = "IRSA role ARNs keyed by logical name."
  value = {
    for key, role in aws_iam_role.irsa : key => role.arn
  }
}

output "irsa_policy_arns" {
  description = "IRSA policy ARNs keyed by logical name."
  value = {
    for key, policy in aws_iam_policy.irsa : key => policy.arn
  }
}
