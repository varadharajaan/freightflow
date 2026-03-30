output "state_bucket_name" {
  description = "State S3 bucket name."
  value       = aws_s3_bucket.state.bucket
}

output "lock_table_name" {
  description = "DynamoDB lock table name."
  value       = aws_dynamodb_table.lock.name
}
