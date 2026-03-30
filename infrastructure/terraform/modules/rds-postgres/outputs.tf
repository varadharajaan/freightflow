output "db_instance_identifier" {
  description = "RDS instance identifier."
  value       = aws_db_instance.this.identifier
}

output "db_endpoint" {
  description = "RDS endpoint address."
  value       = aws_db_instance.this.address
}

output "db_port" {
  description = "RDS endpoint port."
  value       = aws_db_instance.this.port
}

output "db_name" {
  description = "Database name."
  value       = aws_db_instance.this.db_name
}

output "db_security_group_id" {
  description = "Security group ID attached to RDS."
  value       = aws_security_group.this.id
}

output "master_user_secret_arn" {
  description = "Secrets Manager ARN containing managed master credentials."
  value       = aws_db_instance.this.master_user_secret[0].secret_arn
}
