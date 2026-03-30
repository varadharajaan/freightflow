output "cluster_arn" {
  description = "MSK cluster ARN."
  value       = aws_msk_cluster.this.arn
}

output "bootstrap_brokers_tls" {
  description = "TLS bootstrap brokers endpoint."
  value       = aws_msk_cluster.this.bootstrap_brokers_tls
}

output "security_group_id" {
  description = "MSK security group ID."
  value       = aws_security_group.this.id
}
