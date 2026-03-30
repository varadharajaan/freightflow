output "secret_arns" {
  description = "Secret ARNs keyed by logical name."
  value = {
    for name, secret in aws_secretsmanager_secret.this : name => secret.arn
  }
}
