output "cluster_name" {
  description = "EKS cluster name."
  value       = aws_eks_cluster.this.name
}

output "cluster_endpoint" {
  description = "EKS API endpoint."
  value       = aws_eks_cluster.this.endpoint
}

output "cluster_ca_data" {
  description = "Base64 encoded cluster CA data."
  value       = aws_eks_cluster.this.certificate_authority[0].data
}

output "node_security_group_id" {
  description = "Security group ID used by EKS nodes."
  value       = aws_security_group.nodes.id
}

output "oidc_provider_arn" {
  description = "IAM OIDC provider ARN for IRSA."
  value       = var.enable_irsa ? aws_iam_openid_connect_provider.this[0].arn : null
}

output "oidc_issuer_url" {
  description = "OIDC issuer URL."
  value       = aws_eks_cluster.this.identity[0].oidc[0].issuer
}
