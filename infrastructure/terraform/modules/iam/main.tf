locals {
  oidc_issuer_hostpath = replace(var.oidc_issuer_url, "https://", "")
}

resource "aws_iam_role" "irsa" {
  for_each = var.irsa_bindings

  name = "${var.name_prefix}-${each.key}-irsa-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Federated = var.oidc_provider_arn
        }
        Action = "sts:AssumeRoleWithWebIdentity"
        Condition = {
          StringEquals = {
            "${local.oidc_issuer_hostpath}:aud" = "sts.amazonaws.com"
            "${local.oidc_issuer_hostpath}:sub" = "system:serviceaccount:${each.value.namespace}:${each.value.service_account}"
          }
        }
      }
    ]
  })

  tags = merge(var.tags, {
    Name = "${var.name_prefix}-${each.key}-irsa-role"
  })
}

resource "aws_iam_policy" "irsa" {
  for_each = var.irsa_bindings

  name   = "${var.name_prefix}-${each.key}-irsa-policy"
  policy = each.value.policy_json

  tags = merge(var.tags, {
    Name = "${var.name_prefix}-${each.key}-irsa-policy"
  })
}

resource "aws_iam_role_policy_attachment" "irsa" {
  for_each = var.irsa_bindings

  role       = aws_iam_role.irsa[each.key].name
  policy_arn = aws_iam_policy.irsa[each.key].arn
}
